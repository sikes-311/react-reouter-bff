package com.mycompany.app.stocks.infrastructure.downstream

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
@Qualifier("stockServiceBGateway")
class StockServiceBGateway(
    @Qualifier("restClientServiceB") restClient: RestClient,
) : AbstractStockGateway(restClient, "Service B")
