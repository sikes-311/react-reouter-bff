# DEVELOPMENT_RULES.md

## フロントエンド実装ルール

### ディレクトリ・ファイル命名規則

| 対象 | 命名規則 | 例 |
|---|---|---|
| feature ページ | `{feature}-{variant}-page.tsx` | `order-list-page.tsx` |
| feature コンポーネント | `{feature}-{role}.tsx` | `order-form.tsx` |
| UI コンポーネント | `{name}.tsx` | `button.tsx` |
| BFF クライアント | `api.ts`（feature 配下固定） | `features/order/api.ts` |
| 型（生成物） | 編集禁止 | `types/generated/api.d.ts` |

### ルート定義（routes.ts）

`routes.ts` は URL と feature ページのマッピングのみを記述する。ロジックは書かない。

```ts
import { type RouteConfig, route, layout } from "@react-router/dev/routes";

export default [
  route("/login", "../features/auth/pages/login-page.tsx"),
  layout("../features/shared/layouts/app-layout.tsx", [
    route("/orders", "../features/order/pages/order-list-page.tsx"),
    route("/orders/:id", "../features/order/pages/order-detail-page.tsx"),
  ]),
] satisfies RouteConfig;
```

### loader の実装ルール

- loader はBFF へのデータ取得のみを行う
- 認証エラー（401）・サーバーエラー（5xx）は `throw` する
- 成功データはそのまま `return` する

```ts
// features/order/pages/order-list-page.tsx
import type { Route } from "./+types/order-list-page";
import { getOrders } from "../api";

export async function loader({ request }: Route.LoaderArgs) {
  const res = await getOrders(request);
  if (res.status === 401) throw new Response("Unauthorized", { status: 401 });
  if (res.status === 404) throw new Response("Not Found", { status: 404 });
  if (!res.ok) throw new Response("Server Error", { status: 500 });
  return res.json();
}

export default function OrderListPage({ loaderData }: Route.ComponentProps) {
  const orders = loaderData;
  return <OrderList orders={orders} />;
}

export function ErrorBoundary() {
  const error = useRouteError();
  if (isRouteErrorResponse(error)) {
    if (error.status === 401) return <Navigate to="/login" />;
    if (error.status === 404) return <NotFoundMessage />;
  }
  return <GenericErrorMessage />;
}
```

### action の実装ルール

action は「BFF へのリクエスト関数」。エラーハンドリングは以下のルールで行う。

| BFF レスポンス | 手段 | 理由 |
|---|---|---|
| 400 バリデーションエラー | `return { errors }` | フォームにインライン表示するため |
| 401 未認証 | `throw new Response(...)` | ログインページへ遷移させるため |
| 5xx サーバーエラー | `throw new Response(...)` | ユーザーが対処できないため |
| 成功 | `return redirect(...)` | 遷移先へ |

```ts
import type { Route } from "./+types/order-list-page";
import { createOrder } from "../api";

export async function action({ request }: Route.ActionArgs) {
  const formData = await request.formData();
  const res = await createOrder(formData);

  if (res.status === 401) throw new Response("Unauthorized", { status: 401 });
  if (!res.ok && res.status >= 500) throw new Response("Server Error", { status: 500 });
  if (res.status === 400) return { errors: await res.json() };

  return redirect("/orders");
}
```

### フォームバリデーション（React Hook Form + Zod）

クライアントサイドのバリデーションは React Hook Form + Zod で行う。
バリデーションが通った場合のみ action（BFF 呼び出し）が実行される。

```tsx
// features/order/components/order-form.tsx
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useFetcher } from "react-router";

const schema = z.object({
  name: z.string().min(1, "名前は必須です"),
  quantity: z.number({ invalid_type_error: "数値を入力してください" }).positive("1以上を入力してください"),
});

type FormValues = z.infer<typeof schema>;

export function OrderForm() {
  const fetcher = useFetcher();
  const actionErrors = fetcher.data?.errors;

  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  return (
    <form onSubmit={handleSubmit((data) => fetcher.submit(data, { method: "post" }))}>
      <input {...register("name")} />
      {errors.name && <span>{errors.name.message}</span>}

      <input type="number" {...register("quantity", { valueAsNumber: true })} />
      {errors.quantity && <span>{errors.quantity.message}</span>}

      {/* BFF から返ってきた 400 エラーの表示 */}
      {actionErrors && <ErrorMessage errors={actionErrors} />}

      <button type="submit">送信</button>
    </form>
  );
}
```

### BFF クライアント（api.ts）

