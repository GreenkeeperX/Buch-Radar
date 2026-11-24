#!/bin/sh
# Rejects access values, keys and private paths in tracked files (or in the staged
# files with --staged). Prints file and line, never the matched value itself.
# SPDX-License-Identifier: GPL-3.0-only
set -u
mode="${1:-}"
if [ "$mode" = "--staged" ]; then
  files=$(git diff --cached --name-only --diff-filter=ACM)
else
  files=$(git ls-files)
fi
[ -z "$files" ] && exit 0
found=0
check() {
  # $1: description, $2: extended regex, $3: optional extra grep flag (e.g. -i)
  hits=$(printf '%s\n' "$files" | grep -v -e '^tools/check-secrets.sh$' | tr '\n' '\0' |
    xargs -0 grep -HnIE ${3:-} -e "$2" -- 2>/dev/null | cut -d: -f1,2)
  if [ -n "$hits" ]; then
    printf 'Verdacht auf %s:\n%s\n' "$1" "$hits" >&2
    found=1
  fi
}
check "Google-API-Schlüssel" 'AIza[0-9A-Za-z_-]{35}'
check "GitHub-Token" 'gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}'
check "privaten Schlüssel" '-----BEGIN [A-Z ]*PRIVATE KEY-----'
check "fest eingetragenen Zugangs-Header" '(X-API-TOKEN|X-Api-Key|Authorization)["]?[:=] *["]?(Bearer )?[A-Za-z0-9._~+/-]{12,}'
check "Zugangswert im Quelltext" '(token|api_?key|secret|password|passwort)[a-z_]*["]? *[:=] *"[a-z0-9._~+/-]{16,}"' -i
check "Zugangswert in Einstellungen" '^(momox\.apiToken|googleBooks\.apiKey) *= *[^ ]+'
check "lokalen Benutzerpfad" '/home/[a-z][a-z0-9_-]*/|/Users/[A-Za-z][A-Za-z0-9_-]*/|[A-Z]:\\\\Users\\\\'
if [ "$found" -ne 0 ]; then
  echo "Abgelehnt: Zugangswerte, Schlüssel und lokale Pfade gehören nicht ins Repository (siehe CLAUDE.md)." >&2
  exit 1
fi
