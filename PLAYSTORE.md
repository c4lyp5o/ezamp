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
- **Contact email** (shown publicly): `calypso.hellhammer@gmail.com`.
- **Privacy policy URL**: `https://github.com/c4lyp5o/ezamp/blob/master/PRIVACY.md` (works once the repo is public).

## Graphics

| Asset | Spec | State |
| --- | --- | --- |
| App icon | 512×512 PNG, 32-bit | ✅ `dist/play/icon-512.png`, drawn from the launcher icon's geometry and colours |
| Feature graphic | 1024×500 PNG/JPG | ✅ `dist/play/feature-1024x500.png` |
| Phone screenshots | 2–8, 9:16, 1080×1920 | ✅ `dist/play/01…07-*.png`: library, album, search, now playing, playlists, hold-to-add, sound |
| Foreground-service video | short screen capture | ✅ `dist/play/ezamp-background-playback.mp4` (52 s): play → home → shade controls → screen off → lock-screen controls, still playing |

`dist/` is git-ignored; the assets live on the dev box only. Everything was shot on the demo server below, whose music is all CC BY 3.0, so no third-party copyrighted cover art appears in the listing. Attribution, if Play or anyone asks: Softspace "Room With A View" (Musictrade mt012), Nicolas Falcon "Nicolas Falcon" (aaahh011) and Alexandr Katar "Click EP" (Prakteek Sound prak002), all from archive.org netlabels.

## App content (policy forms)

- **Privacy policy**: the URL above.
- **Ads**: no ads.
- **App access**: all functionality is behind a sign-in to a Jellyfin server. Give reviewers the demo server:
  - Server address: `https://media-test.calypso.dedyn.io`
  - Username: `reviewer`. Password: in `~/.hermes/secrets/jellyfin-test.env` (`JF_REVIEWER_PASS`), never in the repo.
  - Instructions: "Enter the server address and tap NEXT, then sign in with the username and password. The library has 3 albums of Creative Commons music."
  - The demo server is a temporary Jellyfin 12.4.0 container on the home box (`~/jellyfin-test`, behind traefik, CNAME on deSEC). **Keep it running until review passes**, and through any later review: Google re-checks updates.
- **Content rating** (IARC questionnaire): a music player with no user-generated content, no chat and no purchases. The app streams the user's own library. The expected result is Everyone / PEGI 3.
- **Target audience**: 18+ (or 13+). Not designed for children.
- **Data safety**:
  - Data collected: **none**. Data shared: **none**.
  - Rationale: data only goes to a server the user runs and chooses. The developer and third parties receive nothing.
  - Encrypted in transit: depends on the user's server. Answer "No", because plain http to a LAN server is allowed.
  - Deletion: there is nothing held by the developer to delete; uninstalling clears the phone.
- **Foreground service declaration**: type `mediaPlayback`. Use: music keeps playing with the screen off or the app in the background, started by the user pressing play. Video: `dist/play/ezamp-background-playback.mp4`. Upload it to YouTube (unlisted) or Drive and paste the link, since Play asks for a URL.
- **Government / financial / health / news**: not applicable.

## Release

1. ✅ `VERSION` is `1.0.0` (versionCode 10000).
2. Build the bundle as above and check its signer.
3. Upload it to an **internal testing** track first, install it from Play on the phone, and check sign-in, playback, downloads and the notification.
4. Promote it to production.
