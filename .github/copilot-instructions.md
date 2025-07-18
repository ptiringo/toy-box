# GitHub Copilot リポジトリ固有指示

## 基本方針（Basic Guidelines）

### 言語とスタイル（Language and Style）
- **コメントとドキュメントは日本語で記述してください**
- 変数名、関数名、クラス名は英語で記述し、意味が明確になるようにしてください
- コードの説明やドキュメンテーションコメントは日本語で詳細に記述してください
- コミットメッセージも日本語で記述してください

### プロジェクト構造（Project Structure）
このリポジトリはKotlin Spring Boot WebFluxを使用したAPIプロジェクトです：
- `api/` ディレクトリにメインのAPIコードが含まれています
- Gradle Kotlin DSLを使用してビルド設定を管理しています
- WebFluxとコルーチンを使用した非同期プログラミングを採用しています

## セキュリティベストプラクティス（Security Best Practices）

### コード提案時の考慮事項
- **入力値検証**: 全ての外部入力に対して適切な検証を実装してください
- **SQLインジェクション対策**: データベースクエリではパラメータ化クエリを使用してください
- **認証・認可**: エンドポイントに適切なセキュリティ設定を提案してください
- **機密情報の保護**: API キー、パスワード、その他の機密情報をハードコーディングしないでください
- **CORS設定**: 適切なCORS設定を提案してください
- **ログ出力**: 機密情報をログに出力しないよう注意してください

### 例外処理とエラーハンドリング
```kotlin
// セキュアなエラーハンドリングの例
suspend fun secureHandler(request: ServerRequest): ServerResponse {
    return try {
        // ビジネスロジック実装
        ServerResponse.ok().bodyValueAndAwait(result)
    } catch (ex: ValidationException) {
        // 詳細なエラー情報は公開しない
        ServerResponse.badRequest()
            .bodyValueAndAwait(mapOf("error" to "不正なリクエストです"))
    } catch (ex: Exception) {
        // 内部エラーの詳細は公開しない
        ServerResponse.status(500)
            .bodyValueAndAwait(mapOf("error" to "内部エラーが発生しました"))
    }
}
```

## パフォーマンスと保守性（Performance and Maintainability）

### パフォーマンス指針
- **非同期処理**: WebFluxとコルーチンを活用した非ブロッキング処理を推奨します
- **効率的なデータ処理**: Streamやシーケンスを適切に使用してメモリ効率を考慮してください
- **キャッシュ戦略**: 適切な場所でキャッシュの実装を提案してください
- **データベース最適化**: N+1問題の回避やクエリ最適化を考慮してください

### コード品質と保守性
- **単一責任原則**: 各クラス・関数は単一の責任を持つよう設計してください
- **依存性注入**: Spring DIコンテナを適切に活用してください
- **イミュータブル設計**: 可能な限りデータクラスをimmutableに設計してください
- **関数型プログラミング**: Kotlinの関数型機能を活用してください

```kotlin
// 保守性の高いコード例
@Component
class UserService(
    private val userRepository: UserRepository,
    private val notificationService: NotificationService
) {
    /**
     * ユーザーを作成し、通知を送信します
     * @param userRequest ユーザー作成リクエスト
     * @return 作成されたユーザー情報
     */
    suspend fun createUser(userRequest: CreateUserRequest): UserResponse {
        // 入力値検証
        validateUserRequest(userRequest)
        
        // ユーザー作成
        val user = userRepository.save(userRequest.toEntity())
        
        // 非同期で通知送信
        notificationService.sendWelcomeNotification(user)
        
        return user.toResponse()
    }
}
```

## テスト指針（Testing Guidelines）

### ユニットテスト
- **テストケース命名**: 日本語でテストの意図を明確に表現してください
- **テストデータ**: 意味のあるテストデータを使用してください
- **モック使用**: 外部依存をモックして単体テストの独立性を保ってください
- **例外テスト**: 正常系だけでなく異常系のテストも実装してください

