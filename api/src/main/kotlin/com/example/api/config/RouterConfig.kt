package com.example.api.config

import com.example.api.handler.HelloHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class RouterConfig {

    @Bean
    fun apiRoutes(helloHandler: HelloHandler): RouterFunction<ServerResponse> {
        return coRouter {
            GET("/api/hello", helloHandler::hello)
        }
    }
}