- feature 配下の `api.ts` に BFF 呼び出し関数をまとめる
- 引数は `request: Request`（Cookie を透過的に転送するため）または必要なパラメータ
- 戻り値は `Response` をそのまま返し、エラーハンドリングは呼び出し元（loader/action）で行う
- 型は `types/generated/api.d.ts` の生成型を使う

```ts
// features/order/api.ts
import type { paths } from "../../types/generated/api";

type OrderListResponse = paths["/api/v1/orders"]["get"]["responses"]["200"]["content"]["application/json"];

export async function getOrders(request: Request): Promise<Response> {
  return fetch("/api/v1/orders", {
    headers: { cookie: request.headers.get("cookie") ?? "" },
  });
}

export async function createOrder(formData: FormData): Promise<Response> {
  return fetch("/api/v1/orders", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(Object.fromEntries(formData)),
  });
}
```

### Feature 間の依存禁止

`features/A` から `features/B` へのimportは禁止。ESLint で自動検出する。

```
// OK
import { Button } from "../../components/ui/button";

// NG（別 feature への直接 import）
import { OrderCard } from "../order/components/order-card";
```

共有が必要なコンポーネントは `components/ui/` に昇格させる。

---

## BFF 実装ルール（Kotlin + Spring Boot）

### パッケージ・ファイル命名規則

| 対象 | 命名規則 | 例 |
|---|---|---|
| Controller | `{Feature}Controller` | `OrderController` |
| Request DTO | `{Feature}Request` | `CreateOrderRequest` |
| Response DTO | `{Feature}Response` | `OrderResponse` |
| UseCase | `{Action}{Feature}UseCase` | `CreateOrderUseCase` |
| Query | `{Action}{Feature}Query` | `GetOrderQuery` |
| QueryResult | `{Action}{Feature}QueryResult` | `GetOrderQueryResult` |
| Command | `{Action}{Feature}Command` | `CreateOrderCommand` |
| CommandResult | `{Action}{Feature}CommandResult` | `CreateOrderCommandResult` |
| Gateway (port) | `{Feature}Gateway` | `OrderGateway` |
| Gateway (impl) | `{Feature}DownstreamGateway` | `OrderDownstreamGateway` |
| Downstream DTO | `{Feature}Downstream{Request\|Response}` | `OrderDownstreamResponse` |

### Controller の実装ルール

- `@RestController` + `@RequestMapping("/api/v1/{feature}")` をクラスに付与
- メソッドは HTTP メソッドに対応したアノテーションを使う
- リクエストの Cookie を UseCase に渡す（セッション管理は Downstream に委譲）
- presentation DTO → Command/Query への変換は Controller が担う

```kotlin
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val getOrderUseCase: GetOrderUseCase,
    private val createOrderUseCase: CreateOrderUseCase,
) {
    @GetMapping
    fun getOrders(
        @CookieValue(name = "session", required = false) sessionCookie: String?,
    ): ResponseEntity<List<OrderResponse>> {
        val query = GetOrderQuery(sessionCookie = sessionCookie ?: "")
        val result = getOrderUseCase.execute(query)
        return ResponseEntity.ok(result.orders.map { OrderResponse.from(it) })
    }

    @PostMapping
    fun createOrder(
        @Valid @RequestBody req: CreateOrderRequest,
        @CookieValue(name = "session", required = false) sessionCookie: String?,
    ): ResponseEntity<OrderResponse> {
        val command = CreateOrderCommand(
            name = req.name,
            quantity = req.quantity,
            sessionCookie = sessionCookie ?: "",
        )
        val result = createOrderUseCase.execute(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(result.order))
    }
}
```

### UseCase の実装ルール

- 1 UseCase = 1 つのユースケース（単一責任）
- 入力は `{Action}{Feature}Query` / `{Action}{Feature}Command`
- 出力は `{Action}{Feature}QueryResult` / `{Action}{Feature}CommandResult`
- 外部依存（Gateway）はコンストラクタインジェクション
- ビジネスロジックはここに書く。Gateway は interface 越しに呼ぶ

```kotlin
// application/usecase/CreateOrderUseCase.kt
@Service
class CreateOrderUseCase(
    private val orderGateway: OrderGateway,
) {
    fun execute(command: CreateOrderCommand): CreateOrderCommandResult {
        val order = orderGateway.create(
            name = command.name,
            quantity = command.quantity,
            sessionCookie = command.sessionCookie,
        )
        return CreateOrderCommandResult(order = order)
    }
}

// application/port/out/OrderGateway.kt
interface OrderGateway {
    fun findAll(sessionCookie: String): List<Order>
    fun create(name: String, quantity: Int, sessionCookie: String): Order
}
```

