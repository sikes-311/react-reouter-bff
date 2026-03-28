#!/usr/bin/env bash
# generate-api-types.sh
# BFF の OpenAPI スペックを生成し、フロントエンドの TypeScript 型を自動生成する
#
# 使い方:
#   bash scripts/generate-api-types.sh
#
# 前提:
#   - JVM 17 以上
#   - Node.js / npm（frontend/node_modules がインストール済み）

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Step 1: openapi.json を生成 (./gradlew generateOpenApiDocs) ==="
cd "$ROOT/bff"
./gradlew generateOpenApiDocs --quiet
echo "✅ docs/api/openapi.json を生成しました"

echo ""
echo "=== Step 2: TypeScript 型を生成 (npm run generate:types) ==="
cd "$ROOT/frontend"
npm run generate:types --silent
echo "✅ frontend/types/generated/api.d.ts を生成しました"

echo ""
echo "=== 生成された型の確認 ==="
echo "--- docs/api/openapi.json (paths) ---"
node -e "
  const spec = JSON.parse(require('fs').readFileSync('../docs/api/openapi.json', 'utf8'));
  const paths = Object.keys(spec.paths ?? {});
  paths.forEach(p => console.log('  ' + p));
  console.log('  schemas: ' + Object.keys(spec.components?.schemas ?? {}).join(', '));
"

echo ""
echo "✅ 完了: frontend/types/generated/api.d.ts を更新しました"
