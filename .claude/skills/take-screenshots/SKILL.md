---
name: take-screenshots
description: Take the keyboard screenshots on an emulator and update them in README.md (media/) and, optionally, the F-Droid metadata. Use when the user asks to take, retake, refresh or update screenshots.
argument-hint: optional notes, e.g. "also update fastlane" or a different word to type
---

# Take Screenshots

You are refreshing the screenshots of Phah Taibun Droid. `README.md` shows two keyboard screenshots side by side:

| File | Shows |
|------|-------|
| `media/keyboard-hanlo.png` | `taigi` typed, Hàn-lô output (candidates 台語, 帶疑, 臺語…) |
| `media/keyboard-tailo.png` | the same input after tapping the input buffer: Tâi-lô output (Tâi-gí, tài-gî…) |

The F-Droid listing uses the same images as `fastlane/metadata/android/{en-US,zh-TW}/images/phoneScreenshots/{1,2}_<locale>.png`.

`scripts/take_screenshots.py` does the capture. It types with the real on-screen keyboard in the settings screen's "Try it" field and crops each screenshot to the keyboard window. Before capturing it sets known settings: English UI, Tâi-lô, Hàn-lô output, default fuzzy rules, and no word history. Afterwards it restores the app's settings, word history and language, and the previously selected keyboard. It writes to the output directory only if every check passes, so a failed run never leaves a wrong image (for example, of another app) in `media/`.

## Workflow

1. **Build** the debug APK: `./gradlew :app:assembleDebug`.
2. **Use an emulator, never a physical device.** The script only picks a device automatically if exactly one emulator is running, and it never picks a physical one. If none is running, boot the `Pixel_8a` AVD (1080×2400; the README images are 1080 px wide) headless, with logs in the scratchpad:
   ```sh
   (nohup ~/Android/Sdk/emulator/emulator -avd Pixel_8a -no-window -no-audio -no-snapshot-save > <scratchpad>/emulator.log 2>&1 &)
   scripts/wait-for-device.sh -s emulator-5554   # booted, services up, screen unlocked, no system dialog
   ```
   With `-no-snapshot-save`, the emulator boots from its last saved snapshot every time, and the app is reinstalled by the script anyway.
3. **Capture into the scratchpad first** and look at both images before replacing anything:
   ```sh
   python3 .claude/skills/take-screenshots/scripts/take_screenshots.py --out-dir <scratchpad>/screenshots
   ```
   Check that they show the keyboard, not another app. The first candidate should be 台語 in the Hàn-lô shot and Tâi-gí in the Tâi-lô shot, the space bar should read 漢 and Tâi-lô respectively, and nothing should be cut off.
4. **Update the repository** once they look right:
   ```sh
   python3 .claude/skills/take-screenshots/scripts/take_screenshots.py            # media/ only
   python3 .claude/skills/take-screenshots/scripts/take_screenshots.py --fastlane # also F-Droid phoneScreenshots
   ```
   Or copy the checked images from the scratchpad. Update the F-Droid copies too, unless the user says otherwise, so the store listing doesn't fall behind.
5. **Check `README.md`.** The images' alt text and the caption under them name the candidates shown. If the candidate list changed, for example after a lexicon or ranking change, update both languages in the alt text.
6. **Stop the emulator** if you booted it: `adb -s emulator-5554 emu kill`.
7. Commit only when the user asks (see `/commit-and-push`), e.g. `Update keyboard screenshots in README`.

## Options

| Option | Default | Use |
|--------|---------|-----|
| `--serial` | the only running emulator | pick a device explicitly |
| `--text` | `taigi` | letters to type |
| `--expect-hanlo` / `--expect-roman` | `台語` / `Tâi-gí` | a candidate to wait for in each mode |
| `--system` | `TAILO` | `POJ` for a POJ romanization shot (the file is still named `keyboard-tailo.png`) |
| `--out-dir` | `media` | where to write the two PNGs |
| `--fastlane` | off | also write the F-Droid phoneScreenshots |
| `--failure-screenshot` | off | if a check fails, save the whole screen to this path (use the scratchpad) |

## Troubleshooting

Rerun with `--failure-screenshot <scratchpad>/fail.png` and look at the image to see what was on screen when a check failed.

- **"timed out waiting for the keyboard":** the emulator reports a hardware keyboard. The service forces its view to show on emulators, but if the device isn't recognised as one, run `adb shell settings put secure show_ime_with_hard_keyboard 1`. The script restores that setting afterwards.
- **"Unknown input method" right after installing:** the script retries for 15 s. If it keeps failing, the APK didn't install; check the `adb install` output.
- **Different candidates:** ranking depends on the lexicon (`app/src/main/assets/lexicon.tsv`) and the default fuzzy rules. The script clears word history and resets settings during the run, so differences come from the code or data, not the device.
