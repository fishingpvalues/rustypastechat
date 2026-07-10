# Feature Gap Analysis — rustypastechat vs. WhatsApp/Telegram/Signal-tier chat apps

## The architectural fact that reframes everything below

This app is a **shared pastebin styled as chat**, not a chat protocol. A "chat" is just a
filename-prefix grouping of files on a rustypaste server. There is no account system, no
per-message sender identity, no push channel, no E2E crypto — messages are plaintext files
behind one bearer token.

That sounds like a limitation, but it cuts both ways:

- **The server IS already shared.** If two people point their apps at the same rustypaste
  instance and the same chat ID, they are already looking at the same files. Multi-device/
  multi-person "chat" isn't blocked by the backend — it's blocked by the *client* only ever
  polling on demand and by `isOutgoing` being a local boolean instead of an author identity.
  A real "two people talking" experience is closer than it looks: it needs background sync +
  an author-id convention in the filename, not a new protocol.
- **Anything that needs real E2E encryption, group membership, presence, or calls is out of
  scope** unless the backend changes — rustypaste has no concept of users, sessions, or
  transport-level channels beyond HTTP request/response.

So the gaps below split into three tiers. Tier 1 is broken promises (fix regardless of
direction). Tier 2 is real value reachable without touching the backend. Tier 3 is the
"become an actual multi-party messenger" option — bigger, and only worth it if that's the
product goal.

---

## Tier 1 — Broken or dead features (fix first, these actively mislead users)

| Feature | Problem |
|---|---|
| **Formatting toolbar** | Inserts literal `**bold**`/`~~strike~~` markers into the message. `RichTextContent` never parses them — the recipient (and sender) just sees literal asterisks. Worse than not having the feature: it looks like it should work. |
| **Forward** | `forwardMessage()` copies message text into your own composer. There's no chat picker — this is "quote into current chat," not forward. |
| **Oneshot / "View once"** | Only a static label under the bubble. Nothing hides or burns the message client-side after viewing — it stays in the log forever. Server-side oneshot only governs whether the *file* is fetchable twice, irrelevant to what's rendered locally. |
| **Message TTL/expiry** | `expiresAt` is stored and sent to the server but never checked client-side. Expired messages just sit there. |
| **Unread badges** | `ChatThread.unreadCount` is rendered as a UI badge but never computed — always 0. |
| **Archive/mute** (`isActive`) | Field exists, nothing reads or writes it. No archive view, no mute toggle. |
| **SFTP export** | Settings advertises "Export to SFTP for off-device storage." `SftpUploader.upload()` unconditionally returns failure — it's a stub with a TODO comment. Ships a button that always fails. |
| **`MessageStatus.READ`** | Defined, has an icon, has a label — never assigned anywhere. Dead state. |

**Recommendation:** either wire these up (mostly small, self-contained fixes — see Tier 2 for
the ones worth investing in) or remove the UI surface for the ones you don't intend to fix
soon (a broken SFTP button and inert formatting toolbar are worse than absent).

---

## Tier 2 — High-value features reachable without changing the backend

Ranked roughly by value-to-effort:

1. **Voice messages.** No `RECORD_AUDIO` permission, no `MediaRecorder`/`MediaPlayer` anywhere.
   This is the single biggest "feels like a real chat app" gap — WhatsApp/Telegram/Signal all
   lead with it. Needs: hold-to-record button, waveform-preview bubble, playback with
   scrubbing. Fully client-side; upload the audio file as a paste like images already are.
2. **Video/file attachment.** `MediaType.VIDEO`/`FILE` already exist as enum values and are
   completely unused — the picker is hardcoded to `image/*`. This is a picker MIME-type change
   plus a video-thumbnail/file-chip bubble renderer, not new architecture.
3. **Link preview unfurling.** Currently URLs are just underlined text (regex auto-link only).
   An OpenGraph fetch + thumbnail/title/description card is a well-understood, self-contained
   feature (background fetch, cache, render a `LinkPreviewCard`).
4. **Fix and enforce oneshot/TTL locally** (Tier 1 items, promoted here because they're genuine
   product value once real — "disappearing messages" is table stakes for a modern chat app,
   and the server-side plumbing already exists).
