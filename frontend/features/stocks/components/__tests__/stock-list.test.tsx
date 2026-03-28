// @SC-7: 両サービス失敗時にエラーメッセージが表示される
// @SC-5: "View more stock prices" リンクが表示される
import { render, screen } from "@testing-library/react";
import { StockList } from "../stock-list";

const sampleStocks = [
  { ticker: "AAPL", price: 172.75, date: "2026/03/28", changePercent: 1.115 },
  { ticker: "AMZN", price: 195.8, date: "2026/03/28", changePercent: 2.1 },
  { ticker: "MSFT", price: 420.3, date: "2026/03/28", changePercent: 0.78 },
  { ticker: "GOOGL", price: 178.2, date: "2026/03/28", changePercent: -0.45 },
  { ticker: "NVDA", price: 890.4, date: "2026/03/28", changePercent: -1.35 },
];

test("error=true の場合、エラーメッセージが表示され stock-card は表示されない", () => {
  render(<StockList stocks={null} error={true} />);

  expect(screen.getByText("現在株価を表示できません。")).toBeInTheDocument();
  expect(screen.queryAllByTestId("stock-card")).toHaveLength(0);
});

test("error=false かつ stocks が5件の場合、5枚のカードが表示される", () => {
  render(<StockList stocks={sampleStocks} error={false} />);

  expect(screen.getAllByTestId("stock-card")).toHaveLength(5);
});

test('"View more stock prices" リンクが表示される', () => {
  render(<StockList stocks={sampleStocks} error={false} />);

  expect(
    screen.getByRole("link", { name: "View more stock prices" })
  ).toBeInTheDocument();
});

test("stocks=null かつ error=false の場合、カードもエラーも表示されない", () => {
  render(<StockList stocks={null} error={false} />);

  expect(screen.queryAllByTestId("stock-card")).toHaveLength(0);
  expect(screen.queryByText("現在株価を表示できません。")).not.toBeInTheDocument();
});
