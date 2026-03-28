import { test, expect } from "@playwright/test";

const WIREMOCK_ADMIN_A = "http://localhost:8081/__admin";
const WIREMOCK_ADMIN_B = "http://localhost:8082/__admin";

// TODO: 認証機能実装後に login() を追加する（認証は別 Issue）

/** WireMock の動的スタブをリセットし、永続スタブのみの状態に戻す */
async function resetWireMock() {
  await Promise.all([
    fetch(`${WIREMOCK_ADMIN_A}/scenarios/reset`, { method: "POST" }),
    fetch(`${WIREMOCK_ADMIN_B}/scenarios/reset`, { method: "POST" }),
    // 動的追加スタブを削除（priority=1 で追加したもの）
    fetch(`${WIREMOCK_ADMIN_A}/mappings/reset`, { method: "POST" }),
    fetch(`${WIREMOCK_ADMIN_B}/mappings/reset`, { method: "POST" }),
  ]);
}

test.afterEach(async () => {
  await resetWireMock();
});

// @SC-1: 両サービスのデータをマージし上位5銘柄が表示される（正常系）
//
// Arrange:
//   - docker-compose の WireMock スタブにより
//     Service A: AAPL(1.23%), GOOGL(-0.45%), MSFT(0.78%)
//     Service B: AAPL(1.00%), AMZN(2.10%), NVDA(-1.35%)
//     を返す状態になっている
//
// マージ結果（changePercent 降順）:
//   1位: AMZN  +2.10%
//   2位: AAPL  +1.115%（平均）
//   3位: MSFT  +0.78%
//   4位: GOOGL -0.45%
//   5位: NVDA  -1.35%
test("SC-1: 両サービスのデータをマージし上位5銘柄が表示される", async ({
  page,
}) => {
  // Act: ホームページにアクセスする
  await page.goto("/");
  await page.waitForLoadState("networkidle");

  // Assert: 株価カードが5枚表示される
  const stockCards = page.locator('[data-testid="stock-card"]');
  await expect(stockCards).toHaveCount(5);

  // Assert: 株価セクション全体が表示される
  await expect(page.locator('[data-testid="stocks-section"]')).toBeVisible();

  // Assert: 1枚目が AMZN（changePercent 最大）
  const firstCard = stockCards.nth(0);
  await expect(firstCard.locator('[data-testid="stock-ticker"]')).toHaveText(
    "AMZN"
  );

  // Assert: 5枚目が NVDA（changePercent 最小）
  const lastCard = stockCards.nth(4);
  await expect(lastCard.locator('[data-testid="stock-ticker"]')).toHaveText(
    "NVDA"
  );

  // Assert: "View more" リンクが表示される
  await expect(
    page.locator('[data-testid="stocks-view-more"]')
  ).toBeVisible();
});
