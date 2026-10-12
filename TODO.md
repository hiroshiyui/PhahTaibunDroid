# TODO

Known gaps and planned work. Check items off (or remove them) as they land, and record user-visible changes in [CHANGELOG.md](./CHANGELOG.md).

## Keyboard UI

- [x] Hide the keyboard's own 🌐 key when the system already shows an IME-switcher button in the navigation bar (both appear on a Pixel 8a emulator).
- [x] Tone-hint labels `ah` / `a̍h` overlap the digits on the 4 and 8 keys.
- [x] The settings screen's "Try it" field stays hidden behind the keyboard; scroll it into view on focus.
- [x] Replace the Android Studio template launcher icon.

## Input engine

- [ ] Physical (hardware) keyboard input via `onKeyDown`; only the on-screen keys work today.
- [x] Better ranking data: ranking learns word choices and word pairs from the user's own history (`FrequencyHistory`) on top of the dictionary weights; no corpus is bundled (see Data).
- [x] Abbreviated input using syllable initials only (e.g. `tg` → Tâi-gí).
- [x] In romanization mode, pieces selected separately are joined with spaces even when they form one word.
- [ ] User dictionary for adding custom words.
- [ ] Measure lexicon load time and memory on a real device; switch to a compact binary format if needed.
- [x] Neutral-tone candidates: offer `--` forms (e.g. 轉--來, 食--飽) from the dictionary and simple rules.
- [x] `?` wildcard for an unsure syllable or part of one (e.g. `s?ah`).
- [ ] A trailing `?` while composing is now a wildcard, so ending a sentence with `?` needs a commit first; consider treating `?` followed by space/enter as punctuation.
- [x] Bigram (word-pair) support and whole-input phrase candidates, so long input converts in one go (e.g. `tsuekinthinnkhitsinho` → 最近天氣真好). The bundled `bigrams.tsv` is empty until a usable corpus is found.
- [x] Cross-check `Romanization` against [KeSi](https://github.com/i3thuan5/KeSi) (MIT), an established POJ↔TL converter, by turning disagreements into test cases.
- [ ] Decide whether POJ output should write Tâi-lô `ir`/`er` as `ṳ`/`o̤` (as the Taihoa POJ data does) instead of `ir`/`er`.

## Data

Candidates come largely from the resource list in the [寫台文 (rime-phah-taibun) guide](https://taigi.anatomind.com/guide#/research). Anything bundled in the APK must be GPL-3.0-or-later compatible and credited in `NOTICE`.

- [x] Look for a Taigi corpus with a GPL-compatible license for word frequencies and word pairs. Decided to rely on the user's history instead. Usable candidates found, if this is revisited: the Barclay (巴克禮) Bible, POJ + Hàn-lô, public domain per [FHL](https://www.fhl.net/main/fhl/fhl8.html); [Common Voice](https://github.com/common-voice/common-voice) nan-tw sentences, CC0, after removing sentences copied from the MOE dictionary; and ChhoeTaigi 台灣白話基礎語句, CC BY-SA 4.0. `tools/dictbuilder` can already ingest a line-aligned corpus (`Corpus.kt`).
- [x] Check whether 教育部本土語言標注臺灣地名 (Taiwan place names, CC BY 3.0 TW) can be combined with GPL-3.0-or-later: unclear (FSF only states compatibility for CC BY 4.0), so place names are skipped for now.
- [ ] Ask for permission before using these; they can't be bundled as they stand:
  - [楊允言 word frequencies (Ungian_2009_KIPsupin)](https://github.com/Taiwanese-Corpus/Ungian_2009_KIPsupin): no license stated.
  - 教育部 臺灣台語常用詞辭典 / [KipSutian](https://github.com/ChhoeTaigi/KipSutianDataMirror): CC BY-ND (no derivatives).
  - 教育部 推薦用字 700 字詞: CC BY-NC-ND.
  - 李江却台語文教基金會 (LKK) 漢羅用字表: terms unclear; reportedly non-commercial.
  - 學科術語臺灣台語對譯 (STTI): all rights reserved.
  - ChhoeTaigi's 台日大辭典, Maryknoll, Embree and 甘字典 sets: CC BY-NC-SA (non-commercial).
  - [iCorpus 臺華平行新聞語料庫, 漢字臺羅版](https://github.com/Taiwanese-Corpus/icorpus_ka1_han3-ji7) (薛丞宏): labelled CC BY 4.0, but built on the [original iCorpus](https://github.com/sih4sing5hong5/icorpus) text, which is CC BY-NC-SA 4.0 (中央研究院資訊所 陳孟彰). Ask both for permission.
  - [新北市 900 例句](https://github.com/Taiwanese-Corpus/Sin1pak8tshi7_2015_900-le7ku3): the repository code is MIT, but the sentences come from a New Taipei City CD/PDF with unclear rights.

## Localization

- [x] Taigi UI locale (`nan-Hant-TW`, Hàn-lô) alongside English and Traditional Chinese.
- [x] Per-app language on Android 8–12 (stored in app preferences and applied by context wrapping; not yet tested on a pre-13 device).

## Testing & release

- [ ] Instrumented tests for the IME service and keyboard.
- [x] F-Droid metadata (`fastlane/metadata/android/{en-US,zh-TW}`).
- [ ] Store screenshots of the settings screen, and zh-TW screenshots with a Chinese UI (the current ones show only the keyboard).
- [x] `PRIVACY-POLICY.md`.
- [x] Release signing (manual, from the tagged commit; see `.claude/skills/release-engineering`) and CI (GitHub Actions: unit tests, lint, debug build).
