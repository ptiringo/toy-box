package com.example.api.domain.iam.model.account

import com.example.api.domain.shared.UpdateConflict
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.Repository

/**
 * [Account] の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。
 */
@Repository
interface AccountRepository {
    /** IdP の subject でアカウントを引く。存在しなければ null（単純 lookup は Result を強制しない）。 */
    fun findBySubjectId(subjectId: SubjectId): Account?

    /**
     * アカウントを永続化する。
     *
     * 集約の [Account.version] が null なら insert、非 null なら楽観ロック付き update になる。
     *
     * **同一 subject の重複（DB の `UNIQUE (subject_id)`）はここでは検知しない**。UNIQUE 違反は `DuplicateKeyException`
     * として未捕捉のまま伝播する。初回ログインのブートストラップのように 並行実行が前提の経路では、この `save` ではなく [saveIfAbsent] を使うこと。
     */
    fun save(account: Account): Result<Account, UpdateConflict>

    /**
     * 同じ subject のアカウントが無ければ保存し、既にあればそれを返す（原子的な get-or-create）。
     *
     * 事前照会（[findBySubjectId]）→ insert という手順は、その 2 手のあいだに別のリクエストが同じ subject を insert しうる TOCTOU
     * のレースを残す。この口は「先に照会してから書く」のではなく **DB の UNIQUE 制約を 唯一の裁定者にする**（`INSERT ... ON CONFLICT DO
     * NOTHING` ＋衝突時の読み直し）ため、並行実行しても アカウントは増えず、UNIQUE 違反の例外も出ない。
     *
     * 渡した [account] の ID は、衝突したときは採用されない（先着の行がそのまま返る）。
     */
    fun saveIfAbsent(account: Account): Account
}
