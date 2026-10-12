# TODO

Known gaps and planned work. Check items off (or remove them) as they land, and record user-visible changes in [CHANGELOG.md](./CHANGELOG.md).

## Keyboard UI

- [x] Hide the keyboard's own 🌐 key when the system already shows an IME-switcher button in the navigation bar (both appear on a Pixel 8a emulator).
- [x] Tone-hint labels `ah` / `a̍h` overlap the digits on the 4 and 8 keys.
- [x] The settings screen's "Try it" field stays hidden behind the keyboard; scroll it into view on focus.
- [x] Replace the Android Studio template launcher icon.

## Input engine

- [ ] Physical (hardware) keyboard input via `onKeyDown`; only the on-screen keys work today.
- [ ] Better ranking data: weights only count how often an entry appears in the sources, so there is no real word-frequency information. Derive frequencies from iCorpus (see Data).
- [ ] Abbreviated input using syllable initials only (e.g. `tg` → Tâi-gí).
- [ ] In romanization mode, pieces selected separately are joined with spaces even when they form one word.
- [ ] User dictionary for adding custom words.
- [ ] Measure lexicon load time and memory on a real device; switch to a compact binary format if needed.
- [ ] Neutral-tone candidates: offer `--` forms (e.g. 轉--來, 食--飽) from the dictionary and simple rules.
- [ ] `?` wildcard for an unsure syllable or part of one (e.g. `s?ah`).
- [ ] Bigram (word-pair) frequencies so whole words stay ahead of single-character compositions in long input.
- [ ] Cross-check `Romanization` against [KeSi](https://github.com/i3thuan5/KeSi) (MIT), an established POJ↔TL converter, by turning disagreements into test cases.

## Data

Candidates come largely from the resource list in the [寫台文 (rime-phah-taibun) guide](https://taigi.anatomind.com/guide#/research). Anything bundled in the APK must be GPL-3.0-or-later compatible and credited in `NOTICE`.

- [ ] Word frequencies from [iCorpus 臺華平行新聞語料庫, 漢字臺羅版](https://github.com/Taiwanese-Corpus/icorpus_ka1_han3-ji7) (薛丞宏, CC BY 4.0): 2008–2014 news text in Hàn-lô with Tâi-lô readings.
- [ ] Supplement with [新北市 900 例句](https://github.com/Taiwanese-Corpus/Sin1pak8tshi7_2015_900-le7ku3) (MIT) for everyday vocabulary.
- [ ] Check whether 教育部本土語言標注臺灣地名 (Taiwan place names, CC BY 3.0 TW) can be combined with GPL-3.0-or-later; if so, add place names.
- [ ] Ask for permission before using these; they can't be bundled as they stand:
  - [楊允言 word frequencies (Ungian_2009_KIPsupin)](https://github.com/Taiwanese-Corpus/Ungian_2009_KIPsupin): no license stated.
  - 教育部 臺灣台語常用詞辭典 / [KipSutian](https://github.com/ChhoeTaigi/KipSutianDataMirror): CC BY-ND (no derivatives).
  - 教育部 推薦用字 700 字詞: CC BY-NC-ND.
  - 李江却台語文教基金會 (LKK) 漢羅用字表: terms unclear; reportedly non-commercial.
  - 學科術語臺灣台語對譯 (STTI): all rights reserved.
  - ChhoeTaigi's 台日大辭典, Maryknoll, Embree and 甘字典 sets: CC BY-NC-SA (non-commercial).

## Localization

- [ ] Taigi UI locale (`nan-TW`) alongside English and Traditional Chinese.

## Testing & release

- [ ] Instrumented tests for the IME service and keyboard.
- [ ] F-Droid metadata (`fastlane/metadata/android/{en-US,zh-TW}`).
- [ ] `PRIVACY-POLICY.md`.
- [ ] Release signing and CI.
