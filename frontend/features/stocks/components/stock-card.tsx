type StockCardProps = {
  ticker: string;
  price: number;
  date: string;
  changePercent: number;
};

export function StockCard({
  ticker,
  price,
  date,
  changePercent,
}: StockCardProps) {
  const isPositive = changePercent >= 0;
  const changeText = isPositive
    ? `+${changePercent.toFixed(2)}%`
    : `${changePercent.toFixed(2)}%`;
  const changeColor = isPositive ? "rgb(0, 128, 0)" : "rgb(255, 0, 0)";
  const changeBg = isPositive ? "#f0fdf4" : "#fff1f2";

  return (
    <div
      data-testid="stock-card"
      className="flex-1 flex flex-col gap-3 rounded-2xl border border-gray-200 bg-white px-6 py-5 shadow-sm hover:shadow-md transition-shadow"
    >
      <div className="flex items-center justify-between">
        <span data-testid="stock-ticker" className="text-lg font-bold text-gray-900 tracking-wide">
          {ticker}
        </span>
        <span
          data-testid="stock-change-percent"
          className="text-sm font-semibold px-2 py-0.5 rounded-full"
          style={{ color: changeColor, backgroundColor: changeBg }}
        >
          {changeText}
        </span>
      </div>
      <p data-testid="stock-price" className="text-2xl font-bold text-gray-800">
        {price}
      </p>
      <p data-testid="stock-date" className="text-xs text-gray-400">
        {date}
      </p>
    </div>
  );
}
