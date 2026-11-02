# Ochre Privacy Policy

**Effective date: 2 November 2026**

Ochre is a notebook app that keeps your writing on your device. This policy explains what
the app does with information, in plain language.

## Summary

- Your notes, attachments and settings stay on your device.
- There are **no** accounts, analytics, or crash-reporting SDKs.
- The free app shows ads from Google AdMob, which may collect a device identifier and ad
  interaction data to serve and measure ads. Note content is never sent to advertisers.
- Network access is used only for ads and optional encrypted WebDAV sync.
- Writing, editing and search work fully offline.

## What is stored on your device

| Data | Where | Purpose |
| --- | --- | --- |
| Notes, tags, links, reminders | App-private SQLite database (Room) | Your notebook |
| Photos, voice memos, files | App-private `files/attachments/` | Attachments you add |
| Preferences (theme, lock timeout, sync server, onboarding, ad-free time) | App-private DataStore | App behaviour |
| Encrypted backups | The location you choose (`.ochre` file) | Backup and restore |
| Markdown exports | The location you choose (`.zip`) | Portable export |

Uninstalling Ochre removes the app's private data. Backups you exported remain wherever
you saved them.

## Permissions and why they are used

| Permission | When it is requested | What it is used for |
| --- | --- | --- |
| `RECORD_AUDIO` | When you tap the voice-note button | Recording audio you choose to attach to a note; nothing is recorded in the background |
| `POST_NOTIFICATIONS` | When you set your first reminder | Reminder notifications and their inline actions |
| `INTERNET` | Never prompted | Requesting ads and optional WebDAV sync to a server **you** configure |
| `ACCESS_NETWORK_STATE` | Never prompted | Lets the ad SDK check connectivity before requesting an ad |
| `VIBRATE` | Never prompted | Haptic feedback for reminders |
| Biometric / device credential | When you enable App lock | Unlocking Ochre through the system biometric prompt. Ochre never receives your fingerprint, face data or PIN |

Photos and files are selected through the Android system photo picker and document
picker. Ochre receives only the items you pick.

## Advertising

Ochre is free and supported by ads served by Google AdMob. The timeline shows a small
banner above the tab bar, an interstitial may appear after closing several notes, and
Settings offers an optional rewarded ad that hides banners for 24 hours.

Ochre never sends note titles, note content, tags, attachments, search queries or your
sync credentials to the ad SDK. Ad requests may include technical information such as
your IP address, device model, operating system version, app version and advertising
identifier. Google processes that data under its own privacy policy
(<https://policies.google.com/technologies/ads>). You can reset or delete your advertising
ID in Android Settings. This build uses Google's test ad units; production builds will use
live AdMob units.

## Optional WebDAV sync

If you enable sync, Ochre uploads an end-to-end encrypted archive to the WebDAV server
whose address and credentials you enter. Encryption (PBKDF2-HMAC-SHA256 key derivation
with 120,000 iterations, AES-256-GCM) happens on your device before upload. The server
operator cannot read your notes. You can disable sync at any time; disabling it does not
delete notes on your device.

Ochre has no server of its own and does not proxy, inspect or retain sync data.

## Backups

Backups are written as a single encrypted `.ochre` archive. The passphrase is chosen by
you and is never stored or transmitted. If you lose the passphrase, the backup cannot be
recovered — by design.

## Children

Ochre is a general-purpose note-taking tool and is not directed at children under 13. It
does not knowingly collect personal information from children.

## Changes to this policy

If the policy changes, the effective date above is updated. Material changes will be
described in the app's release notes.

## Contact

Questions about privacy can be sent to the developer contact address listed on Ochre's
Google Play listing.