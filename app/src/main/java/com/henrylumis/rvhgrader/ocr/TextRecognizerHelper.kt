package com.henrylumis.rvhgrader.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs on-device text recognition (ML Kit) on a captured photo. This replaces the
 * Tesseract.js-in-the-browser approach from the original web app; it's faster, more accurate,
 * and needs no internet connection or CDN script.
 */
suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val image = InputImage.fromBitmap(bitmap, 0)

    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            continuation.resume(visionText.text)
        }
        .addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
        .addOnCompleteListener {
            recognizer.close()
        }
}
