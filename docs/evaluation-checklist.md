# 評価観点チェックリスト

この資料は、コーディングテストの評価観点に対する実装の対応状況と確認根拠をまとめたものです。管理画面はSpring Bootの静的リソースとして追加しており、バックエンドの構成と既存APIの互換性を維持しています。

## 結果一覧

| 評価観点 | 結果 | 根拠 |
| --- | --- | --- |
| 指定された技術スタックの適用 | 対応済み | Kotlin、Java 21、Spring Boot、jOOQ、PostgreSQL、Flyway、Gradle Kotlin DSLを採用 |
| フレームワークやライブラリの適切な利用 | 対応済み | Spring MVC / Validation / Transaction、Flyway、jOOQ、Testcontainers、ktlintを目的ごとに使用 |
| 実行可能性 | 対応済み | Gradle Wrapper、Java 21自動取得、Docker Compose、README、GitHub Actionsを用意 |
| 仕様に沿った動作 | 対応済み | 7 API、入力制約、多対多、出版状況遷移、エラー応答を実装・結合テストで確認 |
| 名前の明確さ | 対応済み | Controller / Service / Repository、DTO、メソッド名が責務を表す |
| Null安全性 | 対応済み | Kotlinの非Null型と`val`、Bean Validation、厳密なJackson設定を使用 |
| コードフォーマットの整合性 | 対応済み | `ktlintCheck`を`clean build`とCIで実行 |
| 再代入を避けるなどのベストプラクティス | 対応済み | 本番コードは`val`とコンストラクタインジェクションを基本とし、`!!`を使用しない |
| オーバーエンジニアリングしていないか | 対応済み | 単一モジュール、3層、必要なDTOのみで構成。不要な汎用化・認証・検索機能は追加しない |
| 適切な単体テスト | 対応済み | 業務ルール単体テストと、実DBを使う結合テストを分離。画面配信・一覧APIの結合テストも追加 |

## 観点ごとの確認内容

### 指定された技術スタックの適用

| 要件 | 実装 |
| --- | --- |
| Kotlin / Java 21 | `build.gradle.kts`でKotlin 2.1.21、Java Toolchain 21を指定。`settings.gradle.kts`でJDK自動取得を設定 |
| Spring Boot | Spring Boot 3.5.16とSpring MVCでREST APIを実装 |
| jOOQ | Flyway適用済みPostgreSQLスキーマからコードを生成し、Repositoryで`DSLContext`を使用 |
| RDB | PostgreSQL 17を`compose.yaml`で提供 |
| Flyway | `src/main/resources/db/migration/V1__create_books_and_authors.sql`をスキーマの正本として利用 |

### フレームワークやライブラリの適切な利用

- ControllerではSpring MVCとBean Validationを使い、HTTPと入力形式を担当させています。
- Serviceでは`@Transactional`で書籍・著者関連の更新を原子的にしています。
- RepositoryではjOOQの生成テーブル・カラムを使い、文字列SQLへの依存を抑えています。
- FlywayはDBスキーマの再現、Testcontainersは実PostgreSQLを使う結合テスト、ktlintはKotlinの整形確認を担当します。

### 実行可能性

- `gradlew` / `gradlew.bat`を同梱しているため、Gradleの別途インストールは不要です。
- Java 21がローカルにない場合も、GradleのToolchain Resolverが自動取得します。
- `docker compose up -d --wait`で開発用PostgreSQLを起動できます。
- `./gradlew clean build`はFlyway、jOOQ生成、コンパイル、ktlint、単体・結合テスト、JAR作成を実行します。
- GitHub Actionsでも同じ`clean build`を実行します。ローカルのDB・レコードにはアクセスしません。

Docker Engineが起動していることは、ローカルでのDB起動・Testcontainers実行の前提です。

### 仕様に沿った動作

| 仕様 | 実装・確認方法 |
| --- | --- |
| 著者の登録・更新 | `POST /authors`、`PUT /authors/{authorId}`。未来の生年月日を拒否 |
| 書籍の登録・更新 | `POST /books`、`PUT /books/{bookId}`。価格、著者、出版状況を検証 |
| 著者に紐づく書籍取得 | `GET /authors/{authorId}/books`。共著者を含む書籍一覧を返す |
| 一覧取得 | `GET /authors`と`GET /books`。管理画面の一覧と著者選択肢に使用 |
| 管理画面 | `GET /`。著者・書籍の一覧、登録、全項目更新、著者別書籍取得をブラウザで確認可能 |
| 複数著者 | `book_authors`中間テーブルと`authorIds`配列で実装 |
| 最低1著者 | `@NotEmpty`とServiceの重複・存在確認で保証 |
| 出版済みから未出版への変更禁止 | 行ロック後の状態遷移判定で409を返す |

### 名前、Null安全性、コーディング規約

- `AuthorController`、`AuthorService`、`AuthorRepository`のようにレイヤーと対象を名前で表しています。
- `replaceAuthors`、`lockStatus`、`findByAuthor`などのメソッド名は実際の処理を表しています。
- 本番コードは`val`、非Null型、コンストラクタインジェクションを使います。`!!`は使用していません。
- DBから必須値を読む箇所には`checkNotNull`を使い、DBのNOT NULL制約とコード上の前提を一致させています。
- `lateinit`は、Springがフィールド注入する結合テストのテストフィクスチャに限定されています。本番コードにはありません。
- `ktlintCheck`がKotlinとGradle Kotlin DSLの整形を確認します。

### 過剰設計を避ける判断

この課題で必要なAPIと業務ルールに絞り、次は意図的に追加していません。

- 認証・認可
- 削除、検索、ページング
- 外部公開、複数モジュール化
- 用途のないインターフェース、汎用基底クラス、独自の抽象化レイヤー

一方で、多対多の整合性、トランザクション、状態遷移、エラー応答は仕様上必要なため実装しています。

### テスト

| 種別 | ファイル | 主な確認内容 |
| --- | --- | --- |
| 単体 | `PublicationStatusTest.kt` | 出版状況の4遷移 |
| 単体 | `BookRequestValidationTest.kt` | 価格の0、負数、整数桁、小数桁の境界 |
| 単体 | `AuthorServiceTest.kt` | Asia/Tokyo基準の生年月日境界 |
| 結合 | `ApiIntegrationTest.kt` | HTTP、DB保存、多著者、置換、一覧、画面配信、400/404/409、ロールバック、同時更新 |

結合テストは`@Tag("integration")`で分離しています。`test`は単体テストのみ、`integrationTest`はSpring Boot・jOOQ・PostgreSQLを組み合わせて実行します。

## 提出前の再確認

```powershell
docker compose up -d --wait
.\gradlew.bat clean build
git status --short
```

最後のコマンドで出力がなければ、未コミットの変更はありません。`bin/`、`build/`、`.gradle/`、`.kotlin/`は生成物・キャッシュとしてGit管理対象外です。
