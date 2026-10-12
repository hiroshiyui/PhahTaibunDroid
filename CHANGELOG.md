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
- Abbreviated input with one letter per syllable (`tg` → 台語, `kms` → 感謝), ranked below fully spelled words.
- `?` wildcard for unsure letters or a whole syllable (`s?ah`, `tai-?`).
- Whole-input phrase candidates for long unseparated input (`tsuekinthinnkhitsinho` → 最近天氣真好), and words that often follow the previously selected one rank higher (word-pair data support; none bundled yet).
- Exact romanization: when every syllable has a tone number (1 and 4 included) or tone mark, the input is offered exactly as typed — first in romanization mode, right after tone-matching words in Hàn-lô mode — including syllables the dictionary lacks (`hiunnh8` → hiu̍nnh); typed `-` and `--` are kept in the output.
- Learning of word pairs from the user's own choices: a word picked after another one ranks higher the next time it follows that word, also across commits; the pairs stay on the device with the rest of the word history.
- Neutral-tone forms: a typed `--` favours neutral-tone entries and offers common suffixes (`tsiahpa--ah` → 食飽矣).
- F-Droid metadata (English and Traditional Chinese), privacy policy, and GitHub Actions CI running unit tests, lint and a debug build.

### Changed

- In romanization mode, pieces selected one by one are joined by what was typed between them (`-` when typed as one run), not always by spaces.
- Romanization: Tâi-lô `ere` takes its tone mark on the second e (`erê`), `o͘ⁿ` is read as `oⁿ`, and POJ `ṳ`/`o̤` are accepted (found by cross-checking with KeSi).
- Cloud backup is disabled: settings and word history are no longer included in Google Drive backups (device-to-device transfer still works).
- Project renamed to Phah Taibun Droid to avoid a naming conflict with [rime-phah-taibun](https://github.com/soanseng/rime-phah-taibun). The application ID is now `org.ghostsinthelab.app.phahtaibundroid`; test installs under the old ID must be uninstalled separately.
