package com.example.somnus.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.somnus.domain.model.BarcodeFormat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * On-device CameraX [ImageAnalysis.Analyzer] using Google ML Kit Barcode Scanning.
 *
 * Guarantees:
 * 1. Privacy: Camera frames are processed strictly in-memory on the physical device.
 *    No frames are persisted, recorded, or transmitted to any server.
 * 2. Debouncing: Throttles detections by 2000ms to prevent repeated lookup storms
 *    while the camera remains pointed at the same food packaging.
 * 3. Resource Hygiene: Reliably closes [ImageProxy] in both success and failure paths
 *    to prevent CameraX buffer starvation.
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (barcode: String, format: BarcodeFormat) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_QR_CODE
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    private var lastScannedCode: String? = null
    private var lastScannedTimestamp: Long = 0L
    private val debouncePeriodMs: Long = 2000L

    @Volatile
    var isPaused: Boolean = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isPaused) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (isPaused) return@addOnSuccessListener
                    val now = System.currentTimeMillis()
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue?.trim() ?: continue
                        if (rawValue.isBlank()) continue

                        if (rawValue == lastScannedCode && (now - lastScannedTimestamp) < debouncePeriodMs) {
                            continue
                        }

                        lastScannedCode = rawValue
                        lastScannedTimestamp = now

                        val format = when (barcode.format) {
                            Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
                            Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
                            Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
                            Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
                            Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
                            else -> BarcodeFormat.OTHER
                        }

                        onBarcodeDetected(rawValue, format)
                        break
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    fun resetThrottle() {
        lastScannedCode = null
        lastScannedTimestamp = 0L
    }
}
