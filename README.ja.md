# vrc-osc-java

[![CI](https://github.com/xxvw/vrc-osc-java/actions/workflows/ci.yml/badge.svg)](https://github.com/xxvw/vrc-osc-java/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Java 17 以降で使える、ランタイム依存のない OSC 1.0 ライブラリです。汎用の OSC コーデックと UDP 送受信に加えて、VRChat のアバターパラメータ、Chatbox、アバター変更、typing を扱う API を提供します。

[English README](README.md)

## 要件

- Java 17 以上
- ランタイム依存なし
- JPMS モジュール名: `io.github.xxvw.vrcosc`

## 導入

Maven 座標:

```text
io.github.xxvw:vrc-osc-java:0.1.0
```

Gradle Kotlin DSL:

```kotlin
dependencies {
    implementation("io.github.xxvw:vrc-osc-java:0.1.0")
}
```

Gradle Groovy DSL:

```groovy
dependencies {
    implementation 'io.github.xxvw:vrc-osc-java:0.1.0'
}
```

Maven:

```xml
<dependency>
  <groupId>io.github.xxvw</groupId>
  <artifactId>vrc-osc-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

## VRChat へ送信する

`VrcOscClient` は設定後すぐに送信できます。既定値は VRChat の `127.0.0.1:9000`（送信先）と `127.0.0.1:9001`（受信）です。

```java
import io.github.xxvw.vrcosc.VrcOscClient;

public final class Example {
    public static void main(String[] args) throws Exception {
        try (VrcOscClient client = VrcOscClient.builder().build()) {
            client.sendAvatarParameter("Mute", true);
            client.sendAvatarParameter("GestureLeft", 2);
            client.sendAvatarParameter("Voice", 0.75f);
            client.sendChatbox("Java からこんにちは", true);
            client.sendAvatarChange("avtr_00000000-0000-0000-0000-000000000000");
            client.setTyping(false);
        }
    }
}
```

`sendChatbox(String text, boolean immediate)` は通知音を有効にします。通知音を制御する場合は `sendChatbox(text, immediate, notification)` を使います。

## OSC を受信する

受信は `start()` で開始します。送信だけなら `start()` は不要です。

```java
import io.github.xxvw.vrcosc.VrcOscClient;
import io.github.xxvw.vrcosc.osc.OscMessage;

public final class ReceiveExample {
    public static void main(String[] args) {
        try (VrcOscClient client =
                VrcOscClient.builder()
                        .listener(packet -> {
                            if (packet instanceof OscMessage message) {
                                System.out.println(message.address() + " " + message.arguments());
                            }
                        })
                        .errorHandler(error -> error.printStackTrace())
                        .build()) {
            client.start();
            Thread.sleep(60_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
```

リスナーは専用の daemon スレッド上で登録順に呼ばれます。重い処理は別の executor に移し、コールバックは短く保ってください。リスナーの例外や不正な datagram はエラーハンドラーに渡り、受信は継続します。`close()` は冪等で、Socket と受信スレッドを解放します。

## ポートとアドレスの変更

```java
VrcOscClient client =
        VrcOscClient.builder()
                .host("127.0.0.1")
                .sendPort(9000)
                .bindAddress("127.0.0.1")
                .receivePort(9001)
                .build();
```

`receivePort(0)` は空きポートを自動で選びます。実際のポートは `start()` 後に `receivePort()` で取得できます。`bindAddress` は受信に使うローカルインターフェースです。

| 方向 | 既定値 | Builder の設定 |
| --- | --- | --- |
| VRChat へ送信 | `127.0.0.1:9000` | `host(...)`, `sendPort(...)` |
| VRChat から受信 | `127.0.0.1:9001` | `bindAddress(...)`, `receivePort(...)` |

## 汎用 OSC

VRChat 向けヘルパーも同じコーデックとトランスポートを使っています。

```java
import io.github.xxvw.vrcosc.osc.OscArgument;
import io.github.xxvw.vrcosc.osc.OscMessage;

client.send("/avatar/parameters/Voice", OscArgument.float32(0.5f));
client.send(
        OscMessage.of(
                "/custom/address",
                OscArgument.int32(42),
                OscArgument.string("hello"),
                OscArgument.array(OscArgument.bool(true), OscArgument.nil())));
```

`OscArgument` は int32、float32、string、blob、int64、double、timetag、char、symbol、MIDI、RGBA、bool、nil、impulse、配列を扱えます。`OscCodec` は UTF-8 と 4 byte パディングを含むメッセージとバンドルのエンコード・デコードに対応します。

```java
import io.github.xxvw.vrcosc.osc.OscCodec;
import io.github.xxvw.vrcosc.osc.OscPacket;

byte[] wire = OscCodec.encode(message);
OscPacket decoded = OscCodec.decode(wire);
```

VRChat 固有の処理が不要な場合は、`UdpOscSender`、`UdpOscReceiver`、`OscListener` を単独で利用できます。

## VRChat の設定

1. VRChat の Action Menu で **Options > OSC > Enabled** を選び、OSC を有効にします。
2. VRChat の OSC 受信ポートが `9000`、送信ポートが `9001` であることを確認します。
3. Chatbox を使う場合は Action Menu で Chatbox を有効にします。
4. 別マシンから受信する意図がない限り、`bindAddress` はループバックのままにします。

ヘルパーが使うアドレスと引数:

| 機能 | アドレス | 引数 |
| --- | --- | --- |
| アバターパラメータ | `/avatar/parameters/<name>` | float、int、bool のいずれか 1 個 |
| Chatbox | `/chatbox/input` | string テキスト、bool immediate、bool notification |
| アバター変更 | `/avatar/change` | string アバター ID |
| typing | `/chatbox/typing` | bool |

## JPMS

JAR にはモジュール記述子が含まれます。

```java
module your.application {
    requires io.github.xxvw.vrcosc;
}
```

公開パッケージは `io.github.xxvw.vrcosc`、`io.github.xxvw.vrcosc.osc`、`io.github.xxvw.vrcosc.transport` だけです。

## 0.1.0 の制約

- Chatbox のテキストは 144 Unicode code point、明示的な改行で区切られた 9 行までです。VRChat は表示幅に応じて自動折り返しするため、描画後の行数が 9 行に収まることはライブラリから保証できません。長い行では折り返し分の余裕を残してください。
- バンドルの timetag は復号して保持しますが、0.1.0 では到着時に即時配送します。将来の timetag を予約配送する機能は後続版で扱います。
- OSCQuery と OSC アドレスパターン照合は未実装です。
- UDP はベストエフォートです。パケットの欠落、重複、順序の入れ替えが起こり得ます。再送は行いません。
- datagram の上限は 65,507 byte です。復号時は 16 段より深いバンドルと、展開後の要素数が 4,096 を超えるパケットを拒否します。
- リスナー例外に対して自動リトライは行いません。必要ならリスナー内でアプリケーション固有の再試行を実装してください。

OSCQuery と timetag の予約配送は後続版の候補です。バンドルの timetag はパケットモデルに保持しているため、スケジューリングを追加してもパケット API は変わりません。

## 開発

```bash
./gradlew check javadoc
./scripts/verify-release-artifacts.sh
./gradlew publishToMavenLocal
```

ビルドは Gradle 9.8.2、Kotlin DSL、Java 17 toolchain を使います。CI では Java 21 と 25 でもテストします。開発の流れは [CONTRIBUTING.ja.md](CONTRIBUTING.ja.md)、公開手順は [RELEASE.ja.md](RELEASE.ja.md)、VRChat での手動確認は [docs/vrchat-smoke-test.ja.md](docs/vrchat-smoke-test.ja.md) を参照してください。

## セキュリティ

脆弱性は [SECURITY.ja.md](SECURITY.ja.md) の手順に従い、GitHub の Private vulnerability reporting から報告してください。公開 Issue に非公開情報を書かないでください。

## ライセンス

MIT。[LICENSE](LICENSE) を参照してください。

本プロジェクトは独立したコミュニティプロジェクトであり、VRChat Inc. とは提携していません。
