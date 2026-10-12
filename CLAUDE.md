# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Phah Taibun Droid (拍台文) is an Android IME for Taigi (台語, Taiwanese Hokkien). It accepts POJ (白話字) and Tâi-lô (台羅) — mixed freely, with or without tones — and fuzzy/ambiguous spellings, and outputs either Hàn-lô (漢字/漢羅) or romanization. The user toggles output by tapping the input-buffer bar or swiping the candidate strip up/down. Licensed GPL-3.0-or-later.

## Commands

```sh
./gradlew :core:test                    # engine unit tests (pure JVM, fast) — most logic lives here
./gradlew :core:test --tests "org.ghostsinthelab.app.phahtaibundroid.core.EngineTest"            # one class
./gradlew :core:test --tests "org.ghostsinthelab.app.phahtaibundroid.core.EngineTest.convertsUnseparatedInput"
./gradlew :app:assembleDebug            # APK → app/build/outputs/apk/debug/org.ghostsinthelab.app.phahtaibundroid_v<ver>-debug.apk
./gradlew :app:lintDebug
./gradlew bumpPatchVersion              # versionCode+1, patch+1 in app/build.gradle.kts

./scripts/fetch-lexicon-sources.sh      # download ChhoeTaigi CSVs into data/sources/ (gitignored)
./gradlew :tools:dictbuilder:buildLexicon   # compile them into app/src/main/assets/lexicon.tsv + bigrams.tsv (committed)
```

Testing the IME on a device/emulator:
```sh
scripts/wait-for-device.sh -s emulator-5554   # wait until a (cold-booted) device is really usable, not just sys.boot_completed
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest   # instrumented tests; pin the serial so a connected phone isn't used too
adb shell ime enable org.ghostsinthelab.app.phahtaibundroid/.ime.PhahTaibunInputMethodService
adb shell ime set    org.ghostsinthelab.app.phahtaibundroid/.ime.PhahTaibunInputMethodService
```
On emulators the service always shows its keyboard (`onEvaluateInputViewShown`), even though the host keyboard counts as a hardware keyboard, so the "Show on-screen keyboard" setting isn't needed.
`MainActivity` (setup + settings) has a "Try it" text field for testing. README/F-Droid screenshots are captured by the `take-screenshots` skill (`.claude/skills/take-screenshots/scripts/take_screenshots.py`, emulator only).

## Architecture

Three Gradle modules; dependencies flow `app → core ← tools:dictbuilder`.

- **`:core`** — pure Kotlin/JVM (no Android). All input logic, so it is unit-testable and shared with the dictionary builder.
  - `romanization/Romanization` — parses POJ/Tâi-lô (Unicode diacritics, numeric tones, ASCII stand-ins `oo`/`ou`/`nn`) into `Syllable`s and renders them back in either system with correct tone-mark placement (the systems differ: e.g. POJ `hôa`/`chúi` vs Tâi-lô `huâ`/`tsuí`). Typed input (not dictionary data) also accepts Mandarin-pinyin initials via `respellPinyinInitial` (`d`→`t`, `z`/`zh`→`ts`, `c`/`q`→`tsh`, `x`→`s`), applied in `Segmenter`. **The canonical pivot form is lowercase ASCII Tâi-lô** (`Syllable.base`, e.g. `tshiunn`); POJ spellings are normalized away at parse time, so POJ⇄Tâi-lô differences are never "fuzzy".
  - `romanization/FuzzyRule` — optional user-selected merges (o/oo, nasal, glottal h, entering-tone codas, nasal codas m/n and n/ng, aspiration, pinyin-style b/g voicing, accent pairs). Matching is done on "loose" keys = canonical bases collapsed by the enabled rules; exact matches still score higher.
  - `lexicon/Lexicon` — in-memory index over `LexiconEntry` keyed by space-joined loose syllable sequences: flat arrays (sorted keys, entries grouped by key, group offsets) searched by binary search, which also serve prefix (still-typing) lookups; the initials index for abbreviations is built lazily. Keys are built without per-syllable strings (`IndexSupport.kt`). Keep load-time garbage low: the index used to dominate the keyboard's memory. Changing fuzzy rules means rebuilding the index via `withRules`. Carries `lexicon/Bigrams` (word-pair counts between entries, from `bigrams.tsv`).
  - `engine/Segmenter` — splits raw keystrokes (`taigi`, `tai5gi2`, `am-gong--e`) into syllables against the lexicon's syllable inventory; digits are tones, `-` separates, `--` marks neutral tone. Produces several ranked segmentations; tracks raw-buffer offsets (`rawStart`/`rawEnd`). Besides COMPLETE/PARTIAL/UNKNOWN pieces it emits INITIAL (abbreviated input, one letter per syllable: `tg`) and WILDCARD (`?`) pieces; `engine/SyllablePatterns` turns those into syllable patterns that `Lexicon` matches through an initials index (`lookupMatching`), ranked below spelled matches.
  - `engine/CandidateEngine` — looks up every segmentation's prefixes, scores (full-input coverage ≫ partial, exact spelling, tone agreement, dictionary weight, user history), and always adds a pure-romanization candidate. When every syllable carries a tone (digit or mark) that candidate is `literal`: candidates are ordered agreeing-with-typed-tones, then the literal, then the rest, and `Composer` puts the literal first in romanization mode; `Segmenter` then also accepts a validly spelled syllable outside the lexicon if a tone delimits it. Separators the user typed (`InputSyllable.separated`) override the dictionary's spaces in romanized output, and a typed `--` always marks neutral tone. `engine/FrequencyHistory` (the `UserHistory` implementation, saved as `history.tsv`) counts the user's word choices and word pairs; pairs boost a candidate after the previous word (`Composer` keeps the last committed word until punctuation, Enter or a cursor move). `engine/PhraseFinder` adds a whole-input multi-word candidate (Viterbi over the entry lattice with a bigram language model) and boosts words that follow the previously selected one. A `Candidate` holds both Hàn-lô and POJ/Tâi-lô renderings, `consumed` raw length and the `entries` it is made of.
  - `engine/Composer` — per-session state machine: raw buffer, partially selected pieces, output mode. Selected pieces are stored as candidates (not strings) so toggling mode re-renders the whole composition. Each selection keeps the separator typed after it, which joins it to the next piece in romanization (`-`, `--` or a space). Returns text to commit; never touches Android APIs.
