package com.intelliresult.nexus.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Matches this project's existing "pure utility classes only" test scope
 * (see README's own Phase 1/17 notes) - {@code MarksheetService},
 * {@code VerificationService}, and the new servlets are Hibernate/
 * HTTP-backed like every other service and controller in the codebase,
 * none of which have tests yet either; Phase 17 covers all of them
 * together. QRCodeUtil is the one Phase 12 addition that, like DateUtil/
 * GradeUtil/ValidationUtil before it, needs nothing but its own inputs to
 * test meaningfully.
 */
class QRCodeUtilTest {

    private static final int TEST_SIZE_PX = 120;

    @Test
    void generatePngBytes_returnsNonEmptyPngForOrdinaryUrl() throws Exception {
        byte[] png = QRCodeUtil.generatePngBytes("https://example.edu/verify/result/abc123", TEST_SIZE_PX);

        assertTrue(png.length > 0, "Expected non-empty PNG bytes.");
        // The 8-byte PNG file signature (0x89 'P' 'N' 'G' \r \n 0x1A \n) -
        // the cheapest possible check that MatrixToImageWriter actually
        // produced a real PNG, not silently garbled or empty output.
        byte[] pngSignature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] actualHeader = new byte[8];
        System.arraycopy(png, 0, actualHeader, 0, 8);
        assertArrayEquals(pngSignature, actualHeader);
    }

    @Test
    void generatePngBytes_differentContentProducesDifferentBytes() throws Exception {
        byte[] first = QRCodeUtil.generatePngBytes("https://example.edu/verify/result/token-one", TEST_SIZE_PX);
        byte[] second = QRCodeUtil.generatePngBytes("https://example.edu/verify/result/token-two", TEST_SIZE_PX);

        assertNotEquals(java.util.Arrays.toString(first), java.util.Arrays.toString(second));
    }

    @Test
    void generatePngBytes_sameContentIsReproducible() throws Exception {
        String content = "https://example.edu/verify/result/stable-token";
        byte[] first = QRCodeUtil.generatePngBytes(content, TEST_SIZE_PX);
        byte[] second = QRCodeUtil.generatePngBytes(content, TEST_SIZE_PX);

        assertArrayEquals(first, second);
    }

    @Test
    void generatePngBytes_rejectsContentThatExceedsQrCapacity() {
        // A QR code (even at the lowest error-correction level) cannot
        // encode arbitrarily long text - WriterException is ZXing's own
        // signal for "this content cannot be encoded", which MarksheetService
        // is responsible for translating into ResultProcessingException.
        String tooLong = "x".repeat(5000);
        assertThrows(com.google.zxing.WriterException.class,
                () -> QRCodeUtil.generatePngBytes(tooLong, TEST_SIZE_PX));
    }

    @Test
    void generatePngBytes_neverProducesAllWhiteImage() throws Exception {
        // A cheap sanity check that something was actually encoded, not a
        // blank square - not a full QR decode (this project has no
        // dependency capable of that), just confirming the byte stream
        // isn't suspiciously small for a 120x120 image with real content.
        byte[] png = QRCodeUtil.generatePngBytes("https://example.edu/verify/result/abc123", TEST_SIZE_PX);
        assertFalse(png.length < 100, "PNG output is implausibly small for a real QR code.");
    }
}
