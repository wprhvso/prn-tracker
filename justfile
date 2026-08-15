set shell := ["bash", "-uc"]

ver := env_var_or_default("VERSION", "dev")
app := "ru.murasya.prn"

default:
    just --list

check-version:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ "{{ver}}" = "dev" ]; then
        exit 0
    fi
    cur="v$(sed -n 's/.*versionName "\(.*\)".*/\1/p' android/app/build.gradle)"
    if [ "$cur" != "{{ver}}" ]; then
        echo "version mismatch: build.gradle=$cur VERSION={{ver}}" >&2
        exit 1
    fi

build: check-version
    #!/usr/bin/env bash
    set -euo pipefail
    shopt -s nullglob
    gradle -p android :app:assembleRelease
    v="$(sed -n 's/.*versionName "\(.*\)".*/\1/p' android/app/build.gradle)"
    out=android/app/build/outputs/apk/release
    apks=("$out"/app-release.apk)
    if [ "${#apks[@]}" -eq 0 ]; then
        echo "no APK produced in $out" >&2
        exit 1
    fi
    mv "$out/app-release.apk" "$out/prn-tracker-v$v.apk"

debug:
    gradle -p android :app:assembleDebug

test:
    gradle -p android test

lint:
    bash <(curl -fsSL https://raw.githubusercontent.com/wprhvso/qa-kotlin/v1/scripts/local.sh)

fix:
    bash <(curl -fsSL https://raw.githubusercontent.com/wprhvso/qa-kotlin/v1/scripts/local.sh) --fix

install:
    #!/usr/bin/env bash
    set -euo pipefail
    apk="$(ls -t android/app/build/outputs/apk/release/*.apk | head -n1)"
    adb install -r "$apk"
    adb shell am start -n {{app}}/.ui.MainActivity

log:
    adb logcat --pid=$(adb shell pidof -s {{app}})

restart:
    #!/usr/bin/env bash
    set -euo pipefail
    adb shell am force-stop {{app}}
    adb shell am start -n {{app}}/.ui.MainActivity

clean:
    rm -rf android/app/build android/build android/.gradle
