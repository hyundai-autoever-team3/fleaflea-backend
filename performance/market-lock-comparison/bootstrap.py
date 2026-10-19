import json
import pathlib
import urllib.request
import subprocess
import os

root = pathlib.Path("/opt/fleaflea-perf")
os.umask(0o077)
data = json.loads((root / "test-data.json").read_text())
account = data["accounts"][0]


def call(path, body, content_type="application/json"):
    headers = {"Content-Type": content_type}
    if account.get("token"):
        headers["Authorization"] = "Bearer " + account["token"]
    request = urllib.request.Request("http://127.0.0.1:8080" + path, body, headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


account["token"] = call(
    "/api/v1/auth/login", json.dumps({key: account[key] for key in ("email", "password")}).encode()
)["accessToken"]
markets = []
for index in range(21):
    boundary = "Lock186Seed"
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="title"\r\n\r\nlock186-{index}\r\n--{boundary}--\r\n'
    ).encode()
    markets.append(call("/api/v1/markets", body, "multipart/form-data; boundary=" + boundary))
result = {
    "accounts": [{"token": account["token"]}],
    "markets": [markets[0], markets[0], markets[0]],
    "spreadMarkets": markets[1:],
}
(root / "lock186/data.json").write_text(json.dumps(result))
with (root / "lock186/seed.dump").open("wb") as dump:
    subprocess.run(
        [
            "sudo",
            "docker",
            "compose",
            "exec",
            "-T",
            "postgres",
            "pg_dump",
            "-U",
            "postgres",
            "-Fc",
            "fleaflea_db",
        ],
        cwd=root,
        stdout=dump,
        check=True,
    )
print("Prepared 21 image-free markets and frozen synthetic DB snapshot")
