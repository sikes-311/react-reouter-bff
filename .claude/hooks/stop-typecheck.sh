#!/usr/bin/env bash
# Stop hook: run tsc type-check when Claude finishes a turn

# Prevent infinite loop: if already re-invoked by this hook, skip
INPUT=$(cat)
STOP_HOOK_ACTIVE=$(echo "$INPUT" | jq -r '.stop_hook_active // false')
if [ "$STOP_HOOK_ACTIVE" = "true" ]; then
  exit 0
fi

ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"

# App typecheck
APP_OUT=$(cd "$ROOT/frontend" && npx tsc --noEmit 2>&1)
APP_EXIT=$?

# E2E typecheck
E2E_OUT=$(cd "$ROOT/frontend" && npx tsc --noEmit -p e2e/tsconfig.json 2>&1)
E2E_EXIT=$?

COMBINED=""
[ $APP_EXIT -ne 0 ] && COMBINED+="### frontend/tsconfig.json errors:\n$APP_OUT\n"
[ $E2E_EXIT -ne 0 ] && COMBINED+="### frontend/e2e/tsconfig.json errors:\n$E2E_OUT\n"

if [ -n "$COMBINED" ]; then
  jq -n --arg reason "TypeScript type errors detected. Fix all errors before stopping:\n$COMBINED" \
    '{
      "decision": "block",
      "reason": $reason
    }'
  exit 0
fi

exit 0
