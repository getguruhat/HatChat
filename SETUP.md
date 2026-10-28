# Getting your Instagram key

You only do this once. It takes about 10 minutes, and the app refreshes the key on its own after that.

You need an Instagram **Creator** or **Business** account. Personal accounts can't use the API.

## 1. Create a Meta developer app
1. Go to <https://developers.facebook.com>, log in, and register as a developer if asked.
2. **My Apps → Create app.**
3. For the use case, choose **"Manage messaging & content on Instagram"**.
4. Give the app any name, for example "My Inbox".

You don't need App Review or to publish the app. It stays in development mode, which is enough for accounts you own.

## 2. Generate the key
1. In your app's dashboard, open **Instagram → API setup with Instagram login**.
2. Under **Generate access tokens**, click **Add account** and log in with your Creator account.
3. Make sure the permissions include `instagram_business_basic` and `instagram_business_manage_messages`.
4. Click **Generate token** and copy it. It's a long string that starts with `IG`.

## 3. Let apps read your messages (Instagram phone app only)
This switch is **not on instagram.com**. It's only in the Instagram app on your phone, and it may only show up after step 2, once a developer app is connected to your account. Keep the Instagram app installed until this step is done.

In the Instagram app: tap your profile → **☰ menu → Messages and story replies → Message controls → Connected tools → Allow access to messages → On**.

If you can't find it, look under **Settings → Privacy → Messages**. Older versions of the app put it there.

Once it's on, you can uninstall the Instagram app. The setting stays on.

## 4. Put it in the app
Open HatChat → **Settings** → paste it into **Access token** → **Save key**.

The app checks the key with Instagram, encrypts it with your phone's Keystore, and shows "Connected as @you".

## Keep it safe
- The key gives access to your DMs. Don't share it or paste it anywhere else.
- If it leaks, remove the account under **Generate access tokens** in the Meta dashboard. That revokes the key.

## Messenger
No key is needed. Open the Messenger tab and sign in to Facebook. If Facebook keeps pushing you to install the Messenger app, turn on **Settings → Desktop mode**.
