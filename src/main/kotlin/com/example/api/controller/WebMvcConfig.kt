package com.example.api.controller

import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Spring MVC のハンドラ引数解決の登録。
 *
 * [CurrentAccountArgumentResolver] は adapter リング（`controller`）のクラスのため、それに依存するこの設定も `config` ではなく
 * `controller` に置く（`SecurityConfig` と同じ理由。内側の `config` パッケージから adapter リングへ依存するとオニオン規約に反するため）。
 */
@Configuration
class WebMvcConfig(private val currentAccountArgumentResolver: CurrentAccountArgumentResolver) :
    WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentAccountArgumentResolver)
    }
}
