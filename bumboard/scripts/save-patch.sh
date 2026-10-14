#!/usr/bin/env bash
# Rewrites one patch from the current upstream working tree.
# Workflow for changing upstream code:
#   scripts/apply-patches.sh --reset          # tree = upstream + all patches
#   ...edit files under upstream/fcitx5-android...
#   scripts/save-patch.sh 0002-ui-hooks.patch -- path/to/changed/file ...
# Only the listed paths go into that patch; other patches keep their own files.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
name="$1"; shift
[ "${1:-}" = "--" ] && shift
[ $# -gt 0 ] || { echo "list the upstream paths that belong to $name" >&2; exit 2; }
cd "$root/upstream/fcitx5-android"
git diff -- "$@" > "$root/patches/$name"
echo "wrote patches/$name ($(grep -c '^+++' "$root/patches/$name") files)"
