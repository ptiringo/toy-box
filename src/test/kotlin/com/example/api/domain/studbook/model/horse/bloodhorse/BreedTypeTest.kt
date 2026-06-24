package com.example.api.domain.studbook.model.horse.bloodhorse

import org.junit.jupiter.api.Test

/** [BreedType.isConsistentWith]（親仔の品種整合）のユニットテスト */
class BreedTypeTest {
    @Test
    fun `サラブレッドは両親ともサラブレッドのとき整合すること`() {
        assert(
            BreedType.THOROUGHBRED.isConsistentWith(BreedType.THOROUGHBRED, BreedType.THOROUGHBRED)
        )
    }

    @Test
    fun `サラブレッドは父母のいずれかが非サラブレッドだと不整合になること`() {
        assert(
            !BreedType.THOROUGHBRED.isConsistentWith(BreedType.ANGLO_ARAB, BreedType.THOROUGHBRED)
        )
        assert(!BreedType.THOROUGHBRED.isConsistentWith(BreedType.THOROUGHBRED, BreedType.ARAB))
        assert(!BreedType.THOROUGHBRED.isConsistentWith(BreedType.ARAB, BreedType.ARAB))
    }

    @Test
    fun `アラブは両親ともアラブのとき整合すること`() {
        assert(BreedType.ARAB.isConsistentWith(BreedType.ARAB, BreedType.ARAB))
    }

    @Test
    fun `アラブは父母のいずれかが非アラブだと不整合になること`() {
        assert(!BreedType.ARAB.isConsistentWith(BreedType.THOROUGHBRED, BreedType.ARAB))
        assert(!BreedType.ARAB.isConsistentWith(BreedType.ARAB, BreedType.ANGLO_ARAB))
        assert(!BreedType.ARAB.isConsistentWith(BreedType.THOROUGHBRED, BreedType.THOROUGHBRED))
    }

    @Test
    fun `アラブ血量の閾値で決まる品種は対象外で父母によらず常に整合すること`() {
        // アングロアラブ / サラブレッド系種 / アラブ系種 は品種型だけでは判定できないため常に true
        for (child in
            listOf(BreedType.ANGLO_ARAB, BreedType.THOROUGHBRED_TYPE, BreedType.ARAB_TYPE)) {
            for (sire in BreedType.entries) {
                for (dam in BreedType.entries) {
                    assert(child.isConsistentWith(sire, dam))
                }
            }
        }
    }
}
