import copy
import importlib.util
import json
import stat
from types import SimpleNamespace
from unittest.mock import patch
from pathlib import Path
import unittest


spec = importlib.util.spec_from_file_location("dev_validate", Path(__file__).parents[1] / "validate.py")
dev_validate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dev_validate)


class DevIsolationTest(unittest.TestCase):
    def setUp(self):
        self.policy = {"GCP_PROJECT_ID": "example-project", "GCP_INSTANCE": "example-dev-vm",
                       "GCP_INSTANCE_ID": "123456789", "GCP_IMAGE_REPOSITORY": "asia-northeast3-docker.pkg.dev/example-project/fleaflea-dev/backend",
                       "GCS_BUCKET": "example-dev-images", "FRONTEND_URL": "https://dev.example.invalid",
                       "SWAGGER_SERVER_URL": "https://api-dev.example.invalid"}
        environment = {
            "APP_ENV": "development", "DB_URL": "jdbc:postgresql://dev-postgres:5432/fleaflea_dev",
            "DB_USERNAME": "fleaflea_dev", "DDL_AUTO": "validate", "REDIS_HOST": "dev-redis",
            "GCS_BUCKET": "example-dev-images", "FRONTEND_URL": "https://dev.example.invalid",
            "CORS_ALLOWED_ORIGINS": "https://dev.example.invalid",
            "SWAGGER_SERVER_URL": "https://api-dev.example.invalid", "IMAGE_STORAGE": "gcs",
            "SPRING_MAIL_HOST": "dev-mailpit",
        }
        for key in ["APP_VERSION", "DB_PASSWORD", "REDIS_PASSWORD", "JWT_SECRET",
                    "EMAIL_VERIFICATION_HASH_SECRET", "SMTP_USERNAME", "SMTP_PASSWORD",
                    "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET", "KAKAO_CLIENT_ID",
                    "KAKAO_CLIENT_SECRET", "NAVER_CLIENT_ID", "NAVER_CLIENT_SECRET"]:
            environment[key] = "test-only-value-" + "a" * 32
        self.config = {
            "name": "fleaflea-dev", "services": {
                "app": {"environment": environment,
                        "image": "asia-northeast3-docker.pkg.dev/example-project/fleaflea-dev/backend@sha256:" + "a" * 64,
                        "ports": [{"host_ip": "127.0.0.1", "published": "18080", "target": 8080}]},
                "dev-postgres": {}, "dev-redis": {},
            },
        }

    def test_target_requires_instance_id_and_repository(self):
        image = self.config["services"]["app"]["image"]
        self.assertTrue(dev_validate.validate_target(image, "example-dev-vm", "example-project", "123456789", self.policy))
        for instance, project, instance_id in [("other-vm", "example-project", "123456789"),
                                               ("example-dev-vm", "other-project", "123456789"),
                                               ("example-dev-vm", "example-project", "987654321")]:
            self.assertFalse(dev_validate.validate_target(image, instance, project, instance_id, self.policy))

    def test_private_policy_is_required(self):
        private = SimpleNamespace(st_uid=0, st_mode=stat.S_IFREG | 0o600)
        with patch.object(dev_validate, "POLICY_PATH") as path:
            path.lstat.return_value = private
            path.read_text.return_value = json.dumps(self.policy)
            self.assertEqual(self.policy, dev_validate.load_policy())
            for mode in [stat.S_IFREG | 0o644, stat.S_IFLNK | 0o600]:
                path.lstat.return_value = SimpleNamespace(st_uid=0, st_mode=mode)
                with self.assertRaises(ValueError):
                    dev_validate.load_policy()
            path.lstat.return_value = private
            path.read_text.return_value = "{}"
            with self.assertRaises(ValueError):
                dev_validate.load_policy()

    def test_accept_isolated_dev(self):
        self.assertEqual([], dev_validate.validate(self.config, self.policy))

    def test_reject_production_references(self):
        for key, value in {
            "DB_URL": "jdbc:postgresql://192.0.2.10:5432/fleaflea_db",
            "GCS_BUCKET": "example-prod-images", "APP_ENV": "production",
            "FRONTEND_URL": "https://www.example.invalid", "REDIS_HOST": "192.0.2.10",
        }.items():
            with self.subTest(key=key):
                config = copy.deepcopy(self.config)
                config["services"]["app"]["environment"][key] = value
                self.assertTrue(dev_validate.validate(config, self.policy))

    def test_reject_public_database_and_mutable_image(self):
        config = copy.deepcopy(self.config)
        config["services"]["dev-postgres"]["ports"] = [{"target": 5432, "published": "5432"}]
        self.assertTrue(dev_validate.validate(config, self.policy))
        config = copy.deepcopy(self.config)
        config["services"]["app"]["image"] = "backend:latest"
        self.assertTrue(dev_validate.validate(config, self.policy))

    def test_reject_production_image_repository(self):
        self.config["services"]["app"]["image"] = (
            "asia-northeast3-docker.pkg.dev/example-project/fleaflea/backend@sha256:" + "a" * 64
        )
        self.assertTrue(dev_validate.validate(self.config, self.policy))

    def test_reject_public_app_or_management_port(self):
        for port in [{"published": "18080", "target": 8080},
                     {"host_ip": "127.0.0.1", "published": "18081", "target": 8081}]:
            config = copy.deepcopy(self.config)
            config["services"]["app"]["ports"] = [port]
            self.assertTrue(dev_validate.validate(config, self.policy))


if __name__ == "__main__":
    unittest.main()
