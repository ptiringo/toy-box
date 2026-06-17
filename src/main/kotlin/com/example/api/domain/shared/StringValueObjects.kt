package com.example.api.domain.shared

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * 単一文字列を値とする値オブジェクトの「ブランクでないこと」検証を共有するコンビネータ。
 *
 * [raw] がブランク（空文字・空白のみ）なら値オブジェクトを構築せず [blank] を [Err] で返し、ブランクでなければ trim した値を [create] に渡して構築し [Ok]
 * で返す。複数の文字列 VO が同じ blank 検証と trim 正規化を個別実装すると正規化方針がドリフトしうるため、ここに一元化する。
 *
 * 各 VO は自身の `companion object.create()` からこのコンビネータを呼び、固有のエラー型 [E] と private constructor を [create]
 * に渡す（「VO ごとに create() で Result を返す」方針は維持する）。
 *
 * @param T 構築する値オブジェクトの型
 * @param E ブランク時に返すエラー型
 * @param raw 検証対象の生文字列
 * @param blank ブランクだった場合に返すエラー値
 * @param create trim 済み文字列から値オブジェクトを構築するファクトリ（private constructor 参照など）
 */
inline fun <T, E> createNonBlank(raw: String, blank: E, create: (String) -> T): Result<T, E> =
    if (raw.isBlank()) Err(blank) else Ok(create(raw.trim()))
