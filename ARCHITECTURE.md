# ARCHITECTURE.md

## 概要

フロントエンド（React Router）と BFF（Kotlin + Spring Boot）を分離したモノレポ構成。
BFF は Downstream API への変換層として機能し、セッション管理は Downstream に委譲する。

---

## リポジトリ構成

```
react-router-bff/
├── frontend/          # React Router v7 (dataMode, CSR)
├── bff/               # Kotlin + Spring Boot (Clean Architecture)
└── docs/
    ├── api/
    │   └── openapi.json   # BFF の API 契約（git 管理・編集禁止・PR レビュー必須）
    └── issues/            # Issue 単位の設計ドキュメント
```

---

## システム構成図

```
Browser
  │
  │ HTTP (Cookie パススルー)
  ▼
[BFF: Spring Boot :8080]
  │
  │ HTTP + Cookie 転送
  ▼
[Downstream API]
```

---

## フロントエンド (`frontend/`)

### 技術スタック

| 用途 | ツール |
|---|---|
| フレームワーク | React Router v7 (dataMode, CSR) |
| 言語 | TypeScript |
| コンポーネントテスト | Storybook |
| インテグレーションテスト | Vitest + Testing Library + MSW |
| E2E テスト | Playwright |
| Linter / Formatter | Biome |

### ディレクトリ構成

feature を起点に、ページ・コンポーネント・BFF クライアントをまとめて配置する。
`routes.ts` は URL と feature ページのマッピングのみを担う。

```
frontend/
├── app/
│   ├── root.tsx                         # ルートレイアウト・エラーバウンダリ
│   └── routes.ts                        # URL → feature ページのマッピング（ポインタのみ）
│
├── features/
│   ├── auth/
│   │   ├── pages/
│   │   │   └── login-page.tsx           # loader + action + ページコンポーネント
│   │   ├── components/                  # auth 専用コンポーネント（Testing Library 対象）
│   │   └── api.ts                       # BFF 呼び出し関数
│   └── {feature}/
│       ├── pages/
│       │   ├── {feature}-list-page.tsx  # loader + action + ページコンポーネント
│       │   └── {feature}-detail-page.tsx
│       ├── components/                  # feature 専用コンポーネント（Testing Library 対象）
│       │   ├── {feature}-list.tsx
│       │   └── {feature}-form.tsx
│       └── api.ts                       # BFF 呼び出し関数
│
├── components/
│   └── ui/                              # 複数 feature をまたぐ共有 UI のみ（Storybook 対象）
│
└── types/
    └── generated/                       # openapi-typescript で自動生成（編集禁止）
```

`routes.ts` の例:

```ts
import { type RouteConfig, route, layout } from "@react-router/dev/routes";

export default [
  route("/login", "../features/auth/pages/login-page.tsx"),
  layout("../features/shared/layouts/app-layout.tsx", [
    route("/{feature}", "../features/{feature}/pages/{feature}-list-page.tsx"),
    route("/{feature}/:id", "../features/{feature}/pages/{feature}-detail-page.tsx"),
  ]),
] satisfies RouteConfig;
```

### データフロー

```
ルート遷移
  └─ loader → features/{feature}/api.ts → BFF API → useLoaderData() → コンポーネント描画

フォーム送信・ページ遷移を伴う更新
  └─ <Form> → action → features/{feature}/api.ts → BFF API → redirect or エラー返却

クライアントサイドイベント（ページ遷移なし）
  └─ useFetcher().submit() → action → BFF API
  └─ useFetcher().load()  → loader → BFF API
```

> **TanStack Query は使用しない。** データ取得・更新はすべて loader / action / useFetcher で行う。

---

## BFF (`bff/`)

### 技術スタック

| 用途 | ツール |
|---|---|
| フレームワーク | Kotlin + Spring Boot |
| HTTP クライアント | RestClient |
| OpenAPI 生成 | springdoc-openapi |
| Unit / Slice テスト | JUnit5 + Mockito + @WebMvcTest |
| Integration テスト | JUnit5 + WireMock |
| E2E テスト | Playwright + Docker Compose |
| JVM | 17 |

### クリーンアーキテクチャ

```
┌──────────────────────────────────────────┐
│  presentation                             │  Controller, Request/Response DTO
├──────────────────────────────────────────┤
│  application                              │  UseCase, Port（interface）
├──────────────────────────────────────────┤
│  domain                                   │  Entity, ValueObject, DomainService
├──────────────────────────────────────────┤
│  infrastructure/downstream                │  Gateway 実装, Downstream DTO, RestClient
└──────────────────────────────────────────┘
```

