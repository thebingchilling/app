#!/usr/bin/env bash
# Applies bumboard/patches/*.patch, in order, to the upstream fcitx5-android checkout.
# Safe to run repeatedly: patches that are already applied are skipped.
#   --reset   discard local changes in the upstream checkout first
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
up="$root/upstream/fcitx5-android"
cd "$up"
if [ "${1:-}" = "--reset" ]; then
  git checkout -q -- .
fi
for p in "$root"/patches/*.patch; do
  name="$(basename "$p")"
  if git apply --reverse --check "$p" 2>/dev/null; then
    echo "already applied: $name"
  elif git apply --check "$p"; then
    git apply "$p"
    echo "applied: $name"
  else
    echo "FAILED: $name does not apply to upstream $(git describe --tags --always)" >&2
    echo "Refresh it: fix the upstream files by hand, then scripts/save-patch.sh $name" >&2
    exit 1
  fi
done
