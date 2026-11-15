# 変更履歴

このプロジェクトの主な変更を記録します。形式は [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) に従い、バージョンは [Semantic Versioning](https://semver.org/) を使います。

[English](CHANGELOG.md)

## [Unreleased]

## [0.1.0] - 2026-11-16

### 追加

- UTF-8、4 byte パディング、型タグ、配列、バンドル timetag に対応した OSC 1.0 メッセージ・バンドルコーデック。
- int32、float32、string、blob、int64、double、timetag、char、symbol、MIDI、RGBA、bool、nil、impulse、配列の型付き引数。
- 専用 daemon 受信スレッドを持つ UDP 送信器とコールバック受信器。
- アバターパラメータ、Chatbox、アバター変更、typing の VRChat ヘルパー。
- リスナー登録、エラーハンドリング、冪等な終了処理、受信ポートの取得。
- JPMS、Gradle Wrapper、sources JAR、Javadoc JAR、Maven Central 公開メタデータを含む Java 17 ビルド。
- 英語・日本語のドキュメント、Issue Form、Pull Request テンプレート、セキュリティポリシー、リリース手順。

### 既知の制約

- バンドルの timetag は保持しますが、このリリースでは到着時に即時配送します。
- OSCQuery と OSC アドレスパターン照合は未実装です。
- Chatbox の明示的な行数が正しくても、自動折り返しにより描画後の行数が 9 行を超える場合があります。

[Unreleased]: https://github.com/xxvw/vrc-osc-java/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/xxvw/vrc-osc-java/releases/tag/v0.1.0