5. **Drafts.** `typingMessage` is in-memory only. Persist per-chat draft text to DataStore —
   small, high perceived-polish payoff (switch chats, come back, your half-typed message is
   still there).
6. **Push/local notifications via background sync.** No `POST_NOTIFICATIONS`, no WorkManager,
   no FCM. Given the "shared server" reframing above, a periodic WorkManager poll (or a
   long-lived connection if rustypaste ever adds one) that diffs the file list and fires a
   local notification on new pastes is what turns this from "an app you have to remember to
   pull-to-refresh" into something that feels alive. This is the highest-leverage architectural
   investment short of Tier 3.
7. **Starred/pinned messages.** Bookmark important pastes, a dedicated "Pinned" view per chat —
   simple local flag + filtered list, no backend change.
8. **Real forward with a chat picker.** Bottom sheet listing your chats, pick one, re-upload the
   paste under the target chat's prefix.
9. **Haptic feedback.** `LocalHapticFeedback` is imported in `ChatScreen.kt` but not called
   anywhere. Wire it to send/delete/long-press/reaction — cheap, and it's the single detail
   that makes an app feel physically responsive rather than flat.
10. **In-app camera with capture guidance**, replacing the current system-camera hand-off —
    matches the "SOTA polish" bar the design pass already set (melanoscan has a full
    camera-guide overlay; this app currently just launches the stock camera app).
11. **Message reactions (as personal tags, not multi-party).** Since there's no second-party
    identity yet, "reactions" here would mean quick-tap emoji tags on your own message history
    (bookmarking/mood-tagging) rather than social reactions — still a recognizable, useful UI
    pattern (long-press → emoji picker → tiny badge on the bubble), and it's the visual language
    users expect from a chat app even in single-user mode.

## Tier 3 — Becoming an actual multi-party messenger (bigger call, needs a product decision)

This only makes sense if the goal shifts from "chat-styled personal paste client" to "actual
chat app two people use." Given the shared-server insight above, the path is more incremental
than "rewrite everything," but it's still a real scope jump:

- **Author identity.** Encode a per-device/user id in the paste filename convention so
  `isOutgoing` becomes "authored by me" vs "authored by them" instead of a local-only boolean.
- **Real read receipts / typing indicators between two parties** — needs a lightweight presence
  channel (even just "last polled at" timestamps written back as tiny marker pastes) since
  rustypaste has no push/session concept.
- **True group chat (3+ participants)** — needs a participant list concept, currently entirely
  absent (a "chat" has no membership, just a filename prefix).
- **End-to-end encryption** — meaningful once there's a real second party; right now "security"
  is just bearer-token auth to your own server, which is a different threat model than E2E
  (protects against a stranger hitting your server, not against the server operator).
- **Voice/video calls** — needs WebRTC or equivalent; nothing in this stack today gets you
  partway there.

Not recommending this tier unless you want to say explicitly "yes, make this a real multi-user
messenger" — it's a different product than what exists today, not a UI pass.

---

## UI/UX direction for the new surfaces in Tier 2 (per the frontend-design pass already applied to this app)

- **Voice message bubble**: waveform rendered from actual amplitude samples (not a fake
  static squiggle), scrubber thumb in the signature Rust accent color, play/pause morphs
  in place rather than swapping icons abruptly.
- **Link preview card**: sits *inside* the bubble, image left-aligned or full-width-top
  depending on aspect ratio, uses the existing `GlassCard`/shape tokens so it doesn't
  introduce a fourth visual language.
- **Reaction picker**: a small horizontal emoji strip that spring-pops from the long-press
  point (reuse `MelanoEasing.Bounce`, already in the animation toolkit and already wired
  into the typing indicator) rather than a generic dropdown menu.
- **Notification**: use the `RustyMark` (already built) as the small-icon, not a generic
  bell/chat glyph — reinforces the identity established in the last pass instead of
  introducing a fifth icon into the app.

---

## Suggested order if you want to proceed

1. Tier 1 fixes (mostly small, and several are prerequisites for Tier 2 items anyway —
   e.g. real oneshot-burn needs to exist before it's worth polishing further).
2. Tier 2, in the ranked order above.
3. Revisit Tier 3 only as an explicit "yes, pivot to multi-user" decision.
