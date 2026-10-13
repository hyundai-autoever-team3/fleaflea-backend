import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class DeploymentRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        for directory in ('etc/fleaflea', 'etc/nginx/conf.d', 'opt/fleaflea', 'run/lock', 'var/log/nginx', 'bin'):
            (self.root / directory).mkdir(parents=True)
        self.state = self.root / 'etc/fleaflea/active-slot'
        self.state.write_text('blue\n')
        (self.root / 'etc/fleaflea/blue.env').write_text('APP_IMAGE=example.invalid/backend@sha256:previous\nAPP_PORT=8081\n')
        self.upstream = self.root / 'etc/nginx/conf.d/fleaflea-backend.conf'
        self.upstream.write_text('original upstream\n')
        source = Path(__file__).resolve().parents[1] / 'deploy.sh'
        script = source.read_text()
        for prefix in ('/etc/fleaflea', '/etc/nginx', '/opt/fleaflea', '/run/lock', '/var/log/nginx'):
            script = script.replace(prefix, str(self.root) + prefix)
        script = script.replace('sleep 5', 'sleep 0.05')
        script = script.replace('sleep 1', 'sleep 0.05')
        self.script = self.root / 'deploy.sh'
        self.script.write_text(script)
        self.stub('docker', '''echo "$*" >> "$TEST_ROOT/docker.log"
if [[ "$TEST_CASE" == startup-failure && "$*" == *"up -d --wait"* ]]; then exit 1; fi
''')
        self.stub('curl', '''COUNT_FILE="$TEST_ROOT/curl-count"
COUNT=$(cat "$COUNT_FILE" 2>/dev/null || echo 0)
COUNT=$((COUNT + 1)); echo "$COUNT" > "$COUNT_FILE"
URL="${@: -1}"
if [[ "$URL" == *":8082/readyz" ]]; then
    if [[ "$TEST_CASE" == legacy-health ]]; then echo 404;
    elif [[ "$TEST_CASE" == readiness-failure ]]; then echo 503;
    else echo 200; fi
    exit 0
fi
if [[ "$URL" == *":8082/actuator/health" ]]; then echo 200; exit 0; fi
HEADER_FILE=""
while (( $# )); do
    if [[ "$1" == --dump-header ]]; then HEADER_FILE="$2"; shift; fi
    shift
done
if [[ -n "$HEADER_FILE" ]]; then
    PORT=8082
    if [[ "$TEST_CASE" == wrong-upstream || ( "$TEST_CASE" == delayed-reload && $COUNT -le 3 ) ]]; then PORT=8081; fi
    printf 'HTTP/1.1 200 OK\\r\\nX-FleaFlea-Upstream: 127.0.0.1:%s\\r\\n\\r\\n' "$PORT" > "$HEADER_FILE"
    if [[ "$TEST_CASE" == missing-header ]]; then : > "$HEADER_FILE"; fi
fi
if [[ "$TEST_CASE" == proxy-failure && $COUNT -ge 2 ]]; then exit 22; fi
if [[ "$TEST_CASE" == delayed-reload && $COUNT -eq 2 ]]; then exit 22; fi
if [[ "$TEST_CASE" == observation-failure && $COUNT -eq 3 ]]; then exit 22; fi
echo '{"status":"UP"}'
if [[ "$TEST_CASE" == api-errors && $COUNT -eq 3 ]]; then
    for i in {1..20}; do
        echo '{"status":500,"uri":"/api/v1/markets","upstream":"127.0.0.1:8082"}' >> "$TEST_ROOT/var/log/nginx/fleaflea-access.log"
    done
fi
''')
        self.stub('nginx', 'exit 0\n')
        self.stub('systemctl', '''COUNT_FILE="$TEST_ROOT/reload-count"
COUNT=$(cat "$COUNT_FILE" 2>/dev/null || echo 0)
COUNT=$((COUNT + 1)); echo "$COUNT" > "$COUNT_FILE"
if [[ "$TEST_CASE" == reload-failure && $COUNT -eq 1 ]]; then exit 1; fi
''')

    def stub(self, name, body):
        path = self.root / 'bin' / name
        path.write_text('#!/usr/bin/env bash\n' + body)
        path.chmod(0o700)

    def deploy(self, scenario):
        environment = {**os.environ, 'PATH': str(self.root / 'bin') + ':' + os.environ['PATH'],
                       'TEST_ROOT': str(self.root), 'TEST_CASE': scenario,
                       'OBSERVE_SECONDS': '1', 'DRAIN_SECONDS': '0', 'SWITCH_TIMEOUT_SECONDS': '1'}
        return subprocess.run(['bash', str(self.script), 'example.invalid/backend@sha256:new'],
                              env=environment, capture_output=True, text=True, timeout=15)

    def assert_previous_preserved(self, scenario):
        result = self.deploy(scenario)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual('blue', self.state.read_text().strip())
        self.assertEqual('original upstream\n', self.upstream.read_text())
        self.assertNotIn('fleaflea-blue --env-file', '\n'.join(
            line for line in (self.root / 'docker.log').read_text().splitlines() if 'stop app' in line))

    def test_unhealthy_candidate_keeps_current_application(self):
        self.assert_previous_preserved('startup-failure')

    def test_failed_proxy_probe_restores_upstream(self):
        self.assert_previous_preserved('proxy-failure')

    def test_failure_during_observation_restores_upstream(self):
        self.assert_previous_preserved('observation-failure')

    def test_failed_nginx_reload_restores_upstream(self):
        self.assert_previous_preserved('reload-failure')

    def test_high_api_error_ratio_restores_upstream(self):
        self.assert_previous_preserved('api-errors')

    def test_old_worker_error_and_old_upstream_success_are_retried(self):
        result = self.deploy('delayed-reload')
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual('green', self.state.read_text().strip())

    def test_old_upstream_success_cannot_verify_new_deployment(self):
        self.assert_previous_preserved('wrong-upstream')

    def test_missing_routing_evidence_restores_upstream(self):
        self.assert_previous_preserved('missing-header')

    def test_legacy_health_path_is_supported(self):
        result = self.deploy('legacy-health')
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_unready_candidate_does_not_fall_back_to_aggregate_health(self):
        result = self.deploy('readiness-failure')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual('blue', self.state.read_text().strip())
        self.assertFalse((self.root / 'reload-count').exists())

    def test_success_records_rollback_image_and_stops_previous_slot(self):
        result = self.deploy('success')
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual('green', self.state.read_text().strip())
        self.assertIn('8082', self.upstream.read_text())
        self.assertEqual('example.invalid/backend@sha256:previous',
                         (self.root / 'etc/fleaflea/rollback-image').read_text().strip())
        self.assertIn('fleaflea-blue --env-file', '\n'.join(
            line for line in (self.root / 'docker.log').read_text().splitlines() if 'stop app' in line))


if __name__ == '__main__':
    unittest.main()
