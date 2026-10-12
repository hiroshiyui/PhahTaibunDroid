# TODO

Known gaps and planned work. Check items off (or remove them) as they land, and record user-visible changes in [CHANGELOG.md](./CHANGELOG.md).

## Keyboard UI

- [x] Hide the keyboard's own 🌐 key when the system already shows an IME-switcher button in the navigation bar (both appear on a Pixel 8a emulator).
- [ ] Tone-hint labels `ah` / `a̍h` overlap the digits on the 4 and 8 keys.
- [ ] The settings screen's "Try it" field stays hidden behind the keyboard; scroll it into view on focus.
- [ ] Replace the Android Studio template launcher icon.

## Input engine

- [ ] Physical (hardware) keyboard input via `onKeyDown`; only the on-screen keys work today.
- [ ] Better ranking data: weights only count how often an entry appears in the sources, so there is no real word-frequency information.
- [ ] Abbreviated input using syllable initials only (e.g. `tg` → Tâi-gí).
- [ ] In romanization mode, pieces selected separately are joined with spaces even when they form one word.
- [ ] User dictionary for adding custom words.
- [ ] Measure lexicon load time and memory on a real device; switch to a compact binary format if needed.

## Data

- [ ] Look for more permissively licensed datasets. Ask the holders of NonCommercial / NoDerivatives datasets (e.g. MOE 臺灣台語常用詞辭典, CC BY-ND) for permission before using them.

## Localization

- [ ] Taigi UI locale (`nan-TW`) alongside English and Traditional Chinese.

## Testing & release

- [ ] Instrumented tests for the IME service and keyboard.
- [ ] F-Droid metadata (`fastlane/metadata/android/{en-US,zh-TW}`).
- [ ] `PRIVACY-POLICY.md`.
- [ ] Release signing and CI.
