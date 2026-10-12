---
name: commit-and-push
description: Commit code changes and push via Git. Use when the user asks to commit, push, or save their work to the repository.
argument-hint: commit message or description of changes
---

# Commit and Push

You are committing and pushing code changes for Phah Taibun.

## Commit Message Convention

- **Subject line**: imperative mood, concise summary of the change (e.g. "Fix tone mark placement for POJ oa", "Add unit tests for the segmenter.")
- **Body** (optional): explain *why* the change was made, not just what. Include context when the change is non-trivial.
- **Signed-off-by trailer**: all commits use `--signoff` to add the `Signed-off-by` trailer automatically.
- No conventional commits prefix (no `feat:`, `fix:`, etc.) — this project uses plain English subject lines.

### Subject Line Patterns

- `Add ...` — new feature or file
- `Fix ...` — bug fix
- `Update ...` — dependency or content update (including a regenerated lexicon)
- `Remove ...` — deletion of code or files
- `Refactor: ...` — code restructuring
- `Style: ...` — formatting or cosmetic changes
- `Upgrade ...` — dependency version upgrades
- `Release X.Y.Z` — release commits (handled by `/release-engineering`)

## Workflow

1. **Review changes** — run `git status` and `git diff` to understand what will be committed.
2. **Stage files** — add specific files by name rather than `git add -A`. Be careful not to stage:
   - Sensitive files (`.env`, credentials, signing keys, `local.properties`)
   - Build artifacts (`build/`, `*/build/`)
   - Downloaded dictionary sources in `data/sources/` (fetched by `scripts/fetch-lexicon-sources.sh`)

   `app/src/main/assets/lexicon.tsv` **is** committed even though it is generated, so the app builds without network access. When it changes, mention the regeneration in the commit message.
3. **Commit** — use `--signoff` flag. Pass the message via HEREDOC for proper formatting:
   ```bash
   git commit --signoff -m "$(cat <<'EOF'
   Subject line here

   Optional body explaining why.
   EOF
   )"
   ```

   **No AI attribution.** Do not add `Co-Authored-By: Claude ...` trailers, "Generated with Claude Code" lines, session links, or any other mention of Claude or Claude Code to commit messages, tags, or PR descriptions. The only trailer is the `Signed-off-by` added by `--signoff`. (Project `.claude/settings.json` also disables the harness's automatic attribution.)
4. **Push** — always confirm with the user before pushing. Check the configured remotes with `git remote -v`; push to `origin` by default and to other remotes (e.g. a GitLab mirror) only if the user requests it. If no remote is configured yet, ask the user rather than adding one.

## Branch Conventions

Check `git branch -a` for the branches in use. Never force-push to the main or active development branch without explicit user approval.

## Task: $ARGUMENTS
