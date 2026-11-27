#!/bin/sh
# Uploads the demo runs in demo/runs/ to ingestion-service.
# Idempotent: running it again answers 200 (already ingested) instead of 201.
#
#   scripts/seed.sh                          against http://localhost:8081
#   INGESTION_URL=http://host:8081 scripts/seed.sh
set -eu

INGESTION_URL="${INGESTION_URL:-http://localhost:8081}"
RUNS_DIR="${RUNS_DIR:-$(dirname "$0")/../demo/runs}"

for run in "$RUNS_DIR"/*/; do
    # shellcheck disable=SC1091
    . "$run/run.env"
    set -- \
        -F "repository=$REPOSITORY" \
        -F "commitSha=$COMMIT_SHA" \
        -F "branch=$BRANCH" \
        -F "externalRunId=$EXTERNAL_RUN_ID" \
        -F "attempt=$ATTEMPT"
    for report in "$run"*.xml; do
        set -- "$@" -F "reports=@$report;type=application/xml"
    done
    status=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
        "$@" "$INGESTION_URL/api/v1/test-runs")
    case "$status" in
        201) echo "created   $(basename "$run")" ;;
        200) echo "unchanged $(basename "$run") (already ingested)" ;;
        *) echo "failed    $(basename "$run"): HTTP $status" >&2; exit 1 ;;
    esac
done
