#!/bin/sh
# Build lab images that must be offline at Containerlab runtime.
# Usage: ./scripts/build-lab-images.sh

set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$ROOT"

echo "building banklab-camera:local (ffmpeg + MediaMTX v1.11.3)"
docker build -t banklab-camera:local ./services/camera
echo "built banklab-camera:local"
