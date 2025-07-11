package com.example.api.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(HelloController::class)
class HelloControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

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