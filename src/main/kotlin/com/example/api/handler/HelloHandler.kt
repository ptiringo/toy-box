package com.example.api.handler

import io.swagger.v3.oas.annotations.Operation
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class HelloHandler {

    @Operation(
        summary = "Hello World エンドポイント",
        description = "簡単な Hello World メッセージを返すエンドポイント",
        tags = ["Hello"]
    )
    suspend fun hello(@Suppress("unused") request: ServerRequest): ServerResponse {
        return ServerResponse.ok()
            .bodyValueAndAwait(mapOf("message" to "Hello World"))
    }
}
