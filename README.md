# RVH Grader — Android (Kotlin)

A native Android port of the R_V_H_Grader web app, built with Kotlin + Jetpack Compose.

## What's included
- **Lower Primary / Upper Primary** mode toggle — same subject lists and layout as the original.
- **Photo scanning**: tap "INGEST PHOTO" to launch the device camera, then on-device OCR
  (Google ML Kit Text Recognition) auto-fills the learner name and subject marks it can
  confidently match — same alias-matching logic as the original (e.g. "MATHEMATICS"/"MATH"/"MTC"
  all map to the MTC field). Works fully offline, no CDN/Tesseract dependency.
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
│   ├── TextRecognizerHelper.kt   # ML Kit on-device OCR
│   ├── ImageUtils.kt             # EXIF-aware image loading (keeps photos upright for OCR)
│   └── OcrFieldParser.kt         # Subject alias matching + name extraction
└── ui/
    ├── PinGate.kt                # Local PIN screen
    └── Dashboard.kt              # Mode toggle, scanner card, form, standings list
```

## Possible next steps
- Persist records locally (Room) so data survives app restarts.
- Export standings to PDF/CSV to share with school administration.
- Multi-class / multi-term support if you're tracking more than one class at a time.

## ⚠️ Known gap: class-list photos with several students
Earlier, on the web version, we established the real photos are **class lists — many students'
names and marks in one handwritten table** — and built a "scan → review each detected row →
correct mistakes → commit all" flow for that. This native rewrite's `OcrFieldParser.kt` is
currently a straight port of the *older, single-student* logic (one photo → fills one form).
It'll still work if you photograph one learner's sheet at a time, but it won't split a class-list
photo into multiple rows yet. ML Kit actually makes this easier than Tesseract did — its result
gives per-line bounding boxes for free (`visionText.textBlocks`), which is exactly what's needed
to cluster the table into rows/columns. Say the word and I'll port that logic + the review screen
over next.

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