```kotlin
@ExtendWith(MockitoExtension::class)
class UserServiceTest {
    
    @Mock
    private lateinit var userRepository: UserRepository
    
    @InjectMocks
    private lateinit var userService: UserService
    
    @Test
    fun `有効なリクエストでユーザー作成が成功すること`() {
        // Given
        val request = CreateUserRequest(
            name = "山田太郎",
            email = "yamada@example.com"
        )
        val savedUser = User(id = 1, name = "山田太郎", email = "yamada@example.com")
        
        whenever(userRepository.save(any())).thenReturn(savedUser)
        
        // When
        val result = runBlocking { userService.createUser(request) }
        
        // Then
        assertThat(result.name).isEqualTo("山田太郎")
        assertThat(result.email).isEqualTo("yamada@example.com")
    }
    
    @Test
    fun `不正なメールアドレスでValidationExceptionがスローされること`() {
        // Given
        val request = CreateUserRequest(
            name = "山田太郎", 
            email = "invalid-email"
        )
        
        // When & Then
        assertThrows<ValidationException> {
            runBlocking { userService.createUser(request) }
        }
    }
}
```

### 統合テスト
- **WebTestClient**: APIエンドポイントのテストにはWebTestClientを使用してください
- **テストスライス**: @WebFluxTestなどの適切なテストアノテーションを使用してください
- **テストデータ**: データベーステストでは適切なテストデータ準備・後始末を行ってください

```kotlin
@WebFluxTest
@Import(RouterConfig::class, UserHandler::class)
class UserControllerTest(@Autowired val webTestClient: WebTestClient) {
    
    @Test
    fun `ユーザー一覧取得APIが正常に動作すること`() {
        webTestClient.get()
            .uri("/api/users")
            .header("Accept", "application/json")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList<UserResponse>()
            .hasSize(2)
    }
}
```

## ドキュメンテーション標準（Documentation Standards）

### コメント記述
- **KDoc**: パブリックなAPI要素にはKDocコメントを必ず記述してください
- **複雑なロジック**: 複雑なビジネスロジックには日本語で詳細な説明を追加してください
- **TODO/FIXME**: 将来の改善点や既知の問題を明記してください

```kotlin
/**
 * ユーザー管理を行うサービスクラス
 * 
 * このクラスはユーザーの作成、更新、削除、および検索機能を提供します。
 * 全ての操作は非同期で実行され、適切なエラーハンドリングが実装されています。
 * 
 * @property userRepository ユーザー情報の永続化を担当するリポジトリ
 * @property validationService ユーザー入力値の検証を行うサービス
 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val validationService: ValidationService
) {
    
    /**
     * 新しいユーザーを作成します
     * 
     * @param request ユーザー作成リクエスト（名前、メールアドレスを含む）
     * @return 作成されたユーザーの情報
     * @throws ValidationException 入力値が不正な場合
     * @throws DuplicateEmailException メールアドレスが既に使用されている場合
     */
    suspend fun createUser(request: CreateUserRequest): UserResponse {
        // メールアドレスの重複チェック
        // TODO: パフォーマンス改善のためキャッシュ機能を追加予定
        if (userRepository.existsByEmail(request.email)) {
            throw DuplicateEmailException("メールアドレス ${request.email} は既に使用されています")
        }
        
        // 入力値検証
        validationService.validateCreateUserRequest(request)
        
        return userRepository.save(request.toEntity()).toResponse()
    }
}
```

## 命名規則とコード構成（Naming Conventions and Code Organization）

### パッケージ構成
- `handler`: WebFluxハンドラー実装
- `service`: ビジネスロジック実装  
- `repository`: データアクセス層
- `config`: 設定クラス
- `dto`: データ転送オブジェクト
- `exception`: カスタム例外クラス

### 命名規則
- **クラス名**: PascalCase（例: UserService, OrderHandler）
- **関数名**: camelCase（例: createUser, validateInput）
- **定数**: UPPER_SNAKE_CASE（例: MAX_RETRY_COUNT）
- **プロパティ**: camelCase（例: userId, emailAddress）

これらの指針に従って、高品質で保守性が高く、セキュリティを考慮したKotlin Spring Bootアプリケーションの開発を支援してください。