#!/usr/bin/env bash
set -uo pipefail
cd /opt/fleaflea-perf/lock186
name="${1:?name}"; mode="${2:?preservation or load}"; rate="${3:-20}"; distribution="${4:-single}"; duration="${5:-60s}"
mkdir -p results
mpstat -P ALL 1 > "results/$name-cpu.txt" &
cpu_pid=$!
trap 'kill "$cpu_pid" 2>/dev/null || true' EXIT
sudo docker run --rm --user 0:0 --network host --name lock186-k6 \
  -v /opt/fleaflea-perf/lock186:/scripts \
  -e BASE_URL=https://10.20.0.2 -e TEST_ORIGIN=https://10.20.0.2 \
  -e CONFIRM_ISOLATED_TEST=yes -e TEST_SELF_SIGNED_TLS=yes \
  -e DATA_FILE=/scripts/data.json -e "RATE=$rate" -e "DISTRIBUTION=$distribution" \
  -e "DURATION=$duration" -e "RUN_ID=$name" -e "SUMMARY_PATH=/scripts/results/$name.json" \
  grafana/k6@sha256:e66db15b860113878fa74670e31f5e274830b7b6e42c8bff28b2f2d86a257603 \
  run --summary-export "/scripts/results/$name-export.json" "/scripts/$mode.js" > "results/$name.log" 2>&1
code=$?
python3 - "$name" "$code" <<'PY'
import json,pathlib,sys
name=sys.argv[1]
path=pathlib.Path('results')/(name+'.json')
if not path.exists(): path=pathlib.Path('results')/(name+'-export.json')
if not path.exists():
    print('NO_SUMMARY '+name)
    print((pathlib.Path('results')/(name+'.log')).read_text()[-1800:])
    sys.exit(2)
s=json.loads(path.read_text());m=s['metrics']
def v(k,f):
    metric=m.get(k,{})
    return metric.get('values',metric).get(f,metric.get('value',0) if f == 'rate' else 0)
print(json.dumps({'name':name,'exit':int(sys.argv[2]),'rps':v('http_reqs','rate'),'writeP95':v('successful_write_duration','p(95)'),'writeP99':v('successful_write_duration','p(99)'),'writes':v('successful_write_requests','count'),'failedRate':v('http_req_failed','rate'),'checks':v('checks','rate'),'dropped':v('dropped_iterations','count'),'500':v('internal_server_error_responses','count')}))
if (v('internal_server_error_responses','count') + v('service_unavailable_responses','count')) > v('http_reqs','count') * .05:
    sys.exit(3)
PY
