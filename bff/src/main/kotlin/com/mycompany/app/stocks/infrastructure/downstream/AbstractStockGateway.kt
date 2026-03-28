package com.mycompany.app.stocks.infrastructure.downstream

import com.mycompany.app.shared.exception.DownstreamServerException
import com.mycompany.app.shared.exception.UnauthorizedException
import com.mycompany.app.stocks.application.port.out.StockGateway
import com.mycompany.app.stocks.domain.model.Stock
import com.mycompany.app.stocks.infrastructure.downstream.dto.StockDownstreamResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.RestClient

abstract class AbstractStockGateway(
    private val restClient: RestClient,
    private val serviceName: String,
) : StockGateway {
    override fun findPopular(sessionCookie: String): List<Stock> {
        val res =
            restClient
                .get()
                .uri("/stocks/popular")
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    when (response.statusCode.value()) {
                        401 -> throw UnauthorizedException()
                        else -> throw DownstreamServerException("Downstream 4xx: ${response.statusCode}")
                    }
                }
                .onStatus(HttpStatusCode::is5xxServerError) { _, response ->
                    throw DownstreamServerException("Downstream 5xx: ${response.statusCode}")
                }
                .body(Array<StockDownstreamResponse>::class.java)
                ?: throw DownstreamServerException("Empty response from $serviceName")

        return res.map { it.toDomain() }
    }
}
