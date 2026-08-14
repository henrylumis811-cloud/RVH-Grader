# RVH Grader — Android (Kotlin)

A native Android port of the R_V_H_Grader web app, built with Kotlin + Jetpack Compose.

## Icon, theme & branding
- **App icon**: a redesigned "RVH" mark — a single HUD-style ring with reticle tick marks and a
  blocky RVH monogram, on a radial navy glow background, in the app's own cyan/teal palette
  (`app/src/main/res/drawable/ic_launcher_foreground.xml` and `..._background.xml`).
- **Light + dark mode**: `ui/Theme.kt` defines proper light and dark color schemes and follows
  the device's system setting automatically via `isSystemInDarkTheme()` — it's not just one look
  forced on everyone. The native pre-Compose window background/status bar (`values/themes.xml`
  and `values-night/themes.xml`) match too, so there's no flash of the wrong color before Compose
  paints the first frame.
- The header now reads "In dedication to Hellen" as a small line under the main title, and the
  same dedication appears again as a styled header on the opening PIN screen — a ring badge
  echoing the app icon, the mainframe title, a small divider ornament, then the dedication in
  italic underneath (`ui/PinGate.kt`'s `BrandHeader`).

## Big update: classes, terms, exports, history, custom grading & backup
This turned into a proper school-management app rather than a single-class grading tool. What's
new:

**Classes & terms.** Instead of a Lower/Upper Primary toggle, pick a class — Primary One through
Primary Seven — from the dashboard's dropdown. P1-P3 automatically use the Lower Primary
computation (7 subjects, ungraded); P4-P7 use Upper Primary (4 subjects, aggregate + division).
A separate Term dropdown (Term 1/2/3) sits next to it — every learner record is tagged with both,
so the standings list, history, and exports are always scoped to whichever class+term you're
looking at.

**Menu.** Tap the hamburger icon (top-left) for a navigation drawer: Grading (the main dashboard),
Student History, Grading Scale (settings), and Backup & Restore.

**Export.** The share icon on the dashboard exports the *current* class+term's whole class list;
from Student History, exporting a specific past record exports just that one learner's report.
Either way you pick PDF (formatted, print-ready — the "professional report"), Excel (.xlsx), or
CSV, then it hands off to Android's share sheet so you can save it, email it, or send it wherever.
None of the three formats need an extra library — PDF uses Android's built-in `PdfDocument`, CSV
is plain text, and Excel is a small hand-rolled `.xlsx` writer (`export/XlsxExporter.kt`) instead
of pulling in Apache POI, which has a rough history on Android.

**Student History.** A learner's name only needs to be typed/scanned once per class+term — after
that, every record for them (across every class and term they've ever been graded in) shows up
grouped by name, newest first, in the History screen. Tap a learner to expand their record list
and export any single one from there.

**Custom grading scale.** Settings lets you edit both tables that used to be hardcoded: which
mark ranges map to which aggregate (1-9), and which aggregate-sum ranges map to which division
(I-IV). Add/remove bands, save, or reset to the original defaults. New scores use whichever scale
is currently saved; scores already recorded keep whatever was in effect when they were graded.

**Backup & Restore.** Exports the entire gradebook — every learner, every class, every term —
plus the custom grading scale into one JSON file you choose the save location for (so it can be
moved to a new phone, backed up to Drive, etc.), and can restore from that same file later
(with a confirmation before it replaces anything).

**Records now persist automatically.** Everything auto-saves to the app's private storage after
every change, so closing the app (or the phone restarting) no longer loses your data — Backup &
Restore is for deliberate, portable copies on top of that, not the only thing keeping data alive.

## What's included
- **Lower Primary / Upper Primary** mode toggle — same subject lists and layout as the original.
- **Class-list photo scanning**: tap "INGEST PHOTO" to capture a whole handwritten class list —
  on-device OCR (Google ML Kit Text Recognition) splits it into one row per learner, matches marks
  to the right subject column, and hands you an editable **Review & Correct** card to fix anything
  before committing. Works fully offline, no CDN/Tesseract dependency. See "Scanning a class list"
  below for details.
- **Same grading rules**: aggregate scale (90+ = 1 ... below 35 = 9) and PLE-style division
  bands for Upper Primary, ported 1:1 from the JS.
- **Live standings list**, sorted by total score, same as the original leaderboard cards.
- **Basic PIN gate** instead of the original's fake hardware-lock + hardcoded password. You set
  your own PIN on first launch. This is a simple deterrent for a shared device, not real
  security — the PIN is stored locally on the device.

## OCR is permanent — no download, no expiry
The web version relied on Tesseract.js pulled from a CDN, which meant the OCR engine had to be
re-fetched over the internet and only stayed cached as long as the browser felt like keeping it.
This app doesn't have that problem: `TextRecognizerHelper.kt` uses the
**`com.google.mlkit:text-recognition`** artifact — the *bundled* ML Kit variant, not
`com.google.android.gms:play-services-mlkit-text-recognition` (the dynamic one that streams a
model down on first use). The bundled variant compiles the trained recognition model directly
into the APK at build time (adds roughly 4MB to the app size). That means:
- OCR works the instant the app is installed, on the very first scan, with no setup step.
- It never needs network access — there is no `INTERNET` permission in the manifest at all.
- It can't go stale, get evicted from a cache, or fail because of a bad connection — the model
  is just... part of the app, the same as any other compiled resource.

If you ever change that dependency line, double-check you keep the `com.google.mlkit:` prefix
(not the `play-services-mlkit-` one) or you'll lose this guarantee.

## What's different from the web version
- Data is **in-memory only** (same as the original — it resets when the app is closed). If you
  want records to persist between sessions, that's a small addition (a local Room database) —
  just ask.
- No "anti-modder" devtools-blocking tricks — those don't apply to a native app and provided no
  real protection in the browser either.

## Opening the project
1. Install [Android Studio](https://developer.android.com/studio) (Koala or newer recommended).
2. Unzip this project, then in Android Studio choose **Open** and select the `RVHGrader` folder.
3. Let Gradle sync (it will download dependencies — needs an internet connection the first time).
4. Connect an Android phone (USB debugging on) or start an emulator, then click **Run**.

## Requirements
- minSdk 24 (Android 7.0+) — covers effectively all learners' teacher devices in use today.
- Camera permission is requested implicitly via the system camera app when you tap "INGEST PHOTO".

## Project structure
```
app/src/main/java/com/henrylumis/rvhgrader/
├── MainActivity.kt              # Entry point: navigation drawer, all state, persistence, exports
├── model/
│   ├── Models.kt                  # SystemMode, SchoolClass (P1-P7), Term, StudentRecord
│   └── GradingScale.kt            # Editable aggregate/division bands
├── grading/
│   ├── GradingLogic.kt            # Subject lists, record building
│   └── GradingScaleRepository.kt  # Persists the custom grading scale (SharedPreferences+JSON)
├── data/
│   ├── GradebookRepository.kt     # Auto-save/load all records (internal storage JSON)
│   └── BackupManager.kt           # Manual backup/restore to a user-chosen file (SAF)
├── export/
│   ├── ExportManager.kt           # Orchestrates export + hands off to the share sheet
│   ├── CsvExporter.kt
│   ├── PdfExporter.kt             # Built on Android's own PdfDocument, no extra library
│   └── XlsxExporter.kt            # Minimal hand-rolled .xlsx writer, no Apache POI
├── ocr/
│   ├── TextRecognizerHelper.kt    # ML Kit on-device OCR (returns word-level bounding boxes)
│   ├── ImageUtils.kt              # EXIF-aware image loading (keeps photos upright for OCR)
│   ├── ClassListParser.kt         # Row/column clustering — splits a class-list photo into per-learner rows
│   └── PendingRow.kt              # Editable row state for the Review & Correct screen
└── ui/
    ├── Screen.kt                  # Drawer destinations
    ├── PinGate.kt                 # Local PIN screen (stylish dedication header)
    ├── Dashboard.kt               # Class/term dropdowns, scanner, review panel, form, standings
    ├── HistoryScreen.kt           # Every learner's records across classes/terms
    ├── SettingsScreen.kt          # Editable grading scale
    ├── BackupScreen.kt            # Backup/restore UI
    ├── ExportFormatDialog.kt      # Shared PDF/Excel/CSV picker
    └── Theme.kt                   # Light/dark color schemes
```

## Possible next steps
- Persist records locally (Room) so data survives app restarts.
- Export standings to PDF/CSV to share with school administration.
- Multi-class / multi-term support if you're tracking more than one class at a time.

## Scanning a class list (many learners, one photo)
Tap "⚡ INGEST PHOTO" and capture the whole handwritten class list in one shot. `ClassListParser.kt`
clusters ML Kit's word-level results into table rows by vertical position, tries to line marks up
under the right subject column (using the sheet's own header row if it can find one, otherwise
left-to-right order), and applies a few handwriting-digit corrections (O→0, I/L→1, S→5, B→8, Z→2).

None of that gets saved automatically — a **Review & Correct** card appears with one editable row
per detected learner: name field, one box per subject, a checkbox to include/exclude the row, and
a red outline on any row or field that was uncertain (missing a name, a corrected digit, or fewer
marks than expected). Fix what's wrong, uncheck anything that's a false read, then
**COMMIT CHECKED ROWS** grades and adds them all to the standings at once. A fresh scan replaces
whatever was in the review card before, so you won't end up double-committing an old batch.

**Design principle: never show nothing.** The parser classifies each OCR word by whichever
character type dominates it (mostly letters -> name, mostly digits -> score) instead of requiring
a perfect clean match — one noisy character in a word used to silently delete that whole word,
and a row with too few clean words used to get dropped entirely, which made real handwritten
photos come back with "NO ROWS DETECTED" far too often. Now a row only gets skipped outright if
it's a single lone token (almost certainly a stray page/index number); everything else gets a
best-effort attempt, and if literally nothing structured could be pulled out, it falls back to
showing the raw OCR text per line as an editable, heavily-flagged row rather than showing nothing
at all — worst case, it's still less retyping than starting from a blank form.

**Names anchor the rows, numbers get matched to the nearest one.** The previous approach tried
clustering a whole row at once (name + every score, spanning the sheet's full width) by raw
vertical position, then attempted to correct for tilt by calibrating off the header — but on a
photo with no clean header, or where handwriting itself isn't perfectly level, that fell apart:
scores would end up matched to the wrong learner, or a name would get split across two rows,
coming out as fragments instead of full names. The row detection is now name-first: names sit in
a narrow column near the left edge where tilt has far less room to cause damage, so rows are built
from name-like words alone, then every number is matched to whichever name-row it's nearest to —
with vertical tolerance that grows the further right the number sits, absorbing unknown tilt
without needing a header to calibrate against at all.

Handwriting varies a lot school to school — if the row-clustering or digit corrections need
tuning against real samples from your school, send me a couple of photos (or just describe what
it's getting wrong) and I'll adjust `ClassListParser.kt`.

## Pushing to GitHub & building via CI
```bash
cd RVHGrader
git init
git add .
git commit -m "Initial commit: RVH Grader Android app"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```
A GitHub Actions workflow (`.github/workflows/android-build.yml`) is already set up — every push
to `main` builds a debug APK and attaches it to the workflow run as a downloadable artifact.

**Note on the Gradle wrapper**: `gradle-wrapper.jar` is a binary file and isn't committed (see
`.gitignore`). Opening the project in Android Studio regenerates it automatically. Building from
the command line without Android Studio first, run `gradle wrapper --gradle-version 8.7` once
(requires Gradle installed locally) to create it, then use `./gradlew` as normal from then on.
