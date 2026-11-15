# vrc-osc-java

[![CI](https://github.com/xxvw/vrc-osc-java/actions/workflows/ci.yml/badge.svg)](https://github.com/xxvw/vrc-osc-java/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A dependency-free OSC 1.0 library for Java 17 and newer, with helpers for VRChat avatar parameters, the Chatbox, avatar changes, and typing. The generic codec and UDP transport work with any OSC peer.

[日本語版 README](README.ja.md)

## Requirements

- Java 17 or newer.
- No runtime dependencies.
- JPMS module name: `io.github.xxvw.vrcosc`.

## Installation

Maven coordinates:

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

## Sending to VRChat

`VrcOscClient` sends immediately and connects to VRChat's defaults: `127.0.0.1:9000` for outgoing data and `127.0.0.1:9001` for incoming data.

```java
import io.github.xxvw.vrcosc.VrcOscClient;

public final class Example {
    public static void main(String[] args) throws Exception {
        try (VrcOscClient client = VrcOscClient.builder().build()) {
            client.sendAvatarParameter("Mute", true);
            client.sendAvatarParameter("GestureLeft", 2);
            client.sendAvatarParameter("Voice", 0.75f);
            client.sendChatbox("Hello from Java", true);
            client.sendAvatarChange("avtr_00000000-0000-0000-0000-000000000000");
            client.setTyping(false);
        }
    }
}
```

`sendChatbox(String text, boolean immediate)` enables the Chatbox notification sound. Use `sendChatbox(text, immediate, notification)` when you need to control that flag.

## Receiving OSC

Receiving starts with `start()`. Sending works without it.

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

Listeners run in registration order on one daemon receive thread. Keep callbacks short; move slow work to your own executor. Listener failures and malformed datagrams go to the configured error handler, and reception continues. `close()` is idempotent and releases the socket and receive thread.

## Custom ports and addresses

```java
VrcOscClient client =
        VrcOscClient.builder()
                .host("127.0.0.1")
                .sendPort(9000)
                .bindAddress("127.0.0.1")
                .receivePort(9001)
                .build();
```

`receivePort(0)` binds an ephemeral port. Read the actual port with `receivePort()` after `start()`. `bindAddress` controls the local interface used by the receiver.

| Direction | Default | Builder setting |
| --- | --- | --- |
| Send to VRChat | `127.0.0.1:9000` | `host(...)`, `sendPort(...)` |
| Receive from VRChat | `127.0.0.1:9001` | `bindAddress(...)`, `receivePort(...)` |

## Generic OSC

The VRChat helpers use the same codec and transport as the rest of the API.

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

`OscArgument` supports int32, float32, string, blob, int64, double, timetag, char, symbol, MIDI, RGBA, bool, nil, impulse, and arrays. `OscCodec` encodes and decodes messages and bundles, including UTF-8 and four-byte padding.

```java
import io.github.xxvw.vrcosc.osc.OscCodec;
import io.github.xxvw.vrcosc.osc.OscPacket;

byte[] wire = OscCodec.encode(message);
OscPacket decoded = OscCodec.decode(wire);
```

For use without VRChat helpers, `UdpOscSender`, `UdpOscReceiver`, and `OscListener` are public and independent of VRChat-specific behavior.

## VRChat setup

1. In VRChat, open the Action Menu and enable OSC under **Options > OSC > Enabled**.
2. Confirm that VRChat's OSC receive port is `9000` and its sender port is `9001`.
3. Enable the Chatbox in the Action Menu if you use `/chatbox/input`.
4. Keep `bindAddress` on loopback unless you intentionally receive from another machine.

The default addresses used by the helpers are:

| Feature | Address | Arguments |
| --- | --- | --- |
| Avatar parameter | `/avatar/parameters/<name>` | one of float, int, or bool |
| Chatbox | `/chatbox/input` | string text, bool immediate, bool notification |
| Avatar change | `/avatar/change` | string avatar ID |
| Typing | `/chatbox/typing` | bool |

## JPMS

The JAR contains a module descriptor:

```java
module your.application {
    requires io.github.xxvw.vrcosc;
}
```

Only `io.github.xxvw.vrcosc`, `io.github.xxvw.vrcosc.osc`, and `io.github.xxvw.vrcosc.transport` are exported.

## Constraints in 0.1.0

- Chatbox text is limited to 144 Unicode code points and 9 explicit newline-separated lines. VRChat can add visual line breaks while wrapping, so the library cannot guarantee that the rendered text stays within nine lines. Leave room for wrapping when using long lines.
- Bundle timetags are decoded and preserved, but packets are delivered immediately when they arrive. Scheduling for future timetags is planned for a later release.
- OSCQuery and OSC address-pattern matching are not implemented.
- UDP is best effort. Packets can be lost, duplicated, or reordered, and this library does not retransmit them.
- A datagram is limited to 65,507 bytes. Decoding rejects bundles deeper than 16 levels and packets with more than 4,096 expanded elements.
- Listener exceptions do not start an automatic retry. Handle application-specific failures inside your listener when retries matter.

OSCQuery discovery and scheduled timetag delivery are candidates for a later release. The wire model already preserves bundle timetags so adding scheduling will not change the packet API.

## Development

```bash
./gradlew check javadoc
./scripts/verify-release-artifacts.sh
./gradlew publishToMavenLocal
```

The build uses Gradle 9.8.2, Kotlin DSL, and a Java 17 toolchain. CI also runs the tests on Java 21 and 25. See [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow, [RELEASE.md](RELEASE.md) for publishing, and [docs/vrchat-smoke-test.md](docs/vrchat-smoke-test.md) for manual VRChat verification.

## Security

Report vulnerabilities through GitHub Private vulnerability reporting as described in [SECURITY.md](SECURITY.md). Do not include private information in public issues.

## License

MIT. See [LICENSE](LICENSE).

This is an independent community project and is not affiliated with or endorsed by VRChat Inc.
