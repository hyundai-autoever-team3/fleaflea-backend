#!/usr/bin/env bash
# GitHub runner steps. Server-side deployment remains in deploy.sh.
set -Eeuo pipefail

validate_step() {
    for KEY in GCP_PROJECT_ID GCP_ZONE GCP_INSTANCE GCP_INSTANCE_ID GCP_IMAGE_REPOSITORY GCP_SSH_HOST_KEY GCP_WORKLOAD_IDENTITY_PROVIDER GCP_DEPLOY_SERVICE_ACCOUNT; do
      printf '::add-mask::%s\n' "${!KEY}"
      test -n "${!KEY}" || { echo "Missing dev deployment configuration: $KEY" >&2; exit 1; }
    done
    [[ "$GCP_PROJECT_ID" =~ ^[a-z][a-z0-9-]+$ ]]
    [[ "$GCP_ZONE" =~ ^[a-z0-9-]+$ ]]
    [[ "$GCP_INSTANCE" =~ ^[a-z][a-z0-9-]+$ ]]
    [[ "$GCP_INSTANCE_ID" =~ ^[0-9]+$ ]]
    [[ "$GCP_IMAGE_REPOSITORY" =~ ^[a-z0-9-]+-docker\.pkg\.dev/"$GCP_PROJECT_ID"/[a-z0-9_-]+/[a-z0-9_/-]+$ ]]
    [[ "$DEPLOY_SHA" =~ ^[0-9a-f]{40}$ ]]
}

build_step() {
    ./gradlew bootJar --no-daemon
    mkdir -p build/deploy
    JAR_PATH="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)"
    test -n "$JAR_PATH"
    cp "$JAR_PATH" build/deploy/fleaflea.jar
    docker build --pull --tag "$GCP_IMAGE_REPOSITORY:dev-$DEPLOY_SHA" .
}

push_step() {
    printf '%s' "$GCP_ACCESS_TOKEN" | docker login "${GCP_IMAGE_REPOSITORY%%/*}" \
      --username oauth2accesstoken --password-stdin
    IMAGE_TAG="$GCP_IMAGE_REPOSITORY:dev-$DEPLOY_SHA"
    docker push "$IMAGE_TAG"
    IMAGE_URI="$(docker inspect --format '{{index .RepoDigests 0}}' "$IMAGE_TAG")"
    test -n "$IMAGE_URI"
    echo "uri=$IMAGE_URI" >> "$GITHUB_OUTPUT"
}

deploy_step() {
    gcloud artifacts docker tags add "$IMAGE_URI" "$GCP_IMAGE_REPOSITORY:dev-candidate" \
      --project="$GCP_PROJECT_ID" --quiet
    mkdir -p "$HOME/.ssh"
    chmod 700 "$HOME/.ssh"
    printf 'compute.%s %s\n' "$GCP_INSTANCE_ID" "$GCP_SSH_HOST_KEY" \
      > "$HOME/.ssh/google_compute_known_hosts"
    chmod 600 "$HOME/.ssh/google_compute_known_hosts"
    tar -czf build/dev-deploy.tar.gz -C deploy/gcp/dev compose.yml deploy.sh validate.py
    PAYLOAD="$(base64 -w 0 build/dev-deploy.tar.gz)"
    REMOTE_COMMAND="set -eu
    DEPLOY_DIR=\$(mktemp -d)
    trap 'rm -rf \"\$DEPLOY_DIR\"' EXIT
    printf '%s' '$PAYLOAD' | base64 -d | tar -xz -C \"\$DEPLOY_DIR\"
    REMOTE_NAME=\$(curl -fsS --max-time 5 -H 'Metadata-Flavor: Google' http://metadata.google.internal/computeMetadata/v1/instance/name)
    REMOTE_PROJECT=\$(curl -fsS --max-time 5 -H 'Metadata-Flavor: Google' http://metadata.google.internal/computeMetadata/v1/project/project-id)
    REMOTE_ID=\$(curl -fsS --max-time 5 -H 'Metadata-Flavor: Google' http://metadata.google.internal/computeMetadata/v1/instance/id)
    sudo python3 /opt/fleaflea-dev/validate.py --check-target '$IMAGE_URI' \"\$REMOTE_NAME\" \"\$REMOTE_PROJECT\" \"\$REMOTE_ID\"
    sudo install -d -m 700 /opt/fleaflea-dev
    sudo install -m 600 \"\$DEPLOY_DIR/compose.yml\" /opt/fleaflea-dev/compose.yml
    sudo install -m 700 \"\$DEPLOY_DIR/validate.py\" /opt/fleaflea-dev/validate.py
    sudo install -m 700 \"\$DEPLOY_DIR/deploy.sh\" /opt/fleaflea-dev/deploy.sh
    sudo /opt/fleaflea-dev/deploy.sh '$IMAGE_URI' '$DEPLOY_SHA'"
    gcloud compute ssh "$GCP_INSTANCE" --project="$GCP_PROJECT_ID" --zone="$GCP_ZONE" \
      --tunnel-through-iap --strict-host-key-checking=yes \
      --ssh-key-expire-after=10m --quiet --command="$REMOTE_COMMAND"
    PREVIOUS_IMAGE="$(gcloud artifacts docker images describe "$GCP_IMAGE_REPOSITORY:dev-current" \
      --project="$GCP_PROJECT_ID" --format='value(image_summary.fully_qualified_digest)' 2>/dev/null)" || PREVIOUS_IMAGE=""
    if [[ -n "$PREVIOUS_IMAGE" ]]; then
      gcloud artifacts docker tags add "$PREVIOUS_IMAGE" "$GCP_IMAGE_REPOSITORY:dev-rollback" \
        --project="$GCP_PROJECT_ID" --quiet
    fi
    gcloud artifacts docker tags add "$IMAGE_URI" "$GCP_IMAGE_REPOSITORY:dev-current" \
      --project="$GCP_PROJECT_ID" --quiet
}

if [[ $# -ne 1 ]]; then
    echo "usage: bash deploy/gcp/dev/workflow.sh <validate|build|push|deploy>" >&2
    exit 2
fi

case "$1" in
    validate) validate_step ;;
    build) build_step ;;
    push) push_step ;;
    deploy) deploy_step ;;
    *) echo "Unknown dev workflow step" >&2; exit 2 ;;
esac
