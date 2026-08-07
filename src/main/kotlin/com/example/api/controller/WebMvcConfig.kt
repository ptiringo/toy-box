package com.example.api.controller

import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Spring MVC のハンドラ引数解決の登録。
 *
 * [CurrentAccountArgumentResolver] / [ActorArgumentResolver] は adapter リング（`controller`）のクラス
 * のため、それに依存するこの設定も `config` ではなく `controller` に置く（`SecurityConfig` と同じ理由。 内側の `config` パッケージから
 * adapter リングへ依存するとオニオン規約に反するため）。
 *
 * 2 つを併存させる。世界が決まらない `/api/me:provision` と、世界そのものを操作する `/api/worlds` 系はアカウントだけを要求し、 ドメイン API
 * は世界のスコープ（[com.example.api.domain.shared.Actor]）を要求するため。
 */
@Configuration
class WebMvcConfig(
    private val currentAccountArgumentResolver: CurrentAccountArgumentResolver,
    private val actorArgumentResolver: ActorArgumentResolver,
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentAccountArgumentResolver)
        resolvers.add(actorArgumentResolver)
    }
}
