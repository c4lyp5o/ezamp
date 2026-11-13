# ezamp privacy policy

Effective 2026-11-13. Applies to ezamp for Jellyfin on Android (`one.calypsocloud.ezamp`).

## The short version

ezamp collects nothing. It talks to one server, the Jellyfin server you sign in to, and nothing else. There are no analytics, no crash reporting, no ads and no accounts with us.

## What the app sends, and to whom

Everything goes to **your Jellyfin server**, at the address you type in, and nowhere else:

- your username and password, once, to sign in (the server answers with an access token);
- requests for your library, artwork and audio;
- playback reports (what is playing, its position, play/pause/stop) and favourite changes, the same as any Jellyfin client sends, so the server can track what you have played;
- a device name (your phone's model), a random id made on install, and the app version, so the server can list ezamp among your devices.

The developer of ezamp does not run that server, cannot see that traffic, and receives no data from the app.

## What stays on your phone

- The access token, the server address and your settings, in the app's private storage.
- A copy of your library index (album, track, artist and genre names), local playlists, the play queue and any downloaded audio.

None of this leaves the phone, except through Android's own backup if you have it on. ezamp's backup rules leave out the access token and downloads: a restored phone signs in again, and keeps your local playlists.

Signing out removes the token and the library index. Uninstalling removes everything.

## Permissions

- **Internet / network state**: to reach your server and notice when you go offline.
- **Notifications and foreground service (media playback)**: to keep playing with the screen off and show the player controls.
- **Wake lock**: so playback does not stop when the phone sleeps.

## Children

ezamp is not aimed at children and collects no data from anyone.

## Changes

Changes to this policy are made in this file; its history is the record.

## Contact

Open an issue at https://github.com/c4lyp5o/ezamp/issues.
