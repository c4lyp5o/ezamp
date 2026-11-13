# Play Store

Everything the Play Console asks for, in the order it asks. Account: the owner's existing personal developer account (it predates November 2023, so no 12-tester closed-test gate).

## Building the bundle

Store bundles are signed with the upload key; Play App Signing holds the app key. The upload key and its passwords live on the build box only, in `C:\ezamp\keys\` (`ezamp-upload.jks` + `keystore.properties`). **Back that folder up somewhere off the box.** Losing it means a Play support reset, which takes days.

```bat
gradle.bat :app:bundleRelease "-Pezamp.keystore=C:/ezamp/keys/keystore.properties"
```

Output: `app/build/outputs/bundle/release/app-release.aab`. Check the signer before uploading. It must be `CN=ezamp`, SHA-256 `B1:5A:D1:88:…:66:83:B6`, not `CN=Android Debug`:

```bat
C:\ezamp\tools\jdk21\bin\keytool.exe -printcert -jarfile app\build\outputs\bundle\release\app-release.aab
```

Plain `assembleRelease` without the property stays debug-signed, so sideload APKs keep upgrading the installs on the test phone and emulator. A Play install and a sideload install have different signatures, so switching between them needs an uninstall.

## Store listing

- **App name** (30 max): `ezamp for Jellyfin`. The launcher label stays `ezamp`.
- **Short description** (80 max): `A fast, minimal music player for your own Jellyfin server.`
- **Full description**:

  > ezamp plays the music on your Jellyfin server and stays out of the way.
  >
  > It keeps a copy of your library index on the phone, so browsing and search are instant (search answers in milliseconds, even with thousands of albums) and nothing waits on the network except the audio itself.
  >
  > • Albums, tracks, artists and genres, one swipe apart, with search on every page
  > • Gapless playback, shuffle and repeat, a sleep timer that fades out
  > • Direct (lossless) or Saver (mp3 192k) streaming
  > • Downloads for albums and playlists, and a downloaded-only mode for flights and bad signal
  > • Favourites shared with Jellyfin, plus playlists kept on your phone: hold any track to add it
  > • An equalizer with your phone's presets or five bands of your own
  > • Plays and positions reported back to Jellyfin, so your history stays in one place
  >
  > ezamp needs a Jellyfin server you can reach (on your home network or over the internet). It collects no data: no analytics, no ads, no account with us. It talks only to your server.
  >
  > ezamp is an independent app. It is not made by or affiliated with the Jellyfin project.

- **Category**: Music & Audio. **Tags**: music player, media server.
- **Contact email**: required, and shown publicly. Owner to choose.
- **Privacy policy URL**: `https://github.com/c4lyp5o/ezamp/blob/master/PRIVACY.md` (works once the repo is public).

## Graphics

| Asset | Spec | State |
| --- | --- | --- |
| App icon | 512×512 PNG, 32-bit | ⬜ render from `ic_launcher_foreground.xml` on `#0B0B0C` |
| Feature graphic | 1024×500 PNG/JPG | ⬜ |
| Phone screenshots | 2–8, 9:16, 1080×1920 | ✅ 7 taken 2026-11-13 on the emulator (library, album, search, now playing, playlists, hold-to-add, sound). They show real album covers; see below. |

Album covers are third-party copyrighted art. Many music apps show them in their listings, but they are a known reason for review rejection under Google's intellectual-property policy. The safest set is shot against a library of Creative Commons or public-domain music.

## App content (policy forms)

- **Privacy policy**: the URL above.
- **Ads**: no ads.
- **App access**: the whole app sits behind a sign-in to the user's own server, so reviewers need credentials for a reachable Jellyfin server that has music. ⬜ Owner to provide: a server URL, username and password, and a note that says "enter the server address, then sign in".
- **Content rating** (IARC questionnaire): a music player with no user-generated content, no chat and no purchases. The app streams the user's own library. The expected result is Everyone / PEGI 3.
- **Target audience**: 18+ (or 13+). Not designed for children.
- **Data safety**:
  - Data collected: **none**. Data shared: **none**.
  - Rationale: data only goes to a server the user runs and chooses. The developer and third parties receive nothing.
  - Encrypted in transit: depends on the user's server. Answer "No", because plain http to a LAN server is allowed.
  - Deletion: there is nothing held by the developer to delete; uninstalling clears the phone.
- **Foreground service declaration**: type `mediaPlayback`. Use: music keeps playing with the screen off or the app in the background, started by the user pressing play. Play asks for a short video showing this: start playback, lock the phone, and show the notification controls.
- **Government / financial / health / news**: not applicable.

## Release

1. Set `VERSION` to `1.0.0` (versionCode 10000) and update the CHANGELOG.
2. Build the bundle as above and check its signer.
3. Upload it to an **internal testing** track first, install it from Play on the phone, and check sign-in, playback, downloads and the notification.
4. Promote it to production.