- **`:app`** — thin Android layer.
  - `ime/PhahTaibunInputMethodService` — maps `KeyAction`s to `Composer` calls and mirrors `Composer.preview()` into the editor's composing region. Implements `LifecycleOwner`/`SavedStateRegistryOwner` because Compose needs them on the IME window's decor view. Bypasses composition in password/email/URL/number fields and disables history learning when the editor sets `IME_FLAG_NO_PERSONALIZED_LEARNING`.
  - `ime/ui/KeyboardScreen` — Compose keyboard rendering a `KeyboardState`; tone-number row, QWERTY, symbols layer.
  - `ime/LexiconRepository` — loads `assets/lexicon.tsv` and `assets/bigrams.tsv` once per process off the main thread.
  - `settings/ImePreferences` — SharedPreferences shared by the settings screen and the service (re-read in `onStartInputView`).
- **`:tools:dictbuilder`** — JVM CLI that parses ChhoeTaigi CSVs with the `:core` parser and writes the lexicon TSV (`hanlo<TAB>numeric-tailo<TAB>weight[<TAB>hanlo-poj]`, numeric Tâi-lô already canonical so loading needs no full parse). It can also count word and word-pair frequencies in a line-aligned Hàn-lô/Tâi-lô corpus in `data/sources/icorpus/` (`Corpus.kt`): weight = dictionary prior + `Bigrams.CORPUS_WEIGHT` × corpus count; frequent corpus words missing from the dictionaries are added; pairs seen ≥ 2 times go to `bigrams.tsv` (`hanlo1<TAB>numeric1<TAB>hanlo2<TAB>numeric2<TAB>count`, resolved against lexicon entries at load). No corpus is bundled today, so the committed `bigrams.tsv` is header-only: iCorpus traces back to CC BY-NC-SA 4.0 data and is used for local experiments only. Regenerate both assets together.

## Conventions and constraints

- Put new input logic in `:core` with tests, not in the service or UI.
- Romanized text: compare in canonical/NFD-aware form, output NFC. `o͘` uses U+0358; POJ nasal is `ⁿ` (U+207F), written before a final `h` (`hiaⁿh`).
- Only permissively licensed dictionary data may be bundled (currently Taihoa 台華線頂對照典 CC BY-SA 4.0, iTaigi CC0). iCorpus 臺華平行新聞語料庫 traces back to CC BY-NC-SA 4.0 data, so nothing derived from it may be committed. ChhoeTaigi's other datasets are NonCommercial or NoDerivatives — don't add them.
- Every external/third-party resource (library, dataset, font, icon, tool) must have its copyright and license recorded in the root `NOTICE` file.
- Commits: plain imperative subject, `--signoff`, and **no Claude/AI attribution** of any kind (see `.claude/skills/commit-and-push`).
- Kotlin: AGP 9 built-in Kotlin for `:app` (don't add `org.jetbrains.kotlin.android`); JVM modules use `kotlin.jvm`. Dependencies go through `gradle/libs.versions.toml`. R8 keep rules live in `app/src/main/keepRules/`. Configuration cache is on — custom tasks must be compatible.
- User-facing strings exist in `values/` (en), `values-zh-rTW/` (Traditional Chinese, Mandarin wording) and `values-b+nan+Hant+TW/` (Taigi in Hàn-lô, MOE recommended characters). Android's settings don't list `nan`, so `MainActivity` has its own language picker. `settings/AppLanguage` stores the choice (`LocaleManager` on API 33+, our preferences below) and wraps contexts with it: `MainActivity.attachBaseContext` below API 33, and the IME service's view on all versions (a running service misses per-app locale changes).
