# API仕様

ベースURLは`http://localhost:8080`です。すべてJSONを送受信します。更新はPUTによるリソース全体の置換であり、部分更新（PATCH）は提供しません。

| メソッド | パス | 概要 | 成功時 |
| --- | --- | --- | --- |
| GET | `/authors` | 著者一覧を取得 | 200 OK |
| POST | `/authors` | 著者を登録 | 201 Created |
| PUT | `/authors/{authorId}` | 著者を更新 | 200 OK |
| GET | `/books` | 書籍一覧を取得 | 200 OK |
| POST | `/books` | 書籍を登録 | 201 Created |
| PUT | `/books/{bookId}` | 書籍と著者の関連を更新 | 200 OK |
| GET | `/authors/{authorId}/books` | 著者が執筆した書籍を取得 | 200 OK |

## 著者

### 一覧取得

`GET /authors`はすべての著者をID昇順で返します。管理画面の一覧と、書籍登録時の著者選択肢に利用します。著者がない場合は空配列`[]`です。

### 登録・更新

`POST /authors`または`PUT /authors/{authorId}`へ、次のJSONを送ります。

```json
{
  "name": "Author A",
  "birthDate": "1990-01-01"
}
```

成功時のレスポンスです。

```json
{
  "id": 1,
  "name": "Author A",
  "birthDate": "1990-01-01"
}
```

`name`は空白のみ不可、`birthDate`は`YYYY-MM-DD`形式かつAsia/Tokyoの現在日以前です。著者名・生年月日の重複は許可します。更新対象は生年月日ではなく、パスの`authorId`で決まります。

## 書籍

### 一覧取得

`GET /books`はすべての書籍をID昇順で返します。各書籍には、著者ID昇順のすべての著者を含めます。書籍がない場合は空配列`[]`です。

### 登録・更新

`POST /books`または`PUT /books/{bookId}`へ、次のJSONを送ります。

```json
{
  "title": "Kotlin Intro",
  "price": 1200.00,
  "authorIds": [1],
  "publicationStatus": "UNPUBLISHED"
}
```

成功時のレスポンスです。

```json
{
  "id": 1,
  "title": "Kotlin Intro",
  "price": 1200.00,
  "publicationStatus": "UNPUBLISHED",
  "authors": [
    { "id": 1, "name": "Author A" }
  ]
}
```

| 項目 | ルール |
| --- | --- |
| `title` | 必須、空白のみ不可 |
| `price` | 0以上、整数10桁・小数2桁まで。`BigDecimal` / `NUMERIC(12,2)`で扱う |
| `authorIds` | 必須、1件以上、重複不可。すべて登録済みの著者IDであること |
| `publicationStatus` | `UNPUBLISHED`または`PUBLISHED` |

書籍は複数著者を持てます。PUT時の`authorIds`は、既存の関連をすべて置き換えます。

出版済みの書籍はタイトル・価格・著者を更新できますが、`PUBLISHED`から`UNPUBLISHED`への変更はできません。

## 著者の書籍一覧

`GET /authors/{authorId}/books`は、対象著者の書籍を返します。書籍ID昇順で、各書籍にはすべての共著者を著者ID昇順で含めます。

```json
[
  {
    "id": 1,
    "title": "Kotlin Intro",
    "price": 1200.00,
    "publicationStatus": "UNPUBLISHED",
    "authors": [
      { "id": 1, "name": "Author A" }
    ]
  }
]
```

著者が存在し、書籍がない場合は空配列`[]`を返します。著者が存在しない場合は404です。

## エラー

エラーは`application/problem+json`形式で返します。`code`でクライアントが種類を判別できます。

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Published books cannot become unpublished",
  "instance": "/books/1",
  "code": "INVALID_STATUS_TRANSITION"
}
```

| HTTP | code | 代表例 |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | 入力不正、未登録著者ID、未来の生年月日 |
| 404 | `NOT_FOUND` | 指定した著者・書籍が存在しない |
| 409 | `INVALID_STATUS_TRANSITION` | 出版済みを未出版へ変更しようとした |
| 500 | `INTERNAL_ERROR` | 想定外のサーバー障害 |
