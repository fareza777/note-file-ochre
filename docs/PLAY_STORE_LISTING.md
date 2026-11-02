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
| Ads | Yes — Google AdMob (test units in this build) |
| In-app purchases | Yes — one-time "Remove ads" product, $4.99 |
| Price | Free with ads; $4.99 one-time to remove ads |

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
• Small banner ads keep Ochre free; remove them permanently with a $4.99 one-time purchase,
  or watch a rewarded ad to hide them for 24 hours
• Your notes stay on-device; only ads, the purchase and optional sync use the network

DESIGNED TO FEEL CALM
• Material 3 with a warm ochre palette, Fraunces and Inter typography
• True dark mode that follows your system
• Considered motion, edge-to-edge layout and comfortable reading measure

Ochre is built for the moment between two things — a thought, a sketch, a line you do
not want to lose. It opens fast, stays out of the way, and your writing only leaves the
phone when you ask it to.
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

The app itself has no developer backend and never sends note content. Google AdMob is used
for advertising; answer the form from Google's current AdMob data-safety guidance:

- Does your app collect or share any of the required user data types? **Yes**, through the
  AdMob SDK.
- Device or other IDs: collected and shared for advertising, marketing and measurement.
- Approximate location: collected and shared for advertising (derived from IP address).
- App activity (app interactions): collected and shared for advertising and measurement.
- Note content, photos, voice memos, tags and search history are never collected or shared
  by Ochre.
- Is all data encrypted in transit? **Yes** — ads use HTTPS and WebDAV sync is encrypted by
  the app before upload.
- Data deletion: users delete notes in the app or uninstall it; the advertising ID can be
  reset or deleted in Android Settings.
- Data collection is not required to use the app, but ads are shown unless you buy Remove
  ads or watch a rewarded ad for 24 hours without banners.
- In-app purchase: payments are processed by Google Play. Ochre receives only the product
  ID and purchase token to verify entitlement and stores a local yes/no flag; it never
  receives payment details or billing address.

## App access

- All functionality is available without login. Choose **All functionality is available
  without special access**.
- No credentials are required for review.

## Content rating questionnaire

- Violence, sexuality, language, controlled substances: **No** for all.
- User-generated content sharing: **No** — notes never leave the device except to storage
  locations the user picks, and there is no social surface.
- Ads: **Yes**.
- Expected rating: Everyone / 3+.

## Release notes (version 1.0.5)

```
You can now remove ads for good.

A single $4.99 purchase turns off the banner and interstitial permanently, and the app
remembers it across reinstalls through your Google Play account. The free rewarded option
still hides ads for 24 hours. Notes stay on your device as always.
```

## Release notes (version 1.0.4)

```
Ochre is now supported by ads.

A small banner sits above the tab bar on the timeline, clear of the system navigation
buttons, and an interstitial may appear after closing several notes. In Settings you can
watch a rewarded ad to hide banners for 24 hours. Notes, photos and voice memos are never
shared with the ad SDK. This build uses Google's test ad units.
```

## Release notes (version 1.0.3)

```
This update helps you organise, capture and share your notes.

Notes can now live in coloured notebooks: create one from the timeline, move a note from
the editor, and tap a notebook to see only its notes. Each note can also carry its own
accent colour. Three starter templates — daily note, task list and journal — are one tap
away in a blank note, and the camera can add a photo straight from the Insert menu.
Photos and files now sit in a tidy two-column gallery. Any note can be exported as a
styled PDF and shared from the editor, and Markdown previews have a clearer hierarchy.

Existing notes and settings carry over automatically.
```

## Release notes (version 1.0.2)

```
This update makes notes more visual and the editor easier to use.

You can now attach photos and any file from your phone, and draw sketches right inside
a note with a full-screen pad: pick a color and pen size, erase, undo, and the drawing
saves as an image in the note. The editor's bottom bar has been simplified into three
labeled menus — Format, Lists and Insert — so every tool is easy to find.
```

## Publishing checklist

1. Create the Play Console app with package `app.ochre.notes` and enroll in Play App
   Signing. Upload `app/build/outputs/bundle/release/app-release.aab`.
2. Create the one-time product `remove_ads` under **Monetize → Products → In-app
   products**, set its price to **$4.99 USD** (Play converts it per market), and activate
   it. The ID must match `BillingConfig.REMOVE_ADS_PRODUCT_ID`; the app shows Google's
   formatted local price, with `$4.99` only as a placeholder until Play responds.
3. Back up `keystore/ochre-upload.jks` and the passwords in `keystore.properties`
   somewhere safe and offline. Without the upload key you cannot ship updates.
4. Paste the short/full description, upload the icon, feature graphic and screenshots.
5. Complete Data safety exactly as above, content rating, and target audience. Declare
   in-app purchases in the pricing section.
6. Add `[PRIVACY URL]` (host `PRIVACY.md`) and `[SUPPORT EMAIL]`; both are mandatory.
7. Before production, replace the test banner/interstitial/rewarded unit IDs in
   `AdConfig.kt` and the test App ID in `AndroidManifest.xml` with live AdMob IDs, and add
   the Google UMP consent flow for EEA/UK users.
8. Run an internal testing track with a signed AAB, add your account as a license tester,
   buy `remove_ads` with a test card, and confirm the banner and interstitial stay off
   after reinstalling. Then promote to production.

## Pre-publish verification

- `./gradlew :app:testDebugUnitTest` — green
- `./gradlew :app:connectedDebugAndroidTest` — green on a device/emulator
- `./gradlew :app:bundleRelease` — builds a signed AAB
- `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` — shows
  `CN=Ochre Notes`
- Fresh install: onboarding, create/edit note, reminder fires, backup/restore, biometric
  lock, widget and tile all work offline (airplane mode); ads simply do not appear.
- Billing (only after the Play Console product exists): purchase `remove_ads` as a license
  tester, confirm the banner and interstitial stop and the entitlement survives a restart
  and reinstall. Before that, the Buy button reports that Google Play is not ready.