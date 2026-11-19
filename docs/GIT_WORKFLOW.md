# Git Workflow

Conventions for branches, commits and releases. The history is part of the deliverable, so it should show small, working, iterative steps.

## Branches

| Branch | Purpose | Created from | Merges into |
|---|---|---|---|
| `main` | Stable, deployable state. Only receives finished phases. Every merge is tagged | n/a | n/a |
| `develop` | Integration branch. Always builds and passes tests | `main` | `main` (end of each phase) |
| `feat/<scope>-<short-name>` | New functionality (for example `feat/triage-classifier`) | `develop` | `develop` |
| `fix/<scope>-<short-name>` | Bug fixes | `develop` | `develop` |
| `docs/`, `chore/`, `test/`, `refactor/` + `<short-name>` | The rest | `develop` | `develop` |
| `hotfix/<short-name>` | Urgent fix of something already on `main` | `main` | `main` and `develop` |

Work branches are short-lived: one plan task or a small group of related tasks, merged within a day. Nobody commits directly to `main` or `develop`.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/), written in English, imperative mood, subject up to 72 characters.

```
<type>(<scope>): <subject>

<optional body explaining why>
```

- Types: `feat`, `fix`, `test`, `docs`, `refactor`, `build`, `chore`, `ci`, `perf`.
- Scopes: `triage`, `assistant`, `auth`, `common`, `security`, `deploy`, `build`.
- One logical change per commit. Tests for a change go in the same commit or the one before it.
- Every commit leaves the build green (`./gradlew build`).
- Never commit secrets, `.env` files or anything under `docs/private/`.

## Pull requests (no local merges)

**Nothing is merged locally.** Never run `git merge` into `develop` or `main`. Every change reaches them through a pull request on the repository host, and neither branch accepts direct pushes.

Flow for a task:

1. Create a work branch from an up-to-date `develop` (`git switch develop && git pull`, then `git switch -c feat/<scope>-<short-name>`).
2. Commit freely inside the branch and push it.
3. Before opening the PR, run `git rebase develop` (if `develop` moved) and `./gradlew build`.
4. Open a PR **into `develop`**. Use **squash and merge**, so `develop` gets one Conventional Commit per task; the squash commit message follows the commit rules above.
5. Delete the branch after the merge.

Flow for a phase:

1. When a plan phase is finished and `develop` builds green, open a PR **`develop` into `main`**.
2. Use **create a merge commit** (no squash), so the per-task commits stay visible on `main`.
3. Tag the merge (see Tags).

Hotfixes: branch from `main`, PR into `main`, then a PR from `main` back into `develop`.

### PR content

- Title: the Conventional Commit subject (for example `feat(triage): classify patients by vital signs`).
- Body: what changed, why, how it was tested, and the plan task it closes. Link ADRs when relevant. The pull request template has a documentation checklist; run `scripts/check-docs.sh` before opening the pull request.
- No secrets or private files.
- The build must pass before the merge.

### Branch protection (repository settings, to configure once the repository exists)

- `main` and `develop`: require a pull request, block direct pushes and force-pushes, require the build check to pass.
- Allowed merge methods: squash for `develop`, merge commit for `main`.

## Tags

Tag the merge into `main` at the end of each plan phase, for example `phase-2-triage-core`, so there is always a version that runs.

## Rules

- No force-push to `main` or `develop`.
- Do not rewrite history that was already pushed, except to remove a leaked secret (then rotate the secret).