### Gateway（DownstreamGateway）の実装ルール

- `application/port/out/{Feature}Gateway` の interface を実装する
- RestClient で Downstream API を呼び出す
- Downstream DTO → domain model への変換はこのクラスが担う
- Downstream のエラーレスポンスは `shared/exception/` の例外クラスに変換する
- Cookie は `Cookie` ヘッダーとして Downstream にそのまま転送する

```kotlin
// infrastructure/downstream/OrderDownstreamGateway.kt
@Component
class OrderDownstreamGateway(
    private val restClient: RestClient,
) : OrderGateway {

    override fun create(name: String, quantity: Int, sessionCookie: String): Order {
        val downstreamReq = CreateOrderDownstreamRequest(name = name, quantity = quantity)

        val res = restClient.post()
            .uri("/orders")
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(downstreamReq)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                when (response.statusCode.value()) {
                    401 -> throw UnauthorizedException()
                    404 -> throw ResourceNotFoundException("Order not found")
                    else -> throw DownstreamClientException("Downstream 4xx: ${response.statusCode}")
                }
            }
            .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                throw DownstreamServerException("Downstream 5xx: ${response.statusCode}")
            }
            .body(CreateOrderDownstreamResponse::class.java)
            ?: throw DownstreamServerException("Empty response from downstream")

        return res.toDomain()
    }
}
```

### 共通例外クラス（shared/exception/）

```kotlin
// shared/exception/AppExceptions.kt
class UnauthorizedException(message: String = "Unauthorized") : RuntimeException(message)
class ResourceNotFoundException(message: String) : RuntimeException(message)
class DownstreamClientException(message: String) : RuntimeException(message)
class DownstreamServerException(message: String) : RuntimeException(message)
```

### GlobalExceptionHandler

すべての例外はここで HTTP レスポンスに変換する。Controller に try-catch は書かない。

```kotlin
// shared/exception/GlobalExceptionHandler.kt
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(e: UnauthorizedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(error = "Unauthorized", status = 401, detail = e.message ?: ""))

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(e: ResourceNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(error = "Not Found", status = 404, detail = e.message ?: ""))

    @ExceptionHandler(DownstreamServerException::class)
    fun handleDownstreamServer(e: DownstreamServerException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(error = "Bad Gateway", status = 502, detail = e.message ?: ""))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val detail = e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = "Bad Request", status = 400, detail = detail))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        // 予期しない例外はログに出力してから 500 を返す
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(error = "Internal Server Error", status = 500, detail = "Unexpected error occurred"))
    }
}

// shared/exception/ErrorResponse.kt
data class ErrorResponse(
    val error: String,
    val status: Int,
    val detail: String,
)
```

### Request DTO のバリデーション

バリデーションアノテーションは Request DTO に付与する。Controller には `@Valid` のみ。

```kotlin
// presentation/dto/CreateOrderRequest.kt
data class CreateOrderRequest(
    @field:NotBlank(message = "名前は必須です")
    @field:Size(max = 255, message = "255文字以内で入力してください")
    val name: String,

    @field:NotNull(message = "数量は必須です")
    @field:Positive(message = "1以上を入力してください")
    val quantity: Int,
)
```

### Cookie パススルー（RestClient 設定）

```kotlin
// shared/config/RestClientConfig.kt
@Configuration
class RestClientConfig {
    @Bean
    fun restClient(): RestClient =
        RestClient.builder()
            .baseUrl(System.getenv("DOWNSTREAM_BASE_URL"))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
}
```

Cookie はリクエストごとに Gateway 内で動的に付与する（上記 Gateway の実装例を参照）。

### Feature 間の依存禁止（ArchUnit）

```kotlin
// shared/architecture/FeatureIsolationTest.kt
@AnalyzeClasses(packages = ["com.example.app"])
class FeatureIsolationTest {

    @ArchTest
    val featuresShouldNotDependOnEachOther: ArchRule =
        ArchRuleDefinition.noClasses()
            .that().resideInAPackage("com.example.app.(*)..")
            .and().resideOutsideOfPackage("com.example.app.shared..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.example.app.(*)..")
            .andShould().resideOutsideOfPackage("com.example.app.shared..")
}
```

---

## テスト実装ルール

### BFF Unit テスト（UseCase・Domain）

