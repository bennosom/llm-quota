# LLM Quota

An Android app that shows how much quota is left on your LLM accounts: Codex, Claude and DeepSeek, as many accounts as you like, on one screen. The phone queries each provider directly; there is no server.

<p align="center"><img src="doc/screenshot.png" width="320" alt="The dashboard with Codex, Claude and DeepSeek accounts"></p>

---

## 1. Using and Configuring LLM Quota

### Install

Requires Android 8.0 (API 26) or newer. Build the APK (see part 2) and install it:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Add accounts

Tap `+` in the top bar and pick a provider:

| Provider | Sign-in |
|----------|---------|
| Codex    | Browser sign-in with your ChatGPT account |
| Claude   | Browser sign-in with your Claude account |
| DeepSeek | Paste an API key; it is checked before it is saved |

Browser sign-in opens Chrome when it is installed (Claude rejects the login in some other mobile browsers) and returns to the app on its own. Accounts of the same provider are told apart by their e-mail address, or for DeepSeek by the masked key (`sk-...abcd`).

### Read the dashboard

Each card shows one account: the remaining share of every quota window as a bar with a percentage, the reset time, and for DeepSeek the balance. Window names come from the provider, for example `5h` and `Week`. Bars turn yellow below 15 % and red below 5 %.

- Tap a card to open the provider's usage page.
- Tap the refresh icon on a card to update that account only; pull down to update all.
- Long-press a card for a menu with Refresh and Delete; keep the finger down and drag to reorder.

### Where credentials live

Tokens and API keys are stored on the phone only, in encrypted app storage. The app talks only to the providers' own endpoints.

---

## 2. Contributing and Building

### Prerequisites

- JDK 17 or newer
- Android SDK with platform 36
- A `local.properties` in the project root (untracked; Android Studio creates it with `sdk.dir`). Add the values the app needs but the repository does not contain:

```properties
sdk.dir=<path to the Android SDK>
codex.clientId=<OAuth client ID of the Codex CLI>
claude.clientId=<OAuth client ID of the Claude Code CLI>
app.id=<optional: application ID, default work.becode.llmquota>
```

Without the two client IDs the app builds, but browser sign-in fails. Changing `app.id` later makes Android treat the app as a new one with no accounts.

### Build and verify

```sh
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # unit tests
```

### Repository layout

Sources are in `app/src/main/java/work/becode/llmquota/`:

| File | Role |
|------|------|
| `MainActivity.kt` | Theme, dashboard, refresh and reorder logic |
| `AccountCard.kt` | Card, usage bars, long-press and drag gestures |
| `Dialogs.kt` | Sign-in and API-key dialogs |
| `Accounts.kt`, `Secrets.kt` | Account list in encrypted storage |
| `Quotas.kt`, `Quota.kt` | Per-provider quota fetching and response parsing |
| `Api.kt` | HTTP calls to the providers |
| `OAuthFlow.kt`, `Loopback.kt`, `Pkce.kt`, `CodexLogin.kt`, `ClaudeLogin.kt` | Browser sign-in with a local callback |
| `DeepSeek.kt` | API-key accounts |
| `TimeText.kt`, `Browser.kt` | Time formatting, opening links |
