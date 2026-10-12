---
name: docs-engineering
description: Writing/updating project documentation (README, PRIVACY-POLICY, NOTICE, changelogs) and maintaining F-Droid metadata. Use when the user asks to update docs, write changelogs, or modify F-Droid store listings.
argument-hint: task description
---

# Document Engineering

You are performing documentation tasks for Phah Taibun Droid (拍台文), a Taigi (台語) input method.

## Bilingual Convention

This project maintains documentation in both **English (en-US)** and **Traditional Chinese (zh-TW)**. When creating or updating user-facing text, always produce both language versions unless instructed otherwise.

- README.md uses interleaved bilingual sections (English heading/paragraph followed by Chinese equivalent).
- F-Droid metadata keeps separate locale directories.

## Project Documentation Files

| File | Purpose |
|------|---------|
| `README.md` | Project overview, usage guide, build instructions (bilingual) |
| `PRIVACY-POLICY.md` | Privacy policy |
| `NOTICE` | Copyright and license information for all external, third-party resources (data, libraries, tooling) |
| `LICENSE` | GPL-3.0-or-later |
| `TODO.md` | Known gaps and planned work as grouped checklists (English only, developer-facing) |
| `CHANGELOG.md` | Developer-facing history in Keep a Changelog format, `## [Unreleased]` on top (English only; user-facing bilingual notes go in the F-Droid changelogs) |
| `CLAUDE.md` | Guidance for Claude Code |

Create any of these that do not exist yet when the task calls for them.

When adding or updating any external resource (dependency, dataset, font, icon, tool), record its copyright and license in `NOTICE`. Check `gradle/libs.versions.toml` and the module build files for current dependencies and their licenses. The bundled lexicon is compiled from ChhoeTaigi datasets (see the `SOURCES` list in `tools/dictbuilder/.../Main.kt` and the header of `app/src/main/assets/lexicon.tsv`); each dataset's original authors, the ChhoeTaigi project, and the dataset license (CC BY-SA 4.0, CC0) must be credited.

## F-Droid Metadata

Metadata lives under `fastlane/metadata/android/` with two locales:

```
fastlane/metadata/android/
  en-US/
    title.txt              # App name (max 50 chars)
    short_description.txt  # Store tagline (max 80 chars)
    full_description.txt   # Full store listing (max 4000 chars)
    changelogs/<versionCode>.txt  # Per-version changelog
    images/                # Screenshots and graphics
  zh-TW/
    (same structure)
```

### Changelogs

- Changelog filenames use **versionCode** (integer), not versionName. Find the current versionCode in `app/build.gradle.kts` (`versionCode = ...`).
- Write changelogs in a friendly, first-person tone consistent with existing entries (see `fastlane/metadata/android/en-US/changelogs/` for examples).
- Always create both `en-US` and `zh-TW` versions.
- Max 500 characters per changelog file.

### Store Descriptions

- `full_description.txt` is written in first person from the app's perspective.
- Use `*` for bullet points in en-US, `＊` (fullwidth asterisk) in zh-TW.

## Writing Style

- Keep language clear and approachable.
- For zh-TW text, use Traditional Chinese characters only. Do not use Simplified Chinese.
- Preserve the existing tone: the app "speaks" in first person in store listings and changelogs; README and other docs use standard third-person technical writing.
- Use the established names for the writing systems: POJ / Pe̍h-ōe-jī (白話字), Tâi-lô (台羅, 臺灣台語羅馬字拼音方案), Hàn-lô (漢羅), Taigi (台語). Write romanized Taigi with proper diacritics (`Tâi-gí`, not `Tai-gi`).
- **Respect external resource providers' efforts.** When describing problems in upstream projects, dictionary datasets, dependencies, or platforms (e.g., ChhoeTaigi, iTaigi, libraries), use softened wording. Prefer "issue", "behavior", or "limitation" over "bug"; prefer "resolved" or "addressed" over "fixed the bug". Frame workarounds as collaborative rather than corrective. This applies to both en-US and zh-TW text (e.g., use 「問題」 rather than 「錯誤」 or 「bug」 when referring to upstream).

## Task: $ARGUMENTS
