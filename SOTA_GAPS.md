# SOTA Gap Analysis - rustypastechat vs. melanoscan

> **2026-09-10 update.** Closed since this document was written, each verified on
> the emulator against the live potatostack rustypaste instance (see the E2E
> screenshots in that session):
>
> - **Chat metadata is persisted.** Rename, category, colour, archive and mute
>   lived in a `mutableListOf<ChatThread>` inside `ChatListViewModel`, i.e. in
>   process memory: renaming a chat survived exactly until the next process
>   death, and `deleteChat` only hid the chat until the next `loadChats()`
>   re-derived it from the file listing. `ChatMetadataStore` (DataStore + JSON)
>   owns it now, and delete really deletes the pastes - falling back to a hidden
>   flag, with a message saying so, when the server has no delete token.
> - **`unreadCount` is computed.** It was rendered as a badge that nothing ever
>   set above zero. It cannot key on a timestamp here: a legacy paste carries no
>   time anywhere (the filename has none and this server answers
>   `creation_date_utc: null`), so the repository stamps it `currentTimeMillis()`
>   on every load - the badge went 30 to 31 across opening the chat. It keys on
>   message ids.
> - **Archive and mute exist.** `ChatThread.isActive` was a dead field. It is now
>   `isArchived` / `isMuted`, with a collapsed "Archived (n)" section in the list
>   and both honoured by the notifier.
> - **Background sync and local notifications.** WorkManager + `@HiltWorker`
>   poll, diff the listing and raise one grouped notification per chat, with a
>   settings page for the interval and the runtime POST_NOTIFICATIONS request.
> - **SFTP export actually uploads.** It was a stub that returned failure
>   unconditionally - a button that always failed. Implemented on the maintained
>   `com.github.mwiede:jsch` fork, with host-key PINNING: an upload refuses until
>   a fingerprint is saved, and "Fetch host key" reports the server's SHA256 in
>   OpenSSH's own format so it can be checked out of band.
> - **The composer is no longer hidden by the keyboard.** `enableEdgeToEdge()`
>   makes `adjustResize` a no-op; the Scaffold needed `imePadding()`.
> - **The chat top bar shows the chat name**, not the constant "RustyPaste Chat".
> - **The auth token is masked** (it was rendered in clear) and no longer lost:
>   `saveSettings` wrote DataStore before SecurePreferences, so the emission it
>   triggered read the OLD secret back into the in-memory settings - and the next
>   save of any unrelated setting wrote that empty string over the real token.
> - **The About page reads its version from BuildConfig**; it was a hardcoded
>   "1.0.0" while `versionName` was 1.1.0.

**Status: items 1-4 below (identity, animation, tokens, accessibility) plus the Settings
back-button fix, avatar/typing-indicator dedup, illustrated empty state, branded splash, tablet
NavigationRail, and a 3-page onboarding carousel have been implemented** (see `git diff` / `git log`
for the change). Still open, deliberately not attempted in this pass:
- A dedicated instrumented/Robolectric accessibility test suite (mirroring melanoscan's
  `AlbumAccessibilityTest.kt`) — this repo has no `androidTest` source set and no prior
  Compose-UI-test precedent to build on, and there's no way to compile/run one on this machine
  to verify it before landing it. Flagged rather than shipped unverified.
- Full retrofit of every ad hoc `8.dp`/`12.dp`/`16.dp` literal onto the new `RustySpacing` scale
  — the token object now exists (`ui/theme/Shape.kt`), but sweeping every screen to use it is a
  large, low-risk-but-high-diff mechanical pass better done incrementally.
- The chat screen's top bar always reads "RustyPaste Chat" instead of the actual chat name —
  pre-existing behavior, out of scope for a design-system pass (a product decision, not a design gap).

---

Baseline: `melanoscan` (github.com/fishingpvalues/myfirstmelanoma), cited in README as
design inspiration. Same author, same Compose/M3 stack, much deeper design-system investment.
This doc lists concrete gaps and a prioritized plan to close them.

## 1. Theme system

| | melanoscan | rustypastechat |
|---|---|---|
| Color | Named semantic object (`MelanoColors`): risk triad, warning vs error, 4-layer OLED dark surface hierarchy, glow/border colors, Fitzpatrick/rarity sub-scales | Flat list of ~30 raw `Color(0xFF...)` constants, no semantic grouping |
| Dynamic color | Explicitly **disabled**, with a documented rationale (must not drift semantic meaning) | Enabled unconditionally on API 31+, no rationale, no override path if it clashes with bubble/status colors |
| Typography | Custom weights + negative letter-spacing on display/headline for an "editorial" feel, still `FontFamily.Default` for a11y | Stock M3 `Typography()` values, zero customization |
| Shape tokens | Split between `GlassShape` and `MaterialTheme.shapes` (flagged as a gap even there) | Same duplication, plus raw `fontSize = 10.sp/11.sp/14.sp` literals bypassing the type scale in `MessageBubble.kt`/`ChatListScreen.kt` |
| Spacing tokens | Ad hoc (not present either) | Ad hoc, fully unstructured |

