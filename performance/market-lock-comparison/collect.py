import datetime
import json
import pathlib
import subprocess
import time
import urllib.request

root = pathlib.Path("/opt/fleaflea-perf/lock186")
while not (root / "STOP").exists():
    result = {"utc": datetime.datetime.now(datetime.timezone.utc).isoformat()}
    for node, port in [("a", 18081), ("b", 18083)]:
        try:
            with urllib.request.urlopen(
                f"http://127.0.0.1:{port}/actuator/prometheus", timeout=3
            ) as response:
                metrics = response.read().decode()
            (root / "results" / f"latest-{node}.prom").write_text(metrics)
            result[node] = [
                line
                for line in metrics.splitlines()
                if not line.startswith("#")
                and line.startswith(
                    (
                        "market_",
                        "redis_lock",
                        "hikaricp_connections_pending",
                        "jvm_memory_used_bytes",
                        "process_cpu_usage",
                    )
                )
            ]
        except Exception as error:
            result[node] = {"error": type(error).__name__}
    stats = subprocess.run(
        ["sudo", "docker", "stats", "--no-stream", "--format", "{{json .}}"],
        capture_output=True,
        text=True,
        timeout=10,
    )
    result["docker"] = stats.stdout.splitlines()
    query = "SELECT wait_event_type,wait_event,count(*) FROM pg_stat_activity WHERE datname='fleaflea_db' GROUP BY 1,2;"
    result["dbWaits"] = subprocess.run(
        [
            "sudo",
            "docker",
            "compose",
            "exec",
            "-T",
            "postgres",
            "psql",
            "-U",
            "postgres",
            "-d",
            "fleaflea_db",
            "-At",
            "-c",
            query,
        ],
        cwd=root.parent,
        capture_output=True,
        text=True,
        timeout=5,
    ).stdout.splitlines()
    with (root / "results" / "samples.jsonl").open("a") as output:
        output.write(json.dumps(result) + "\n")
    time.sleep(3)
