# RustyPaste Chat

A lightweight, Material Design 3 chat app for your [rustypaste](https://github.com/orhun/rustypaste) instance. Chat with paste notes, share media inline, and get AI-powered replies via OpenAI-compatible endpoints.

## Features

- **Chat Interface** — Google Messages-style with WhatsApp message status indicators (single check → double check → blue double check)
- **Paste Notes as Messages** — Every message is a paste note on your rustypaste server
- **Media Sharing** — Inline image display, gallery picker, file attachments
- **AI Assistant** — Streaming chat completions via OpenAI-compatible API (GPT-3.5, GPT-4, local LLMs)
- **Material Design 3** — Full light/dark theme with dynamic color (Material You) on Android 12+
- **Glass Card System** — M3-aligned card components with glow and gradient border support
- **Custom Animations** — Spring physics, shimmer loading, fade-in-scale entry animations (M3 motion spec)
- **Hub-and-Spoke Settings** — Clean Material 3 settings with sub-page navigation
- **Connection Testing** — Test your paste server connection directly from settings

## Screenshots

*(Coming soon)*

## Requirements

- Android 8.0+ (API 26)
- A running [rustypaste](https://github.com/orhun/rustypaste) instance
- Optional: OpenAI API key (or compatible endpoint) for AI chat

## Quick Start

1. Download the [latest APK](https://github.com/fishingpvalues/rustypastechat/releases) or build from source
2. Open Settings → Paste Server → enter your rustypaste server URL
3. Start chatting!

## Build from Source

```bash
# Prerequisites: JDK 17, Android SDK 35, Gradle 8.9+

# Clone
git clone git@github.com:fishingpvalues/rustypastechat.git
cd rustypastechat

# Set env vars (or create local.properties)
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Build
make debug

# Install on device
make install
```

## Architecture

```
app/src/main/java/com/rustypastechat/
├── data/
│   ├── api/           # Retrofit + OkHttp (RustyPasteApi, OpenAiApi)
│   ├── local/         # DataStore preferences
│   ├── model/         # Domain models (Message, PasteItem, LlmModels)
│   └── repository/    # PasteRepository, LlmRepository
├── ui/
│   ├── animations/    # M3 motion easing + animation primitives
│   ├── chat/          # ChatScreen, ChatViewModel
│   │   └── components/  # MessageBubble, MessageInput, MessageStatus
│   ├── components/    # GlassComponents (GlassCard, GlowCard, etc.)
│   ├── navigation/    # NavGraph
│   ├── settings/      # SettingsScreen (hub-and-spoke)
│   └── theme/         # Color, Theme, Typography (full M3 scheme)
└── util/              # Extensions
```

## Tech Stack

| Library | Purpose |
|---|---|
| Jetpack Compose + Material 3 | UI framework |
| Kotlin 2.1.0 | Language |
| Navigation Compose | Screen navigation |
| Retrofit + OkHttp | HTTP client |
| Kotlinx Serialization | JSON |
| Coil | Image loading |
| DataStore | Settings persistence |
| Coroutines + Flow | Async data |

## Settings

| Category | Options |
|---|---|
| **Paste Server** | URL, auth token, connection test |
| **LLM Integration** | Enable/disable, endpoint URL, API key, model name |

## License

MIT

## Credits

- [rustypaste](https://github.com/orhun/rustypaste) — The pastebin server that powers this app
- [melanoscan](https://github.com/fishingpvalues/myfirstmelanoma) — Design patterns and M3 component inspiration
