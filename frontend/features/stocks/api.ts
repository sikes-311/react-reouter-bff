import type { components } from "../../types/generated/api";

export type StockResponse = components["schemas"]["StockResponse"];

export async function getPopularStocks(request: Request): Promise<Response> {
  const url = new URL("/api/v1/stocks/popular", request.url);
  return fetch(url.toString());
}
