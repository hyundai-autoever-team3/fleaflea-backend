import json
import pathlib
import subprocess
import time

root = pathlib.Path("/opt/fleaflea-perf/lock186")
while not (root / "STOP").exists():
    try:
        with (root / "results/samples.jsonl").open("rb") as source:
            source.seek(0, 2)
            size = source.tell()
            source.seek(max(0, size - 100000))
            lines = source.read().splitlines()
        sample = json.loads(lines[-1])
        for line in sample["docker"]:
            stats = json.loads(line)
            if "perf-app" in stats["Name"] and float(stats["MemPerc"].rstrip("%")) >= 90:
                (root / "results/ABORT.txt").write_text(
                    "Container memory reached 90%: " + stats["Name"]
                )
                (root / "STOP").touch()
                subprocess.run(
                    [
                        "sudo",
                        "docker",
                        "stop",
                        "fleaflea-gcp-perf-app-1",
                        "fleaflea-gcp-perf-app-replica-1",
                    ]
                )
                break
    except (OSError, ValueError, KeyError, IndexError):
        pass
    time.sleep(3)
