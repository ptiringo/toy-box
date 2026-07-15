package com.example.api.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * ハンドラ引数の解決に [ActorArgumentResolver] を足す。
 *
 * `SecurityConfig` と同じ理由で adapter リング（controller パッケージ）に置く。 `@ConditionalOnWebApplication` は
 * `webEnvironment = NONE` の `@SpringBootTest` を壊さないため。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class WebMvcConfig(private val actorArgumentResolver: ActorArgumentResolver) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(actorArgumentResolver)
    }
}
