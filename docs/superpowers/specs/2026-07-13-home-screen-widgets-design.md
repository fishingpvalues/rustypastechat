# Home Screen Widgets — Design

Date: 2026-07-13

## Goal

Add Android home-screen widgets to RustyPasteChat: a quick-note shortcut into a
pinned chat, and a recent-chats glance list. Both use Jetpack Glance
(Compose-style widget API), matching the app's existing Compose UI stack.

## 1. Pinning (new capability)

`ChatThread` (app/src/main/java/com/rustypastechat/data/model/ChatThread.kt)
gains:

```kotlin
val isPinned: Boolean = false
```

Default value means existing persisted JSON (via `PreferencesManager`) still
deserializes fine — no migration step needed.

`ChatListViewModel` gets `fun togglePin(chatId: String)`: setting a chat's pin
clears `isPinned` on any other chat first, since exactly one pinned chat is
supported (the widget needs a single deterministic target). Persists through
existing `PreferencesManager` save path.

`ChatListScreen`: long-press on a chat row opens a context menu with a
"Pin for quick-note" / "Unpin" entry. The pinned chat renders a pin icon and
sorts to the top of the list.

## 2. QuickNoteWidget (Glance, min size 2×1)

Displays pinned chat's name + last-message preview + a "+ Note" button.

- Tap → deep link into `ChatScreen` for the pinned chat, with an intent extra
  (`focusInput=true`) that auto-focuses `MessageInput` and raises the
  keyboard immediately on arrival. (Glance has no text-input composable —
  only `Text`/`Button`/`Image`/`LazyColumn`/`CheckBox`/`Switch`/`RadioButton`
  — so real inline typing on the home screen isn't available; tap-to-focus
  is the supported pattern.)
- No pinned chat set → widget shows "Tap to pin a chat", opens
  `ChatListScreen` in pin-picker mode.
- Theming: `GlanceTheme.colors` for Material You dynamic color (API 31+),
  falls back to the app's brand palette below that.
- Data: reads pinned chat id/name/preview from `PreferencesManager` through a
  `GlanceStateDefinition`.

## 3. RecentChatsWidget (Glance, resizable 2×2 → 4×4)

`LazyColumn` of chat rows (name, last-message preview, timestamp). Row count
adapts to widget height via `LocalSize.current`. Capped at 10 most-recent
chats (by `lastTimestamp`), scrollable within that. Each row tap deep-links
directly into that chat's `ChatScreen`. Same dynamic-color theming as
QuickNoteWidget.

## 4. Update triggers

A small `WidgetUpdater` object wraps `GlanceAppWidgetManager` /
`updateAll()` calls for both widgets. Called from:

- `ChatListViewModel.togglePin()`
- `ChatViewModel` message send/receive paths (new message → recent-chats
  widget + quick-note preview may change)
- `WhatsAppImportManager` import completion

## 5. Plumbing

- Add `androidx.glance:glance-appwidget` and `androidx.glance:glance-material3`
  to `gradle/libs.versions.toml` + `app/build.gradle.kts`.
- Two `GlanceAppWidgetReceiver` subclasses + widget-info XML
  (`minWidth`/`minHeight`/`resizeMode`/`previewImage`/`targetCellWidth`)
  registered as `<receiver>` entries in `AndroidManifest.xml`.
- `NavGraph`/deep-link handling extended to parse widget-launched intents
  (`chatId`, `focusInput`).

## Testing

- Unit test `togglePin` single-pin-invariant logic in
  `ChatListViewModelTest`.
- Manual: add both widgets via long-press home screen → widget picker,
  resize RecentChatsWidget through its size range, verify tap deep links and
  pin/unpin flow update both widgets live.

## Out of scope (this round)

- Unread-count widget, new-paste/share-sheet widget (deferred, per earlier
  scoping — only quick-note + recent-chats this round).
- Inline home-screen text entry (Glance limitation, see §2).
