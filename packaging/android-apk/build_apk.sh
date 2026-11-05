#!/usr/bin/env bash
# Build the phone app: the payload (the rootfs, the build tools, the tool's tree) and the APK.
#
#   bash packaging/android-apk/build_apk.sh [debug|release]
#
# The steps:
#   1. the rootfs from the nixport payload release (the patched glibc, Python and the rest of the runtime)
#   2. the build tools: Google's NDK (its sysroot and compiler-rt) + an ARM64 LLVM + glslc from Debian
#   3. the tool's own tree and the Python modules, staged as the app's assets
#   4. the APK (Gradle)
#
# The downloads are kept in packaging/android-apk/.cache (not committed). The payload release is private: the
# download needs the gh CLI with access to NixPort/nixport-rootfs.
set -euo pipefail
cd -- "$(dirname -- "$0")/../.."
root=$PWD
here=$root/packaging/android-apk
cache=$here/.cache
variant=${1:-debug}
# --no-payload: only the app, without the rootfs and the build tools (the phone already has them unpacked)
payload_flag=""
for a in "$@"; do
    [[ $a == --no-payload ]] && payload_flag="-PnoPayload"
done

die() { echo "android-apk: $*" >&2; exit 1; }

command -v gh >/dev/null || die "the gh CLI is needed (the rootfs comes from the nixport payload release)"
command -v python3 >/dev/null || die "python3 is needed"

mkdir -p "$cache"

# 1. the rootfs (the supply channel's release)
if [[ ! -s $cache/payload-arm64.tar.gz ]]; then
    echo ">> the payload (nixport-rootfs@payload-latest)"
    gh release download payload-latest -R NixPort/nixport-rootfs -p payload-arm64.tar.gz -D "$cache" \
        || die "the payload is not reachable (gh release download payload-latest -R NixPort/nixport-rootfs)"
fi

# 2. the build tools
echo ">> the build tools"
python3 "$here/fetch_debs.py" --out "$cache/debs" --cache "$cache/.deb-cache" glslc >/dev/null
python3 "$here/build_toolchain.py" --out "$cache/toolchain" --cache "$cache" --debs "$cache/debs"

# 3. the payload the app ships
echo ">> the app's assets"
if [[ -n $payload_flag ]]; then
    echo "   skipped (--no-payload): the phone must already have the environment"
else
    python3 "$here/build_assets.py" --out "$cache/assets" --payload "$cache/payload-arm64.tar.gz" --toolchain "$cache/toolchain"
fi

# 4. the APK
echo ">> the APK"
cd "$root/android"
./gradlew ":app:assemble$(python3 -c "print('Debug' if '$variant' == 'debug' else 'Release')")" $payload_flag --console=plain
apk=$(ls -t "$root/android/app/build/outputs/apk/$variant"/*.apk | head -1)
echo
echo "APK: $apk ($(du -m "$apk" | cut -f1) MB)"
