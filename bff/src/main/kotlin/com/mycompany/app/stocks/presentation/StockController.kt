package com.mycompany.app.stocks.presentation

import com.mycompany.app.stocks.application.usecase.GetPopularStocksQuery
import com.mycompany.app.stocks.application.usecase.GetPopularStocksUseCase
import com.mycompany.app.stocks.presentation.dto.StockResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/stocks")
class StockController(
    private val getPopularStocksUseCase: GetPopularStocksUseCase,
) {
    @GetMapping("/popular")
    fun getPopularStocks(
        @CookieValue(name = "session", required = false) sessionCookie: String?,
    ): ResponseEntity<List<StockResponse>> {
        val query = GetPopularStocksQuery(sessionCookie = sessionCookie ?: "")
        val result = getPopularStocksUseCase.execute(query)
        return ResponseEntity.ok(result.stocks.map { StockResponse.from(it) })
    }
}
