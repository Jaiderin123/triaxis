# Git Workflow

Conventions for branches, commits and releases. The history is part of the deliverable, so it should show small, working, iterative steps.

## Branches

| Branch | Purpose | Created from | Merges into |
|---|---|---|---|
| `main` | Stable, deployable state. Only receives finished phases. Every merge is tagged | n/a | n/a |
| `develop` | Integration branch. Always builds and passes tests | `main` | `main` (end of each phase) |
| `feat/<phase-or-feature>` | One branch per phase or feature (for example `feat/phase-4-escalation`, `feat/platform-foundation`) | `develop` | `develop` |
| `fix/<name>`, `docs/<name>`, `chore/<name>`, `refactor/<name>` | Small work that is not a phase | `develop` | `develop` |
| `hotfix/<name>` | Urgent fix of something already on `main` | `main` | `main` and `develop` |

Work is organized by **phase or feature, not by task**: one branch and one pull request per phase, with a few logical commits inside it (for example domain, application, persistence, API, docs). Nobody commits directly to `main` or `develop`.

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

Flow for a phase or feature:

1. Create the branch from an up-to-date `develop` (`git switch develop && git pull`, then `git switch -c feat/<name>`).
2. Work in a few meaningful commits; every commit leaves the build green. Push the branch.
3. Update the documentation in the same branch (see the checklist of the pull request template) and run `scripts/check-docs.sh`.
4. Before opening the pull request, run `git rebase develop` (if `develop` moved) and `./gradlew build`.
5. Open one pull request **into `develop`** and merge it with **rebase and merge**: the commits stay in the history one by one and the history stays linear.
6. Delete the branch after the merge.

Flow for a release:

1. When a phase is finished and `develop` builds green, open a pull request **`develop` into `main`**.
2. Use **create a merge commit** (no squash), so the commits of the phase stay visible on `main`.
3. Tag the merge (see Tags).

Hotfixes: branch from `main`, pull request into `main`, then a pull request from `main` back into `develop`.

### PR content

- Title: the Conventional Commit subject (for example `feat(triage): classify patients by vital signs`).
- Body: what changed, why, how it was tested, and the plan task it closes. Link ADRs when relevant. The pull request template has a documentation checklist; run `scripts/check-docs.sh` before opening the pull request.
- No secrets or private files.
- The build must pass before the merge.

### Branch protection (repository settings)

- `main` and `develop`: require a pull request, block direct pushes and force-pushes. No status check is required, because there is no CI workflow (it is on the cut list of `PLAN.md`); the build is run locally before every pull request, and a fresh clone of the branch must compile.
- Allowed merge methods: rebase and merge for `develop`, merge commit for `main`.

## Tags

Tag the merge into `main` at the end of each plan phase, for example `phase-2-triage-core`, so there is always a version that runs.

## Rules

- No force-push to `main` or `develop`.
- Do not rewrite history that was already pushed, except to remove a leaked secret (then rotate the secret).
