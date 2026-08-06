package com.example.api.controller

/**
 * ハンドラ引数に「いまリクエストしているアカウントのID」を注入するマーカー。
 *
 * 解決は [CurrentAccountArgumentResolver] が担う。
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentAccount
