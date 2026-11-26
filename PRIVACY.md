# Lemuroid-S Privacy Policy

_Last updated: 26 November 2026_

Lemuroid-S is a free, open-source emulator for Android. It has no ads, no analytics and no tracking,
and it does not collect, store or share any personal data. This page explains what the app does with
your device and the internet.

## Data collected

None. Lemuroid-S has no account system and no servers of its own, and it sends no information about you,
your device or your games to the developer or to anyone else.

## Your files

Your games, saves, save states, BIOS files and covers stay on your device, in the folders you choose
(the Lemuroid-S folder and, optionally, a ROMs folder). Lemuroid-S only reads and writes the folders you
pick with Android's folder picker. It does not ask for "All files access". On some Android TV devices
without a folder picker, it uses the older storage permission to read your ROMs folder instead.

## Internet connections

Lemuroid-S connects to the internet only for these things:

- **Emulator cores.** Cores that are not built into the app are downloaded when a game needs them: from
  Google Play (Google Play version) or from the Lemuroid-S repository on GitHub (other versions).
- **Covers.** Box art, screenshots and title screens are downloaded from the libretro thumbnails server
  (thumbnails.libretro.com) and its copy on GitHub (raw.githubusercontent.com), based on the game's
  name. Downloaded covers are saved in your Lemuroid-S folder, so each one is downloaded only once.
- **Google Drive save sync (optional, Google Play version only).** If you turn it on, Lemuroid-S copies
  your saves and save states to a private app folder in your own Google Drive and back. It can only see
  the files it created there, not the rest of your Drive. Sign-in is handled by Google Play services. You
  can turn it off at any time in the app, which also withdraws Lemuroid-S's access, or remove access in
  your Google Account settings.
- **Rating prompt (Google Play version only).** Google Play may occasionally show its standard "rate this
  app" prompt. This is handled entirely by Google Play.

These services see the usual information any internet request includes, such as your IP address. Their
own privacy policies apply: [GitHub](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement),
[Google](https://policies.google.com/privacy), [libretro](https://www.libretro.com/).

## Permissions

- **Internet and network state:** for the downloads above.
- **Notifications and foreground service:** to show progress, and keep working, while scanning games,
  importing saves or saving a game in the background.
- **Storage (older Android versions and some Android TV devices only):** to read your ROMs folder where
  Android's folder picker isn't available.
- **Android TV home screen:** to show your recently played games as a channel on the TV home screen.
- **Microphone (optional):** only for Nintendo DS games that use it. Audio is passed to the game and is
  never recorded or sent anywhere.
- **Vibration:** for touch-control and rumble feedback.

## Children

Lemuroid-S does not collect data from anyone, including children.

## Changes

Changes to this policy will be published on this page, with a new date at the top.

## Contact

Questions about this policy: open an issue at https://github.com/DCaedus/Lemuroid-S/issues
