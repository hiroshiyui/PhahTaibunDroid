#!/usr/bin/env python3
"""Capture the README keyboard screenshots on an Android emulator.

Types a word into the settings screen's "Try it" field with the on-screen keyboard and saves
the keyboard area in Hàn-lô mode and, after tapping the input buffer, in romanization mode.
Settings, word history, the app locale and the selected IME are restored afterwards.

Nothing in the output directory is replaced unless every check passes.

Requires adb on PATH and Pillow (for cropping). Run from the repository root after
`./gradlew :app:assembleDebug`.
"""

import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

from PIL import Image

PACKAGE = "org.ghostsinthelab.app.phahtaibundroid"
IME = f"{PACKAGE}/.ime.PhahTaibunInputMethodService"
ACTIVITY = f"{PACKAGE}/.MainActivity"
PREFS = "shared_prefs/settings.xml"
HISTORY = "files/history.tsv"

# Known settings so the screenshots don't depend on whatever the device was left with.
SETTINGS_XML = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="roman_system">{system}</string>
    <string name="output_mode">HANLO</string>
    <set name="fuzzy_rules">
        <string>O_OO</string>
        <string>NASAL</string>
    </set>
</map>
"""


class Abort(Exception):
    pass


class Device:
    def __init__(self, serial):
        self.serial = serial

    def adb(self, *args, check=True, input=None, binary=False):
        result = subprocess.run(
            ["adb", "-s", self.serial, *args],
            input=input, capture_output=True, text=not binary,
        )
        if check and result.returncode != 0:
            raise Abort(f"adb {' '.join(args)} failed: {result.stderr or result.stdout}")
        return result.stdout

    def shell(self, command, check=True):
        return self.adb("shell", command, check=check)

    def run_as(self, command, input=None, check=True):
        """Runs [command] in the app's data directory (debug builds only); [input] goes to stdin."""
        return self.adb("shell", f"run-as {PACKAGE} sh -c '{command}'", input=input, check=check)

    def windows(self):
        """Parsed `uiautomator dump --windows`: list of (window element, type)."""
        for _ in range(3):
            self.shell("uiautomator dump --windows /sdcard/ss-dump.xml >/dev/null", check=False)
            xml = self.shell("cat /sdcard/ss-dump.xml", check=False)
            if xml.startswith("<?xml"):
                root = ET.fromstring(xml)
                return [(w, w.get("type")) for w in root.iter("window")]
            time.sleep(0.5)
        raise Abort("uiautomator dump failed")

    def screencap(self, path):
        with open(path, "wb") as f:
            f.write(subprocess.run(["adb", "-s", self.serial, "exec-out", "screencap", "-p"],
                                   capture_output=True, check=True).stdout)


def bounds(node_or_window):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node_or_window.get("bounds")))
    return x1, y1, x2, y2


def centre(node):
    x1, y1, x2, y2 = bounds(node)
    return (x1 + x2) // 2, (y1 + y2) // 2


def pick_serial(requested):
    if requested:
        return requested
    lines = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True).stdout
    emulators = [l.split()[0] for l in lines.splitlines()[1:] if l.endswith("\tdevice") and l.startswith("emulator-")]
    if len(emulators) != 1:
        raise Abort(f"need exactly one running emulator (found {emulators or 'none'}); "
                    "boot one or pass --serial. Physical devices are never picked automatically.")
    return emulators[0]


def wait_for(description, predicate, timeout=30.0, interval=0.5):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = predicate()
        if value is not None and value is not False:
            return value
        time.sleep(interval)
    raise Abort(f"timed out waiting for {description}")


def ime_window(dev):
    for window, kind in dev.windows():
        if kind == "TYPE_INPUT_METHOD":
            return window
    return None


