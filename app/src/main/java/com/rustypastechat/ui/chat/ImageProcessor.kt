package com.rustypastechat.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val MAX_DIMENSION = 1920
        const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2MB
        const val JPEG_QUALITY = 85
    }

    fun compressImage(
        inputUri: Uri,
        outputFile: File,
        maxDimension: Int = MAX_DIMENSION,
        jpegQuality: Int = JPEG_QUALITY
    ): Result<File> {
        return try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return Result.failure(Exception("Cannot open image"))

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            var bitmap = loadScaledBitmap(inputUri, options, maxDimension)
            bitmap = fixOrientation(inputUri, bitmap)

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outputStream)

            var bytes = outputStream.toByteArray()
            var quality = jpegQuality
            while (bytes.size > MAX_FILE_SIZE_BYTES && quality > 30) {
                quality -= 10
                outputStream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                bytes = outputStream.toByteArray()
            }

            if (!outputFile.parentFile?.exists()!!) outputFile.parentFile!!.mkdirs()
            FileOutputStream(outputFile).use { it.write(bytes) }
            bitmap.recycle()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadScaledBitmap(uri: Uri, options: BitmapFactory.Options, maxDimension: Int = MAX_DIMENSION): Bitmap {
        val input = context.contentResolver.openInputStream(uri)!!
        val scaleFactor = if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
            val widthRatio = options.outWidth.toFloat() / maxDimension
            val heightRatio = options.outHeight.toFloat() / maxDimension
            maxOf(widthRatio, heightRatio).roundToInt().coerceAtLeast(1)
        } else 1
        val opts = BitmapFactory.Options().apply { inSampleSize = scaleFactor }
        val bitmap = BitmapFactory.decodeStream(input, null, opts)!!
        input.close()
        return bitmap
    }

    private fun fixOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(input)
            input.close()
            val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation == 0f) bitmap
            else {
                val matrix = Matrix().apply { postRotate(rotation) }
                val corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (corrected != bitmap) bitmap.recycle()
                corrected
            }
        } catch (_: Exception) { bitmap }
    }
}
