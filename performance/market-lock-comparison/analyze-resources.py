"""Summarize resource samples inside each run's observed before/after metric window."""

import argparse
import csv
import datetime
import json
from pathlib import Path
import statistics

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("runs", type=Path)
parser.add_argument("server_results", type=Path)
parser.add_argument("runner_results", type=Path)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
samples = [
    json.loads(line) for line in (args.server_results / "samples.jsonl").read_text().splitlines()
]
rows = []
with args.runs.open() as source:
    for run in csv.DictReader(source):
        name = f'{run["variant"]}-{run["distribution"]}-{run["rate"]}-{run["repeat"]}'
        start = max(
            (args.server_results / f"{name}-before-{node}.prom").stat().st_mtime
            for node in ("a", "b")
        )
        recovery = args.server_results / f"{name}-recovery.json"
        if recovery.exists():
            end = datetime.datetime.fromisoformat(
                json.loads(recovery.read_text())["sampleUtc"]
            ).timestamp()
        else:
            end = min(
                (args.server_results / f"{name}-after-{node}.prom").stat().st_mtime
                for node in ("a", "b")
            )
        selected = [
            sample
            for sample in samples
            if start <= datetime.datetime.fromisoformat(sample["utc"]).timestamp() <= end
        ]
        app_cpu, app_memory, db_cpu, db_memory, lock_waiters = [], [], [], [], []
        pending_connections = []
        for sample in selected:
            for node in ("a", "b"):
                if isinstance(sample.get(node), list):
                    pending_connections.extend(
                        float(line.rsplit(" ", 1)[-1])
                        for line in sample[node]
                        if line.startswith("hikaricp_connections_pending{")
                    )
            for item in sample["docker"]:
                stats = json.loads(item)
                if "perf-app" in stats["Name"]:
                    app_cpu.append(float(stats["CPUPerc"].rstrip("%")))
                    app_memory.append(float(stats["MemPerc"].rstrip("%")))
                if "perf-postgres" in stats["Name"]:
                    db_cpu.append(float(stats["CPUPerc"].rstrip("%")))
                    db_memory.append(float(stats["MemPerc"].rstrip("%")))
            lock_waiters.append(
                sum(
                    int(wait.split("|")[-1])
                    for wait in sample["dbWaits"]
                    if wait.startswith("Lock|")
                )
            )
        runner_cpu = []
        for line in (args.runner_results / f"{name}-cpu.txt").read_text().splitlines():
            if " all " in line:
                try:
                    runner_cpu.append(100 - float(line.split()[-1]))
                except ValueError:
                    continue
        rows.append(
            {
                "name": name,
                "serverSamples": len(selected),
                "appCpuPeakOneCorePct": max(app_cpu, default=0),
                "appMemoryPeakLimitPct": max(app_memory, default=0),
                "dbCpuPeakOneCorePct": max(db_cpu, default=0),
                "dbMemoryPeakHostPct": max(db_memory, default=0),
                "runnerCpuMeanAllCoresPct": (
                    round(statistics.mean(runner_cpu), 3) if runner_cpu else ""
                ),
                "runnerCpuPeakAllCoresPct": max(runner_cpu, default=0),
                "observedLockWaitersMax": max(lock_waiters, default=0),
                "hikariPendingPeakPerNode": max(pending_connections, default=0),
            }
        )
with args.output.open("w") as output:
    writer = csv.DictWriter(output, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
print(f"Summarized resources for {len(rows)} measurements")
