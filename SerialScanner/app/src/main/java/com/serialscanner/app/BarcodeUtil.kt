package com.serialscanner.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.min

object BarcodeUtil {
    fun newScanner(): BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_39, Barcode.FORMAT_PDF417
            ).build()
    )

    private fun decode(ctx: Context, uri: Uri, maxDim: Int = 3000): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Full-image scan plus overlapping horizontal bands (thin 1D codes need more pixels), then rotations if nothing found. */
    suspend fun scanUri(ctx: Context, uri: Uri): List<String> {
        val scanner = newScanner()
        try {
            val bmp = decode(ctx, uri) ?: return emptyList()
            val found = LinkedHashMap<String, Int>()

            suspend fun run(b: Bitmap, offsetY: Int) {
                for (code in scanner.process(InputImage.fromBitmap(b, 0)).await()) {
                    val v = code.rawValue ?: continue
                    if (v !in found) found[v] = offsetY + (code.boundingBox?.top ?: 0)
                }
            }

            suspend fun pass(b: Bitmap) {
                run(b, 0)
                val h = b.height
                for (y in listOf(0, h / 4, h / 2)) {
                    val hh = min(h / 2, h - y)
                    if (hh > 50) run(Bitmap.createBitmap(b, 0, y, b.width, hh), y)
                }
            }

            pass(bmp)
            if (found.isEmpty()) {
                for (deg in listOf(90f, 180f, 270f)) {
                    val m = Matrix().apply { postRotate(deg) }
                    pass(Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true))
                    if (found.isNotEmpty()) break
                }
            }
            return found.entries.sortedBy { it.value }.map { it.key }
        } catch (e: Exception) {
            return emptyList()
        } finally {
            scanner.close()
        }
    }
}

class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onCodes: (List<String>) -> Unit
) : ImageAnalysis.Analyzer {
    @ExperimentalGetImage
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) { proxy.close(); return }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { codes ->
                val values = codes.sortedBy { it.boundingBox?.top ?: 0 }.mapNotNull { it.rawValue }
                if (values.isNotEmpty()) onCodes(values)
            }
            .addOnCompleteListener { proxy.close() }
    }
}
