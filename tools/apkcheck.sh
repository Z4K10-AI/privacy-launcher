#!/bin/bash
# Checks the promises that can be read straight off a built APK.
#
#   tools/apkcheck.sh app/build/outputs/apk/release/app-release.apk
#
# Run by CI on every push, and by the release workflow on the exact file it
# publishes. The device tests check the same things on an installed app;
# this checks the file itself, so nothing can change between the check and
# what people download.
#
#   - The permissions are exactly the two the README lists, plus the one
#     AndroidX generates for the app to talk to itself. Not "no INTERNET":
#     exactly these, so a new permission fails here whether or not anyone
#     thought to forbid it.
#   - No start-up helper asks a font provider for emoji fonts.
#   - No "dependency information" block, an encrypted list of libraries the
#     Android build adds for Google Play. F-Droid rejects APKs that carry it,
#     and nobody can check what is in it.
#   - The APK is under the 10 MB budget.
set -u
APK="${1:-}"
[ -f "$APK" ] || { echo "usage: tools/apkcheck.sh path/to/app.apk" >&2; exit 2; }
cd "$(dirname "$0")/.." >/dev/null && APK="$(cd "$OLDPWD" && cd "$(dirname "$APK")" && pwd)/$(basename "$APK")"
. tools/env.sh >/dev/null 2>&1

AAPT2="$(ls "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
[ -x "$AAPT2" ] || { echo "no aapt2 under \$ANDROID_HOME/build-tools" >&2; exit 2; }

FAIL=0
ok()  { echo "  ok    $1"; }
bad() { echo "  FAIL  $1"; FAIL=1; }

echo "checking $(basename "$APK")"

PKG="$("$AAPT2" dump packagename "$APK" 2>/dev/null)"
GOT="$("$AAPT2" dump permissions "$APK" | sed -n "s/^uses-permission: name='\(.*\)'.*/\1/p" | sort)"
WANT="$(printf '%s\n' \
  android.permission.READ_CONTACTS \
  android.permission.WRITE_SETTINGS \
  "$PKG.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" | sort)"
if [ "$GOT" = "$WANT" ]; then
  ok "permissions are exactly: Contacts, Modify system settings, and the app's own"
else
  bad "permissions differ from the promise"
  diff <(echo "$WANT") <(echo "$GOT") | sed 's/^/        /'
fi
case "$GOT" in
  *INTERNET*|*ACCESS_NETWORK_STATE*|*AD_ID*) bad "a network or advertising permission is present" ;;
  *) ok "no internet, network-state or advertising-ID permission" ;;
esac

if "$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" | grep -qi 'EmojiCompatInitializer'; then
  bad "the emoji-font start-up helper is registered"
else
  ok "no start-up helper asking for emoji fonts"
fi

# The APK Signing Block holds the signatures, and also any block the build
# adds. 0x504b4453 is the one for dependency information.
if python3 - "$APK" <<'PY'
import struct, sys
data = open(sys.argv[1], "rb").read()
eocd = data.rfind(b"PK\x05\x06")
cd_offset = struct.unpack("<I", data[eocd + 16:eocd + 20])[0]
if data[cd_offset - 16:cd_offset] != b"APK Sig Block 42":
    sys.exit(0)  # unsigned: no signing block, so no extra blocks either
size = struct.unpack("<Q", data[cd_offset - 24:cd_offset - 16])[0]
pos, end = cd_offset - size - 8 + 8, cd_offset - 24
ids = []
while pos < end:
    length = struct.unpack("<Q", data[pos:pos + 8])[0]
    ids.append(struct.unpack("<I", data[pos + 8:pos + 12])[0])
    pos += 8 + length
sys.exit(1 if 0x504B4453 in ids else 0)
PY
then
  ok "no dependency-information block"
else
  bad "the APK carries a dependency-information block (F-Droid rejects it)"
fi

BYTES="$(wc -c < "$APK" | tr -d ' ')"
if [ "$BYTES" -lt $((10 * 1024 * 1024)) ]; then
  ok "size $BYTES bytes, under the 10 MB budget"
else
  bad "size $BYTES bytes, over the 10 MB budget"
fi

[ "$FAIL" -eq 0 ] && echo "all checks passed" || echo "SOME CHECKS FAILED"
exit "$FAIL"
