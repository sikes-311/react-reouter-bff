package com.mycompany.app.stocks.presentation

import com.mycompany.app.shared.exception.DownstreamServerException
import com.mycompany.app.shared.exception.UnauthorizedException
import com.mycompany.app.stocks.application.usecase.GetPopularStocksQuery
import com.mycompany.app.stocks.application.usecase.GetPopularStocksQueryResult
import com.mycompany.app.stocks.application.usecase.GetPopularStocksUseCase
import com.mycompany.app.stocks.domain.model.Stock
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal

@WebMvcTest(StockController::class)
class StockControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var getPopularStocksUseCase: GetPopularStocksUseCase

    // @SC-1: 正常系 - 200 と StockResponse のリストを返す
    // @CookieValue(name = "session") は Cookie の値部分のみを取得するため
    // sessionCookie は Cookie 名なしの値のみ（"abc"）となる
    @Test
    fun `GET popular - 正常系 200とStockResponseのリストを返すこと`() {
        // Arrange
        val stocks = listOf(
            Stock(ticker = "AMZN", price = BigDecimal("195.80"), date = "2026-03-28", changePercent = 2.10),
            Stock(ticker = "AAPL", price = BigDecimal("172.75"), date = "2026-03-28", changePercent = 1.115),
            Stock(ticker = "MSFT", price = BigDecimal("420.30"), date = "2026-03-28", changePercent = 0.78),
        )
        // @CookieValue は Cookie 値のみを返すため "abc" が sessionCookie に格納される
        val query = GetPopularStocksQuery(sessionCookie = "abc")
        doReturn(GetPopularStocksQueryResult(stocks = stocks))
            .`when`(getPopularStocksUseCase).execute(query)

        // Act & Assert
        mockMvc.get("/api/v1/stocks/popular") {
            cookie(Cookie("session", "abc"))
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].ticker") { value("AMZN") }
            jsonPath("$[0].price")  { value(195.80) }
            // date: yyyy-MM-dd → yyyy/MM/dd 変換確認
            jsonPath("$[0].date")   { value("2026/03/28") }
            jsonPath("$[0].changePercent") { value(2.10) }
            jsonPath("$[1].ticker") { value("AAPL") }
            jsonPath("$[2].ticker") { value("MSFT") }
        }

        // Cookie 値が UseCase に渡されること
        verify(getPopularStocksUseCase).execute(query)
    }

    // @SC-1: 正常系 - Cookie なしでも空文字として UseCase が呼ばれること
    @Test
    fun `GET popular - Cookieなしの場合、空文字セッションクッキーでUseCaseが呼ばれること`() {
        val query = GetPopularStocksQuery(sessionCookie = "")
        doReturn(GetPopularStocksQueryResult(stocks = emptyList()))
            .`when`(getPopularStocksUseCase).execute(query)

        mockMvc.get("/api/v1/stocks/popular").andExpect {
            status { isOk() }
        }

        verify(getPopularStocksUseCase).execute(query)
    }

    // @SC-7: UseCase が DownstreamServerException をスローした場合 502 を返すこと
    @Test
    fun `GET popular - UseCaseがDownstreamServerExceptionをスローした場合502を返すこと`() {
        val query = GetPopularStocksQuery(sessionCookie = "")
        doThrow(DownstreamServerException("Both downstream services failed"))
            .`when`(getPopularStocksUseCase).execute(query)

        mockMvc.get("/api/v1/stocks/popular").andExpect {
            status { isBadGateway() }
            jsonPath("$.error")  { value("Bad Gateway") }
            jsonPath("$.status") { value(502) }
            jsonPath("$.detail") { isNotEmpty() }
        }
    }

    // @SC-9: UseCase が UnauthorizedException をスローした場合 401 を返すこと
    @Test
    fun `GET popular - UseCaseがUnauthorizedExceptionをスローした場合401を返すこと`() {
        // Cookie 値 "expired" が sessionCookie に格納される
        val query = GetPopularStocksQuery(sessionCookie = "expired")
        doThrow(UnauthorizedException())
            .`when`(getPopularStocksUseCase).execute(query)

        mockMvc.get("/api/v1/stocks/popular") {
            cookie(Cookie("session", "expired"))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error")  { value("Unauthorized") }
            jsonPath("$.status") { value(401) }
        }
    }
}
