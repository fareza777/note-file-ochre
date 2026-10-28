# Ochre Privacy Policy

**Effective date: 27 October 2026**

Ochre is a notebook app that keeps your writing on your device. This policy explains what
the app does with information, in plain language.

## Summary

- Ochre does **not** collect, transmit, sell or share personal data.
- There are **no** accounts, analytics, advertising, or crash-reporting SDKs.
- Notes, tags, attachments and settings are stored only in the app's private storage on
  your device.
- Network access is used **only** when you configure optional encrypted WebDAV sync.
- Everything else works fully offline.

## What is stored on your device

| Data | Where | Purpose |
| --- | --- | --- |
| Notes, tags, links, reminders | App-private SQLite database (Room) | Your notebook |
| Photos, voice memos, files | App-private `files/attachments/` | Attachments you add |
| Preferences (theme, lock timeout, sync server, onboarding) | App-private DataStore | App behaviour |
| Encrypted backups | The location you choose (`.ochre` file) | Backup and restore |
| Markdown exports | The location you choose (`.zip`) | Portable export |

Uninstalling Ochre removes the app's private data. Backups you exported remain wherever
you saved them.

## Permissions and why they are used

| Permission | When it is requested | What it is used for |
| --- | --- | --- |
| `RECORD_AUDIO` | When you tap the voice-note button | Recording audio you choose to attach to a note; nothing is recorded in the background |
| `POST_NOTIFICATIONS` | When you set your first reminder | Reminder notifications and their inline actions |
| `INTERNET` | Never prompted | Optional WebDAV sync to a server **you** configure; no other network calls |
| `VIBRATE` | Never prompted | Haptic feedback for reminders |
| Biometric / device credential | When you enable App lock | Unlocking Ochre through the system biometric prompt. Ochre never receives your fingerprint, face data or PIN |

Photos and files are selected through the Android system photo picker and document
picker. Ochre receives only the items you pick.

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

Ochre is a general-purpose note-taking tool. It does not knowingly collect any personal
information from anyone, including children.

## Changes to this policy

If the policy changes, the effective date above is updated. Material changes will be
described in the app's release notes.

## Contact

Questions about privacy can be sent to the developer contact address listed on Ochre's
Google Play listing.