# Google Play Console: answers for Lemuroid-S

Answers for the Play Console forms, based on what the app does. Keep them in sync with
[PRIVACY.md](../PRIVACY.md).

## App content > Foreground service permissions

Type declared: **Data sync** (`FOREGROUND_SERVICE_DATA_SYNC`).

**Game save service** (`GameService`, shown as a "game running" notification while a game is open):

> While a game is running, Lemuroid-S keeps a foreground service so the game's progress (the game's
> save file and the autosave state) can be written to the user's storage, and copied to their Google
> Drive if they turned on save sync, when they leave the game or switch apps. Stopping it early would
> lose unsaved progress. It stops as soon as saving is done.

**Background tasks** (WorkManager, shown as notifications with progress):

> Scanning the user's ROMs folder to build the game library, importing saves chosen by the user,
> downloading covers and syncing saves with the user's Google Drive. Each task is started by the
> user (or by turning the feature on) and shows its progress in a notification.

Video: record starting a game, leaving it (notification shown, then gone), and a library scan with
its notification.

## App content > Data safety

- Does the app collect or share user data? **No.**
- Data is encrypted in transit: **Yes** (all connections use HTTPS).
- Users can request deletion: not applicable, nothing is collected. Google Drive sync stores files
  in the user's own Drive app folder, which the user controls.
- Note: Google Drive sync sends the user's saves to the user's own Google Drive. Google's guidance
  treats data the user transfers to their own account as not "shared" or "collected" by the app.

## App content > Content rating (IARC questionnaire)

- Category: **Utility, productivity, communication or other** (an emulator with no games included).
- Violence, sexuality, language, controlled substances, gambling: **No** (the app contains no game
  content).
- User interaction / user-generated content / sharing location / digital purchases: **No.**
- Expected rating: Everyone / PEGI 3.

## App content > Target audience

- Age groups: **18 and over** (or 13+). Not designed for children; avoids the Families policy.

## App content > Ads

- Contains ads: **No.**

## App content > Permissions declarations

- `RECORD_AUDIO`: optional, only for Nintendo DS games that use the microphone; requested when
  such a game starts.
- No "All files access", no location, no contacts.

## Store listing

- Texts and images: `fastlane/metadata/android/en-US/`.
- Privacy policy URL: https://github.com/DCaedus/Lemuroid-S/blob/master/PRIVACY.md
- Category: Entertainment (or Tools).

## Release

- Upload `lemuroid-s-<version>.aab` (artifact of the Publish workflow, or `./gradlew bundlePlayDynamicRelease`).
- Use Play App Signing; the upload key is the release key from the Publish workflow secrets.
- Start with an internal test track, then closed testing (new personal developer accounts need 12+
  testers for 14 days before production).
