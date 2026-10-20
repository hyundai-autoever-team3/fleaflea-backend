#!/usr/bin/env python3
"""Validate effective Compose settings without printing credentials."""

import json
import re
import stat
import subprocess
import sys
from pathlib import Path


POLICY_PATH = Path("/etc/fleaflea-dev/deployment-policy.json")


def load_policy():
    information = POLICY_PATH.lstat()
    if information.st_uid != 0 or not stat.S_ISREG(information.st_mode) or information.st_mode & 0o077:
        raise ValueError("Deployment policy must be a root-owned private regular file")
    policy = json.loads(POLICY_PATH.read_text())
    if not isinstance(policy, dict):
        raise ValueError("Deployment policy must be an object")
    required = ["GCP_PROJECT_ID", "GCP_INSTANCE", "GCP_INSTANCE_ID", "GCP_IMAGE_REPOSITORY",
                "GCS_BUCKET", "FRONTEND_URL", "SWAGGER_SERVER_URL"]
    if any(not isinstance(policy.get(key), str) or not policy[key].strip() for key in required):
        raise ValueError("Deployment policy is incomplete")
    repository_pattern = (
        r"[a-z0-9-]+-docker\.pkg\.dev/" + re.escape(policy["GCP_PROJECT_ID"]) + r"/[a-z0-9_-]+/[a-z0-9_/-]+"
    )
    if not re.fullmatch(repository_pattern, policy["GCP_IMAGE_REPOSITORY"]):
        raise ValueError("Deployment policy repository is invalid")
    return policy


def valid_image(image, policy):
    return re.fullmatch(re.escape(policy["GCP_IMAGE_REPOSITORY"]) + r"@sha256:[0-9a-f]{64}", image) is not None


def validate_target(image, instance, project, instance_id, policy):
    return (instance == policy["GCP_INSTANCE"] and project == policy["GCP_PROJECT_ID"]
            and instance_id == policy["GCP_INSTANCE_ID"] and valid_image(image, policy))


def validate(config, policy):
    errors = []
    services = config.get("services", {})
    app = services.get("app", {})
    environment = app.get("environment", {})
    expected = {
        "APP_ENV": "development",
        "DB_URL": "jdbc:postgresql://dev-postgres:5432/fleaflea_dev",
        "DB_USERNAME": "fleaflea_dev",
        "DDL_AUTO": "validate",
        "REDIS_HOST": "dev-redis",
        "GCS_BUCKET": policy["GCS_BUCKET"],
        "FRONTEND_URL": policy["FRONTEND_URL"],
        "CORS_ALLOWED_ORIGINS": policy["FRONTEND_URL"],
        "SWAGGER_SERVER_URL": policy["SWAGGER_SERVER_URL"],
        "IMAGE_STORAGE": "gcs",
        "SPRING_MAIL_HOST": "dev-mailpit",
    }
    for key, value in expected.items():
        if environment.get(key) != value:
            errors.append(f"{key}: expected isolated dev setting")
    required = [
        "APP_VERSION", "DB_PASSWORD", "REDIS_PASSWORD", "JWT_SECRET",
        "EMAIL_VERIFICATION_HASH_SECRET", "SMTP_USERNAME", "SMTP_PASSWORD",
        "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET", "KAKAO_CLIENT_ID",
        "KAKAO_CLIENT_SECRET", "NAVER_CLIENT_ID", "NAVER_CLIENT_SECRET",
    ]
    for key in required:
        if not environment.get(key):
            errors.append(f"{key}: required")
    for key in ["JWT_SECRET", "EMAIL_VERIFICATION_HASH_SECRET"]:
        if len(environment.get(key, "")) < 32:
            errors.append(f"{key}: at least 32 characters required")
    if not valid_image(app.get("image", ""), policy):
        errors.append("APP_IMAGE: expected repository and immutable digest")
    if config.get("name") != "fleaflea-dev":
        errors.append("Compose project must be fleaflea-dev")
    for name in ["dev-postgres", "dev-redis"]:
        if services.get(name, {}).get("ports"):
            errors.append(f"{name}: database ports must not be published")
    ports = app.get("ports", [])
    if len(ports) != 1 or any(
        port.get("host_ip") != "127.0.0.1"
        or str(port.get("published")) != "18080"
        or port.get("target") != 8080
        for port in ports
    ):
        errors.append("app: publish only loopback port 18080 to API port 8080")
    return errors


def main():
    try:
        policy = load_policy()
    except (OSError, ValueError, TypeError):
        print("A valid private deployment policy is required", file=sys.stderr)
        return 2
    if len(sys.argv) == 6 and sys.argv[1] == "--check-target":
        if not validate_target(*sys.argv[2:], policy):
            print("Refusing deployment outside the approved dev target", file=sys.stderr)
            return 2
        return 0
    if len(sys.argv) != 2 or not Path(sys.argv[1]).is_absolute():
        print("usage: validate.py <absolute-dev-env-file>", file=sys.stderr)
        return 2
    result = subprocess.run(
        ["docker", "compose", "--project-name", "fleaflea-dev", "--env-file", sys.argv[1],
         "-f", str(Path(__file__).with_name("compose.yml")), "config", "--format", "json"],
        capture_output=True, text=True, check=False,
    )
    if result.returncode:
        # Compose errors can contain interpolated values: do not echo its output.
        print("Cannot render dev Compose configuration; check required settings", file=sys.stderr)
        return 1
    errors = validate(json.loads(result.stdout), policy)
    for error in errors:
        print(error, file=sys.stderr)
    return int(bool(errors))


if __name__ == "__main__":
    sys.exit(main())
