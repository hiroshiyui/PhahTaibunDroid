#!/bin/sh
# Waits until an Android device or emulator is ready for instrumented tests or screenshots:
# booted, boot animation finished, package manager and input-method service answering, screen
# awake and unlocked, and no system dialog in front. `sys.boot_completed` alone is not enough;
# on a cold emulator boot, services keep starting for a while after it.
#
# Usage: scripts/wait-for-device.sh [-s SERIAL] [-t TIMEOUT_SECONDS]
# Without -s, ANDROID_SERIAL is used, else adb's default device (exactly one must be attached).
set -eu

serial=${ANDROID_SERIAL:-}
timeout=300
while getopts s:t: opt; do
    case $opt in
        s) serial=$OPTARG ;;
        t) timeout=$OPTARG ;;
        *) echo "usage: $0 [-s SERIAL] [-t TIMEOUT_SECONDS]" >&2; exit 2 ;;
    esac
done

adb_() {
    if [ -n "$serial" ]; then adb -s "$serial" "$@"; else adb "$@"; fi
}

deadline=$(( $(date +%s) + timeout ))

# Polls `$2` (a shell snippet) until it succeeds; `$1` describes what we are waiting for.
wait_until() {
    while ! eval "$2" >/dev/null 2>&1; do
        if [ "$(date +%s)" -ge "$deadline" ]; then
            echo "timed out after ${timeout}s waiting for $1" >&2
            exit 1
        fi
        sleep 2
    done
    echo "ready: $1"
}

wait_until "the device to appear" '[ "$(adb_ get-state 2>/dev/null)" = device ]'
wait_until "boot to complete" '[ "$(adb_ shell getprop sys.boot_completed | tr -d "\r")" = 1 ]'
wait_until "the boot animation to stop" '[ "$(adb_ shell getprop init.svc.bootanim | tr -d "\r")" != running ]'
wait_until "the package manager" 'adb_ shell pm path android | grep -q package:'
wait_until "the input-method service" 'adb_ shell ime list -s | grep -q /'

# Wake and unlock the screen (emulators have no secure lock screen).
adb_ shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb_ shell wm dismiss-keyguard >/dev/null 2>&1 || true
wait_until "the screen to be awake and unlocked" \
    'adb_ shell dumpsys power | grep -q "mWakefulness=Awake" && ! adb_ shell dumpsys window | grep -q "mDreamingLockscreen=true"'

# The launcher starts after sys.boot_completed; until it is up, it can come to the front over
# whatever an app or test has just opened.
wait_until "an activity in front" \
    'adb_ shell dumpsys activity activities | grep -qE "topResumedActivity=ActivityRecord"'

# A just-booted system sometimes shows "System UI isn't responding" or similar; anything with
# focus that isn't an app or the launcher means it isn't ready yet.
wait_until "no system dialog in front" \
    '! adb_ shell dumpsys window | grep -E "mCurrentFocus=.*(Application Not Responding|Application Error|NotResponding)"'

echo "device ready${serial:+: $serial}"
