#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FORT_IOS_DIR="${SCRIPT_DIR}/../Fort-ios"
FORTWEB_DIR="${FORTWEB_DIR:-${SCRIPT_DIR}/../fortweb}"
PAYLOAD_SOURCE="${PAYLOAD_SOURCE:-fortweb}"
WRAPPER_PAYLOAD_DIR="${SCRIPT_DIR}/app/src/main/assets/payload"
BRIDGE_CONTRACT_SRC="${FORT_IOS_DIR}/generated/BridgeContract.kt"
BRIDGE_CONTRACT_DEST_DIR="${SCRIPT_DIR}/app/src/main/java/org/kerifoundation/fort/bridge"
BRIDGE_CONTRACT_DEST="${BRIDGE_CONTRACT_DEST_DIR}/BridgeContract.kt"
FORTWEB_MANIFEST_TOOL="${FORT_IOS_DIR}/tools/gen-fortweb-bundle-manifest.mjs"
PAYLOAD_VALIDATOR="${FORT_IOS_DIR}/tools/validate-mobile-payload.mjs"

write_fortweb_redirect() {
  cat > "${WRAPPER_PAYLOAD_DIR}/index.html" <<'EOF'
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
  node "${FORTWEB_MANIFEST_TOOL}" \
    --payload-root "${WRAPPER_PAYLOAD_DIR}" \
    --fortweb-dir "${FORTWEB_DIR}" \
    --build-command 'PAYLOAD_SOURCE=fortweb ./sync-payload.sh'
}

sync_fortios_payload() {
  if [[ ! -f "${FORT_IOS_DIR}/build-payload.sh" ]]; then
    echo "error: expected shared payload builder at ${FORT_IOS_DIR}/build-payload.sh" 1>&2
    echo "       Normal Android builds remain self-contained because app/src/main/assets/payload/ is committed here." 1>&2
    echo "       This script is only needed when refreshing the bundled shared payload from a workspace checkout." 1>&2
    exit 1
  fi

  source "${FORT_IOS_DIR}/build-payload.sh"

  node "${PAYLOAD_VALIDATOR}" \
    --payload-dir "${PAYLOAD_DIST_DIR}" \
    --target android-asset-payload

  echo "[sync-payload] syncing shared payload into Android assets"
  mkdir -p "${WRAPPER_PAYLOAD_DIR}"
  rm -rf "${WRAPPER_PAYLOAD_DIR}"/*
  cp -R "${PAYLOAD_DIST_DIR}"/. "${WRAPPER_PAYLOAD_DIR}/"
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

  if [[ ! -d "${FORTWEB_DIR}/vendor" ]]; then
    echo "error: FortWeb vendor directory missing at ${FORTWEB_DIR}/vendor" 1>&2
    exit 1
  fi

  if [[ ! -d "${FORTWEB_DIR}/wheels" ]]; then
    echo "error: FortWeb wheels directory missing at ${FORTWEB_DIR}/wheels" 1>&2
    exit 1
  fi

  echo "[sync-payload] syncing FortWeb payload into Android assets"
  mkdir -p "${WRAPPER_PAYLOAD_DIR}"
  rm -rf "${WRAPPER_PAYLOAD_DIR}"/*
  mkdir -p "${WRAPPER_PAYLOAD_DIR}/fortweb"
  cp -R "${FORTWEB_DIR}/app" "${WRAPPER_PAYLOAD_DIR}/fortweb/app"
  cp -R "${FORTWEB_DIR}/vendor" "${WRAPPER_PAYLOAD_DIR}/fortweb/vendor"
  cp -R "${FORTWEB_DIR}/wheels" "${WRAPPER_PAYLOAD_DIR}/fortweb/wheels"
  cp "${FORTWEB_DIR}/pyscript-ci.toml" "${WRAPPER_PAYLOAD_DIR}/fortweb/pyscript-ci.toml"
  write_fortweb_redirect
  write_fortweb_manifest
}

case "${PAYLOAD_SOURCE}" in
  fort-ios)
    sync_fortios_payload
    ;;
  fortweb)
    sync_fortweb_payload
    ;;
  *)
    echo "error: unsupported PAYLOAD_SOURCE=${PAYLOAD_SOURCE}" 1>&2
    exit 1
    ;;
esac

if [[ ! -f "${BRIDGE_CONTRACT_SRC}" ]]; then
  echo "error: expected generated bridge contract at ${BRIDGE_CONTRACT_SRC}" 1>&2
  exit 1
fi

mkdir -p "${BRIDGE_CONTRACT_DEST_DIR}"
cp "${BRIDGE_CONTRACT_SRC}" "${BRIDGE_CONTRACT_DEST}"

node "${PAYLOAD_VALIDATOR}" \
  --payload-dir "${WRAPPER_PAYLOAD_DIR}" \
  --target android-asset-payload

if [[ ! -f "${WRAPPER_PAYLOAD_DIR}/index.html" ]]; then
  echo "error: expected index.html missing after sync" 1>&2
  exit 1
fi

if [[ ! -f "${WRAPPER_PAYLOAD_DIR}/build-manifest.json" ]]; then
  echo "error: expected build-manifest.json missing after sync" 1>&2
  exit 1
fi

if [[ ! -f "${BRIDGE_CONTRACT_DEST}" ]]; then
  echo "error: expected BridgeContract.kt missing after sync" 1>&2
  exit 1
fi

FILE_COUNT=$(find "${WRAPPER_PAYLOAD_DIR}" -type f | wc -l | tr -d ' ')
DIST_HASH=$(python3 -c 'import json; print(json.load(open("'"${WRAPPER_PAYLOAD_DIR}/build-manifest.json"'"))["dist_tree_sha256"])')

echo "[sync-payload] ok: source=${PAYLOAD_SOURCE} files=${FILE_COUNT} dist_tree_sha256=${DIST_HASH}"