# Ochre

**A quieter place for your thoughts.**

Ochre is a local-first Markdown notebook for Android. Notes stay on the device, open in
milliseconds, and remain readable as plain files you own. No account, no analytics, and no
network calls unless you enable optional sync.

The product and data model are inspired by [usememos/memos](https://github.com/usememos/memos)
(MIT). Ochre is an independent Kotlin/Jetpack Compose implementation; it does not bundle
Memos source code.

## Features

- **Quick notes** — capture from the app, the Quick Settings tile, home-screen widget,
  launcher shortcuts (`New note`, `Search`, `Echo`) or share text from other apps.
- **Timeline** — day-grouped cards, newest/oldest sorting, pinning, multi-select with bulk
  pin, archive, delete and tag, trash with restore, media and reminder filters, tag
  browsing.
- **Markdown editor** — headings, bold/italic/strike, nested lists, checklists (tappable
  in preview), quotes, code, GFM tables, links, tags and `[[wiki links]]`; find & replace
  with match highlighting, a heading outline for quick jumps, duplicate note, and
  autosave with character/word feedback. The bottom bar is grouped into three labeled
  menus (Format, Lists, Insert) instead of a wall of icons.
- **Smart search** — full-text search over content and titles (SQLite FTS4) plus a small
  query language: `tag:studio`, `is:pinned`, `has:image`, `has:audio`,
  `before:2026-09-01`, `after:…`, `on:…`.
- **Attachments** — photos and arbitrary files from the phone, voice memos recorded
  in-app with a waveform preview, and finger sketches drawn on a full-screen pad
  (colors, three pen sizes, eraser, undo, clear) that save into the note as images.
  A full-screen viewer offers pinch-zoom and share.
- **Threads** — wiki links resolve to notes; every note shows *Links out* and
  *Mentioned in* backlinks.
- **Echo** — gentle resurfacing of older notes so ideas do not sink.
- **Capsules** — seal a note until a future date, then let the reminder open it.
- **Reminders** — per-note reminders with snooze/done actions and deep links back to the
  note; notifications are scheduled with WorkManager.
- **Encrypted vault** — export/restore a single `.ochre` archive encrypted with
  PBKDF2-HMAC-SHA256 (120,000 iterations) and AES-256-GCM. Attachments and settings are
  included; restore merges by UID and never duplicates notes.
- **Markdown export** — a zip of `notes/*.md` with YAML front matter, readable anywhere.
- **Optional sync** — user-supplied WebDAV server, end-to-end encrypted before upload,
  scheduled or manual.
- **Privacy lock** — biometric (or device credential) gate, with configurable re-lock
  timeout and a locked-notes view in Recents.
- **Widget, tile and shortcuts** — a RemoteViews notebook widget with a *Write* button, a
  `New note` tile, and static launcher shortcuts.
- **Material 3 design** — warm light theme, true dark theme, Fraunces/Inter typography,
  responsive motion, edge-to-edge layout, dynamic color disabled to keep the Ochre
  identity consistent.

## Architecture

Single-module Android app, no DI framework. `OchreApplication` builds an `AppContainer`
that owns the database, repositories, settings, backup and sync services. Screens are
Compose functions that receive a `ViewModel` created by a small factory
(`ochreViewModel`), with `LocalAppContainer` for deep components.

```
app/src/main/java/app/ochre/notes/
├── MainActivity.kt            edge-to-edge host, deep links, splash, biometric gate
├── OchreApplication.kt        container creation
├── AppContainer.kt            manual DI: db, repository, settings, backup, sync
├── data/
│   ├── NoteRepository.kt      CRUD, FTS feed, tags, links, trash, attachments
│   ├── local/                 Room entities/DAOs, FTS4 triggers, migrations
│   ├── backup/                encrypted vault + Markdown export/restore
│   ├── sync/                  WebDAV client, E2E payloads, scheduler
│   ├── media/                 attachment storage, image downscaling, EXIF strip
│   ├── security/              biometric helper, encrypted preferences
│   └── settings/              DataStore-backed user settings
├── domain/                    Markdown parsing, search query language, Echo planner
├── notifications/             reminder scheduling, worker, notifier, actions
├── ui/                        Compose screens (timeline, editor, search, echo, …)
└── widget/                    app widget provider and Quick Settings tile
```

### Data

- **Room 2.7** with an FTS4 virtual table kept in sync by triggers. Search queries hit
  FTS and fall back to `LIKE` for short prefixes.
- **DataStore Preferences** for settings (theme, lock timeout, sync config, onboarding).
- **Files** under `files/attachments/`; originals are copied, images downscaled and EXIF
  metadata stripped before storage.
- **WorkManager** for reminders and auto-sync; unique work names prevent duplicate jobs.

## Build

Requirements: JDK 17, Android SDK 36, Gradle wrapper (included).

```bash
./gradlew :app:assembleDebug          # debug APK (applicationId app.ochre.notes.debug)
./gradlew :app:assembleRelease        # signed release APK (requires keystore.properties)
./gradlew :app:bundleRelease          # Play-ready AAB
./gradlew :app:testDebugUnitTest      # JVM tests
./gradlew :app:connectedDebugAndroidTest  # instrumented tests (device/emulator)
```

### Signing

`app/build.gradle.kts` reads `keystore.properties` at the repository root:

```properties
storeFile=keystore/ochre-upload.jks
storePassword=…
keyAlias=ochre
keyPassword=…
```

If the file is absent the release build stays unsigned, so CI forks can still build.
**Back up the keystore and passwords before publishing.** Losing the upload key means
losing the ability to update the app on Google Play (Play App Signing can help, but the
upload key itself must be recoverable).

## Testing

- 41 JVM tests cover Markdown parsing (including outlines), the search query language,
  Echo planning, vault cryptography, sync indexing and picker date conversion.
- Instrumented tests (8) cover encrypted vault round-trip and merge behavior against an
  in-memory SQLite database.
- Manual QA on a 1080×2400 device covers onboarding, recording/playback, reminders and
  notifications, widget, tile, shortcuts, biometric lock, backup/restore and deep links.
  Artifacts live in `qa/`.

## Store assets

- `store/art/icon-512.png` — Play icon (512×512)
- `store/art/feature-1024x500.png` — feature graphic
- `store/screenshots/` — phone screenshots
- `docs/PLAY_STORE_LISTING.md` — listing copy and data-safety answers

## Privacy

Ochre does not collect, transmit or sell any data. There is no analytics, crash reporting
or advertising SDK. See [PRIVACY.md](PRIVACY.md) for the full policy and the permission
list.

## License and attribution

- App code and assets: © 2026 Ochre. All rights reserved.
- Product inspiration and data-model concepts: [usememos/memos](https://github.com/usememos/memos),
  MIT License. No Memos source code is included in this repository.
- **Fraunces** typeface: © The Fraunces Project Authors, SIL Open Font License 1.1
  (`fonts-fraunces-OFL.txt`).
- **Inter** typeface: © The Inter Project Authors, SIL Open Font License 1.1
  (`fonts-inter-OFL.txt`).
- Material Symbols / Material Icons: Apache License 2.0, via `androidx.compose.material`.
- AndroidX, Kotlin, OkHttp, Coil, kotlinx.serialization: their respective licenses
  (Apache 2.0 / MIT / BSD), consumed as unmodified dependencies.