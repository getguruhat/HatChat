# OpenInbox

A small, open-source Android app for your Instagram DMs and Facebook Messenger chats, so you can uninstall the official apps.

- **Instagram tab:** reads and answers DMs through Meta's **official** Instagram API. Needs an Instagram **Creator or Business** account and an access key you generate yourself (see [SETUP.md](SETUP.md)).
- **Messenger tab:** Facebook's own web inbox (`facebook.com/messages`) inside the app. You sign in on Facebook's real page.
- **Your key stays on your phone.** It's encrypted with an AES-256 key held in the Android Keystore. Backups are turned off, and the app has no server and no analytics. The only network code is [`InstagramApi.kt`](app/src/main/java/dev/openinbox/app/data/InstagramApi.kt).

> Not affiliated with, endorsed by, or connected to Meta, Instagram, or Facebook.

## What it can't do (Meta's limits, not ours)

| | |
|---|---|
| Instagram history | Only the **20 most recent messages** of each chat. |
| Replying late | Replies are allowed only within **24 hours** of the other person's last message. Ticking "Human Agent" extends that to 7 days. |
| Starting new chats | You can't DM someone who hasn't messaged you first. |
| Group chats | Not available through the API. |
| Notifications | v0.1 has no push notifications. Instagram chats refresh every 15 s while open. |
| Feed / Reels / Stories | Not included, on purpose. |

## Build

Requirements: JDK 17 and the Android SDK (platform 35).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install` or copy it to your phone.

## Project layout

```
app/src/main/java/dev/openinbox/app/
  MainActivity.kt          tabs + theme
  InboxViewModel.kt        state, polling, key refresh
  data/TokenStore.kt       Keystore-encrypted key storage
  data/InstagramApi.kt     official Instagram Graph API client
  ui/InstagramScreen.kt    conversation list + chat
  ui/MessengerScreen.kt    Messenger WebView
  ui/SettingsScreen.kt     paste / refresh / remove key
```

## License

MIT. See [LICENSE](LICENSE).
