package com.mycompany.app.stocks.application.usecase

import com.mycompany.app.shared.exception.DownstreamServerException
import com.mycompany.app.shared.exception.UnauthorizedException
import com.mycompany.app.stocks.application.port.out.StockGateway
import com.mycompany.app.stocks.domain.model.Stock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class GetPopularStocksUseCaseTest {

    @Mock
    private lateinit var stockServiceAGateway: StockGateway

    @Mock
    private lateinit var stockServiceBGateway: StockGateway

    private fun buildUseCase() = GetPopularStocksUseCase(stockServiceAGateway, stockServiceBGateway)

    // @SC-1: 両サービスのデータをマージし上位5銘柄が表示される
    @Test
    fun `正常系 - 両サービスが正常レスポンスを返す場合、changePercent降順Top5が返ること`() {
        // Arrange
        // Service A: 3銘柄 (AAPL=1.23, GOOGL=-0.45, MSFT=0.78)
        val stocksA = listOf(
            Stock(ticker = "AAPL",  price = BigDecimal("175.50"), date = "2026-03-28", changePercent = 1.23),
            Stock(ticker = "GOOGL", price = BigDecimal("178.20"), date = "2026-03-28", changePercent = -0.45),
            Stock(ticker = "MSFT",  price = BigDecimal("420.30"), date = "2026-03-28", changePercent = 0.78),
        )
        // Service B: 3銘柄 (AAPL=1.00[重複], AMZN=2.10, NVDA=-1.35)
        val stocksB = listOf(
            Stock(ticker = "AAPL", price = BigDecimal("170.00"), date = "2026-03-28", changePercent = 1.00),
            Stock(ticker = "AMZN", price = BigDecimal("195.80"), date = "2026-03-28", changePercent = 2.10),
            Stock(ticker = "NVDA", price = BigDecimal("890.40"), date = "2026-03-28", changePercent = -1.35),
        )
        `when`(stockServiceAGateway.findPopular("session=abc")).thenReturn(stocksA)
        `when`(stockServiceBGateway.findPopular("session=abc")).thenReturn(stocksB)

        val useCase = buildUseCase()

        // Act
        val result = useCase.execute(GetPopularStocksQuery(sessionCookie = "session=abc"))

        // Assert: 上位5銘柄が changePercent 降順で返ること
        // AMZN(2.10) > AAPL(avg=1.115) > MSFT(0.78) > GOOGL(-0.45) > NVDA(-1.35)
        assertThat(result.stocks).hasSize(5)
        assertThat(result.stocks[0].ticker).isEqualTo("AMZN")
        assertThat(result.stocks[1].ticker).isEqualTo("AAPL")
        assertThat(result.stocks[2].ticker).isEqualTo("MSFT")
        assertThat(result.stocks[3].ticker).isEqualTo("GOOGL")
        assertThat(result.stocks[4].ticker).isEqualTo("NVDA")
        // changePercent が降順であること
        val percents = result.stocks.map { it.changePercent }
        assertThat(percents).isSortedAccordingTo(Comparator.reverseOrder())
    }

    // @SC-6: 同一(ticker+date)は price/changePercent が平均値で表示される
    @Test
    fun `正常系 - 同一tickerとdateが両サービスに存在する場合、priceとchangePercentが平均値になること`() {
        // Arrange
        val stocksA = listOf(
            Stock(ticker = "AAPL", price = BigDecimal("180.00"), date = "2026-03-28", changePercent = 2.00),
        )
        val stocksB = listOf(
            Stock(ticker = "AAPL", price = BigDecimal("170.00"), date = "2026-03-28", changePercent = 1.00),
        )
        `when`(stockServiceAGateway.findPopular("session=abc")).thenReturn(stocksA)
        `when`(stockServiceBGateway.findPopular("session=abc")).thenReturn(stocksB)

        val useCase = buildUseCase()

        // Act
        val result = useCase.execute(GetPopularStocksQuery(sessionCookie = "session=abc"))

        // Assert: price = (180.00 + 170.00) / 2 = 175.00、changePercent = (2.00 + 1.00) / 2 = 1.50
        assertThat(result.stocks).hasSize(1)
        val aapl = result.stocks[0]
        assertThat(aapl.ticker).isEqualTo("AAPL")
        assertThat(aapl.price).isEqualByComparingTo(BigDecimal("175.00"))
        assertThat(aapl.changePercent).isEqualTo(1.5)
    }

    // @SC-8: Service A のみ失敗した場合は Service B のデータのみで処理される
    @Test
    fun `異常系 - Service Aのみ失敗した場合、Service Bのデータのみで処理されること`() {
        // Arrange
        val stocksB = listOf(
            Stock(ticker = "AMZN", price = BigDecimal("195.80"), date = "2026-03-28", changePercent = 2.10),
            Stock(ticker = "NVDA", price = BigDecimal("890.40"), date = "2026-03-28", changePercent = -1.35),
        )
        `when`(stockServiceAGateway.findPopular("session=abc"))
            .thenThrow(DownstreamServerException("Service A 500"))
        `when`(stockServiceBGateway.findPopular("session=abc")).thenReturn(stocksB)

        val useCase = buildUseCase()

        // Act
        val result = useCase.execute(GetPopularStocksQuery(sessionCookie = "session=abc"))

        // Assert: Service B の2銘柄のみが返ること
        assertThat(result.stocks).hasSize(2)
        assertThat(result.stocks.map { it.ticker }).containsExactlyInAnyOrder("AMZN", "NVDA")
    }

    // @SC-8: Service B のみ失敗した場合は Service A のデータのみで処理される
    @Test
    fun `異常系 - Service Bのみ失敗した場合、Service Aのデータのみで処理されること`() {
        // Arrange
        val stocksA = listOf(
            Stock(ticker = "AAPL",  price = BigDecimal("175.50"), date = "2026-03-28", changePercent = 1.23),
            Stock(ticker = "GOOGL", price = BigDecimal("178.20"), date = "2026-03-28", changePercent = -0.45),
        )
        `when`(stockServiceAGateway.findPopular("session=abc")).thenReturn(stocksA)
        `when`(stockServiceBGateway.findPopular("session=abc"))
            .thenThrow(DownstreamServerException("Service B 500"))

        val useCase = buildUseCase()

        // Act
        val result = useCase.execute(GetPopularStocksQuery(sessionCookie = "session=abc"))

        // Assert: Service A の2銘柄のみが返ること
        assertThat(result.stocks).hasSize(2)
        assertThat(result.stocks.map { it.ticker }).containsExactlyInAnyOrder("AAPL", "GOOGL")
    }

    // UnauthorizedException は握りつぶさず再スローされる
    @Test
    fun `異常系 - Service Aが UnauthorizedException をスローした場合、そのまま再スローされること`() {
        // Arrange
        `when`(stockServiceAGateway.findPopular("session=abc"))
            .thenThrow(UnauthorizedException())
        `when`(stockServiceBGateway.findPopular("session=abc"))
            .thenReturn(emptyList())

        val useCase = buildUseCase()

        // Act & Assert: UnauthorizedException が再スローされること（Service B の成功に関わらず）
        assertThrows<UnauthorizedException> {
            useCase.execute(GetPopularStocksQuery(sessionCookie = "session=abc"))
        }
    }

    // @SC-7: 両サービス失敗時に DownstreamServerException がスローされる
    @Test
    fun `異常系 - 両サービスが失敗した場合、DownstreamServerExceptionがスローされること`() {
        // Arrange
        `when`(stockServiceAGateway.findPopular("session=abc"))
            .thenThrow(DownstreamServerException("Service A 500"))
        `when`(stockServiceBGateway.findPopular("session=abc"))
            .thenThrow(DownstreamServerException("Service B 500"))

        val useCase = buildUseCase()

        // Act & Assert
        assertThrows<DownstreamServerException> {
            useCase.execute(GetPopularStocksQuery(sessionCookie = "session=abc"))
        }
    }
}