**Gap:** no identity. The palette reads as "default M3 blue" rather than something that says
"this is a paste/chat app." Fix: pick 1 signature hue for message-sent bubbles + accent (not
Google's default blue/teal), name every color role, kill unconditional dynamic color or gate it
behind a setting that defaults off.

## 2. Component library

melanoscan: `GlassCard`, `GlassSurface`, `GradientBorderCard`, `GlowCard` + a large
feature-specific library (badges, charts, body map, camera overlay) — 10+ files.

rustypastechat: `GlassCard`/`GlassSurface`/`GlowCard` exist but `GlassCard` is "glass" in name
only — plain tinted `Card`, no blur/translucency. `GradientBorderCard` (present in melanoscan)
is missing here.

**Gaps:**
- No real glass/blur material (Compose `Modifier.blur` / `haze` library) despite the naming.
- No shared avatar component — duplicated inline twice in `ChatListScreen.kt`.
- No shared button/chip/input styling — every screen re-specifies `MaterialTheme.shapes.medium` etc.
- `MediaPreview.kt` has a dead duplicate `TypingIndicator` alongside the real
  `AnimatedTypingIndicator` — dead code, remove.

## 3. Animation/motion

Both apps have a `MelanoEasing`-style animation toolkit (rustypastechat's is literally named
`MelanoAnimations.kt` — inherited from the sibling project). The difference: melanoscan's is
threaded through the splash screen, XP bars, risk bars, card-flip, badge unlocks. **rustypastechat's
is almost entirely unused** — only `MelanoEasing.Bounce` is consumed anywhere. `shimmerEffect`
and `rememberPulseAnim` are built and never called.

**Gaps:**
- No skeleton/shimmer loading states anywhere (chat list / message load = spinner + "Loading...").
- No entry animations on message bubbles arriving, chat list items appearing, or settings sub-pages.
- No custom splash screen content — default OS splash only, despite `installSplashScreen()` being wired.

## 4. Navigation

rustypastechat has 3 nav-graph destinations + Settings paginated locally with 7 sub-pages that
bypass the nav-graph/back-stack entirely (system back button doesn't map onto sub-page navigation
via the same model as ChatList/Chat/Settings). melanoscan uses one consistent NavHost + adaptive
rail/bottom-nav switch for tablet/large-screen.

**Gaps:**
- Settings sub-page navigation is architecturally inconsistent with the rest of the app.
- No tablet/foldable adaptive layout (`NavigationRail` at `screenWidthDp >= 600`).
- No deep linking (minor, chat app doesn't obviously need it yet).

## 5. Accessibility — the largest gap

melanoscan has ~151 `semantics{}` call sites, a dedicated a11y test suite citing WCAG 1.4.1,
`mergeDescendants` so risk/status is announced as text (not color-only), `heading()` semantics
on section titles.

rustypastechat: 26 `contentDescription` uses total, only 2 files touch `Modifier.semantics{}`
at all (`BottomNavBar.kt`, `LockScreen.kt`). No TalkBack-specific work, no `stateDescription`,
no `liveRegion` on message-status changes, no a11y tests, no `testTag`s for UI tests at all.

**This is the single highest-leverage fix** — message status icons (sent/delivered/read) convey
state via icon shape alone with a contentDescription, but chat bubbles, swipe actions, and the
formatting toolbar have none. A screen reader user currently cannot use this app well.

## 6. "Wow factor" / distinctive elements

melanoscan: home-screen Glance widget, custom Canvas body-map + charts, AR-style camera guide
overlay, gamified card/badge system, bespoke splash animation.

rustypastechat, present: `GlowCard`, animated pill bottom nav, `MediaGalleryGrid`
(WhatsApp-style grid), swipe-to-reply bubble.

rustypastechat, **absent**: onboarding flow (first run drops straight into ChatList/LockScreen),
home-screen widget, empty-state illustration (plain centered text only), any chart/visualization
(Storage settings page is raw text stats), skeleton loading, custom splash content.

## 7. State management

Both use Hilt + `StateFlow` + `.copy()` — consistent. rustypastechat has a generic `UiState<T>`
wrapper in `ui/common/` that's defined but **not used** — every screen hand-rolls its own flat
`UiState` data class duplicating the same `isLoading`/`error` shape. Minor inconsistency, not
urgent.

---

## Priority-ordered plan to reach SOTA

1. **Accessibility pass** (highest impact, currently near-zero investment): semantics on message
   bubbles (role, merged description "You, sent, 2:30pm, delivered"), `heading()` on section
   titles in Settings, live region on new-message arrival, contentDescription on every
   decorative-vs-informative icon distinction, a dedicated a11y test file mirroring melanoscan's.
2. **Give the app a real visual identity**: replace the flat raw-hex `Color.kt` with a named
   semantic palette built around one signature accent hue (not stock M3 blue) for sent-bubble +
   primary actions; gate dynamic color behind an explicit opt-in setting (default off) so the
   chosen identity isn't overridden by the user's wallpaper.
3. **Wire up the existing but unused animation toolkit**: shimmer skeleton for chat-list/message
   loading, fade+slide entry for new messages and list items, replace plain `CircularProgressIndicator`
   busy states.
4. **Unify shape/spacing tokens**: single `Shape.kt`, add a `Spacing` object, delete the
   duplicate `GlassShape`, migrate the stray `fontSize = Xsp` literals onto the type scale.
5. **Fix Settings sub-page navigation** to participate in the same back-stack model as the rest
   of the app (or explicitly document why it's intentionally local state).
6. **Small polish**: shared `Avatar` composable (dedupe the two inline copies), remove dead
   duplicate `TypingIndicator` in `MediaPreview.kt`, illustrated empty states instead of plain text,
   custom branded splash content.
7. **Stretch**: tablet/foldable adaptive layout (`NavigationRail`), lightweight onboarding
   (3-screen carousel matching melanoscan's, skippable, no monetization ask).

Items 1–4 are the ones that actually move the needle on "SOTA" — they're substance
(accessibility, identity, motion, consistency), not decoration. Items 6–7 are polish once the
foundation is fixed.
