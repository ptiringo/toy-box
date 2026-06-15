package com.example.api.domain.horseracing.model.horse.bloodhorse

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/** マイクロチップ番号が 15 桁の数字でない。 */
data object InvalidMicrochipNumber

/**
 * マイクロチップ番号。
 *
 * 個体識別のために馬体に埋め込まれるマイクロチップの識別番号。ISO 11784/11785 に準拠し 15 桁の数字で表される。
 *
 * @property value 15 桁の数字からなるマイクロチップ番号
 */
@ValueObject
@JvmInline
value class MicrochipNumber private constructor(val value: String) {
    companion object {
        private const val DIGITS = 15

        /** 15 桁の数字であることを検証して [MicrochipNumber] を生成する。 */
        fun create(value: String): Result<MicrochipNumber, InvalidMicrochipNumber> {
            val trimmed = value.trim()
            return if (trimmed.length == DIGITS && trimmed.all { it.isDigit() }) {
                Ok(MicrochipNumber(trimmed))
            } else {
                Err(InvalidMicrochipNumber)
            }
        }
    }
}
