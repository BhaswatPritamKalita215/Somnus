package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.BarcodeFormat

/**
 * Validates, normalizes, and inspects retail packaged food barcodes.
 *
 * Guarantees:
 * 1. Leading-Zero Preservation: Preserves significant leading zeros (e.g. UPC-A / EAN-8 codes starting with 0).
 *    Never parses to an integer or strips zeros that would cause accidental collisions or misidentification.
 * 2. GS1 India Awareness: Recognizes the 890 GS1 India country code prefix while strictly enforcing that
 *    no product is rejected or favored solely based on country prefix.
 * 3. Modulo-10 Checksum Verification: Validates official GS1 check digit integrity for EAN-13, EAN-8, and UPC-A.
 */
object BarcodeValidator {

    /**
     * Sanitizes and normalizes a barcode string.
     * Trims leading/trailing whitespace and control characters while strictly preserving leading zeros.
     */
    fun normalizeBarcode(raw: String): String {
        return raw.trim()
    }

    /**
     * Identifies if a barcode has the GS1 India prefix (starts with "890").
     * Note: Per GS1 rules, prefix 890 indicates the issuing GS1 national organization was GS1 India,
     * but does not constitute proof of domestic manufacturing.
     */
    fun isGs1IndiaPrefix(barcode: String): Boolean {
        val clean = normalizeBarcode(barcode)
        return clean.startsWith("890") && (clean.length == 13 || clean.length == 14)
    }

    /**
     * Verifies the standard GS1 Modulo-10 check digit for GTIN-8, GTIN-12 (UPC-A), GTIN-13 (EAN-13), or GTIN-14.
     * Returns true if the check digit matches, or false if invalid/corrupted.
     * If barcode contains non-numeric characters, returns false.
     */
    fun verifyGs1CheckDigit(barcode: String): Boolean {
        val clean = normalizeBarcode(barcode)
        if (clean.length !in listOf(8, 12, 13, 14)) return false
        if (!clean.all { it.isDigit() }) return false

        val digits = clean.map { it.digitToInt() }
        val checkDigit = digits.last()
        val dataDigits = digits.dropLast(1)

        // For GS1: weights alternate 3 and 1 from right to left starting with 3 on the digit immediately before check digit
        var weight = 3
        var sum = 0
        for (i in dataDigits.indices.reversed()) {
            sum += dataDigits[i] * weight
            weight = if (weight == 3) 1 else 3
        }

        val expectedCheckDigit = (10 - (sum % 10)) % 10
        return checkDigit == expectedCheckDigit
    }

    /**
     * Infers the likely barcode format from string characteristics.
     */
    fun detectFormat(barcode: String): BarcodeFormat {
        val clean = normalizeBarcode(barcode)
        return when {
            clean.length == 13 && clean.all { it.isDigit() } -> BarcodeFormat.EAN_13
            clean.length == 8 && clean.all { it.isDigit() } -> BarcodeFormat.EAN_8
            clean.length == 12 && clean.all { it.isDigit() } -> BarcodeFormat.UPC_A
            clean.length in 6..8 && clean.all { it.isDigit() } -> BarcodeFormat.UPC_E
            clean.startsWith("http://") || clean.startsWith("https://") -> BarcodeFormat.QR_CODE
            else -> BarcodeFormat.OTHER
        }
    }
}
