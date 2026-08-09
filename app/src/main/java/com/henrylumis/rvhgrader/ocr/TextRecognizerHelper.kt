package com.henrylumis.rvhgrader.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs on-device text recognition (ML Kit) on a captured photo. This replaces the
 * Tesseract.js-in-the-browser approach from the original web app; it's faster, more accurate,
 * and needs no internet connection or CDN script.
 *
 * Returns the full [Text] result (not just a flat string) — [ClassListParser] needs the
 * per-word bounding boxes to split a class-list photo into one row per learner.
 */
suspend fun recognizeText(bitmap: Bitmap): Text = suspendCancellableCoroutine { continuation ->
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val image = InputImage.fromBitmap(bitmap, 0)

    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            continuation.resume(visionText)
        }
        .addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
        .addOnCompleteListener {
            recognizer.close()
        }
}
