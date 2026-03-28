package com.mycompany.app.stocks.domain.model

import java.math.BigDecimal

data class Stock(
    val ticker: String,
    val price: BigDecimal,
    val date: String,
    val changePercent: Double,
)
