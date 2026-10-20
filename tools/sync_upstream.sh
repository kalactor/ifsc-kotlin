#!/usr/bin/env bash
# Regenerate bundled data from a razorpay/ifsc release.
#
# Usage:
#   ./tools/sync_upstream.sh            # latest GitHub release
#   ./tools/sync_upstream.sh v2.0.62    # a specific tag
#
# The downloaded upstream checkout is removed after a successful sync.
# Set KEEP_UPSTREAM=1 to keep it under build/upstream for inspection.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TAG="${1:-}"
if [[ -z "$TAG" ]]; then
    TAG="$(curl -fsSL https://api.github.com/repos/razorpay/ifsc/releases/latest \
        | python3 -c 'import json, sys; print(json.load(sys.stdin)["tag_name"])')"
fi

if [[ "$TAG" == "null" || -z "$TAG" ]]; then
    echo "Could not determine the latest upstream release tag." >&2
    exit 1
fi

echo "Syncing razorpay/ifsc $TAG"

UPSTREAM="$ROOT/build/upstream"
rm -rf "$UPSTREAM"
mkdir -p "$UPSTREAM"

git clone --depth 1 --branch "$TAG" https://github.com/razorpay/ifsc.git "$UPSTREAM/ifsc"
curl -fsSL "https://github.com/razorpay/ifsc/releases/download/$TAG/by-bank.tar.gz" \
    -o "$UPSTREAM/by-bank.tar.gz"
tar xzf "$UPSTREAM/by-bank.tar.gz" -C "$UPSTREAM"

python3 tools/generate_data.py \
    --upstream "$UPSTREAM/ifsc" \
    --by-bank "$UPSTREAM/by-bank" \
    --version "$TAG" \
    --out "$ROOT"

if [[ "${KEEP_UPSTREAM:-0}" != "1" ]]; then
    rm -rf "$UPSTREAM"
fi

echo
echo "Regenerated data. Review the diff, then run:"
echo "  ./gradlew :core:test :offline:testDebugUnitTest"
