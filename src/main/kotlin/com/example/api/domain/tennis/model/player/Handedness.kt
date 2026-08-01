package com.example.api.domain.tennis.model.player

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 利き手。
 *
 * バックハンドの片手／両手はここでは表さない（必要になった時点で別の値オブジェクトとしてモデリングする）。
 */
@ValueObject
enum class Handedness {
    /** 右利き */
    RIGHT,

    /** 左利き */
    LEFT,
}
