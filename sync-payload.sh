#!/usr/bin/env bash
# ── sync-payload.sh ───────────────────────────────────────────────────────────
#
# Android-specific payload sync entrypoint.
#
# Supports two payload sources:
#   1. fortweb   - mainline convergence path copied into assets/fortweb/
#   2. fort-ios  - legacy local Vite/TypeScript proof harness built via build-payload.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAYLOAD_SOURCE="${PAYLOAD_SOURCE:-fortweb}"
FORTWEB_DIR="${FORTWEB_DIR:-${SCRIPT_DIR}/../fortweb}"
FORT_IOS_DIR="${FORT_IOS_DIR:-${SCRIPT_DIR}/../Fort-ios}"
WRAPPER_ASSET_DIR="${SCRIPT_DIR}/app/src/main/assets"
BRIDGE_CONTRACT_SRC="${FORT_IOS_DIR}/generated/BridgeContract.kt"
BRIDGE_CONTRACT_DEST_DIR="${SCRIPT_DIR}/app/src/main/java/org/kerifoundation/fort/bridge"
BRIDGE_CONTRACT_DEST="${BRIDGE_CONTRACT_DEST_DIR}/BridgeContract.kt"

write_fortweb_redirect() {
	cat > "${WRAPPER_ASSET_DIR}/index.html" <<'EOF'
<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover" />
  <title>KERI Wallet</title>
  <script>
    window.location.replace('./fortweb/app/index.html');
  </script>
</head>
<body></body>
</html>
EOF
}

write_fortweb_manifest() {
	FORTWEB_DIR="${FORTWEB_DIR}" WRAPPER_ASSET_DIR="${WRAPPER_ASSET_DIR}" python3 - <<'PY'
import hashlib
import json
import os
import subprocess
from datetime import datetime, timezone

root = os.environ["WRAPPER_ASSET_DIR"]
fortweb_dir = os.environ["FORTWEB_DIR"]


def iter_files():
    for current_root, _, files in os.walk(root):
        for filename in files:
            rel = os.path.relpath(os.path.join(current_root, filename), root)
            if rel == "build-manifest.json":
                continue
            yield rel


digest = hashlib.sha256()
for rel in sorted(iter_files()):
    digest.update(rel.encode("utf-8"))
    digest.update(b"\0")
    with open(os.path.join(root, rel), "rb") as fp:
        digest.update(fp.read())
    digest.update(b"\0")

try:
    git_sha = subprocess.check_output(
        ["git", "-C", fortweb_dir, "rev-parse", "HEAD"],
        text=True,
        stderr=subprocess.DEVNULL,
    ).strip()
except Exception:
    git_sha = None

manifest = {
    "schema": 1,
    "created_at": datetime.now(timezone.utc).isoformat(),
    "git_sha": git_sha,
    "node_version": None,
    "package_lock_sha256": None,
    "dist_tree_sha256": digest.hexdigest(),
    "payload_source": "fortweb",
}

with open(os.path.join(root, "build-manifest.json"), "w", encoding="utf-8") as fp:
    json.dump(manifest, fp, indent=2)
    fp.write("\n")
PY
}

sync_fortweb_payload() {
	if [[ ! -d "${FORTWEB_DIR}" ]]; then
		echo "error: FortWeb repo not found at ${FORTWEB_DIR}" 1>&2
		exit 1
	fi

	if [[ ! -f "${FORTWEB_DIR}/app/index.html" ]]; then
		echo "error: FortWeb app/index.html missing at ${FORTWEB_DIR}/app/index.html" 1>&2
		exit 1
	fi

	if [[ ! -f "${FORTWEB_DIR}/pyscript-ci.toml" ]]; then
		echo "error: FortWeb pyscript-ci.toml missing at ${FORTWEB_DIR}/pyscript-ci.toml" 1>&2
		exit 1
	fi

	echo "[sync-payload] syncing FortWeb payload into Android assets/"
	mkdir -p "${WRAPPER_ASSET_DIR}"
	rm -rf "${WRAPPER_ASSET_DIR}"/*
	mkdir -p "${WRAPPER_ASSET_DIR}/fortweb"
	cp -R "${FORTWEB_DIR}/app" "${WRAPPER_ASSET_DIR}/fortweb/app"
	cp -R "${FORTWEB_DIR}/vendor" "${WRAPPER_ASSET_DIR}/fortweb/vendor"
	cp -R "${FORTWEB_DIR}/wheels" "${WRAPPER_ASSET_DIR}/fortweb/wheels"
	cp "${FORTWEB_DIR}/pyscript-ci.toml" "${WRAPPER_ASSET_DIR}/fortweb/pyscript-ci.toml"
	write_fortweb_redirect
	write_fortweb_manifest
}

sync_fortios_payload() {
	if [[ ! -f "${FORT_IOS_DIR}/build-payload.sh" ]]; then
		echo "error: expected shared payload builder at ${FORT_IOS_DIR}/build-payload.sh" 1>&2
		echo "       Normal Android builds remain self-contained because assets are committed here." 1>&2
		echo "       This script is only needed when refreshing the bundled shared payload from a workspace checkout." 1>&2
		exit 1
	fi

	source "${FORT_IOS_DIR}/build-payload.sh"

	echo "[sync-payload] syncing Fort-ios payload into Android assets/"
	mkdir -p "${WRAPPER_ASSET_DIR}"
	rm -rf "${WRAPPER_ASSET_DIR}"/*
	cp -R "${PAYLOAD_DIST_DIR}"/. "${WRAPPER_ASSET_DIR}/"

	if [[ -f "${BRIDGE_CONTRACT_SRC}" ]]; then
		mkdir -p "${BRIDGE_CONTRACT_DEST_DIR}"
		cp "${BRIDGE_CONTRACT_SRC}" "${BRIDGE_CONTRACT_DEST}"
		echo "[sync-payload] updated BridgeContract.kt from generated source"
	else
		echo "warning: generated BridgeContract.kt not found at ${BRIDGE_CONTRACT_SRC}, skipping" 1>&2
	fi
}

case "${PAYLOAD_SOURCE}" in
	fortweb)
		sync_fortweb_payload
		;;
	fort-ios)
		sync_fortios_payload
		;;
	*)
		echo "error: unsupported PAYLOAD_SOURCE=${PAYLOAD_SOURCE}" 1>&2
		exit 1
		;;
esac

# ── Validate sync output ─────────────────────────────────────────────────────
if [[ ! -f "${WRAPPER_ASSET_DIR}/index.html" ]]; then
	echo "error: expected index.html missing after sync" 1>&2
	exit 1
fi

if [[ ! -f "${WRAPPER_ASSET_DIR}/build-manifest.json" ]]; then
	echo "error: expected build-manifest.json missing after sync" 1>&2
	exit 1
fi

FILE_COUNT=$(find "${WRAPPER_ASSET_DIR}" -type f | wc -l | tr -d ' ')
DIST_HASH=$(python3 - <<'PY' "${WRAPPER_ASSET_DIR}/build-manifest.json"
import json
import sys

with open(sys.argv[1], 'r', encoding='utf-8') as fp:
    print(json.load(fp)['dist_tree_sha256'])
PY
)

echo "[sync-payload] ok: source=${PAYLOAD_SOURCE} files=${FILE_COUNT} dist_tree_sha256=${DIST_HASH}"