**依存の方向**: `presentation` → `application` → `domain` ← `infrastructure`

- `infrastructure` は `application/port/out/` のインターフェースを実装する
- `domain` は他のどの層にも依存しない

### ディレクトリ構成

```
bff/src/main/kotlin/com/example/app/
├── {feature}/
│   ├── domain/
│   │   ├── model/
│   │   │   └── {Feature}.kt                        # Entity / ValueObject
│   │   └── service/
│   │       └── {Feature}DomainService.kt            # ドメインサービス（任意）
│   ├── application/
│   │   ├── usecase/
│   │   │   └── {Action}{Feature}UseCase.kt          # UseCase
│   │   └── port/
│   │       └── out/
│   │           └── {Feature}Gateway.kt              # Downstream アクセスのポート（interface）
│   ├── presentation/
│   │   ├── {Feature}Controller.kt
│   │   └── dto/
│   │       ├── {Feature}Request.kt                  # リクエスト DTO（バリデーションアノテーション付き）
│   │       └── {Feature}Response.kt                 # レスポンス DTO
│   └── infrastructure/
│       └── downstream/
│           ├── {Feature}DownstreamGateway.kt        # Gateway interface の実装
│           └── dto/
│               ├── {Feature}DownstreamRequest.kt    # Downstream 送信 DTO
│               └── {Feature}DownstreamResponse.kt   # Downstream 受信 DTO
└── shared/
    ├── exception/
    │   ├── ErrorResponse.kt                         # 統一エラーレスポンス
    │   └── GlobalExceptionHandler.kt                # @RestControllerAdvice
    └── config/
        └── RestClientConfig.kt                      # RestClient Bean 定義
```

---

## 型契約（OpenAPI）

BFF が OpenAPI spec を公開し、フロントエンドが TypeScript 型を自動生成する。

```
bff/ (springdoc-openapi)
  └─ GET /v3/api-docs → docs/api/openapi.json（git 管理）
                                   │
                                   ▼ openapi-typescript
              frontend/types/generated/api.d.ts（編集禁止）
```

### openapi.json の管理ルール

- `docs/api/openapi.json` を git で管理し、**PR レビューの対象**とする
- BFF サーバーを起動せずにフロントエンドが型生成できるようにするため、常に最新状態を維持する
- BFF の DTO を変更したら必ず再生成してコミットする

```bash
# BFF サーバー起動後、spec を取得してコミット
curl http://localhost:8080/v3/api-docs -o docs/api/openapi.json

# フロントエンドの型を生成（docs/api/openapi.json から生成）
cd frontend
npm run generate:types
```

`frontend/types/generated/` 配下のファイルは自動生成のため手動編集禁止。
型変更が必要な場合は BFF の DTO を修正して再生成する。

### API パスのバージョニング

すべての BFF エンドポイントは `/api/v1/` プレフィックスを使用する。

```
/api/v1/auth/login
/api/v1/{feature}
/api/v1/{feature}/{id}
```

feature ごとに独立してバージョンアップできる（例: `/api/v2/{feature}` のみ上げるなど）。

---

## 認証フロー（Cookie パススルー）

セッション管理は Downstream に委譲する。BFF はセッションを保持しない。

```
【ログイン】
Browser → POST /api/auth/login → BFF
BFF → POST /auth/login (Cookie なし) → Downstream
Downstream → Set-Cookie: session=xxx → BFF → Browser に Set-Cookie を転送

【認証済みリクエスト】
Browser → GET /api/{feature} (Cookie: session=xxx) → BFF
BFF → GET /{feature} (Cookie: session=xxx をそのまま転送) → Downstream
Downstream → 200 OK → BFF → フロントエンドへ変換して返却

【セッション切れ】
Downstream → 401 → BFF → 401 ErrorResponse → フロントエンドはログインページへリダイレクト
```

BFF の RestClient は `Cookie` ヘッダーを透過的に転送するよう設定する。

---

## エラーレスポンス統一形式

BFF から返却するすべてのエラーは以下の形式に統一する。

```json
{
  "error": "Not Found",
  "status": 404,
  "detail": "Feature with id=xxx was not found"
}
```

