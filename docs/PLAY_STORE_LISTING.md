# Google Play listing — Ochre

Everything needed to publish the first release. Fill the two bracketed fields
(`[SUPPORT EMAIL]`, `[PRIVACY URL]`) when the Play Console account is ready.

## Identity

| Field | Value |
| --- | --- |
| App name (30 char max) | `Ochre — Notes & Markdown` |
| Package | `app.ochre.notes` |
| Category | Productivity |
| Tags | notes, markdown, offline, journal, privacy |
| Default language | English (US) |
| Content rating | Everyone |
| Ads | No |
| In-app purchases | No |
| Price | Decide at publish time; the APK contains no billing code |

## Short description (80 char max)

`A quieter place for your thoughts. Offline notes, Markdown, voice, reminders.`

## Full description (4000 char max)

```
A quieter place for your thoughts.

Ochre is a notebook that lives on your device. Open it and write — no account, no
subscription, no feed. Notes stay on your phone, open instantly, and can be exported as
plain Markdown files you own.

WRITE THE WAY YOU THINK
• Quick capture from the app, home-screen widget, Quick Settings tile or launcher shortcut
• Autosaving Markdown editor: headings, bold, italic, lists, checklists, quotes, code
• Tags and [[wiki links]] that turn related notes into threads
• Photos and voice memos attached to any note
• Timeline grouped by day, with pinning, archive and a recoverable trash

FIND THINGS AGAIN
• Full-text search that works offline
• A small query language when you need precision: tag:studio, is:pinned, has:voice,
  before:2026-09-01
• Browse by tag, media or reminders

REMEMBER GENTLY
• Per-note reminders with snooze and done actions
• Capsules: seal a note until a future date, then meet it again
• Echo: quiet resurfacing of older notes so good ideas do not sink

KEEP IT YOURS
• Encrypted vault backups (AES-256-GCM) you can restore on a new phone
• Markdown export: a zip of .md files with front matter
• Optional end-to-end encrypted WebDAV sync to a server you choose
• App lock with biometrics or your device PIN
• No analytics, no tracking, no advertising SDKs
• 100% offline by default — the internet permission is only used if you enable sync

DESIGNED TO FEEL CALM
• Material 3 with a warm ochre palette, Fraunces and Inter typography
• True dark mode that follows your system
• Considered motion, edge-to-edge layout and comfortable reading measure

Ochre is built for the moment between two things — a thought, a sketch, a line you do
not want to lose. It opens fast, stays out of the way, and never phones home.
```

## Graphics

| Asset | File | Spec |
| --- | --- | --- |
| App icon | `store/art/icon-512.png` | 512×512 PNG, no alpha |
| Feature graphic | `store/art/feature-1024x500.png` | 1024×500 PNG |
| Phone screenshots | `store/screenshots/01…06*.png` | 1080×2138 PNG, min 2, max 8 |

Screenshot order and captions for the listing:

1. `01_timeline.png` — **Your notebook, at a glance.** Timeline with tags and greeting.
2. `02_markdown.png` — **Markdown that stays readable.** Note with heading and bullet list.
3. `03_threads.png` — **Notes that link to each other.** *Links out* and *Mentioned in*.
4. `04_search.png` — **Find any thought.** Query results with highlighted matches.
5. `05_photo_note.png` — **Photos and voice, kept with the note.**
6. `06_settings.png` — **Private by design.** Security and backup settings.

## Data safety form

Answer exactly as follows (the app is local-first and has no developer backend):

- Does your app collect or share any of the required user data types? **No.**
- Is all of the user data collected by your app encrypted in transit? *Not applicable*
  (no data leaves the device unless the user configures their own WebDAV server; that
  transfer is end-to-end encrypted by the app).
- Do you provide a way for users to request that their data is deleted? **Not applicable**
  — data never leaves the device; uninstalling removes it. Backups are user-controlled.
- Data types: none selected.
- Security practices: data is encrypted at rest in backups (AES-256-GCM); users can
  export and delete all data at any time from Settings → Data.

## App access

- All functionality is available without login. Choose **All functionality is available
  without special access**.
- No credentials are required for review.

## Content rating questionnaire

- Violence, sexuality, language, controlled substances: **No** for all.
- User-generated content sharing: **No** — notes never leave the device except to storage
  locations the user picks, and there is no social surface.
- Ads: **No**.
- Expected rating: Everyone / 3+.

## Release notes (version 1.0.1)

```
This update makes writing richer and faster.

Markdown preview now renders GFM tables with per-column alignment and nested bullet
lists. The editor gains find & replace with live match highlighting, a heading outline
for jumping through long notes, and one-tap duplicate (attachments included). Multi-select
in the timeline can now add a tag to several notes at once.
```

## Publishing checklist

1. Create the Play Console app with package `app.ochre.notes` and enroll in Play App
   Signing. Upload `app/build/outputs/bundle/release/app-release.aab`.
2. Back up `keystore/ochre-upload.jks` and the passwords in `keystore.properties`
   somewhere safe and offline. Without the upload key you cannot ship updates.
3. Paste the short/full description, upload the icon, feature graphic and screenshots.
4. Complete Data safety exactly as above, content rating, and target audience.
5. Add `[PRIVACY URL]` (host `PRIVACY.md`) and `[SUPPORT EMAIL]`; both are mandatory.
6. Run an internal testing track with a signed AAB, verify install from Play on a clean
   device, then promote to production.

## Pre-publish verification

- `./gradlew :app:testDebugUnitTest` — green
- `./gradlew :app:connectedDebugAndroidTest` — green on a device/emulator
- `./gradlew :app:bundleRelease` — builds a signed AAB
- `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` — shows
  `CN=Ochre Notes`
- Fresh install: onboarding, create/edit note, reminder fires, backup/restore, biometric
  lock, widget and tile all work offline (airplane mode).