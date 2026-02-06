package com.example.api.handler

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class HelloHandler {
    data class HelloResponse(
        val message: String,
    )

    @Operation(
        summary = "Hello World エンドポイント",
        description = "簡単な Hello World メッセージを返すエンドポイント",
        tags = ["Hello"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "OK",
                content = [Content(schema = Schema(implementation = HelloResponse::class), mediaType = "application/json")],
            ),
        ],
    )
    suspend fun hello(
        @Suppress("unused") request: ServerRequest,
    ): ServerResponse =
        ServerResponse
            .ok()
            .bodyValueAndAwait(HelloResponse("Hello World"))
}
