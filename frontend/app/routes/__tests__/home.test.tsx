// @SC-1: 両サービスのデータをマージし上位5銘柄が表示される
// @SC-7: 両サービス失敗時にエラーメッセージが表示される
// @SC-9: 認証切れ時にログインページへリダイレクトされる
import { render, screen } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import Home, { clientLoader, ErrorBoundary } from "../home";

const popularStocks = [
  { ticker: "AMZN", price: 195.8, date: "2026/03/28", changePercent: 2.1 },
  { ticker: "AAPL", price: 172.75, date: "2026/03/28", changePercent: 1.115 },
  { ticker: "MSFT", price: 420.3, date: "2026/03/28", changePercent: 0.78 },
  { ticker: "GOOGL", price: 178.2, date: "2026/03/28", changePercent: -0.45 },
  { ticker: "NVDA", price: 890.4, date: "2026/03/28", changePercent: -1.35 },
];

const BFF_STOCKS_URL = "http://localhost/api/v1/stocks/popular";

const server = setupServer(
  http.get(BFF_STOCKS_URL, () => HttpResponse.json(popularStocks))
);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function buildStub() {
  return createRoutesStub([
    {
      path: "/",
      Component: Home,
      loader: (args) => clientLoader({ ...args, serverLoader: async () => undefined }),
      ErrorBoundary,
    },
    {
      path: "/login",
      Component: () => <p>ログインページ</p>,
    },
  ]);
}

// @SC-1: 正常系 — 5件のデータが返ると株価セクションが表示される
test("正常系: 5件のデータが返ると株価カードが5枚表示される", async () => {
  const Stub = buildStub();
  render(<Stub initialEntries={["/"]} />);

  // 5枚の株価カードが表示されるまで待機
  const cards = await screen.findAllByTestId("stock-card");
  expect(cards).toHaveLength(5);

  // 各 ticker が表示されていることを確認
  expect(screen.getByText("AMZN")).toBeInTheDocument();
  expect(screen.getByText("AAPL")).toBeInTheDocument();
  expect(screen.getByText("MSFT")).toBeInTheDocument();
  expect(screen.getByText("GOOGL")).toBeInTheDocument();
  expect(screen.getByText("NVDA")).toBeInTheDocument();
});

// @SC-7: 5xx エラー — 「現在株価を表示できません。」が表示される
test("5xx エラー: 「現在株価を表示できません。」が表示される", async () => {
  server.use(
    http.get(BFF_STOCKS_URL, () =>
      new HttpResponse(null, { status: 502 })
    )
  );

  const Stub = buildStub();
  render(<Stub initialEntries={["/"]} />);

  expect(
    await screen.findByText("現在株価を表示できません。")
  ).toBeInTheDocument();
  expect(screen.queryAllByTestId("stock-card")).toHaveLength(0);
});

// @SC-9: 401 エラー — ローダーが throw してログインページへリダイレクトされる
test("401 エラー: ログインページへリダイレクトされる", async () => {
  server.use(
    http.get(BFF_STOCKS_URL, () =>
      new HttpResponse(null, { status: 401 })
    )
  );

  const Stub = buildStub();
  render(<Stub initialEntries={["/"]} />);

  expect(await screen.findByText("ログインページ")).toBeInTheDocument();
});
