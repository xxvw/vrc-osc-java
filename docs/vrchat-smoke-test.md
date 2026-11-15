# Manual VRChat smoke test

[日本語版](vrchat-smoke-test.ja.md)

Automated tests cover the codec and loopback UDP behavior only. Run this check before a release when a VRChat installation is available.

## Prerequisites

- Java 17 or newer.
- VRChat on desktop, signed in.
- OSC enabled under **Options > OSC > Enabled**.
- The Chatbox enabled in the Action Menu.
- An avatar with a writable parameter, or a built-in parameter such as `VRCEmote`.

## Send check

Run the following with the library on the classpath. Replace the avatar ID with an avatar you own.

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

Confirm that the emote runs, the Chatbox text appears without a notification sound, the typing indicator turns on and off, and the avatar changes. A missing Chatbox often means the Chatbox setting is disabled or the text exceeds VRChat's limit.

## Receive check

Start a client with a listener, then change an avatar parameter from the Action Menu or trigger an avatar change.

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

Confirm that parameter updates arrive as `/avatar/parameters/<name>` messages and that an avatar change produces `/avatar/change`. Keep the callback short and do not block the receive thread.

## Failure checks

- Send a Chatbox message longer than 144 code points and confirm that the client throws `IllegalArgumentException` before sending.
- Send a message with more than nine explicit lines and confirm the same validation.
- Close the client twice and confirm that no exception is thrown.
- Start another client on the same receive port after closing the first one. A bind failure means the port was not released.

Record the VRChat version, OS, Java version, library version, and observed OSC addresses in the release notes or the related issue. Do not attach private logs or account information.
