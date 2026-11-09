#!/usr/bin/env bash
# Fail if the public git tree would include secrets, keys, or private docs.
set -euo pipefail
cd "$(dirname "$0")/.."

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "error: not a git repository" >&2
  exit 1
fi

# Staged + unstaged candidates when dirty; otherwise all tracked files.
if git status --porcelain | grep -q .; then
  files="$(
    {
      git diff --cached --name-only --diff-filter=ACMR
      git ls-files --others --exclude-standard
      git ls-files
    } | awk 'NF' | sort -u
  )"
else
  files="$(git ls-files)"
fi

fail=0
count=0

echo "Checking public tree candidates..."

while IFS= read -r f; do
  [ -z "$f" ] && continue
  count=$((count + 1))
  base="${f##*/}"

  case "$f" in
    keystore.properties|local.properties|google-services.json|credentials.json|\
    */keystore.properties|*/local.properties|*/google-services.json|*/credentials.json)
      echo "BLOCKED: $f (secret/config)"; fail=1; continue ;;
  esac
  case "$base" in
    keystore.properties|local.properties|google-services.json|credentials.json)
      echo "BLOCKED: $f (secret/config)"; fail=1; continue ;;
  esac

  case "$f" in
    keystore/*|docs/*|docs-obsidian/*|store/*|.icon-preview/*|.idea/*|.cursor/*|.gradle/*|*/build/*)
      echo "BLOCKED: $f (private path)"; fail=1; continue ;;
  esac

  case "$f" in
    *.jks|*.keystore|*.p12|*.pem|*.apk|*.aab|*.secret)
      echo "BLOCKED: $f (binary/secret extension)"; fail=1; continue ;;
  esac
done <<EOF
$files
EOF

# Generic secret / path patterns only — do not embed private hostnames or account names.
content_hits="$(
  git grep -n -I -E \
    'storePassword[[:space:]]*=[[:space:]]*[^[:space:]"#]+|keyPassword[[:space:]]*=[[:space:]]*[^[:space:]"#]+|BEGIN (RSA |OPENSSH )?PRIVATE KEY|AKIA[0-9A-Z]{16}|/Users/[^/]+/|/Volumes/[^/]+/|(^|/)[^[:space:]]*passwords?\.txt$|[A-Za-z0-9._%+-]+@(gmail|yandex|mail|icloud)\.(com|ru)\b' \
    -- . \
    ':!LICENSE' \
    ':!tools/check-public-tree.sh' \
    ':!*.example' \
    2>/dev/null || true
)"

if [ -n "$content_hits" ]; then
  filtered="$(
    printf '%s\n' "$content_hits" | grep -v -E \
      'app/build\.gradle\.kts:.*(storePassword|keyPassword) = keystoreProperties' \
      || true
  )"
  if [ -n "$filtered" ]; then
    echo "BLOCKED content matches:"
    printf '%s\n' "$filtered"
    fail=1
  fi
fi

# Commit metadata must not leak personal mailboxes.
bad_authors="$(
  git log --format='%ae%n%ce' | sort -u | grep -vE '@users\.noreply\.github\.com$|@example\.com$' || true
)"
if [ -n "$bad_authors" ]; then
  echo "BLOCKED commit author/committer emails (use GitHub noreply):"
  printf '%s\n' "$bad_authors"
  fail=1
fi

echo "Scanned $count paths."

if [ "$fail" -ne 0 ]; then
  echo
  echo "Public tree check FAILED."
  exit 1
fi

echo "Public tree check OK."
