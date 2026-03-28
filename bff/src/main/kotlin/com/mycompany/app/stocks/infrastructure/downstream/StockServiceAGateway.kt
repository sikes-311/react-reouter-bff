package com.mycompany.app.stocks.infrastructure.downstream

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
@Qualifier("stockServiceAGateway")
class StockServiceAGateway(
    @Qualifier("restClientServiceA") restClient: RestClient,
) : AbstractStockGateway(restClient, "Service A")
