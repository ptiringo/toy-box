package com.example.api.controller

import com.example.api.config.RouterConfig
import com.example.api.handler.HelloHandler
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient

class HelloControllerTest {

    private val helloHandler = HelloHandler()
    private val routerConfig = RouterConfig()
    private val webTestClient = WebTestClient.bindToRouterFunction(routerConfig.apiRoutes(helloHandler)).build()

    @Test
    fun `should return Hello World message when calling hello endpoint`() {
        webTestClient.get()
            .uri("/api/hello")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.message").isEqualTo("Hello World")
    }
}
