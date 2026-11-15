# vrc-osc-java へのコントリビュート

[English](CONTRIBUTING.md)

Issue と Pull Request は日本語・英語のどちらでも受け付けます。目的が絞られた変更のほうがレビューしやすくなります。

## Pull Request の前に

新しい機能や公開 API の変更は、実装前に Issue で相談してください。利用目的、提案する API、検討した代替案を書いてください。小さな不具合修正とドキュメント修正は、直接 Pull Request を送っても構いません。

既存の Issue を確認してください。VRChat の挙動に関わる変更では、確認した VRChat のバージョン、OSC アドレス、引数の型を具体的に記載してください。

## 開発

Java 17 以上を使ってください。ビルドのバージョンは Gradle Wrapper で固定されています。

```bash
./gradlew clean check javadoc
./scripts/verify-release-artifacts.sh
./gradlew publishToMavenLocal
```

Pull Request を出す前に `check` と Javadoc を通してください。挙動を変える場合はテストを追加・更新してください。利用者に見える変更は英語版 README と日本語版 README の両方を更新してください。公開 Javadoc は英語で記述します。

## Pull Request

- 1 つの Pull Request は 1 つの目的に絞ってください。無関係なリファクタリング、ドキュメント変更、挙動変更は分割します。
- Conventional Commits 形式のタイトルを使ってください。例: `feat: add avatar parameter helpers`、`fix: release the receiver port on close`。
- 関連する Issue を説明に記載してください。
- 何を変えたか、なぜ変えたか、どのように確認したか、残る制約を説明してください。
- 利用者に見える挙動にはテストとドキュメントを追加してください。
- 認証情報、トークン、非公開のログ、個人情報、その他の非公開情報を含めないでください。
- メンテナは Squash merge を使います。最終的なコミットメッセージが意味を持つようにしてください。

## Issue

- 日本語または英語で書いてください。
- Bug Report または Feature Request のフォームを使ってください。
- ライブラリのバージョン、Java のバージョン、OS、関係する VRChat のバージョンを記載してください。
- 認証情報、個人情報、非公開のログを削除してください。公開 Issue に秘密情報を書かないでください。
- セキュリティ脆弱性は公開 Issue ではなく、GitHub の Private vulnerability reporting から報告してください。[SECURITY.ja.md](SECURITY.ja.md) を参照してください。

## コミットとバージョン

Conventional Commits と Semantic Versioning に従います。リリースタグには `v0.1.0` のように `v` を付けます。公開 API を壊す変更は、`1.0.0` 以降はメジャーバージョン、`0.x` の間はマイナーバージョンを上げます。

## コードスタイル

周囲の Java コードに合わせてください。4 スペースのインデント、UTF-8、LF 改行、ワイルドカード import なしとします。公開型は可能な範囲で不変にし、スレッド安全性の制約を Javadoc に書いてください。空白の設定は [.editorconfig](.editorconfig) に従います。

## ライセンス

コントリビュートしたコードは [LICENSE](LICENSE) の MIT License で提供されることに同意してください。
