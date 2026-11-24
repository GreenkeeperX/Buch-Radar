#!/bin/sh
# Rejects access values, keys and private paths. Prints file and line, never the value.
# SPDX-License-Identifier: GPL-3.0-only
#
#   tools/check-secrets.sh                 tracked files in the working tree
#   tools/check-secrets.sh --staged        the staged blobs (what the commit will contain)
#   tools/check-secrets.sh --range A..B    every commit in A..B, so a value added and
#                                          removed again in a later commit is still found
#
# git grep reads the blobs itself (no shell word splitting, no quoted paths), and any
# error other than "no match" fails the check instead of passing it.
set -u
LC_ALL=C
export LC_ALL

case "${1:-}" in
  --staged) targets="--cached" ;;
  --range)
    [ -n "${2:-}" ] || { echo "check-secrets: --range braucht A..B" >&2; exit 2; }
    targets=$(git rev-list "$2") || { echo "check-secrets: Commitbereich $2 nicht lesbar" >&2; exit 2; }
    [ -n "$targets" ] || exit 0 ;;
  "") targets="--worktree" ;;
  *) echo "check-secrets: unbekannte Option $1" >&2; exit 2 ;;
esac

found=0
report() { printf 'Verdacht auf %s:\n%s\n' "$1" "$2" >&2; found=1; }

# $1 description, $2 extended regex, $3 optional -i. Output: [commit:]path<TAB>line.
check() {
  for target in $targets; do
    case "$target" in
      --worktree) set -- "$1" "$2" "${3:-}" ;;
      *) set -- "$1" "$2" "${3:-}" "$target" ;;
    esac
    hits=$(git -c core.quotePath=false grep -n -I -z -E ${3:+"$3"} -e "$2" ${4:+"$4"} -- . ':(exclude)tools/check-secrets.sh' |
      tr '\0' '\t' | cut -f1,2)
    status=$?
    # The pipeline status is cut's; ask git grep again for its own status when nothing was printed.
    if [ -z "$hits" ]; then
      git -c core.quotePath=false grep -q -I -E ${3:+"$3"} -e "$2" ${4:+"$4"} -- . ':(exclude)tools/check-secrets.sh'
      status=$?
      [ "$status" -le 1 ] || { echo "check-secrets: git grep fehlgeschlagen ($status)" >&2; exit 2; }
      [ "$status" -eq 0 ] && report "$1" "(Treffer ohne Zeilenangabe)"
    else
      [ "$status" -eq 0 ] || { echo "check-secrets: Ausgabe nicht lesbar" >&2; exit 2; }
      report "$1" "$hits"
    fi
  done
}

check "Google-API-Schlüssel" 'AIza[0-9A-Za-z_-]{35}'
check "GitHub-Token" 'gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}'
check "privaten Schlüssel" '-----BEGIN [A-Z ]*PRIVATE KEY-----'
# Header with a literal value: "X-API-TOKEN: v", header("X-Api-Key", "v"), Authorization = "Bearer v" …
check "fest eingetragenen Zugangs-Header" '(x-api-token|x-api-key|authorization)["'"'"']?[[:space:]]*[:=,][[:space:]]*["'"'"']?(bearer[[:space:]]+)?[a-z0-9._~+/-]{12,}' -i
# The app's own access names in any syntax: shell, YAML, properties, Gradle, Kotlin.
# "NAME=v", "NAME = v", "NAME: v"; not "NAME= ./gradlew" (an empty shell assignment before a command).
check "Zugangswert der App" '(MOMOX_API_TOKEN|GOOGLE_BOOKS_API_KEY|momox\.apiToken|googleBooks\.apiKey)["'"'"']?([:=]|[[:space:]]+=[[:space:]]+|:[[:space:]]+)["'"'"']?[A-Za-z0-9._~+/-]{8,}'
# Other names that end in token/key/secret/password and get a long literal (not tokenLabel = "…").
check "Zugangswert im Quelltext" '[a-z0-9_]*(token|api_?key|secret|password|passwort)["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"'][a-z0-9._~+/-]{16,}["'"'"']' -i
check "lokalen Benutzerpfad" '/home/[a-z][a-z0-9_-]*/|/Users/[A-Za-z][A-Za-z0-9_-]*/|[A-Z]:\\\\Users\\\\'

# Binary key containers are skipped by -I above, so they are refused by name.
for target in $targets; do
  case "$target" in
    --worktree) names=$(git -c core.quotePath=false ls-files) ;;
    --cached) names=$(git -c core.quotePath=false ls-files --cached) ;;
    *) names=$(git -c core.quotePath=false ls-tree -r --name-only "$target" | sed "s|^|$target:|") ;;
  esac || { echo "check-secrets: Dateiliste nicht lesbar" >&2; exit 2; }
  keys=$(printf '%s\n' "$names" | grep -iE '\.(jks|keystore|p12|pfx|pem|key)$')
  [ -n "$keys" ] && report "Schlüsseldatei" "$keys"
done

if [ "$found" -ne 0 ]; then
  echo "Abgelehnt: Zugangswerte, Schlüssel und lokale Pfade gehören nicht ins Repository (siehe CLAUDE.md)." >&2
  exit 1
fi
exit 0
