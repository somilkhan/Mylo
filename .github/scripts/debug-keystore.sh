#!/usr/bin/env bash
# Keeps every CI dev build signed with the one debug key the phone already
# trusts, so an APK from any run installs over the last one instead of being
# rejected for a signature mismatch.
#
#   debug-keystore.sh install          decode $DEBUG_KEYSTORE_BASE64 to
#                                      ~/.android/debug.keystore, check it is a
#                                      usable debug key, and record its SHA-256
#   debug-keystore.sh verify <apk>...  fail unless each APK is signed with that
#                                      same certificate
#
# The secret is required. Without it AGP quietly generates a throwaway key on
# every run, and each APK then needs an uninstall first, so a missing or broken
# secret fails the job here instead. A pull request from a fork gets no secrets
# by GitHub's design; there it is a warning and the APK is not meant to be
# installed over anything.
set -euo pipefail

KEYSTORE="${HOME}/.android/debug.keystore"
# The fixed values AGP's default debug signing config uses.
STORE_PASS="android"
KEY_ALIAS="androiddebugkey"
FINGERPRINT_FILE="${RUNNER_TEMP:-/tmp}/debug-keystore.sha256"

fail() { echo "::error title=Debug keystore::$*" >&2; exit 1; }

install_keystore() {
  if [[ -z "${DEBUG_KEYSTORE_BASE64:-}" ]]; then
    if [[ "${IS_FORK_PR:-false}" == "true" ]]; then
      echo "::warning title=Debug keystore::Fork pull request: secrets are not available, so this APK is signed with a throwaway key and will not install over an existing dev build."
      exit 0
    fi
    fail "DEBUG_KEYSTORE_BASE64 is empty or not set. Add it under Settings > Secrets and variables > Actions: base64 -w0 ~/.android/debug.keystore | gh secret set DEBUG_KEYSTORE_BASE64 --repo ${GITHUB_REPOSITORY:-<owner>/<repo>}"
  fi

  mkdir -p "$(dirname "$KEYSTORE")"
  # Tolerates a value pasted with line breaks or stray whitespace.
  if ! printf '%s' "$DEBUG_KEYSTORE_BASE64" | tr -d ' \r\n\t' | base64 --decode > "$KEYSTORE" 2>/dev/null; then
    rm -f "$KEYSTORE"
    fail "DEBUG_KEYSTORE_BASE64 is not valid base64. Re-create it with: base64 -w0 ~/.android/debug.keystore"
  fi
  [[ -s "$KEYSTORE" ]] || { rm -f "$KEYSTORE"; fail "DEBUG_KEYSTORE_BASE64 decodes to an empty file."; }

  # AGP signs debug builds with alias androiddebugkey and password android. A
  # keystore that does not open with those would fail packaging later with a
  # far less obvious error, so it is checked here.
  local listing
  if ! listing="$(keytool -list -v -keystore "$KEYSTORE" -storepass "$STORE_PASS" -alias "$KEY_ALIAS" 2>&1)"; then
    rm -f "$KEYSTORE"
    echo "$listing" | tail -n 5 >&2
    fail "The keystore in DEBUG_KEYSTORE_BASE64 does not open as a debug key (store password '${STORE_PASS}', alias '${KEY_ALIAS}')."
  fi

  local sha256
  sha256="$(printf '%s\n' "$listing" | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n 1 | tr -d ':' | tr 'A-F' 'a-f')"
  [[ -n "$sha256" ]] || fail "Couldn't read the SHA-256 fingerprint of the debug key."
  printf '%s\n' "$sha256" > "$FINGERPRINT_FILE"

  echo "Debug key installed. Certificate SHA-256: ${sha256}"
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    printf '### Debug signing key\nCertificate SHA-256: `%s`\n' "$sha256" >> "$GITHUB_STEP_SUMMARY"
  fi
}

find_apksigner() {
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -n "$sdk" ]] || fail "ANDROID_HOME is not set; can't find apksigner."
  local tool
  tool="$(ls -1 "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1 || true)"
  [[ -n "$tool" ]] || fail "apksigner not found under $sdk/build-tools."
  printf '%s' "$tool"
}

verify_apks() {
  (( $# > 0 )) || fail "verify needs at least one APK path."
  if [[ ! -s "$FINGERPRINT_FILE" ]]; then
    if [[ "${IS_FORK_PR:-false}" == "true" ]]; then
      echo "Fork pull request: no stable debug key to compare against; skipping."
      exit 0
    fi
    fail "No recorded debug key fingerprint. Run 'debug-keystore.sh install' before the build."
  fi
  local expected apksigner apk actual checked=0
  expected="$(cat "$FINGERPRINT_FILE")"
  apksigner="$(find_apksigner)"
  for apk in "$@"; do
    [[ -f "$apk" ]] || continue
    local output
    if ! output="$("$apksigner" verify --print-certs "$apk" 2>&1)"; then
      echo "$output" >&2
      fail "$(basename "$apk") failed signature verification."
    fi
    # "Signer #1 certificate SHA-256 digest: …" on older build-tools; newer
    # ones may name the signer differently, so only the field is matched.
    # Every signer listed has to be the stable key.
    actual="$(printf '%s\n' "$output" | sed -n 's/^.*certificate SHA-256 digest:[[:space:]]*//p' | sort -u)"
    if [[ "$actual" != "$expected" ]]; then
      echo "$output" >&2
      fail "$(basename "$apk") is signed with ${actual:-an unreadable certificate}, not the stable debug key ${expected}. It would not install over the existing dev build."
    fi
    echo "OK  $(basename "$apk")  ${actual}"
    checked=$((checked + 1))
  done
  (( checked > 0 )) || fail "No APKs found to verify: $*"
}

case "${1:-}" in
  install) install_keystore ;;
  verify) shift; verify_apks "$@" ;;
  *) echo "usage: $0 install | verify <apk>..." >&2; exit 2 ;;
esac
