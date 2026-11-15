# VRChat 手動スモークテスト

[English](vrchat-smoke-test.md)

自動テストはコーデックと loopback UDP の範囲だけを確認します。VRChat を利用できる環境では、リリース前に次の確認を行います。

## 前提

- Java 17 以上
- デスクトップ版 VRChat にサインイン済み
- **Options > OSC > Enabled** で OSC を有効化済み
- Action Menu で Chatbox を有効化済み
- 書き込み可能なアバターパラメータ、または `VRCEmote` などの組み込みパラメータがあるアバター

## 送信の確認

ライブラリを classpath に置いて次を実行します。アバター ID は所有しているアバターのものに置き換えてください。

```java
import io.github.xxvw.vrcosc.VrcOscClient;

try (VrcOscClient client = VrcOscClient.builder().build()) {
    client.sendAvatarParameter("VRCEmote", 1);
    client.sendChatbox("vrc-osc-java smoke test", true, false);
    client.setTyping(true);
    Thread.sleep(2_000);
    client.setTyping(false);
    client.sendAvatarChange("avtr_your_avatar_id");
}
```

エモートが実行され、通知音なしで Chatbox が表示され、typing 表示がオン・オフし、アバターが変更されることを確認します。Chatbox が出ない場合は、Chatbox 設定が無効か、文字数制限を超えている可能性があります。

## 受信の確認

リスナー付きでクライアントを起動し、Action Menu からアバターパラメータを変更するか、アバター変更を実行します。

```java
import io.github.xxvw.vrcosc.VrcOscClient;
import io.github.xxvw.vrcosc.osc.OscMessage;

try (VrcOscClient client =
        VrcOscClient.builder()
                .listener(packet -> System.out.println(packet instanceof OscMessage message
                        ? message.address() + " " + message.arguments()
                        : packet))
                .errorHandler(Throwable::printStackTrace)
                .build()) {
    client.start();
    Thread.sleep(60_000);
}
```

パラメータ更新が `/avatar/parameters/<name>` として届き、アバター変更で `/avatar/change` が届くことを確認します。コールバックは短くし、受信スレッドをブロックしないでください。

## 異常系の確認

- 144 code point を超える Chatbox テキストが、送信前に `IllegalArgumentException` になることを確認します。
- 明示的な改行が 10 行以上あるテキストも同じく拒否されることを確認します。
- `close()` を 2 回呼んでも例外にならないことを確認します。
- 最初のクライアントを閉じた後、同じ受信ポートで別のクライアントを起動します。bind に失敗する場合はポートが解放されていません。

リリースノートまたは関連 Issue に、VRChat のバージョン、OS、Java のバージョン、ライブラリのバージョン、確認した OSC アドレスを記録してください。非公開のログやアカウント情報を添付しないでください。
