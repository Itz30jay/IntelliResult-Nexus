package com.intelliresult.nexus.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Sec. 20's QR image generation, isolated to exactly this one concern
 * (encode text -&gt; PNG bytes) so {@code PDFUtil} - and any later phase
 * that wants a QR image for something other than a marksheet - never needs
 * to touch the ZXing API directly. ERROR_CORRECTION.M (~15% of the symbol
 * can be damaged/obscured and still scan) is deliberately not the library
 * default of L: this code ends up on a printed, possibly creased or
 * photocopied physical document, which a screen-only QR code never has to
 * survive. MARGIN is trimmed to 1 module (ZXing's own default is 4) because
 * the marksheet layout already surrounds the code with its own whitespace
 * and a caption - stacking two quiet zones only wastes page space.
 */
public final class QRCodeUtil {

    private static final ErrorCorrectionLevel ERROR_CORRECTION = ErrorCorrectionLevel.M;
    private static final int QUIET_ZONE_MODULES = 1;

    private QRCodeUtil() {
        // Static-only utility class.
    }

    /**
     * Encodes {@code content} as a square QR code and returns it as PNG
     * bytes, ready for {@code com.lowagie.text.Image.getInstance(byte[])}.
     *
     * @throws WriterException if {@code content} cannot be encoded (e.g. it
     *         exceeds the QR format's maximum symbol capacity).
     * @throws IOException     if PNG encoding fails.
     */
    public static byte[] generatePngBytes(String content, int sizePx) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ERROR_CORRECTION);
        hints.put(EncodeHintType.MARGIN, QUIET_ZONE_MODULES);

        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        }
    }
}
