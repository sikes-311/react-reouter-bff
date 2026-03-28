package com.mycompany.app.stocks.application.usecase

import com.mycompany.app.shared.exception.DownstreamServerException
import com.mycompany.app.shared.exception.UnauthorizedException
import com.mycompany.app.stocks.application.port.out.StockGateway
import com.mycompany.app.stocks.domain.model.Stock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

private fun <T> Result<T>.rethrowIfUnauthorized(): Result<T> =
    onFailure { e ->
        val cause = (e as? CompletionException)?.cause ?: e
        if (cause is UnauthorizedException) throw cause
    }

data class GetPopularStocksQuery(val sessionCookie: String)

data class GetPopularStocksQueryResult(val stocks: List<Stock>)

@Service
class GetPopularStocksUseCase(
    @Qualifier("stockServiceAGateway") private val stockServiceAGateway: StockGateway,
    @Qualifier("stockServiceBGateway") private val stockServiceBGateway: StockGateway,
) {
    companion object {
        private const val TOP_N = 5
    }

    fun execute(query: GetPopularStocksQuery): GetPopularStocksQueryResult {
        val futureA =
            CompletableFuture.supplyAsync {
                stockServiceAGateway.findPopular(query.sessionCookie)
            }
        val futureB =
            CompletableFuture.supplyAsync {
                stockServiceBGateway.findPopular(query.sessionCookie)
            }

        val resultsA = runCatching { futureA.join() }.rethrowIfUnauthorized().getOrNull()
        val resultsB = runCatching { futureB.join() }.rethrowIfUnauthorized().getOrNull()

        if (resultsA == null && resultsB == null) {
            throw DownstreamServerException("Both downstream services failed")
        }

        val combined = (resultsA ?: emptyList()) + (resultsB ?: emptyList())

        val merged = merge(combined)

        return GetPopularStocksQueryResult(stocks = merged)
    }

    private fun merge(stocks: List<Stock>): List<Stock> {
        return stocks
            .groupBy { Pair(it.ticker, it.date) }
            .map { (_, group) ->
                val avgPrice =
                    group
                        .map { it.price }
                        .fold(BigDecimal.ZERO) { acc, v -> acc + v }
                        .divide(BigDecimal(group.size), 10, RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                val avgChangePercent = group.map { it.changePercent }.average()
                Stock(
                    ticker = group.first().ticker,
                    price = avgPrice,
                    date = group.first().date,
                    changePercent = avgChangePercent,
                )
            }
            .sortedByDescending { it.changePercent }
            .take(TOP_N)
    }
}
