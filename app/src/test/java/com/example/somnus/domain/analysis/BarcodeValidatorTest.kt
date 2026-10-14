package com.example.somnus.domain.analysis

import com.example.somnus.domain.model.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeValidatorTest {

    @Test
    fun normalizeBarcode_preservesLeadingZeros() {
        val upcAWithZero = "012345678905"
        val normalized = BarcodeValidator.normalizeBarcode("  012345678905  \n")
        assertEquals(upcAWithZero, normalized)
        assertEquals(12, normalized.length)
        assertTrue(normalized.startsWith("0"))
    }

    @Test
    fun normalizeBarcode_ean8LeadingZeroPreserved() {
        val ean8WithZero = "01234565"
        val normalized = BarcodeValidator.normalizeBarcode(" 01234565 ")
        assertEquals(ean8WithZero, normalized)
        assertEquals(8, normalized.length)
        assertTrue(normalized.startsWith("0"))
    }

    @Test
    fun isGs1IndiaPrefix_correctlyIdentifies890Barcodes() {
        // Real Indian product barcodes
        assertTrue(BarcodeValidator.isGs1IndiaPrefix("8901719101053")) // Parle-G
        assertTrue(BarcodeValidator.isGs1IndiaPrefix("8901058852370")) // Maggi
        assertTrue(BarcodeValidator.isGs1IndiaPrefix("8901262010054")) // Amul Butter
        assertTrue(BarcodeValidator.isGs1IndiaPrefix("8904063200109")) // Haldiram's

        // Non-Indian barcodes (must return false, not throw or reject)
        assertFalse(BarcodeValidator.isGs1IndiaPrefix("3017620422003")) // France (Nutella)
        assertFalse(BarcodeValidator.isGs1IndiaPrefix("7622210449283")) // Switzerland
        assertFalse(BarcodeValidator.isGs1IndiaPrefix("012345678905"))  // US UPC-A
        assertFalse(BarcodeValidator.isGs1IndiaPrefix("89012"))         // Too short to be GTIN
    }

    @Test
    fun verifyGs1CheckDigit_validatesCorrectCheckDigits() {
        // EAN-13 valid
        assertTrue(BarcodeValidator.verifyGs1CheckDigit("8901719101052")) // Parle-G (modulo-10 check digit is 2)
        assertTrue(BarcodeValidator.verifyGs1CheckDigit("3017620422003")) // Nutella
        assertTrue(BarcodeValidator.verifyGs1CheckDigit("8901058852370")) // Maggi
        assertTrue(BarcodeValidator.verifyGs1CheckDigit("8901262010054")) // Amul Butter

        // UPC-A valid (12 digits)
        assertTrue(BarcodeValidator.verifyGs1CheckDigit("012345678905"))

        // EAN-8 valid (8 digits)
        assertTrue(BarcodeValidator.verifyGs1CheckDigit("89012345"))
        assertTrue(BarcodeValidator.verifyGs1CheckDigit("96385074"))

        // Corrupted check digits
        assertFalse(BarcodeValidator.verifyGs1CheckDigit("8901719101053")) // Wrong last digit (should be 2)
        assertFalse(BarcodeValidator.verifyGs1CheckDigit("89012343")) // Wrong EAN-8 last digit (should be 5)
        assertFalse(BarcodeValidator.verifyGs1CheckDigit("3017620422000")) // Wrong last digit
        assertFalse(BarcodeValidator.verifyGs1CheckDigit("012345678906")) // Wrong last digit
    }

    @Test
    fun detectFormat_identifiesStandardFormats() {
        assertEquals(BarcodeFormat.EAN_13, BarcodeValidator.detectFormat("8901719101053"))
        assertEquals(BarcodeFormat.EAN_8, BarcodeValidator.detectFormat("89012343"))
        assertEquals(BarcodeFormat.UPC_A, BarcodeValidator.detectFormat("012345678905"))
        assertEquals(BarcodeFormat.UPC_E, BarcodeValidator.detectFormat("012345"))
        assertEquals(BarcodeFormat.QR_CODE, BarcodeValidator.detectFormat("https://somnus.app/food/123"))
        assertEquals(BarcodeFormat.OTHER, BarcodeValidator.detectFormat("CUSTOM-INTERNAL-CODE"))
    }
}