- Spring コンテキスト不要（`@SpringBootTest` は使わない）
- Gateway は Mockito でモック

```kotlin
@ExtendWith(MockitoExtension::class)
class CreateOrderUseCaseTest {

    @Mock lateinit var orderGateway: OrderGateway
    @InjectMocks lateinit var useCase: CreateOrderUseCase

    @Test
    fun `正常系 - Gatewayを呼び結果を返す`() {
        val command = CreateOrderCommand(name = "test", quantity = 1, sessionCookie = "session=abc")
        val order = Order(id = "1", name = "test", quantity = 1)
        whenever(orderGateway.create(any(), any(), any())).thenReturn(order)

        val result = useCase.execute(command)

        assertThat(result.order).isEqualTo(order)
        verify(orderGateway).create(name = "test", quantity = 1, sessionCookie = "session=abc")
    }
}
```

### BFF Slice テスト（Controller）

- `@WebMvcTest` で Web 層のみ起動
- UseCase は `@MockBean` でモック
- HTTP 契約・バリデーション・エラーレスポンス形式を検証する

```kotlin
@WebMvcTest(OrderController::class)
class OrderControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @MockBean lateinit var createOrderUseCase: CreateOrderUseCase

    @Test
    fun `POST - 正常系 201を返す`() {
        val result = CreateOrderCommandResult(order = Order(id = "1", name = "test", quantity = 1))
        whenever(createOrderUseCase.execute(any())).thenReturn(result)

        mockMvc.post("/api/v1/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "test", "quantity": 1}"""
            cookie(Cookie("session", "abc"))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value("1") }
        }
    }

    @Test
    fun `POST - バリデーションエラー 400を返す`() {
        mockMvc.post("/api/v1/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "", "quantity": -1}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Bad Request") }
            jsonPath("$.status") { value(400) }
        }
    }
}
```

### BFF Integration テスト（Gateway）

- Spring コンテキスト不要
- WireMock で Downstream をモック
- RestClient の実インスタンスを使う
- Downstream DTO → domain model 変換ロジックを検証する

```kotlin
@ExtendWith(WireMockExtension::class)
class OrderDownstreamGatewayTest {

    private lateinit var gateway: OrderDownstreamGateway

    @BeforeEach
    fun setUp(wireMock: WireMockRuntimeInfo) {
        val restClient = RestClient.builder()
            .baseUrl(wireMock.httpBaseUrl)
            .build()
        gateway = OrderDownstreamGateway(restClient)
    }

    @Test
    fun `create - Downstreamにリクエストを送りドメインモデルを返す`() {
        stubFor(post("/orders").willReturn(
            aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"id": "1", "name": "test", "quantity": 1}""")
        ))

        val order = gateway.create(name = "test", quantity = 1, sessionCookie = "session=abc")

        assertThat(order.id).isEqualTo("1")
        verify(postRequestedFor(urlEqualTo("/orders"))
            .withHeader("Cookie", equalTo("session=abc")))
    }
}
```

### フロントエンド Integration テスト（Testing Library + MSW）

- ユーザー操作起点でテストを書く（実装の詳細はテストしない）
- BFF への HTTP は MSW でモック
- `getByRole` / `getByText` / `findByText` を優先し、`data-testid` は最終手段

```tsx
// features/order/__tests__/order-list-page.test.tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import OrderListPage, { loader, action } from "../pages/order-list-page";

const server = setupServer(
  http.get("/api/v1/orders", () =>
    HttpResponse.json([{ id: "1", name: "テスト注文", quantity: 2 }])
  )
);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

test("注文一覧が表示される", async () => {
  const Stub = createRoutesStub([
    { path: "/orders", Component: OrderListPage, loader },
  ]);
  render(<Stub initialEntries={["/orders"]} />);

  expect(await screen.findByText("テスト注文")).toBeInTheDocument();
});

test("フォーム送信でBFFが呼ばれ一覧に追加される", async () => {
  server.use(
    http.post("/api/v1/orders", () =>
      HttpResponse.json({ id: "2", name: "新規注文", quantity: 1 }, { status: 201 })
    )
  );
  const Stub = createRoutesStub([
    { path: "/orders", Component: OrderListPage, loader, action },
  ]);
  render(<Stub initialEntries={["/orders"]} />);

  await userEvent.type(await screen.findByLabelText("名前"), "新規注文");
  await userEvent.click(screen.getByRole("button", { name: "送信" }));

  expect(await screen.findByText("新規注文")).toBeInTheDocument();
});
```
