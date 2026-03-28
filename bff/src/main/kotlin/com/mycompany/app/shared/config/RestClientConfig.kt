package com.mycompany.app.shared.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig {
    @Value("\${downstream.service-a.base-url}")
    private lateinit var serviceABaseUrl: String

    @Value("\${downstream.service-b.base-url}")
    private lateinit var serviceBBaseUrl: String

    @Bean("restClientServiceA")
    fun restClientServiceA(): RestClient =
        RestClient.builder()
            .baseUrl(serviceABaseUrl)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()

    @Bean("restClientServiceB")
    fun restClientServiceB(): RestClient =
        RestClient.builder()
            .baseUrl(serviceBBaseUrl)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()
}
