package com.example.api.controller

/**
 * ハンドラ引数に「いまリクエストしている [com.example.api.domain.shared.Actor]」を注入するマーカー。
 *
 * 解決は [ActorArgumentResolver] が担う。世界が決まらない `/api/me:provision` と、世界そのものを操作する `/api/worlds` 系は
 * [CurrentAccount] を使い続ける。
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentActor
