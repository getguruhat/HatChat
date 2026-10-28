# HatChat

A small, open-source Android app from [GuruHat](https://github.com/getguruhat) for your Instagram DMs and Facebook Messenger chats, so you can uninstall the official apps.

- **Instagram tab:** reads and answers DMs through Meta's **official** Instagram API. Needs an Instagram **Creator or Business** account and an access key you generate yourself (see [SETUP.md](SETUP.md)).
- **Messenger tab:** Facebook's own web inbox (`facebook.com/messages`) inside the app. You sign in on Facebook's real page.
- **Your key stays on your phone.** It's encrypted with an AES-256 key held in the Android Keystore. Backups are turned off, and the app has no server and no analytics. The only network code is [`InstagramApi.kt`](app/src/main/java/com/getguruhat/hatchat/data/InstagramApi.kt).

> Not affiliated with, endorsed by, or connected to Meta, Instagram, or Facebook.

## What it can't do (Meta's limits, not ours)

| | |
|---|---|
| Instagram history | Only the **20 most recent messages** of each chat. |
| Replying late | Replies are allowed only within **24 hours** of the other person's last message. Ticking "Human Agent" extends that to 7 days. |
| Starting new chats | You can't DM someone who hasn't messaged you first. |
| Group chats | Not available through the API. |
| Notifications | No push notifications yet. Instagram chats refresh every 15 s while open. |
| Feed / Reels / Stories | Not included, on purpose. |

## Download / build

The APK is built by GitHub Actions. Nothing is needed on your computer.

- **Latest build:** open the **Actions** tab, pick the newest "Build APK" run, and download **HatChat-apk**.
- **Releases:** pushing a tag like `v0.1.0` attaches the APK to a GitHub Release.

**Updates:** when HatChat opens, it checks this repo's latest release. If there's a newer version, it offers **Update**, downloads the APK, and opens Android's installer. The first time, Android asks you to allow HatChat to install apps.

**Publishing a release:** push a tag like `v0.1.3`. The version is taken from the tag.

To build locally instead, you need JDK 17 and the Android SDK, then run `./gradlew assembleDebug`.

## Project layout

```
app/src/main/java/com/getguruhat/hatchat/
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
