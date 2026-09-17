# プロジェクト構成

このプロジェクトは、単一モジュールのKotlin / Spring Bootアプリケーションです。アプリケーションコードは`src/main`、テストは`src/test`、DBスキーマの正本はFlywayマイグレーションに置いています。

```text
.
├── .github/workflows/ci.yml             # GitHub Actions: build・テスト・整形確認
├── docs/                                # 開発者向け資料
│   ├── project-structure.md             # このファイル
│   ├── architecture.md                  # レイヤー構成とデータフロー
│   ├── database.md                      # ER図・テーブル定義・jOOQ生成
│   └── api.md                           # HTTP APIの契約
├── examples/                            # 動作確認用のUTF-8 JSON
│   ├── author-ja.json
│   └── book-ja.json
├── gradle/wrapper/                      # Gradle Wrapper
├── src/
│   ├── main/
│   │   ├── kotlin/com/example/bookmanagement/
│   │   │   ├── BookManagementApplication.kt  # Spring Boot起点・Clock Bean
│   │   │   ├── author/                       # 著者のController・Service・Repository・DTO
│   │   │   ├── book/                         # 書籍のController・Service・Repository・DTO
│   │   │   └── common/                       # API例外と共通エラー応答
│   │   └── resources/
│   │       ├── application.yaml              # DB接続・jOOQ・HTTP文字コード設定
│   │       ├── db/migration/                 # Flyway SQL
│   │       └── static/                       # 管理画面のHTML・CSS・JavaScript
│   └── test/kotlin/com/example/bookmanagement/
│       ├── ApiIntegrationTest.kt             # HTTP・DB・同時更新の結合テスト
│       ├── author/                           # 著者の単体テスト
│       └── book/                             # 書籍の単体テスト
├── build.gradle.kts                      # 依存関係・Flyway・jOOQ・テストタスク
├── compose.yaml                          # 開発用PostgreSQL 17
├── gradlew / gradlew.bat                 # Unix / Windows用のGradle起動スクリプト
├── README.md                             # 起動手順・動作確認コマンド・提出情報
└── settings.gradle.kts                   # プロジェクト名・Java 21自動取得設定
```

## 主な責務

| 場所 | 役割 |
| --- | --- |
| `author/` | 著者の登録・更新と生年月日検証 |
| `book/` | 書籍の登録・更新、著者との関連、出版状況の遷移 |
| `common/` | 400 / 404 / 409 / 500のエラー形式を統一 |
| `db/migration/` | DBテーブルと制約を定義する唯一の正本 |
| `examples/` | 端末文字コードの影響を受けずに日本語を送るためのUTF-8 JSON |
| `resources/static/` | `GET /`で配信する、著者・書籍管理用の画面 |

## 生成物とGit管理

`build/`にはjOOQ生成コード、テストレポート、実行可能JARが出力されます。これらはGit管理しません。マイグレーションとGradle設定から再生成できます。

`.gradle/`と`.kotlin/`はローカルキャッシュです。`.env`には認証情報を置く可能性があるため、Git管理対象外です。
