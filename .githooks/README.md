# Git hooks

Tracked hooks that enforce this repo's workflow. Git does **not** use these
automatically — each clone (and each `git worktree`) must point at this
directory once:

```bash
git config core.hooksPath .githooks
```

Run that after cloning. `core.hooksPath` lives in the shared `.git/config`, so a
single run covers all worktrees of that clone; a brand-new `git clone` needs it
again.

## Hooks

- **`pre-commit`** — refuses commits made directly on `main`/`master`. Create a
  `feat/`, `fix/`, or `chore/` branch first (see *Branching & Workflow* in
  `CLAUDE.md`). Bypass in a genuine emergency with `git commit --no-verify`.
