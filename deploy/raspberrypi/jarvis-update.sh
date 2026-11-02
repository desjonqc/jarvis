#!/usr/bin/env bash
# Update JARVIS from git : if the branch has new commits, pull them, rebuild the images and restart the stack.
# Run every 15 min by jarvis-update.timer (installed by setup.sh), or by hand :
#   sudo jarvis-update            update if there is something new
#   sudo jarvis-update --force    rebuild and restart even without new commit
# .env and data/ are ignored by git : they are never touched.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/../.." && pwd)"
BRANCH="${JARVIS_BRANCH:-master}"
STATE_DIR=/var/lib/jarvis-update
FORCE=0
[[ "${1:-}" == "--force" ]] && FORCE=1

[[ $EUID -eq 0 ]] || { echo "Run with sudo"; exit 1; }
mkdir -p "$STATE_DIR"
exec 9> "$STATE_DIR/lock"
flock -n 9 || { echo "An update is already running"; exit 0; }

cd "$REPO_DIR"
OWNER="$(stat -c %U "$REPO_DIR")"
git_() { runuser -u "$OWNER" -- git "$@"; } # the repository belongs to the user, not to root

git_ fetch --quiet origin "$BRANCH"
REMOTE="$(git_ rev-parse "origin/$BRANCH")"
DEPLOYED="$(cat "$STATE_DIR/deployed" 2>/dev/null || true)"
FAILED="$(cat "$STATE_DIR/failed" 2>/dev/null || true)"

if [[ $FORCE -eq 0 ]]; then
    [[ "$REMOTE" == "$DEPLOYED" ]] && exit 0
    if [[ "$REMOTE" == "$FAILED" ]]; then
        echo "Commit ${REMOTE:0:8} already failed to build : waiting for a new commit (or --force)"
        exit 0
    fi
fi

echo "Updating to ${REMOTE:0:8} ($(git_ log -1 --format=%s "$REMOTE"))"
git_ reset --hard --quiet "$REMOTE"

# The build is the long part (~10 min on a Pi 3) : the running stack keeps serving meanwhile
if ! docker compose build; then
    echo "$REMOTE" > "$STATE_DIR/failed"
    echo "Build failed : the previous version keeps running"
    exit 1
fi
systemctl restart jarvis.service
echo "$REMOTE" > "$STATE_DIR/deployed"
rm -f "$STATE_DIR/failed"
docker image prune -f > /dev/null
echo "JARVIS updated to ${REMOTE:0:8}"
