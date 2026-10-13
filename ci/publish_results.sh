#!/usr/bin/env bash
# Publishes CI output (logs, screenshots) to the `ci-results` branch so results can be read with git
# even where Actions logs and artifacts are not reachable. Only the current run is kept.
# Parallel jobs may publish at the same time, so each attempt re-reads the branch and retries on conflict.
# Usage: ci/publish_results.sh <source-dir> <job-name>
set -u
SRC="$(cd "$1" && pwd)"
JOB="$2"
RUN="${GITHUB_RUN_NUMBER:-0}"
URL="https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
COMMIT="$(git -C "$GITHUB_WORKSPACE" rev-parse --short HEAD 2>/dev/null)"

for attempt in 1 2 3 4 5 6; do
  WORK="$(mktemp -d)"
  cd "$WORK" || exit 0
  git init -q
  git remote add origin "$URL"
  BASE=""
  if git fetch -q --depth 1 origin ci-results 2>/dev/null; then
    BASE="$(git rev-parse FETCH_HEAD)"
    git checkout -q FETCH_HEAD -- . 2>/dev/null || true
  fi
  mkdir -p runs
  # The first job of a new run starts the branch afresh (no history of old screenshots).
  FRESH=0
  [ -d "runs/$RUN" ] || FRESH=1
  # A newer run replaces everything from older runs.
  find runs -mindepth 1 -maxdepth 1 ! -name "$RUN" -exec rm -rf {} + 2>/dev/null || true
  rm -rf "runs/$RUN/$JOB"
  mkdir -p "runs/$RUN/$JOB"
  cp -r "$SRC"/. "runs/$RUN/$JOB/"
  echo "$RUN $COMMIT" > "runs/$RUN/commit.txt"
  git add -A
  TREE="$(git write-tree)"
  if [ -n "$BASE" ] && [ "$FRESH" = 0 ]; then
    NEW="$(git -c user.name=sakina-ci -c user.email=ci@users.noreply.github.com commit-tree "$TREE" -p "$BASE" -m "CI results run $RUN ($JOB)")"
  else
    NEW="$(git -c user.name=sakina-ci -c user.email=ci@users.noreply.github.com commit-tree "$TREE" -m "CI results run $RUN ($JOB)")"
  fi
  if [ "$FRESH" = 1 ]; then PUSH_OPTS="-f"; else PUSH_OPTS=""; fi
  if git push -q $PUSH_OPTS origin "$NEW:refs/heads/ci-results"; then echo "published runs/$RUN/$JOB"; exit 0; fi
  sleep $((attempt * 3))
done
echo "::warning::Could not publish CI results"
