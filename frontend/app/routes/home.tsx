import { isRouteErrorResponse, Navigate, useRouteError } from "react-router";
import type { StockResponse } from "../../features/stocks/api";
import { getPopularStocks } from "../../features/stocks/api";
import { StockList } from "../../features/stocks/components/stock-list";
import type { Route } from "./+types/home";

export function meta(_: Route.MetaArgs) {
  return [
    { title: "New React Router App" },
    { name: "description", content: "Welcome to React Router!" },
  ];
}

type LoaderData = {
  stocks: StockResponse[] | null;
  stocksError: boolean;
};

export async function clientLoader({ request }: Route.ClientLoaderArgs): Promise<LoaderData> {
  const res = await getPopularStocks(request);
  if (res.status === 401) throw new Response("Unauthorized", { status: 401 });
  if (!res.ok) return { stocks: null, stocksError: true };
  const data: StockResponse[] = await res.json();
  return { stocks: data, stocksError: false };
}
clientLoader.hydrate = true as const;

export default function Home({ loaderData }: Route.ComponentProps) {
  if (!loaderData) return null;
  const { stocks, stocksError } = loaderData;
  return <StockList stocks={stocks} error={stocksError} />;
}

export function ErrorBoundary() {
  const error = useRouteError();
  if (isRouteErrorResponse(error)) {
    if (error.status === 401) return <Navigate to="/login" />;
  }
  return <p>予期しないエラーが発生しました</p>;
}
