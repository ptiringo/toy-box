package com.example.api.config

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 時刻源（[Clock]）の DI 定義。
 *
 * アダプター（Controller 等）が発生時刻を採取する際の時刻源を Bean として一元供給する。 本番は UTC のシステムクロックを使い、テストでは固定 [Clock] を差し替えて
 * 時刻を決定的にできるようにするための注入点。
 */
@Configuration
class ClockConfiguration {
    /** 本番用の時刻源。タイムゾーン非依存のドメインイベント時刻に合わせて UTC を用いる。 */
    @Bean fun clock(): Clock = Clock.systemUTC()
}
