package com.example.api.domain.studbook.model

import com.example.api.domain.shared.Permission

/**
 * 軽種馬登録（studbook）コンテキストの権限語彙。
 *
 * 値は DB の `iam.role_permission.permission` と一字一句一致する契約（V15）。ここを変えるときは マイグレーションも同時に変える。共有カーネルには
 * `Permission` 値クラスだけを置き、語彙は各 コンテキストが持つ（#606）。
 */
object StudbookPermissions {
    val HORSE_REGISTER = Permission("studbook:horse:register")
    val HORSE_REGISTER_IMPORTED = Permission("studbook:horse:registerImported")
    val HORSE_REGISTER_FOAL = Permission("studbook:horse:registerFoal")
    val HORSE_NAME = Permission("studbook:horse:name")
    val INSPECTION_RECORD = Permission("studbook:inspection:record")
    val BREEDING_REGISTRATION_REGISTER = Permission("studbook:breedingRegistration:register")
    val BREEDING_RESULT_RECORD_COVERING = Permission("studbook:breedingResult:recordCovering")
    val BREEDING_RESULT_RECORD_UNCOVERED = Permission("studbook:breedingResult:recordUncovered")
    val BREEDING_RESULT_REPORT_FOALING = Permission("studbook:breedingResult:reportFoaling")
    val BREEDING_RESULT_SUBMIT_REPORT = Permission("studbook:breedingResult:submitReport")
    val COVERING_REPORT_SUBMIT = Permission("studbook:coveringReport:submit")
}
