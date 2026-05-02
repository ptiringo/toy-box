package com.example.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {
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
    @GetMapping("/api/hello")
    fun hello(): HelloResponse = HelloResponse("Hello World")
}
