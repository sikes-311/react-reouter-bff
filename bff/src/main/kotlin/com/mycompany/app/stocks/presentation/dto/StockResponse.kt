package com.mycompany.app.stocks.presentation.dto

import com.mycompany.app.stocks.domain.model.Stock
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class StockResponse(
    val ticker: String,
    val price: BigDecimal,
    val date: String,
    val changePercent: Double,
) {
    companion object {
        private val DOWNSTREAM_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val RESPONSE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd")

        fun from(stock: Stock): StockResponse {
            val formattedDate =
                LocalDate
                    .parse(stock.date, DOWNSTREAM_DATE_FORMAT)
                    .format(RESPONSE_DATE_FORMAT)
            return StockResponse(
                ticker = stock.ticker,
                price = stock.price,
                date = formattedDate,
                changePercent = stock.changePercent,
            )
        }
    }
}
