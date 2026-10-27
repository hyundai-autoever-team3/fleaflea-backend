import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class DevDeploymentRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        for name in ["etc/fleaflea-dev", "run/lock", "bin"]:
            (self.root / name).mkdir(parents=True)
        self.image_prefix = "asia-northeast3-docker.pkg.dev/example-project/fleaflea-dev/backend@sha256:"
        self.previous = self.image_prefix + "a" * 64
        self.candidate = self.image_prefix + "b" * 64
        self.state = self.root / "etc/fleaflea-dev"
        (self.state / "current-image").write_text(self.previous + "\n")
        (self.state / "current-version").write_text("c" * 40 + "\n")
        (self.state / "runtime.env").write_text("")
        source = (Path(__file__).parents[1] / "deploy.sh").read_text()
        for prefix in ["/etc/fleaflea-dev", "/run/lock"]:
            source = source.replace(prefix, str(self.root) + prefix)
        # Test the deployment behavior without requiring sudo on the test runner.
        source = source.replace("$EUID -ne 0", "0 -ne 0")
        self.script = self.root / "deploy.sh"
        self.script.write_text(source)
        policy_path = self.root / "deployment-policy.json"
        policy_path.write_text(json.dumps({"GCP_PROJECT_ID": "example-project", "GCP_INSTANCE": "example-dev-vm",
            "GCP_INSTANCE_ID": "123456789", "GCP_IMAGE_REPOSITORY": self.image_prefix.split("@")[0],
            "GCS_BUCKET": "example-dev-images", "FRONTEND_URL": "https://dev.example.invalid",
            "SWAGGER_SERVER_URL": "https://api-dev.example.invalid"}))
        policy_path.chmod(0o600)
        validator = (Path(__file__).parents[1] / "validate.py").read_text()
        validator = validator.replace('Path("/etc/fleaflea-dev/deployment-policy.json")', "Path(" + repr(str(policy_path)) + ")")
        validator = validator.replace('information.st_uid != 0', 'information.st_uid != ' + str(os.getuid()))
        # Compose rendering is tested separately; retain the real target-policy check here.
        validator = validator.replace('if len(sys.argv) != 2 or', 'if len(sys.argv) == 2: return 0\n    if len(sys.argv) != 2 or')
        (self.root / "validate.py").write_text(validator)
        self.stub("flock", "exit 0\n")
        self.stub("docker", '''
echo "$APP_IMAGE $*" >> "$TEST_ROOT/docker.log"
if [[ "$*" == login* ]]; then cat >/dev/null; fi
if [[ "$TEST_CASE" == startup-failure && "$*" == *"up -d --wait"* && "$APP_IMAGE" == "$CANDIDATE" ]]; then exit 1; fi
''')
        self.stub("curl", '''
URL="${@: -1}"
case "$URL" in
  */instance/name)
    if [[ "$TEST_CASE" == production-target ]]; then echo example-prod-vm; else echo example-dev-vm; fi ;;
  */project/project-id) echo example-project ;;
  */instance/id) echo 123456789 ;;
  */default/token) echo '{"access_token":"test-only-token"}' ;;
  */readyz) if [[ "$TEST_CASE" == readiness-failure ]]; then exit 22; fi ;;
  *) exit 1 ;;
esac
''')

    def stub(self, name, body):
        path = self.root / "bin" / name
        path.write_text("#!/usr/bin/env bash\n" + body)
        path.chmod(0o700)

    def deploy(self, case="success", image=None):
        environment = dict(os.environ)
        environment.update({
            "PATH": str(self.root / "bin") + os.pathsep + environment["PATH"],
            "TEST_ROOT": str(self.root), "TEST_CASE": case, "CANDIDATE": self.candidate,
        })
        return subprocess.run(
            ["bash", str(self.script), image or self.candidate, "d" * 40],
            env=environment, text=True, capture_output=True, check=False,
        )

    def test_refuse_production_vm_before_docker(self):
        result = self.deploy("production-target")
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertFalse((self.root / "docker.log").exists())

    def test_refuse_mutable_image(self):
        result = self.deploy(image="backend:latest")
        self.assertEqual(2, result.returncode)
        self.assertFalse((self.root / "docker.log").exists())

    def test_success_records_candidate_and_rollback(self):
        result = self.deploy()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(self.candidate, (self.state / "current-image").read_text().strip())
        self.assertEqual(self.previous, (self.state / "rollback-image").read_text().strip())
        self.assertEqual("d" * 40, (self.state / "current-version").read_text().strip())

    def test_failed_start_or_readiness_restores_previous_image(self):
        for case in ["startup-failure", "readiness-failure"]:
            with self.subTest(case=case):
                result = self.deploy(case)
                self.assertEqual(1, result.returncode, result.stdout + result.stderr)
                commands = (self.root / "docker.log").read_text().splitlines()
                self.assertTrue(any(line.startswith(self.previous) and "up -d --wait" in line for line in commands))
                self.assertEqual(self.previous, (self.state / "current-image").read_text().strip())

    def test_first_failed_deployment_stops_only_app(self):
        (self.state / "current-image").unlink()
        result = self.deploy("startup-failure")
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        commands = (self.root / "docker.log").read_text()
        self.assertIn("stop app", commands)
        self.assertNotIn("down", commands)


if __name__ == "__main__":
    unittest.main()
