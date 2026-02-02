package com.example.api.controller

import com.example.api.config.RouterConfig
import com.example.api.handler.HelloHandler
import org.junit.jupiter.api.Test
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest
@Import(RouterConfig::class, HelloHandler::class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class HelloControllerTest(
    val webTestClient: WebTestClient
) {

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
