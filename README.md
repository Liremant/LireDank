# <img src="https://user-images.githubusercontent.com/85196642/153115305-e39f854d-6dc9-4975-a60b-653f6ecc87df.png" height="75"> LireDank

A Twitch chat client for Android — a fork of [DankChat](https://github.com/flex3r/DankChat) with a focus on 7TV integration and UI polish.

## Features

Everything from DankChat, plus:

- **7TV nickname paints** — real shader rendering matching the 7TV website (`background-clip: text` semantics): linear and radial gradients with authored stop positions, hard edges, repeating gradients, texture paints stretched over the glyphs, chained drop shadows, and multi-layer paints
- **7TV badges** on messages
- **7TV personal emotes** that actually load
- **Homies badges**
- **Message search** in the app-bar menu
- **Tab strip** with most of the width and ping counts
- **Compact input row** by default
- **T9 composition fix** — the IME composing region survives autocorrection

## Downloads

Grab an APK from the [latest release](https://github.com/Liremant/LireDank/releases/latest):

| APK | Use case |
|-----|----------|
| `LireDank-release-arm64-v8a.apk` | Modern 64-bit phones (recommended) |
| `LireDank-release-armeabi-v7a.apk` | Older 32-bit phones |
| `LireDank-release-x86_64.apk` | Emulators / x86 devices |
| `LireDank-release-universal.apk` | All architectures in one |

## Building

```bash
./gradlew :app:assembleDebug
```

Output lands in `app/build/outputs/apk/debug/` as per-ABI and universal APKs.

Release builds prefer a `app/keystore/LireDank.jks` keystore with credentials
from `local.properties` (`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`) or the matching environment variables; without one
they fall back to the debug keystore so CI builds still ship.

## Attribution

This project is a fork of [flex3r/DankChat](https://github.com/flex3r/DankChat) (MIT licensed).
All upstream work is credited to its author; the 7TV paint/badge/emote work in this fork
follows the rendering behavior of the [7TV website](https://github.com/SevenTV/Website) and
[chatterino7](https://github.com/SevenTV/chatterino7).

## License

[MIT](LICENSE) © flex3r, LireDank contributors
