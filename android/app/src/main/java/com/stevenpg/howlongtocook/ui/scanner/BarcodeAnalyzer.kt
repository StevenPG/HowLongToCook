package com.stevenpg.howlongtocook.ui.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX analyzer that decodes barcodes with ZXing from the Y (luminance) plane.
 * 1D retail barcodes are only found when the bars cross the image rows, so each frame
 * is tried as-is and rotated 90°, which makes portrait and landscape scanning both work.
 */
class BarcodeAnalyzer(private val onDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to SUPPORTED_FORMATS,
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }
    private val paused = AtomicBoolean(false)

    /** Stops reporting until [resume] is called (e.g. while a result is handled). */
    fun pause() = paused.set(true)
    fun resume() = paused.set(false)

    override fun analyze(image: ImageProxy) {
        try {
            if (paused.get()) return
            val plane = image.planes[0]
            val width = image.width
            val height = image.height
            val luminance = plane.toPackedLuminance(width, height)
            val text = decode(luminance, width, height) ?: decode(rotate90(luminance, width, height), height, width)
            if (text != null && paused.compareAndSet(false, true)) onDetected(text)
        } finally {
            image.close()
        }
    }

    private fun decode(data: ByteArray, width: Int, height: Int): String? = try {
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: NotFoundException) {
        null
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }

    private fun ImageProxy.PlaneProxy.toPackedLuminance(width: Int, height: Int): ByteArray {
        val buffer = buffer.duplicate().apply { rewind() }
        val out = ByteArray(width * height)
        if (rowStride == width && pixelStride == 1) {
            buffer.get(out, 0, minOf(out.size, buffer.remaining()))
            return out
        }
        val row = ByteArray(rowStride)
        for (y in 0 until height) {
            buffer.position(y * rowStride)
            val len = minOf(rowStride, buffer.remaining())
            buffer.get(row, 0, len)
            for (x in 0 until width) out[y * width + x] = row[x * pixelStride]
        }
        return out
    }

    private fun rotate90(src: ByteArray, width: Int, height: Int): ByteArray {
        val dst = ByteArray(src.size)
        for (y in 0 until height) {
            for (x in 0 until width) dst[x * height + (height - 1 - y)] = src[y * width + x]
        }
        return dst
    }

    companion object {
        val SUPPORTED_FORMATS = listOf(
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.ITF,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
        )
    }
}
