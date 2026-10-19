"""Join per-run request counts with before/after counters from both test app nodes."""

import argparse
import csv
import datetime
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("runs", type=Path)
parser.add_argument("server_results", type=Path)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--recover-from-samples", type=Path)
args = parser.parse_args()


def parse_metrics(text):
    values = {}
    for line in text.splitlines():
        if line.startswith("#") or not line.strip():
            continue
        key, value = line.rsplit(" ", 1)
        name = key.split("{", 1)[0]
        values[name] = values.get(name, 0.0) + float(value)
    return values


def metrics(path):
    return parse_metrics(path.read_text())


def recover_after(name, expected):
    before_paths = [args.server_results / f"{name}-before-{node}.prom" for node in ("a", "b")]
    if not all(path.exists() for path in before_paths):
        return False
    before = [metrics(path) for path in before_paths]
    lower = max(path.stat().st_mtime for path in before_paths)
    for line in args.recover_from_samples.read_text().splitlines():
        sample = json.loads(line)
        timestamp = datetime.datetime.fromisoformat(sample["utc"]).timestamp()
        if not lower <= timestamp <= lower + 180:
            continue
        if not all(isinstance(sample.get(node), list) for node in ("a", "b")):
            continue
        after = [parse_metrics("\n".join(sample[node])) for node in ("a", "b")]
        count = sum(
            after[index].get("market_change_execution_seconds_count", 0)
            - before[index].get("market_change_execution_seconds_count", 0)
            for index in range(2)
        )
        if count != expected:
            continue
        for node in ("a", "b"):
            path = args.server_results / f"{name}-after-{node}.prom"
            if not path.exists():
                path.write_text(
                    "# Recovered from collect.py sample UTC "
                    + sample["utc"]
                    + "\n"
                    + "\n".join(sample[node])
                    + "\n"
                )
        (args.server_results / f"{name}-recovery.json").write_text(
            json.dumps(
                {
                    "sampleUtc": sample["utc"],
                    "expectedWrites": expected,
                    "windowSeconds": 180,
                    "source": "samples.jsonl",
                },
                indent=2,
            )
            + "\n"
        )
        return True
    return False


rows = []
with args.runs.open() as source:
    for run in csv.DictReader(source):
        name = f'{run["variant"]}-{run["distribution"]}-{run["rate"]}-{run["repeat"]}'
        recovered = (args.server_results / f"{name}-recovery.json").exists()
        if (
            not all(
                (args.server_results / f"{name}-after-{node}.prom").exists() for node in ("a", "b")
            )
            and args.recover_from_samples
        ):
            if run["variant"] == "redisson-db":
                raise SystemExit(
                    "Periodic samples lack Redis counters; cannot recover this variant"
                )
            recovered = recover_after(name, float(run["writes"]))
        deltas = []
        for node in ("a", "b"):
            before = metrics(args.server_results / f"{name}-before-{node}.prom")
            after = metrics(args.server_results / f"{name}-after-{node}.prom")
            deltas.append(
                {key: after.get(key, 0) - before.get(key, 0) for key in set(before) | set(after)}
            )

        def total(key):
            return sum(delta.get(key, 0) for delta in deltas)

        row = {
            "name": name,
            "httpSuccessfulWrites": run["writes"],
            "metricSource": "periodic sample" if recovered else "before/after snapshot",
        }
        for field, prefix in [
            ("change", "market_change_execution"),
            ("lookup", "market_locked_lookup"),
            ("redisAcquire", "redis_lock_acquire"),
            ("redisHold", "redis_lock_hold"),
        ]:
            count = total(prefix + "_seconds_count")
            row[field + "Count"] = count
            row[field + "MeanMs"] = (
                round(total(prefix + "_seconds_sum") / count * 1000, 4) if count > 0 else ""
            )
        row["nodeAChanges"] = deltas[0].get("market_change_execution_seconds_count", 0)
        row["nodeBChanges"] = deltas[1].get("market_change_execution_seconds_count", 0)
        row["countsMatch"] = row["changeCount"] == float(run["writes"])
        rows.append(row)
with args.output.open("w") as output:
    writer = csv.DictWriter(output, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
print(
    "Matched writes for "
    + str(sum(row["countsMatch"] for row in rows))
    + "/"
    + str(len(rows))
    + " runs"
)
if not all(row["countsMatch"] for row in rows):
    raise SystemExit("Request/counter mismatch; inspect before interpreting lock times")
