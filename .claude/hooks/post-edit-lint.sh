#!/usr/bin/env bash
# PostToolUse hook: format and lint TypeScript files after Write/Edit

INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# Only process TypeScript/TSX files
[ -z "$FILE" ] && exit 0
[[ "$FILE" =~ \.(ts|tsx|mts)$ ]] || exit 0
[[ "$FILE" =~ node_modules ]] && exit 0
[[ "$FILE" =~ \.next ]] && exit 0

# 1. Auto-format and apply safe fixes with biome
npx biome check --write "$FILE" 2>/dev/null || true

# 2. Run oxlint and capture errors
OXLINT_OUT=$(npx oxlint "$FILE" 2>&1)
OXLINT_EXIT=$?

# Report oxlint errors back to Claude as context
if [ $OXLINT_EXIT -ne 0 ] && [ -n "$OXLINT_OUT" ]; then
  jq -n --arg ctx "oxlint found issues in $FILE:\n$OXLINT_OUT" \
    '{"hookSpecificOutput":{"hookEventName":"PostToolUse","additionalContext":$ctx}}'
fi