def texts(window):
    return [n for n in window.iter("node") if n.get("text")]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", help="adb serial (default: the only running emulator)")
    parser.add_argument("--text", default="taigi", help="what to type (letters only)")
    parser.add_argument("--expect-hanlo", default="台語", help="candidate expected in Hàn-lô mode")
    parser.add_argument("--expect-roman", default="Tâi-gí", help="candidate expected in romanization mode")
    parser.add_argument("--system", choices=["TAILO", "POJ"], default="TAILO", help="romanization system")
    parser.add_argument("--out-dir", default="media", help="where keyboard-hanlo.png / keyboard-tailo.png go")
    parser.add_argument("--fastlane", action="store_true",
                        help="also copy them to fastlane phoneScreenshots (1_/2_<locale>.png)")
    parser.add_argument("--failure-screenshot", help="if a check fails, save the whole screen here")
    args = parser.parse_args()

    if not os.path.isfile("settings.gradle.kts"):
        raise Abort("run from the repository root")
    apks = sorted(glob.glob("app/build/outputs/apk/debug/*.apk"), key=os.path.getmtime)
    if not apks:
        raise Abort("no debug APK; run ./gradlew :app:assembleDebug first")

    dev = Device(pick_serial(args.serial))
    print(f"device: {dev.serial}")
    if dev.shell("getprop sys.boot_completed").strip() != "1":
        raise Abort("device has not finished booting")

    # Read before installing: reinstalling the selected IME resets the selection.
    previous_ime = dev.shell("settings get secure default_input_method").strip()
    dev.adb("install", "-r", apks[-1])
    previous_show = dev.shell("settings get secure show_ime_with_hard_keyboard").strip()
    previous_locale = re.sub(r".*are \[|\].*", "", dev.shell(f"cmd locale get-app-locales {PACKAGE}").strip())

    dev.shell(f"am force-stop {PACKAGE}")
    saved_prefs = dev.run_as(f"cat {PREFS}", check=False)
    had_history = dev.run_as(f"test -f {HISTORY} && echo yes", check=False).strip() == "yes"
    out_tmp = tempfile.mkdtemp(prefix="screenshots-")
    succeeded = False
    try:
        # Known state: English UI, chosen romanization, Hàn-lô output, default fuzzy rules, no history.
        dev.shell(f"cmd locale set-app-locales {PACKAGE} --locales en-US")
        settings = SETTINGS_XML.format(system=args.system)
        dev.run_as(f"mkdir -p shared_prefs && cat > {PREFS}", input=settings)
        if dev.run_as(f"cat {PREFS}") != settings:
            raise Abort("could not write the app's settings (is this a debug build?)")
        if had_history:
            dev.run_as(f"mv {HISTORY} {HISTORY}.screenshots")
        # A freshly installed IME may take a moment to be registered.
        wait_for("the IME to be enabled",
                 lambda: "Unknown input method" not in dev.shell(f"ime enable {IME}", check=False), timeout=15)
        dev.shell(f"ime set {IME}")

        dev.shell(f"am start -W -n {ACTIVITY}")

        def app_on_top():
            return PACKAGE in dev.shell("dumpsys activity activities | grep -m1 topResumedActivity", check=False)
        wait_for("the settings screen", app_on_top)

        def find_field():
            for window, kind in dev.windows():
                if kind == "TYPE_APPLICATION":
                    for n in window.iter("node"):
                        if n.get("class") == "android.widget.EditText" and n.get("package") == PACKAGE:
                            return n
            dev.shell("input swipe 540 1700 540 500 200")
            return None
        field = wait_for("the Try it field", find_field, timeout=20, interval=0.2)
        dev.shell("input tap %d %d" % centre(field))

        def keyboard():
            window = ime_window(dev)
            return window if window is not None and any(n.get("text") == "q" for n in window.iter("node")) else None
        window = wait_for("the keyboard", keyboard)
        ime_top = bounds(window)[1]

        # Letter keys: single-letter labels; when a letter also appears higher up (candidate strip),
        # the lowest node is the key.
        keys = {}
        for n in texts(window):
            t = n.get("text")
            if len(t) == 1 and t.isalpha() and (t not in keys or centre(n)[1] > keys[t][1]):
                keys[t] = centre(n)
        wait_for("the dictionary to load",
                 lambda: not any(n.get("text", "").startswith("Loading") for n in texts(ime_window(dev))),
                 timeout=60)
        for ch in args.text:
            if ch not in keys:
                raise Abort(f"no key for {ch!r}")
            dev.shell("input tap %d %d" % keys[ch])
            time.sleep(0.1)

        def has_candidate(text):
            def check():
                w = ime_window(dev)
                return w if w is not None and any(n.get("text") == text for n in w.iter("node")) else None
            return check

        def capture(name):
            path = os.path.join(out_tmp, name)
            dev.screencap(path)
            image = Image.open(path)
            if image.height <= ime_top:
                raise Abort("screenshot smaller than expected")
            image.crop((0, ime_top, image.width, image.height)).save(path, optimize=True)
            print(f"captured {name}")

        window = wait_for(f"candidate {args.expect_hanlo}", has_candidate(args.expect_hanlo))
        if not app_on_top():
            raise Abort("another app came to the front")
        capture("keyboard-hanlo.png")

        # The input buffer is the topmost text in the keyboard window; tapping it toggles the output.
        buffer = min(texts(window), key=lambda n: bounds(n)[1])
        dev.shell("input tap %d %d" % centre(buffer))
        wait_for(f"candidate {args.expect_roman}", has_candidate(args.expect_roman))
        if not app_on_top():
            raise Abort("another app came to the front")
        capture("keyboard-tailo.png")

        os.makedirs(args.out_dir, exist_ok=True)
        for name in ("keyboard-hanlo.png", "keyboard-tailo.png"):
            shutil.copyfile(os.path.join(out_tmp, name), os.path.join(args.out_dir, name))
            print(f"wrote {os.path.join(args.out_dir, name)}")
        if args.fastlane:
            for locale in ("en-US", "zh-TW"):
                target = f"fastlane/metadata/android/{locale}/images/phoneScreenshots"
                os.makedirs(target, exist_ok=True)
                for i, name in enumerate(("keyboard-hanlo.png", "keyboard-tailo.png"), 1):
                    shutil.copyfile(os.path.join(out_tmp, name), os.path.join(target, f"{i}_{locale}.png"))
            print("wrote fastlane phoneScreenshots")
        succeeded = True
    finally:
        if not succeeded and args.failure_screenshot:
            dev.screencap(args.failure_screenshot)
            print(f"screen at failure saved to {args.failure_screenshot}", file=sys.stderr)
        dev.shell("input keyevent KEYCODE_BACK", check=False)
        dev.shell(f"am force-stop {PACKAGE}", check=False)
        if saved_prefs.startswith("<?xml"):
            dev.run_as(f"cat > {PREFS}", input=saved_prefs, check=False)
        else:
            dev.run_as(f"rm -f {PREFS}", check=False)
        dev.run_as(f"rm -f {HISTORY}", check=False)
        if had_history:
            dev.run_as(f"mv {HISTORY}.screenshots {HISTORY}", check=False)
        dev.shell(f"cmd locale set-app-locales {PACKAGE} --locales '{previous_locale}'", check=False)
        if previous_ime and previous_ime != "null" and previous_ime != IME:
            dev.shell(f"ime set {previous_ime}", check=False)
        if previous_show in ("0", "1"):
            dev.shell(f"settings put secure show_ime_with_hard_keyboard {previous_show}", check=False)
        shutil.rmtree(out_tmp, ignore_errors=True)


if __name__ == "__main__":
    try:
        main()
    except Abort as e:
        print(f"error: {e}", file=sys.stderr)
        sys.exit(1)
