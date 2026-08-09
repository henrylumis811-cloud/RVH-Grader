# RVH Grader — Android (Kotlin)

A native Android port of the R_V_H_Grader web app, built with Kotlin + Jetpack Compose.

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
├── MainActivity.kt              # App entry point, wires state + camera + OCR together
├── model/Models.kt               # SystemMode, StudentRecord, SubjectScore
├── grading/GradingLogic.kt       # Aggregate/division rules, record building
├── ocr/
│   ├── TextRecognizerHelper.kt   # ML Kit on-device OCR (returns word-level bounding boxes)
│   ├── ImageUtils.kt             # EXIF-aware image loading (keeps photos upright for OCR)
│   ├── ClassListParser.kt        # Row/column clustering — splits a class-list photo into per-learner rows
│   └── PendingRow.kt             # Editable row state for the Review & Correct screen
└── ui/
    ├── PinGate.kt                # Local PIN screen
    └── Dashboard.kt              # Mode toggle, scanner card, review panel, form, standings list
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
