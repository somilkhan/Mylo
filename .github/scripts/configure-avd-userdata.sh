#!/usr/bin/env bash
set -euo pipefail

# android-emulator-runner appends disk.dataPartition.size, but the AVD
# template can already contain a larger value. The emulator reads the
# existing config and otherwise allocates the template's 7.3 GB userdata.
config="${HOME}/.android/avd/test.avd/config.ini"
test -f "${config}"
sed -i '/^disk.dataPartition.size=/d' "${config}"
printf '%s\n' 'disk.dataPartition.size=2G' >> "${config}"
grep -Fxq 'disk.dataPartition.size=2G' "${config}"
