---
name: code-review-and-security-audit
description: Review code for quality, correctness, and security vulnerabilities. Use when the user asks to review code, audit for security issues, or check for bugs and anti-patterns.
argument-hint: file path, component name, or scope of review
---

# Code Review and Security Audit

You are performing code review and security auditing for Phah Taibun Droid.

## Scope

This skill covers two complementary concerns:

1. **Code Review** — correctness, readability, maintainability, and adherence to project conventions.
2. **Security Audit** — identifying vulnerabilities, unsafe patterns, and potential attack surfaces.

## Review Checklist

### Code Quality

- Null safety: proper use of Kotlin null-safe operators; avoid `!!` unless justified.
- Thread safety: correct use of coroutines, synchronized access to shared state (e.g. the lexicon loaded off the main thread, `FrequencyHistory`).
- Resource management: no leaked streams or readers.
- Error handling: appropriate use of try/catch; no silently swallowed exceptions.
- Consistency: follows existing patterns — input logic belongs in the pure-Kotlin `:core` module; the app module stays a thin layer over `Composer`.
- No dead code, unused imports, or redundant logic.

### Code Smells

- **Long methods**: methods doing too many things; should be broken into smaller, focused functions.
- **Large classes**: classes with too many responsibilities; consider splitting by concern.
- **Duplicated code**: repeated logic that should be extracted into a shared function.
- **Deep nesting**: excessive `if`/`when`/`try` nesting that harms readability; consider early returns or extraction.
- **Magic numbers/strings**: unexplained literal values that should be named constants.
- **Feature envy**: a method that uses another class's data more than its own.
- **Inappropriate intimacy**: classes that depend too heavily on each other's internals.
- **Primitive obsession**: overuse of primitives where a domain type (enum, data class) would be clearer.
- **Long parameter lists**: functions with many parameters; consider grouping into a data class or builder.
- **Mutable shared state**: prefer immutable data and local state; flag unnecessary `var` or global mutable collections.

### Code Refactoring Suggestions

- **Extract method**: identify blocks of code within a method that perform a distinct task and can be extracted.
- **Extract class/interface**: when a class handles multiple responsibilities, suggest splitting into focused classes or introducing an interface.
- **Replace conditional with polymorphism**: complex `when`/`if-else` chains that switch on type can often be replaced with polymorphic dispatch.
- **Introduce sealed class/enum**: when a set of related constants or states is represented loosely (strings, ints), suggest modeling with a sealed class or enum.
- **Use Kotlin idioms**: replace Java-style patterns with idiomatic Kotlin — e.g., `let`/`apply`/`also` scope functions, destructuring, extension functions, `buildList`/`buildString`.
- **Simplify lifecycle management**: leverage `lifecycleScope`, `repeatOnLifecycle`, or `LifecycleObserver` to reduce manual lifecycle bookkeeping.
- **Improve testability**: flag Android-dependent code that holds logic which could live in `:core` and be unit tested on the JVM.

### Input Engine Correctness

The romanization and matching logic is the heart of this project. Pay special attention to:

- **Unicode normalization**: comparisons of romanized text must not mix NFC and NFD; tone marks and `o͘` (U+0358) are combining characters. Output should be NFC.
- **POJ ⇄ Tâi-lô conversion**: spelling rules (`ch`/`ts`, `oa`/`ua`, `oe`/`ue`, `eng`/`ing`, `ek`/`ik`, `o͘`/`oo`, `ⁿ`/`nn`) and tone mark placement, which differ between the systems.
- **Raw-buffer offsets**: segmenter and candidate `consumed` offsets must stay consistent with the raw input, or partial selection corrupts the composition.
- **Combinatorial blow-up**: segmentation and fuzzy matching must stay bounded for long unseparated input.

### Android-Specific Security

- **Input method security**: as an IME, this app handles all user keystrokes. Ensure no logging or leaking of user input. Composition must be bypassed (and nothing recorded to user history) in password and other sensitive fields.
- **User history / SharedPreferences**: verify that learned selections are stored only in app-private storage and never include text from sensitive fields.
- **Intent handling**: check for intent injection or unvalidated intent extras.
- **Export controls**: verify that components (activities, services, receivers) are not inadvertently exported; the IME service must require `android.permission.BIND_INPUT_METHOD`.
- **R8**: ensure optimization rules don't strip security-critical code.

### General Security

- No hardcoded secrets, API keys, or credentials.
- No command injection via `Runtime.exec()` or `ProcessBuilder`.
- No path traversal vulnerabilities in file operations.
- No insecure random number generation for security-sensitive operations.
- Dependencies: check for known vulnerabilities in third-party libraries.
- Dictionary tooling (`tools/dictbuilder`) parses downloaded third-party data: check that malformed input is rejected rather than trusted.

## Output Format

Report findings using this structure:

### Critical / High

Issues that must be fixed — security vulnerabilities, crashes, data loss risks.

### Medium

Issues that should be fixed — logic bugs, thread safety concerns, code smell.

### Low / Informational

Suggestions for improvement — style, readability, minor optimizations.

For each finding, include:
- **File and line number** (e.g., `Segmenter.kt:42`)
- **Description** of the issue
- **Impact** — what could go wrong
- **Recommendation** — how to fix it, with code if appropriate

## How to Run

When invoked without arguments, review recently changed files:
```bash
git diff --name-only HEAD~5
```

When invoked with a specific scope (file, directory, or component name), focus the review on that area.

For a full audit, systematically review:
1. Romanization and matching (`core/.../romanization/`, `core/.../engine/Segmenter.kt`)
2. Lexicon and ranking (`core/.../lexicon/`, `CandidateEngine.kt`, `FrequencyHistory.kt`)
3. Composition state (`Composer.kt`)
4. Service layer (`app/.../ime/`)
5. UI layer (keyboard composables) and settings (`app/.../settings/`, `MainActivity.kt`)
6. Configuration (`AndroidManifest.xml`, `res/xml/method.xml`, `app/src/main/keepRules/`)
7. Dictionary tooling (`tools/dictbuilder/`)
8. Dependencies (`gradle/libs.versions.toml`)

## Task: $ARGUMENTS
