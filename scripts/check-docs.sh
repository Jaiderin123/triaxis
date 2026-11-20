#!/usr/bin/env bash
# Checks that the documentation did not drift from the repository. Run it before opening a pull request.
# It only reads files; it never changes anything. Exit code 1 when a check fails.
set -u
cd "$(dirname "$0")/.."

failures=0
fail() { echo "FAIL: $1"; failures=$((failures + 1)); }
mark=0
start() { mark=$failures; }
ok() { [ "$failures" -eq "$mark" ] && echo "ok:   $1"; start; }

# 1. Every ADR file is listed in docs/adr/README.md, and every listed ADR exists.
for file in docs/adr/[0-9][0-9][0-9][0-9]-*.md; do
  name=$(basename "$file")
  [ "$name" = "0000-template.md" ] && continue
  grep -q "($name)" docs/adr/README.md || fail "$name is not listed in docs/adr/README.md"
done
for listed in $(grep -o '([0-9]\{4\}-[a-z0-9-]*\.md)' docs/adr/README.md | tr -d '()'); do
  [ -f "docs/adr/$listed" ] || fail "docs/adr/README.md lists $listed, which does not exist"
done
ok "ADR files and index"

# 2. ARCHITECTURE.md section 14: ADRs marked as written exist, and written files are marked.
while IFS='|' read -r _ number _ status _; do
  number=$(echo "$number" | tr -d ' ')
  status=$(echo "$status" | tr -d ' ')
  [[ "$number" =~ ^[0-9]{4}$ ]] || continue
  exists=$(ls docs/adr/"$number"-*.md 2>/dev/null | head -1)
  if [ "$status" = "written" ] && [ -z "$exists" ]; then fail "ADR $number is marked written in ARCHITECTURE.md but has no file"; fi
  if [ "$status" != "written" ] && [ -n "$exists" ]; then fail "ADR $number has a file but ARCHITECTURE.md section 14 does not mark it as written"; fi
done < <(sed -n '/^## 14\. ADR plan/,/^## 15\./p' docs/ARCHITECTURE.md | grep '^| [0-9]')
ok "ADR plan in ARCHITECTURE.md"

# 3. Versions pinned in the catalog appear in TECH_STACK.md.
for key in spring-boot spring-ai resilience4j archunit blockhound mapstruct; do
  version=$(grep -E "^$key = " gradle/libs.versions.toml | sed -E 's/.*"(.*)".*/\1/')
  [ -z "$version" ] && continue
  grep -qF "$version" docs/TECH_STACK.md || fail "$key $version (gradle/libs.versions.toml) is not mentioned in docs/TECH_STACK.md"
done
ok "pinned versions in TECH_STACK.md"

# 4. Phases marked done in PLAN.md have their tag (only when this is a git repository).
if git rev-parse --git-dir >/dev/null 2>&1; then
  while IFS='|' read -r _ phase state tag _; do
    state=$(echo "$state" | tr -d ' ')
    tag=$(echo "$tag" | tr -d ' `')
    [ "$state" = "done" ] && [ -n "$tag" ] && {
      git rev-parse -q --verify "refs/tags/$tag" >/dev/null || fail "phase$phase is done in PLAN.md but the tag $tag does not exist"
    }
  done < <(sed -n '/^## Status/,/^### Where to resume/p' docs/PLAN.md | grep '^| [0-9]')
  ok "phase tags in PLAN.md"
fi

# 5. Every assumption and ADR number mentioned in CLAUDE.md exists.
for adr in $(grep -o 'ADR[s]\? [0-9]\{4\}' CLAUDE.md | grep -o '[0-9]\{4\}' | sort -u); do
  ls docs/adr/"$adr"-*.md >/dev/null 2>&1 || fail "CLAUDE.md mentions ADR $adr, which does not exist"
done
ok "ADR references in CLAUDE.md"

# 6. Every Gradle module is described in CLAUDE.md and TECH_STACK.md.
for module in $(grep -o "'triaxis-[a-z-]*'" settings.gradle | tr -d "'"); do
  grep -q "$module" CLAUDE.md || fail "$module (settings.gradle) is not described in CLAUDE.md"
  grep -q "$module" docs/TECH_STACK.md || fail "$module (settings.gradle) is not described in docs/TECH_STACK.md"
done
ok "modules documented"

# 7. Files that must not be committed.
if git rev-parse --git-dir >/dev/null 2>&1; then
  tracked=$(git ls-files docs/private .env 2>/dev/null)
  [ -z "$tracked" ] || fail "private files are tracked: $tracked"
  ok "no private files tracked"
fi

if [ "$failures" -gt 0 ]; then
  echo; echo "$failures check(s) failed. Update the documents (see CLAUDE.md, section Documentation sync)."
  exit 1
fi
echo; echo "Documentation is consistent."
