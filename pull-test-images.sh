#!/usr/bin/env bash
# Pre-pull the images the test suite starts. Needed with socktainer (macOS Apple
# Container): docker-java cannot parse its pull-progress stream
# (https://github.com/socktainer/socktainer/issues/359). Harmless elsewhere.
# Keep the Ryuk tag in sync with the Testcontainers version.
set -euo pipefail
IMAGES=(
    "postgres:18-alpine"
    "testcontainers/ryuk:0.14.0"
)
for image in "${IMAGES[@]}"; do
    echo "==> pulling ${image}"
    docker pull "${image}"
done
