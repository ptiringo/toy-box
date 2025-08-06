package com.example.api.config

import com.example.api.handler.HelloHandler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.RouterOperation
import org.springdoc.core.annotations.RouterOperations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class RouterConfig {

    @Bean
    @RouterOperations(
        RouterOperation(
            path = "/api/hello",
            method = [RequestMethod.GET],
            operation = Operation(
                operationId = "hello",
                summary = "Hello World エンドポイント",
                description = "簡単な Hello World メッセージを返すエンドポイント",
                tags = ["Hello"]
            )
        )
    )
    fun helloRoute(helloHandler: HelloHandler): RouterFunction<ServerResponse> {
        return coRouter {
            GET("/api/hello", helloHandler::hello)
        }
    }
}