| HTTP ステータス | error | 主な用途 |
|---|---|---|
| 400 | Bad Request | リクエストバリデーションエラー |
| 401 | Unauthorized | 未認証・セッション切れ |
| 403 | Forbidden | 権限不足 |
| 404 | Not Found | リソース未存在 |
| 502 | Bad Gateway | Downstream 通信エラー |
| 500 | Internal Server Error | 予期しないエラー |

---

## テスト戦略

### BFF テストピラミッド

```
          E2E
   Playwright + Docker Compose
   担保: ユーザージャーニーの結合動作

       Integration
    JUnit5 + WireMock
    担保: Downstream 通信・DTO 変換ロジック

      Slice
   @WebMvcTest + MockMvc
   担保: HTTP 契約・バリデーション・エラーハンドリング

    Unit
  JUnit5 (+ Mockito)
  担保: ドメインロジック・UseCase のオーケストレーション
```

| 種別 | 対象レイヤー | ツール | Spring コンテキスト |
|---|---|---|---|
| Unit | domain, application | JUnit5 + Mockito | 不要 |
| Slice | presentation (Controller) | @WebMvcTest + MockMvc | Web 層のみ |
| Integration | infrastructure (Gateway) | JUnit5 + WireMock | 不要 |
| E2E | 全体 | Playwright + Docker Compose | フル起動 |

**Slice テストが担保するもの**
「このHTTPリクエストが来たとき、正しい UseCase を正しい引数で呼び、正しい HTTP レスポンスを返すか」
UseCase はモック。実 DB・実 HTTP クライアントは使わない。

**Integration テストが担保するもの**
「UseCase が Gateway を呼んだとき、正しい Downstream リクエストが構築され、レスポンスが正しくドメインモデルに変換されるか」
Downstream は WireMock でモック。実 RestClient を使う。

### フロントエンドテスト

| 種別 | 対象 | ツール | 担保するもの |
|---|---|---|---|
| Component | `components/ui/` | Storybook | コンポーネントの見た目・状態バリエーション |
| Integration | `features/{feature}/` | Vitest + Testing Library + MSW | ユーザー操作〜BFF 通信の結合動作 |
| E2E | 全体 | Playwright + Docker Compose | ユーザージャーニーの結合動作 |

Integration テストは Kent C. Dodds のアプローチに従い、実装の詳細ではなくユーザー視点の振る舞いをテストする。BFF への HTTP は MSW でモックする。

### E2E 環境

```
docker-compose.yml
├── frontend (React Router, CSR)
├── bff (Spring Boot)
└── downstream-mock (WireMock / モックサーバー)
```

Jenkins CI 上で `docker compose up` → Playwright 実行 → `docker compose down` の順に実行する。

---

## Feature チーム独立性

将来的に feature ごとのチームが独立してリリースできる構成を目指す。
今から以下のルールを守ることで、後の分離コストを最小化する。

### Feature 間の依存禁止

**フロントエンド**: `features/A` が `features/B` を直接 import することを ESLint で禁止する。

```js
// eslint.config.js（eslint-plugin-boundaries を使用）
rules: {
  "boundaries/element-types": ["error", {
    elements: [
      { type: "feature", pattern: "features/*" },
      { type: "ui", pattern: "components/ui" },
      { type: "generated", pattern: "types/generated" },
    ],
    rules: [
      { from: "feature", disallow: ["feature"] },
    ]
  }]
}
```

feature 間で共有が必要になったコンポーネントは `components/ui/` に昇格させる。

**BFF**: feature 間の package 依存を ArchUnit で検出する。

```kotlin
@Test
fun `features must not depend on each other`() {
    val classes = ClassFileImporter().importPackages("com.example.app")
    ArchRuleDefinition.noClasses()
        .that().resideInAPackage("..(*)..")
        .and().resideOutsideOfPackage("..shared..")
        .should().dependOnClassesThat()
        .resideInAPackage("..(*)..")
        .andShould().haveSimpleNameNotStartingWith(
            // 同一 feature 内は許可
        )
        .check(classes)
}
```

feature 間で共有が必要になったコードは `shared/` パッケージに昇格させる。

### 将来の拡張パス

| フェーズ | 対応内容 |
|---|---|
| 現在 | feature 間依存禁止ルール・API バージョニング・openapi.json git 管理 |
| チーム分離時 | Module Federation（フロント）/ BFF サービス分割 |
| 独立リリース本格化時 | Feature flags / Consumer-Driven Contract Test（Pact） |
