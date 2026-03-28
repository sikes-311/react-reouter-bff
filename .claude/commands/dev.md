# /dev - Issue 実装コマンド

`/planning` で生成した計画書をもとに、BDD-first でシナリオ単位の実装・受け入れ検証までを自動化します。

**使い方**: `/dev <issue番号>`  例: `/dev 42`

**前提**: `docs/issues/{issue番号}/plan.md` が存在すること（`/planning <issue_url>` で生成）

---

## このコマンドが実行するプロセス

```
Step 1: plan.md 読み込み

         ┌──────────────────────────────────────────────────────┐
         │  Step 2: シナリオループ（SC-1 から順に1つずつ）          │
         │                                                      │
         │  Phase A: テストファースト                              │
         │    e2e-agent がシナリオのテストを先行作成                │
         │    👤 人間レビュー: テストは仕様を正しく表現しているか？   │
         │         ↓ yes                                        │
         │  Phase B: 実装                                        │
         │    bff-agent + frontend-agent 並行実装                │
         │    bff-test-agent + frontend-test-agent              │
         │    e2e-agent がテストを実行 → Pass 確認                │
         │         ↓ Pass                                       │
         │  Phase C: 実装コードレビュー                           │
         │    👤 人間レビュー: 実装コードは問題ないか？             │
         │         ↓ yes → 次のシナリオへ                         │
         └──────────────────────────────────────────────────────┘

Step 3: 最終受け入れ検証
  3-1: ビルド検証（BFF + フロントエンド）
  3-2: 全ユニットテスト（BFF + フロントエンド）
  3-3: Phase A で作成した全 E2E テスト一括実行 → 全 Pass 確認
  3-4: 探索的テスト（Playwright MCP）
  3-5: 検証レポート作成
  3-6: Issue にコメント（ユーザー承認後）
```

---

## Step 1: plan.md 読み込み

以下のファイルを読み込み、実装に必要な情報を取得してください。

```bash
cat docs/issues/$ARGUMENTS/plan.md
```

取得する情報:
- **機能概要・影響範囲**
- **BDD シナリオ一覧**: シナリオ ID（SC-1, SC-2, ...）と Gherkin 詳細
- **Downstream WireMock スタブ設計**: 各シナリオが期待するスタブ
- **API コントラクト**: BFF エンドポイント・DTO 概要

plan.md が存在しない場合は以下を出力して終了してください。

```
❌ docs/issues/$ARGUMENTS/plan.md が見つかりません。
先に /planning <issue_url> を実行して計画書を生成してください。
```

---

## Step 2: シナリオループ

plan.md のシナリオ ID リスト（SC-1, SC-2, ...）を先頭から **1つずつ** 処理します。

```
for each シナリオ in [SC-1, SC-2, ...]:
  Phase A → Phase B → Phase C → 次のシナリオ
```

---

### Phase A: テストファースト

#### A-1. e2e-agent を起動してテストを先行作成する

以下の情報を渡して e2e-agent を起動してください。

```
対象シナリオ: {シナリオID} — {シナリオ名}
Gherkin: （plan.md から該当シナリオをそのまま引用）
WireMock スタブ設計: （plan.md から該当シナリオ分を引用）
参照ファイル:
  - docs/issues/{issue番号}/plan.md
  - docker-compose.yml
  - playwright.config.ts
  - e2e/{feature}.spec.ts（既存ファイルがあれば）
  - DEVELOPMENT_RULES.md
指示:
  1. e2e/features/{feature}.feature を作成・更新する（Gherkin をそのまま記載）
  2. e2e/{feature}.spec.ts に対象シナリオのテストを追加する
  3. この時点では実装が存在しないためテストは Fail して構わない
  4. 作成したテストコードを SendMessage で team-lead に報告する
```

e2e-agent からテストコードの報告を受け取ったら Phase A-2 へ進みます。

#### A-2. 👤 人間レビュー: テスト仕様の確認

以下を提示してユーザーに確認を求めてください。

```
## Phase A: {シナリオID} テストレビュー

以下の E2E テストが作成されました。
このテストはシナリオの仕様を正しく表現しているか確認してください。

### Gherkin（シナリオ定義）
{plan.md の該当 Gherkin}

### 作成した Playwright テスト
{e2e-agent が作成したテストコード}

### 確認ポイント
- セレクター（data-testid）は仕様と合っているか？
- アサーションの期待値は正しいか？
- WireMock スタブの設定との整合性はとれているか？

テスト仕様を承認しますか？ [yes / 修正内容を記載]
```

- **yes** → Phase B へ進む
- **修正内容あり** → e2e-agent に修正を依頼し、修正後に再度確認を求める

---

### Phase B: 実装

#### B-1. 並行開発チームを起動する

**タスク作成と起動エージェント**:

| タスクID | 担当エージェント | 内容 | 依存 |
|---|---|---|---|
| #1 | bff-agent | BFF 実装（Controller / UseCase / Gateway / DTO） | - |
| #2 | frontend-agent | フロントエンド実装（loader / action / components / api.ts） | - |
| #3 | bff-test-agent | BFF テスト（Unit / Slice / Integration） | #1完了後 |
| #4 | frontend-test-agent | フロントエンド Integration テスト | #2完了後 |
| #5 | e2e-agent | E2E テスト実行・Pass 確認 | #1・#2完了後 |
| #6 | code-review-agent | 内部品質レビュー | #1〜#4完了後 |
| #7 | security-review-agent | セキュリティレビュー | #1・#2完了後 |

