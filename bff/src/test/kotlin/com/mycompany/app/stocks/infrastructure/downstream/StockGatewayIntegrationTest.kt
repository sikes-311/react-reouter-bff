package com.mycompany.app.stocks.infrastructure.downstream

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.mycompany.app.shared.exception.DownstreamServerException
import com.mycompany.app.shared.exception.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.math.BigDecimal

/**
 * StockServiceAGateway と StockServiceBGateway は実装がほぼ同一であるため、
 * 両 Gateway を同一テストクラスで結合テストする。
 */
class StockGatewayIntegrationTest {

    companion object {
        @RegisterExtension
        @JvmField
        val wireMockServer: WireMockExtension = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build()
    }

    private lateinit var gatewayA: StockServiceAGateway
    private lateinit var gatewayB: StockServiceBGateway

    @BeforeEach
    fun setUp() {
        // WireMockExtension.newInstance() で作成した場合、wireMockServer から URL を取得できる
        val baseUrl = "http://localhost:${wireMockServer.port}"
        val restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
        gatewayA = StockServiceAGateway(restClient)
        gatewayB = StockServiceBGateway(restClient)
    }

    // ---- StockServiceAGateway ----

    @Nested
    inner class ServiceAGatewayTest {

        // @SC-1, @SC-2: 正常レスポンスがドメインモデルに変換されること
        @Test
        fun `正常系 - レスポンスがStockドメインモデルに正しく変換されること（date変換含む）`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/stocks/popular")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            [
                              { "ticker": "AAPL",  "price": 175.50, "date": "2026-03-28", "changePercent": 1.23 },
                              { "ticker": "GOOGL", "price": 178.20, "date": "2026-03-28", "changePercent": -0.45 }
                            ]
                            """.trimIndent(),
                        ),
                ),
            )

            val result = gatewayA.findPopular("session=abc")

            // ドメインモデルの各フィールドが正しくマッピングされること
            assertThat(result).hasSize(2)
            val aapl = result[0]
            assertThat(aapl.ticker).isEqualTo("AAPL")
            assertThat(aapl.price).isEqualByComparingTo(BigDecimal("175.50"))
            // toDomain() では date 変換をしない（変換は StockResponse.from() が担当）
            assertThat(aapl.date).isEqualTo("2026-03-28")
            assertThat(aapl.changePercent).isEqualTo(1.23)

            // Cookie ヘッダーが正しく転送されること
            wireMockServer.verify(
                getRequestedFor(urlEqualTo("/stocks/popular"))
                    .withHeader("Cookie", equalTo("session=abc")),
            )
        }

        // @SC-9: Downstream が 401 を返した場合 UnauthorizedException をスローすること
        @Test
        fun `異常系 - Downstreamが401を返した場合UnauthorizedExceptionをスローすること`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/stocks/popular")).willReturn(
                    aResponse().withStatus(401),
                ),
            )

            assertThrows<UnauthorizedException> {
                gatewayA.findPopular("session=expired")
            }
        }

        // @SC-7: Downstream が 500 を返した場合 DownstreamServerException をスローすること
        @Test
        fun `異常系 - Downstreamが500を返した場合DownstreamServerExceptionをスローすること`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/stocks/popular")).willReturn(
                    aResponse().withStatus(500),
                ),
            )

            assertThrows<DownstreamServerException> {
                gatewayA.findPopular("session=abc")
            }
        }
    }

    // ---- StockServiceBGateway ----

    @Nested
    inner class ServiceBGatewayTest {

        // @SC-1, @SC-2: 正常レスポンスがドメインモデルに変換されること
        @Test
        fun `正常系 - レスポンスがStockドメインモデルに正しく変換されること（date変換含む）`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/stocks/popular")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            [
                              { "ticker": "AMZN", "price": 195.80, "date": "2026-03-28", "changePercent": 2.10 },
                              { "ticker": "NVDA", "price": 890.40, "date": "2026-03-28", "changePercent": -1.35 }
                            ]
                            """.trimIndent(),
                        ),
                ),
            )

            val result = gatewayB.findPopular("session=abc")

            assertThat(result).hasSize(2)
            val amzn = result[0]
            assertThat(amzn.ticker).isEqualTo("AMZN")
            assertThat(amzn.price).isEqualByComparingTo(BigDecimal("195.80"))
            assertThat(amzn.date).isEqualTo("2026-03-28")
            assertThat(amzn.changePercent).isEqualTo(2.10)

            wireMockServer.verify(
                getRequestedFor(urlEqualTo("/stocks/popular"))
                    .withHeader("Cookie", equalTo("session=abc")),
            )
        }

        // @SC-9: Downstream が 401 を返した場合 UnauthorizedException をスローすること
        @Test
        fun `異常系 - Downstreamが401を返した場合UnauthorizedExceptionをスローすること`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/stocks/popular")).willReturn(
                    aResponse().withStatus(401),
                ),
            )

            assertThrows<UnauthorizedException> {
                gatewayB.findPopular("session=expired")
            }
        }

        // @SC-7: Downstream が 500 を返した場合 DownstreamServerException をスローすること
        @Test
        fun `異常系 - Downstreamが500を返した場合DownstreamServerExceptionをスローすること`() {
            wireMockServer.stubFor(
                get(urlEqualTo("/stocks/popular")).willReturn(
                    aResponse().withStatus(500),
                ),
            )

            assertThrows<DownstreamServerException> {
                gatewayB.findPopular("session=abc")
            }
        }
    }
}
