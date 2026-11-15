# リリース手順

[English](RELEASE.md)

本プロジェクトは Central Portal を通じて `io.github.xxvw:vrc-osc-java` を Maven Central に公開します。`0.1.0` では GitHub Release を契機に署名済みバンドルを `USER_MANAGED` でアップロードし、メンテナが Central Portal で Release します。

## 初回のリポジトリ設定

`maven-central` GitHub Environment を作成し、次の Environment secrets を登録します。

| Secret | 内容 |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal のユーザートークン名 |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal のユーザートークンパスワード |
| `SIGNING_KEY` | ASCII armor 形式の GPG 秘密鍵 |
| `SIGNING_PASSWORD` | 署名鍵のパスフレーズ |

Central Portal で `io.github.xxvw` namespace を認証してください。Central が署名を検証できるよう、署名鍵の公開鍵を公開 keyserver から取得できる状態にします。

**Settings > Code security and analysis** で Private vulnerability reporting を有効にします。`maven-central` Environment はリリース担当者だけに制限してください。

## リリースの準備

1. `gradle.properties` の `version` を更新します。タグは `v` の後にその値をそのまま付けたものにします。
2. [CHANGELOG.ja.md](CHANGELOG.ja.md) の `Unreleased` の項目を新しいバージョンへ移します。
3. ローカルで全体を検証します。

```bash
./gradlew clean check javadoc
./scripts/verify-release-artifacts.sh
./gradlew publishToMavenLocal
```

4. `build/libs/vrc-osc-java-<version>.jar` に `module-info.class` があり、公開パッケージが 3 つだけであることを確認します。検証スクリプトがこの内容を確認します。
5. バージョンと変更履歴を `chore(release): 0.1.0` などのメッセージでコミットします。
6. annotated tag を作成して push します。

```bash
git tag -a v0.1.0 -m "vrc-osc-java 0.1.0"
git push origin main v0.1.0
```

## バンドルの公開

1. タグから GitHub Release を作成して公開します。draft のままでは公開処理は動きません。
2. Release workflow がタグと `version` の一致を確認し、`maven-central` Environment で `publishAggregationToCentralPortal` を実行します。
3. アップロードは `USER_MANAGED` です。workflow は deployment を自動で Release しません。
4. Central Portal で deployment を確認し、バンドルと署名が正しければ Release を選びます。
5. POM、JAR、sources JAR、Javadoc JAR、署名が期待する座標で取得できることを確認します。

アップロード前に workflow が失敗した場合は、原因を直してから GitHub Release を再度公開します。アップロード済みで Release 前の deployment が誤っている場合は、Central Portal で drop し、修正した deployment をアップロードします。公開済みの Central artifact は置き換えられないため、新しい patch バージョンを公開します。

## ローカルでのバンドル確認

ローカルの Maven リポジトリには統合確認用のコピーが入ります。

```bash
./gradlew publishToMavenLocal
```

`~/.m2/repository/io/github/xxvw/vrc-osc-java/` に配置された POM とファイルを確認します。

## 認証情報

署名鍵、トークン、`~/.gradle/gradle.properties` をコミットしないでください。上記の GitHub Environment secrets を使います。鍵やトークンが漏れた場合は失効させ、secret をローテーションし、利用者に影響がある場合だけセキュリティ情報を公開します。
