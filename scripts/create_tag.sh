#!/usr/bin/env bash
# Create an annotated release tag.
#   scripts/create_tag.sh [version]
# version defaults to versionName in app/build.gradle.kts.
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "${REPO_ROOT}"

VERSION=${1:-$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' app/build.gradle.kts | head -n1)}
if [ -z "${VERSION}" ]; then
  echo "Could not read versionName from app/build.gradle.kts; pass the version explicitly." >&2
  exit 1
fi
TAG="v${VERSION}"

if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
  echo "Tag ${TAG} already exists. Bump versionName/versionCode in app/build.gradle.kts first." >&2
  exit 1
fi

echo "Creating annotated tag ${TAG}"
git tag -a "${TAG}" -m "SoundVault ${VERSION}"
echo "Tag ${TAG} created locally. Push it with: git push origin ${TAG}"