各エージェントへの指示に必ず含めること:
- 対象シナリオ ID と `docs/issues/{issue番号}/plan.md` への参照
- `ARCHITECTURE.md`, `DEVELOPMENT_RULES.md` への参照
- **完了条件**:
  - BFF: `./gradlew build` エラーなし + `./gradlew ktlintCheck` エラーなし
  - フロントエンド: `npm run typecheck` エラーなし + `npm run lint` エラーなし
- **完了後**: TaskUpdate(completed) → SendMessage で team-lead に結果報告

e2e-agent（#5）への指示には追加で以下を含めること:
- Phase A で作成済みのテストファイル（`e2e/{feature}.spec.ts`）を実行すること
- Docker Compose が起動済みであることを確認してからテストを実行すること
- Fail した場合は原因を特定し、bff-agent / frontend-agent への修正依頼を team-lead に報告すること
- 修正後に再実行し、Pass を確認してから完了報告すること

#### B-2. e2e テスト Pass の確認

e2e-agent（#5）が「対象シナリオ Pass」を報告するまで待ちます。

e2e-agent が Fail を報告した場合:
- e2e-agent の報告に従い bff-agent / frontend-agent に修正を依頼する
- e2e-agent に再実行を依頼する
- 修正依頼が5回を超えても Pass しない場合はユーザーに状況を報告して判断を仰ぐ

---

### Phase C: 実装コードレビュー

以下をユーザーに提示して確認を求めてください。

```
## Phase C: {シナリオID} 実装コードレビュー

{シナリオID}: {シナリオ名} ✅ E2E Pass しました。

### 変更ファイル一覧
{git diff --stat の出力}

### テスト結果
BFF（Unit / Slice / Integration）: {N}件パス
フロントエンド Integration: {N}件パス

### E2E テスト結果
✅ {シナリオ名} — Pass

実装コードを確認してください。
次のシナリオ（{次のシナリオID}: {次のシナリオ名}）の Phase A に進んでよいですか？ [yes / 修正内容を記載]
```

- **yes** → 次のシナリオの Phase A へ進む
- **修正内容あり** → 該当エージェントに修正を依頼し、修正後に再度確認を求める

---

## Step 3: 最終受け入れ検証

全シナリオの Phase C が承認されたら実施します。

### 3-1. ビルド検証

```bash
# BFF
cd bff && ./gradlew build

# フロントエンド
cd frontend && npm run build
```

### 3-2. 全テスト実行

```bash
# BFF（Unit / Slice / Integration）
cd bff && ./gradlew test

# フロントエンド Integration テスト
cd frontend && npx vitest run --coverage
```

### 3-3. Phase A で作成した全 E2E シナリオの一括実行・Pass 確認

```bash
npx playwright test e2e/{feature}.spec.ts
```

1件でも Fail があれば Phase B と同様に原因を特定・修正してから次へ進んでください。

### 3-4. 探索的テスト（Playwright MCP）

自動化テストでは検出できない **UI の見た目・操作感・エッジケース** を Playwright MCP を用いて探索的にテストします。

| 観点 | 確認内容 |
|---|---|
| **初期表示** | ページロード直後の状態が仕様通りか |
| **インタラクション** | ボタン・フォーム等の操作が自然に動作するか |
| **エラー表示** | API エラー時のメッセージが適切な場所・スタイルで表示されるか |
| **空状態** | データ 0 件のときに適切なフィードバックがあるか |
| **レスポンシブ** | モバイル幅（375px）でレイアウトが崩れていないか |
| **アクセシビリティ** | キーボード操作・フォーカス順が自然か |

### 3-5. 検証レポート作成

`docs/issues/{issue番号}/acceptance-report.md` に保存:

```markdown
# 受け入れ検証レポート - Issue #{issue番号}

検証日時: YYYY-MM-DD
検証者: Claude Code

## E2E シナリオ検証結果（自動テスト）

| シナリオID | シナリオ名 | 結果 | 備考 |
|---|---|---|---|
| SC-1 | {シナリオ名} | ✅ Pass | |

## 探索的テスト結果（Playwright MCP）

| 観点 | 結果 | 備考 |
|---|---|---|
| 初期表示 | ✅ 問題なし | |
| インタラクション | ✅ 問題なし | |
| エラー表示 | ✅ 問題なし | |
| 空状態 | ✅ 問題なし | |
| レスポンシブ | ✅ 問題なし | |
| アクセシビリティ | ✅ 問題なし | |

## テストカバレッジ
- BFF: XX%
- フロントエンド: XX%

## 総合判定
✅ 全シナリオ Pass・探索テスト完了 → Issue クローズ可能
```

### 3-6. Issue にレポートをコメント（ユーザーの承認後）

ユーザーに確認を求めてから実行してください。

```bash
gh issue comment $ARGUMENTS --body "$(cat docs/issues/$ARGUMENTS/acceptance-report.md)"
```

---

## 各エージェントの役割定義

詳細は `.claude/agents/` 配下の各ファイルを参照してください。

- [bff-agent](./../agents/bff-agent.md)
- [frontend-agent](./../agents/frontend-agent.md)
- [bff-test-agent](./../agents/bff-test-agent.md)
- [frontend-test-agent](./../agents/frontend-test-agent.md)
- [e2e-agent](./../agents/e2e-agent.md)
- [code-review-agent](./../agents/code-review-agent.md)
- [security-review-agent](./../agents/security-review-agent.md)
