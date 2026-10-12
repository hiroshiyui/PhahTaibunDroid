# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). User-facing release notes in English and Traditional Chinese are kept separately in the F-Droid changelogs under `fastlane/metadata/android/`.

## [Unreleased]

### Added

- Taigi input method accepting POJ (Pe̍h-ōe-jī) and Tâi-lô, freely mixed; both are normalized to canonical Tâi-lô, so spelling differences between the systems (`ch`/`ts`, `oa`/`ua`, `oe`/`ue`, `eng`/`ing`, `ek`/`ik`, `o͘`/`oo`, `ⁿ`/`nn`) always match.
- Tone marks filled in from the dictionary; optional tone numbers (`tai5gi2`) narrow the candidates.
- Optional fuzzy matching: `o`/`o͘`, omitted nasalization, omitted final `h`, omitted or mixed-up entering-tone codas (`p`/`t`/`k`/`h`), nasal codas `m`/`n` and `n`/`ng`, aspiration, pinyin-style voicing (`b`/`p`, `g`/`k`), and accent variants (`ue`/`e`, `i`/`u`, `iⁿ`/`eⁿ`, `j`/`l`).
- Mandarin-pinyin initials accepted in typed input: `d` → `t`, `z`/`zh` → `ts`, `c`/`q` → `tsh`, `x` → `s` (e.g. `dageho` → 逐家好 with the entering-tone and voicing rules on).
- Segmentation of unseparated input (e.g. `lihokamsia` → 你好 感謝), with piece-by-piece candidate selection.
- Output in Hàn-lô or romanization (POJ or Tâi-lô), toggled by tapping the input buffer or swiping the candidate bar up or down; already-selected pieces are re-rendered.
- Learning from the user's choices, disabled in incognito fields; conversion bypassed in password, e-mail and URL fields.
- Lexicon of 86,340 entries compiled from ChhoeTaigi datasets: 台文華文線頂辭典 (CC BY-SA 4.0) and iTaigi 華台對照典 (CC0).
- Setup and settings screen (romanization system, default output, fuzzy rules), in English and Traditional Chinese.
- Screen-reader labels for the keyboard's symbol keys (shift, delete, space, enter, layer and input-method switch), in English and Traditional Chinese.
- Per-app language, independently of the device language: English, Traditional Chinese or Taigi in the app's own settings on every supported Android version; on Android 13+ English and Traditional Chinese can also be chosen in the system's App info → Language.
- Taigi UI in Hàn-lô (`nan-Hant-TW`). Android's settings don't list `nan`, so it is chosen in the app; the keyboard follows a language change without restarting.
- GPL-3.0-or-later license, third-party `NOTICE`, and bilingual `README.md`.
- F-Droid metadata (English and Traditional Chinese), privacy policy, and GitHub Actions CI running unit tests, lint and a debug build.

### Changed

- Cloud backup is disabled: settings and word history are no longer included in Google Drive backups (device-to-device transfer still works).
- Project renamed to Phah Taibun Droid to avoid a naming conflict with [rime-phah-taibun](https://github.com/soanseng/rime-phah-taibun). The application ID is now `org.ghostsinthelab.app.phahtaibundroid`; test installs under the old ID must be uninstalled separately.
