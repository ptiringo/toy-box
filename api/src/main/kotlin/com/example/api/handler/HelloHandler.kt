package com.example.api.handler

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

@Component
class HelloHandler {

    fun hello(request: ServerRequest): Mono<ServerResponse> {
        return ServerResponse.ok()
            .bodyValue(mapOf("message" to "Hello World"))
    }
}