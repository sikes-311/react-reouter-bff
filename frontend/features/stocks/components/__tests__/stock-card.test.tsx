// @SC-2: カードに銘柄・価格・日付・前日比が表示される
// @SC-3: 前日比が正の場合、緑色+プラス符号で表示される
// @SC-4: 前日比が負の場合、赤色+マイナス符号で表示される
import { render, screen } from "@testing-library/react";
import { StockCard } from "../stock-card";

test("ticker / price / date / changePercent が表示される", () => {
  render(
    <StockCard
      ticker="AAPL"
      price={175.5}
      date="2026/03/28"
      changePercent={1.23}
    />
  );

  expect(screen.getByText("AAPL")).toBeInTheDocument();
  expect(screen.getByText("175.5")).toBeInTheDocument();
  expect(screen.getByText("2026/03/28")).toBeInTheDocument();
  expect(screen.getByText("+1.23%")).toBeInTheDocument();
});

test("changePercent が正の場合、+1.23% が緑色で表示される", () => {
  render(
    <StockCard
      ticker="AAPL"
      price={175.5}
      date="2026/03/28"
      changePercent={1.23}
    />
  );

  const changeEl = screen.getByText("+1.23%");
  expect(changeEl).toBeInTheDocument();
  expect(changeEl).toHaveStyle({ color: "rgb(0, 128, 0)" });
});

test("changePercent が負の場合、-0.45% が赤色で表示される", () => {
  render(
    <StockCard
      ticker="GOOGL"
      price={178.2}
      date="2026/03/28"
      changePercent={-0.45}
    />
  );

  const changeEl = screen.getByText("-0.45%");
  expect(changeEl).toBeInTheDocument();
  expect(changeEl).toHaveStyle({ color: "rgb(255, 0, 0)" });
});
