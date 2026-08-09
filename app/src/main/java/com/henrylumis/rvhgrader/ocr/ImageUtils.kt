package com.henrylumis.rvhgrader.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Loads a bitmap from a content Uri and rotates it upright based on EXIF orientation.
 * Phone cameras often save photos "sideways" with a rotation flag rather than physically
 * rotating pixels, which otherwise confuses OCR.
 */
fun loadUprightBitmap(context: Context, uri: Uri): Bitmap {
    val original = context.contentResolver.openInputStream(uri).use { stream ->
        BitmapFactory.decodeStream(stream)
    } ?: throw IllegalStateException("Could not decode captured image")

    val rotationDegrees = context.contentResolver.openInputStream(uri).use { stream ->
        val exif = ExifInterface(stream!!)
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }

    if (rotationDegrees == 0f) return original

    val matrix = Matrix().apply { postRotate(rotationDegrees) }
    return Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
}
