"""Run only on the isolated performance server with lock186 synthetic market IDs."""

import json
import pathlib
import subprocess
import time
import urllib.request

root = pathlib.Path("/opt/fleaflea-perf")
data = json.loads((root / "lock186/data.json").read_text())
market_id = int(data["markets"][2]["marketId"])
psql = [
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
    "-v",
    "ON_ERROR_STOP=1",
]


def sql(statement):
    return subprocess.run(
        psql, input=statement, text=True, capture_output=True, check=True, cwd=root
    ).stdout.strip()


results = []
for ending in ("COMMIT", "ROLLBACK"):
    sql(
        f"UPDATE markets SET title='probe-initial', description='probe-initial' WHERE market_id={market_id};"
    )
    holder = subprocess.Popen(
        psql,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        cwd=root,
    )
    holder.stdin.write(
        f"BEGIN; UPDATE markets SET description='probe-committed' WHERE market_id={market_id}; SELECT 'LOCK186_READY'; SELECT pg_sleep(2); {ending};\n"
    )
    holder.stdin.close()
    while True:
        line = holder.stdout.readline()
        if "LOCK186_READY" in line:
            break
        if not line:
            raise RuntimeError("SQL holder did not acquire the row")
    boundary = "Lock186TransactionProbe"
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="title"\r\n\r\nprobe-api-title\r\n--{boundary}--\r\n'
    ).encode()
    started = time.monotonic()
    request = urllib.request.Request(
        f"http://127.0.0.1:8080/api/v1/markets/{market_id}",
        body,
        {
            "Authorization": "Bearer " + data["accounts"][0]["token"],
            "Content-Type": "multipart/form-data; boundary=" + boundary,
        },
        method="PATCH",
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        status = response.status
    duration = time.monotonic() - started
    holder.wait(timeout=15)
    if holder.returncode:
        raise RuntimeError(holder.stderr.read())
    state = sql(f"SELECT title,description FROM markets WHERE market_id={market_id};")
    expected = "probe-api-title|" + ("probe-committed" if ending == "COMMIT" else "probe-initial")
    results.append(
        {
            "holder": ending,
            "httpStatus": status,
            "apiSeconds": round(duration, 3),
            "preserved": state == expected,
            "state": state,
        }
    )
print(json.dumps(results))
