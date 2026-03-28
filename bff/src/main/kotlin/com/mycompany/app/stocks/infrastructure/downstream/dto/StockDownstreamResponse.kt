package com.mycompany.app.stocks.infrastructure.downstream.dto

import com.mycompany.app.stocks.domain.model.Stock
import java.math.BigDecimal

data class StockDownstreamResponse(
    val ticker: String,
    val price: BigDecimal,
    val date: String,
    val changePercent: Double,
) {
    fun toDomain(): Stock =
        Stock(
            ticker = ticker,
            price = price,
            date = date,
            changePercent = changePercent,
        )
}
