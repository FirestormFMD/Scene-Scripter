#!/usr/bin/env bash
# Prints parts of the decompiled Minecraft sources for the queries in queries.txt.
# Used by .github/workflows/inspect-sources.yml so the game code can be read from environments that
# cannot download Minecraft. Query lines:
#   find <name-glob>            list source files whose name matches
#   outline <path>              declarations (classes, fields, methods) with line numbers
#   lines <path> <from> <to>    a range of lines
#   method <path> <name>        body of every method with that name
#   grep <path> <regex>         matching lines under a file or directory (first 150); the regex is the rest of the line
set -euo pipefail
SRC="$1"
QUERIES="$2"
while IFS= read -r line || [[ -n "$line" ]]; do
	[[ -z "$line" || "$line" == \#* ]] && continue
	read -r cmd a b c <<<"$line"
	echo "=================== $line"
	case "$cmd" in
		find) (cd "$SRC" && find . -name "$a" | sed 's#^\./##' | head -50) ;;
		outline) grep -nE '^\s*(public|protected|private|static|abstract|final|default|record|enum|class|interface|sealed|@Nullable)[^=;]*[({;]' "$SRC/$a" | grep -vE '^\s*[0-9]+:\s*(return|if|else|for|while)\b' | head -400 || true ;;
		lines) sed -n "${b},${c}p" "$SRC/$a" | nl -ba -v "$b" ;;
		method) awk -v name="$b" 'index($0, name "(") && /^\t(public|protected|private|static|final|abstract|@)/ {p=1; depth=0} p {print NR": "$0; depth+=gsub(/\{/,"{"); depth-=gsub(/\}/,"}"); if (depth<=0 && /\}/) {p=0}}' "$SRC/$a" | head -120 ;;
		grep) (cd "$SRC" && grep -rnE -- "${line#grep $a }" "$a" | head -150) || true ;;
		*) echo "unknown query" ;;
	esac
done <"$QUERIES"
