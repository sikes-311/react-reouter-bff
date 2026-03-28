import type { StockResponse } from "../api";
import { StockCard } from "./stock-card";

type StockListProps = {
  stocks: StockResponse[] | null;
  error: boolean;
};

export function StockList({ stocks, error }: StockListProps) {
  return (
    <section data-testid="stocks-section" className="px-6 py-8">
      {error ? (
        <p className="text-center text-gray-500">現在株価を表示できません。</p>
      ) : (
        <>
          <div className="flex gap-4">
            {stocks?.map((stock) => (
              <StockCard
                key={`${stock.ticker}-${stock.date}`}
                ticker={stock.ticker}
                price={stock.price}
                date={stock.date}
                changePercent={stock.changePercent}
              />
            ))}
          </div>
          <div className="mt-4 text-right">
            <a
              href="/stocks"
              data-testid="stocks-view-more"
              className="text-sm text-blue-600 hover:underline"
            >
              View more stock prices
            </a>
          </div>
        </>
      )}
    </section>
  );
}
