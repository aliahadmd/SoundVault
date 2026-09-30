#!/usr/bin/env bash
# Publish a signed release APK to GitHub.
#   scripts/publish_github_release.sh [version] [notes-file]
# version defaults to versionName in app/build.gradle.kts; without a notes file,
# GitHub generates notes from the commits since the previous tag.
set -euo pipefail

if ! command -v gh >/dev/null 2>&1; then
  echo "The GitHub CLI (gh) is required. Install it from https://cli.github.com/" >&2
  exit 1
fi

REPO_ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "${REPO_ROOT}"

VERSION=${1:-$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' app/build.gradle.kts | head -n1)}
if [ -z "${VERSION}" ]; then
  echo "Could not read versionName from app/build.gradle.kts; pass the version explicitly." >&2
  exit 1
fi
NOTES_FILE=${2:-}
TAG="v${VERSION}"
APK_PATH="app/build/outputs/apk/release/app-release.apk"
UNSIGNED_APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"

if [ ! -f "${APK_PATH}" ]; then
  if [ -f "${UNSIGNED_APK_PATH}" ]; then
    echo "Unsigned APK found at ${UNSIGNED_APK_PATH}, but publishing requires a signed release APK." >&2
    echo "Configure SOUNDVAULT_RELEASE_STORE_FILE, SOUNDVAULT_RELEASE_STORE_PASSWORD, SOUNDVAULT_RELEASE_KEY_ALIAS, and SOUNDVAULT_RELEASE_KEY_PASSWORD, then rebuild with ./gradlew :app:assembleRelease." >&2
    exit 1
  fi
  echo "APK not found at ${APK_PATH}. Build it first with ./gradlew :app:assembleRelease" >&2
  exit 1
fi

if ! git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
  echo "Tag ${TAG} does not exist locally. Create it first with scripts/create_tag.sh ${VERSION}" >&2
  exit 1
fi

NOTES_ARGS=(--generate-notes)
if [ -n "${NOTES_FILE}" ]; then
  NOTES_ARGS=(--notes-file "${NOTES_FILE}")
fi

gh release create "${TAG}" "${APK_PATH}" \
  --title "SoundVault ${VERSION}" \
  --verify-tag \
  "${NOTES_ARGS[@]}"

echo "GitHub release ${TAG} published with ${APK_PATH}."
