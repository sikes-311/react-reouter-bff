package com.mycompany.app.stocks.application.port.out

import com.mycompany.app.stocks.domain.model.Stock

interface StockGateway {
    fun findPopular(sessionCookie: String): List<Stock>
}
