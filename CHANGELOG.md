# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). User-facing release notes in English and Traditional Chinese are kept separately in the F-Droid changelogs under `fastlane/metadata/android/`.

## [Unreleased]

### Added

- Taigi input method accepting POJ (Pe̍h-ōe-jī) and Tâi-lô, freely mixed; both are normalized to canonical Tâi-lô, so spelling differences between the systems (`ch`/`ts`, `oa`/`ua`, `oe`/`ue`, `eng`/`ing`, `ek`/`ik`, `o͘`/`oo`, `ⁿ`/`nn`) always match.
- Tone marks filled in from the dictionary; optional tone numbers (`tai5gi2`) narrow the candidates.
- Optional fuzzy matching: `o`/`o͘`, omitted nasalization, omitted final `h`, aspiration, and accent variants (`ue`/`e`, `i`/`u`, `iⁿ`/`eⁿ`, `j`/`l`).
- Segmentation of unseparated input (e.g. `lihokamsia` → 你好 感謝), with piece-by-piece candidate selection.
- Output in Hàn-lô or romanization (POJ or Tâi-lô), toggled by tapping the input buffer or swiping the candidate bar up or down; already-selected pieces are re-rendered.
- Learning from the user's choices, disabled in incognito fields; conversion bypassed in password, e-mail and URL fields.
- Lexicon of 86,340 entries compiled from ChhoeTaigi datasets: 台文華文線頂辭典 (CC BY-SA 4.0) and iTaigi 華台對照典 (CC0).
- Setup and settings screen (romanization system, default output, fuzzy rules), in English and Traditional Chinese.
- GPL-3.0-or-later license, third-party `NOTICE`, and bilingual `README.md`.

### Changed

- Project renamed to Phah Taibun Droid to avoid a naming conflict with [rime-phah-taibun](https://github.com/soanseng/rime-phah-taibun). The application ID is now `org.ghostsinthelab.app.phahtaibundroid`; test installs under the old ID must be uninstalled separately.
