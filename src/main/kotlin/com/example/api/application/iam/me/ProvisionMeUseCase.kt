package com.example.api.application.iam.me

import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.BlankSubjectId
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.iam.model.world.World
import com.example.api.domain.iam.model.world.WorldNameValidationError
import com.example.api.domain.iam.model.world.WorldRepository
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.UpdateConflict
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 初回ログインのブートストラップの入力コマンド。
 *
 * @property subjectId 検証済み ID トークンの `sub`
 */
data class ProvisionMeCommand(val subjectId: String)

/** ブートストラップ時に発生しうる失敗。 */
sealed interface ProvisionMeError {
    /** IdP の subject がブランクだった（トークン検証を通っていれば起きないはずの防御的分岐）。 */
    data object InvalidSubject : ProvisionMeError

    /** 既定の世界名が不変条件を満たさなかった（定数の設定ミスでしか起きない）。 */
    data class InvalidDefaultWorldName(val cause: WorldNameValidationError) : ProvisionMeError

    /**
     * 並行する同一 subject のブートストラップと競合した。クライアントは再試行できる（冪等操作のため）。
     *
     * **既知の限界**: 事前照会（`findBySubjectId` / `existsByAccountId`）と insert の間には TOCTOU の レース窓が残る。真に同時な
     * 2 リクエストが両方とも「未登録」を見て両方 insert を試みると、2 回目は DB の UNIQUE 制約（`uq_account_subject_id` /
     * `uq_world_account_id_name`）に弾かれ、この `Conflict` ではなく未捕捉の `DuplicateKeyException`（500）として伝播する。
     *
     * このバリアントが実際に返るのは、事前照会が通った後・insert 前の別経路（例: 楽観ロック起因）に限られ、 通常の同時 provision レースでは 500
     * になる。read-your-conflict 化（`DuplicateKeyException` を捕まえて `findBySubjectId`
     * を引き直す）は当面見送る。PostgreSQL は UNIQUE 違反時点でトランザクションを abort
     * 済みのため、同一トランザクション内でのリトライは効かず、別トランザクション境界の手当てが要る。 `:provision` はサインイン直後にフロントが必ず 1
     * 回叩く設計で、レースの窓は実用上ごく短く、 かつ呼び出し側は 500 を含めて再試行してよい（冪等）ため、複雑さに見合わないと判断した。
     */
    data object Conflict : ProvisionMeError
}

/**
 * 初回ログイン時にアカウントと最初の世界を用意するユースケース。
 *
 * **冪等**であることが要件。フロントエンドは Identity Platform でのサインイン直後に必ず 1 回叩くが、 リロードや再ログインで何度呼ばれてもアカウントも世界も増えない。
 *
 * GET に副作用を持たせる（一覧取得のついでにアカウントを作る）案は採らない。ブートストラップは明示的な 書き込みであり、失敗が呼び出し側に見えるべきだから。
 *
 * `Actor` を引数に取らないのは、この時点ではまだ「どの世界で操作しているか」が決まっていないため。 世界の所有確認を伴う操作は次の段階（世界スコープ化）で `Actor` を受ける。
 *
 * **並行実行の既知の限界**: 事前照会（`findBySubjectId` / `existsByAccountId`）と実際の insert の間に TOCTOU
 * のレースが残っており、真に同時な 2 リクエストが競合すると `Conflict`（409）ではなく DB の UNIQUE 制約違反に由来する未捕捉の例外（500）になる。詳細と判断理由は
 * [ProvisionMeError.Conflict] を参照。
 */
@Service
class ProvisionMeUseCase(
    private val accounts: AccountRepository,
    private val worlds: WorldRepository,
) {
    @Transactional
    operator fun invoke(command: Command<ProvisionMeCommand>): Result<AccountId, ProvisionMeError> =
        binding {
            val subjectId = SubjectId(command.payload.subjectId)
            val existing = accounts.findBySubjectId(subjectId)
            val account =
                if (existing != null) {
                    existing
                } else {
                    val created =
                        Account.create(command.payload.subjectId)
                            .mapError { _: BlankSubjectId -> ProvisionMeError.InvalidSubject }
                            .bind()
                    accounts
                        .save(created)
                        .mapError { _: UpdateConflict -> ProvisionMeError.Conflict }
                        .bind()
                }

            if (!worlds.existsByAccountId(account.id)) {
                World.create(account.id, DEFAULT_WORLD_NAME)
                    .mapError { ProvisionMeError.InvalidDefaultWorldName(it) }
                    .bind()
                    .let { worlds.save(it) }
                    .mapError { _: UpdateConflict -> ProvisionMeError.Conflict }
                    .bind()
            }

            account.id
        }

    companion object {
        /** 最初の世界に自動で付く名前。プレイヤーは後から改名できる。 */
        private const val DEFAULT_WORLD_NAME = "はじまりの世界"
    }
}
