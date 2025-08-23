package com.example.api.config

import com.example.api.handler.HelloHandler
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.RouterOperation
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Configuration
@OpenAPIDefinition(
    info = Info(title = "toy-box", summary = "toy-box の API 定義です。"),
    tags = [
        Tag(name = "Hello", description = "サンプル")
    ]
)
class RouterConfig {

    @Bean
    @RouterOperation(beanClass = HelloHandler::class, beanMethod = "hello")
    fun helloRoute(helloHandler: HelloHandler): RouterFunction<ServerResponse> {
        return coRouter {
            GET("/api/hello", helloHandler::hello)
        }
    }
}
