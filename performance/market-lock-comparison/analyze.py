"""Summarize downloaded lock186 result directories without reading account tokens."""

import argparse
import csv
import json
from pathlib import Path
import statistics

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("results", type=Path)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
rows = []
for path in sorted(args.results.glob("*.json")):
    name = path.stem
    if name.endswith("-export") or "-warmup" in name or "-preservation" in name:
        continue
    parts = name.split("-")
    if len(parts) < 4 or parts[-2] not in ("20", "100"):
        continue
    variant = "-".join(parts[:-3])
    distribution, rate, repeat = parts[-3:]
    data = json.loads(path.read_text())
    metrics = data.get("metrics", {})

    def value(key, field):
        metric = metrics.get(key, {})
        return metric.get("values", metric).get(
            field, metric.get("value", 0) if field == "rate" else 0
        )

    rows.append(
        {
            "variant": variant,
            "distribution": distribution,
            "rate": int(rate),
            "repeat": repeat,
            "actualRps": value("http_reqs", "rate"),
            "writeRps": value("successful_write_requests", "rate"),
            "writes": value("successful_write_requests", "count"),
            "writeP95Ms": value("successful_write_duration", "p(95)"),
            "writeP99Ms": value("successful_write_duration", "p(99)"),
            "errorRate": value("http_req_failed", "rate"),
            "dropped": value("dropped_iterations", "count"),
            "busy409": value("lock_busy_responses", "count"),
            "unavailable503": value("service_unavailable_responses", "count"),
            "internal500": value("internal_server_error_responses", "count"),
        }
    )
args.output.mkdir(parents=True, exist_ok=True)
if not rows:
    raise SystemExit("No measurement JSON found")
with (args.output / "runs.csv").open("w") as output:
    writer = csv.DictWriter(output, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
lines = [
    "# 마켓 락 비교 측정값",
    "",
    "수정 지연은 성공 요청 기준이며, 각 셀은 반복 측정의 중앙값이다. 반복별 값은 runs.csv에 남긴다. 일반 조회는 정합성 실패 대조군이므로 지연만으로 우수하다고 판단하지 않는다.",
    "",
    "| 버전 | 분포 | 목표 RPS | 반복 | 실측 RPS | 수정/초 | 수정 p95 ms | 수정 p99 ms | 누락 합계 | 오류율 최대 |",
    "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
]
for key in sorted({(r["variant"], r["distribution"], r["rate"]) for r in rows}):
    group = [r for r in rows if (r["variant"], r["distribution"], r["rate"]) == key]
    med = lambda field: statistics.median(r[field] for r in group)
    lines.append(
        f'| {key[0]} | {key[1]} | {key[2]} | {len(group)} | {med("actualRps"):.2f} | {med("writeRps"):.2f} | {med("writeP95Ms"):.2f} | {med("writeP99Ms"):.2f} | {sum(r["dropped"] for r in group)} | {max(r["errorRate"] for r in group):.2%} |'
    )
(args.output / "RESULTS.md").write_text("\n".join(lines) + "\n")
print("Summarized " + str(len(rows)) + " measurements")
