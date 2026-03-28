package com.mycompany.app.shared.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig {
    @Value("\${downstream.base-url}")
    private lateinit var downstreamBaseUrl: String

    @Bean
    fun restClient(): RestClient =
        RestClient.builder()
            .baseUrl(downstreamBaseUrl)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()
}
