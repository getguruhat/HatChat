<img src="docs/images/icon.svg" width="96" alt="HatChat icon">

# HatChat

A small, open-source Android app from [GuruHat](https://github.com/getguruhat) for your Instagram DMs and Facebook Messenger chats, so you can uninstall the official apps.

- **Instagram tab:** Instagram's own DM website (`instagram.com/direct/inbox`) inside the app. All chats, history, groups, photos and voice notes. You sign in on Instagram's real login page; HatChat never sees your password.
- **Messenger tab:** Facebook's own web inbox (`facebook.com/messages`), the same way.
- **No feed, no Reels.** Each tab opens straight to your messages.
- **Optional key mode:** Creator and Business accounts can use Meta's official Instagram API instead (Settings → turn off *Use Instagram website*, then see [SETUP.md](SETUP.md)). Meta only shows chats with your app's testers until the app passes App Review, so most people should keep website mode.
- **Private by design:** no server and no analytics. Logins stay in the app's private storage, and the optional API key is encrypted with the Android Keystore.

> Not affiliated with, endorsed by, or connected to Meta, Instagram, or Facebook.

## Limits

| | |
|---|---|
| Notifications | No push notifications yet. |
| Look and feel | The tabs show Instagram's and Facebook's own websites, so they control the layout. |
| Key mode (optional) | Meta's API only returns the last 20 messages per chat, allows replies within 24 h, can't start new chats or see groups, and hides chats from non-testers until App Review. |

## Download / build

The APK is built by GitHub Actions. Nothing is needed on your computer.

- **Latest build:** open the **Actions** tab, pick the newest "Build APK" run, and download **HatChat-apk**.
- **Releases:** pushing a tag like `v0.1.0` attaches the APK to a GitHub Release.

**Updates:** when HatChat opens, it checks this repo's latest release. If there's a newer version, it offers **Update**, downloads the APK, and opens Android's installer. The first time, Android asks you to allow HatChat to install apps.

**Publishing a release:** push a tag like `v0.1.3`. The version is taken from the tag.

To build locally instead, you need JDK 17 and the Android SDK, then run `./gradlew assembleDebug`.

## Debugging

With the phone connected by USB (USB debugging on), you only need `adb`, no compiler:

```bash
adb install -r HatChat-v0.1.5.apk   # install a build downloaded from Releases
adb logcat -s HatChat               # live API log: paths, status codes, Instagram's errors
```

The log never contains your key or message text. **Settings → Run diagnostics** shows the same information on the phone.

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
