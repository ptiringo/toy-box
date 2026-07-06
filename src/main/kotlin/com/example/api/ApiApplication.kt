package com.example.api

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@OpenAPIDefinition(
    info =
        Info(
            title = "toy-box",
            summary = "toy-box の API 定義です。",
            description = "複数のドメインモデル（軽種馬登録・競馬・エンターテイメント・テニス）を探索する sandbox API です。",
            version = "1.0",
        ),
    tags =
        [
            Tag(name = "Hello", description = "サンプル"),
            Tag(name = "Jockey", description = "騎手リソース（JRA: 騎手の登録・取得）"),
            Tag(name = "BloodHorse", description = "軽種馬リソース（JAIRS: 血統登録・馬名登録）"),
            Tag(name = "HorseInspection", description = "審査リソース（JAIRS: 個体識別・親子判定の記録・取得）"),
            Tag(name = "BreedingRegistration", description = "繁殖登録リソース（JAIRS: 繁殖の用に供するための登録）"),
            Tag(name = "BreedingResult", description = "繁殖成績リソース（JAIRS: 種付・分娩結果・成績報告・年次集計）"),
            Tag(name = "CoveringReport", description = "種付成績報告リソース（JAIRS: 様式第13号の年次提出）"),
        ],
)
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
