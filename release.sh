#!/usr/bin/env bash
#
# release.sh — tag the working tree with a vN.N.N version and push the
# tag. The deploy.yml GitHub Actions workflow watches v* tags, builds
# the two images on github-hosted runners, pushes them to GHCR, and
# runs `docker compose pull && up -d` on the self-hosted dockermacmini
# runner.
#
# Usage:
#     ./release.sh 1.1.0
#
# Refuses to release from a dirty tree, refuses to overwrite an
# existing tag, and refuses to release from anywhere but main or when
# local main has drifted from origin/main. Override the branch check
# with FORCE_BRANCH=1; override the sync check with FORCE_SYNC=1.
#

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <version>   e.g. $0 1.1.0" >&2
  exit 64
fi

VERSION="$1"
TAG="v${VERSION}"

# Strict semver-ish: digits.digits.digits, optional pre-release suffix.
if ! [[ "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]]; then
  echo "Version must look like 1.2.3 or 1.2.3-rc1; got '${VERSION}'" >&2
  exit 64
fi

if [[ -z "${FORCE_BRANCH:-}" ]]; then
  CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
  if [[ "${CURRENT_BRANCH}" != "main" ]]; then
    echo "Releases run from main; you're on '${CURRENT_BRANCH}'." >&2
    echo "Set FORCE_BRANCH=1 to override." >&2
    exit 1
  fi
fi

if [[ -z "${FORCE_SYNC:-}" ]]; then
  git fetch origin main --quiet
  LOCAL_SHA="$(git rev-parse HEAD)"
  REMOTE_SHA="$(git rev-parse origin/main)"
  BASE_SHA="$(git merge-base HEAD origin/main)"
  if [[ "${LOCAL_SHA}" != "${REMOTE_SHA}" ]]; then
    if [[ "${LOCAL_SHA}" == "${BASE_SHA}" ]]; then
      echo "Local main is behind origin/main — run 'git pull --ff-only' first." >&2
    elif [[ "${REMOTE_SHA}" == "${BASE_SHA}" ]]; then
      echo "Local main is ahead of origin/main — push your commits first." >&2
    else
      echo "Local main and origin/main have diverged — reconcile manually before tagging." >&2
    fi
    echo "Set FORCE_SYNC=1 to override." >&2
    exit 1
  fi
fi

if ! git diff-index --quiet HEAD --; then
  echo "Working tree is dirty — commit or stash first." >&2
  exit 1
fi

if git rev-parse "${TAG}" >/dev/null 2>&1; then
  echo "Tag ${TAG} already exists." >&2
  exit 1
fi

echo "Tagging $(git rev-parse --short HEAD) as ${TAG}…"
git tag -a "${TAG}" -m "VBGone ${VERSION}"
git push origin "${TAG}"

echo
echo "Pushed ${TAG}. Watch the deploy workflow at:"
echo "  https://github.com/gregochr/vbgone-app/actions"
