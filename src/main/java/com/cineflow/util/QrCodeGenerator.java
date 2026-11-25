package com.cineflow.util;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * CineFlow v2 QR Code Generator & Decoder.
 * Renders high-contrast QR tickets as JavaFX images and decodes uploaded ticket snapshots.
 */
public class QrCodeGenerator {

    /**
     * Renders a QR code as a JavaFX WritableImage.
     */
    public static Image generateQrImage(String text, int width, int height) {
        if (text == null || text.isEmpty()) return null;

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints);

            int matrixWidth = bitMatrix.getWidth();
            int matrixHeight = bitMatrix.getHeight();
            WritableImage fxImage = new WritableImage(matrixWidth, matrixHeight);
            PixelWriter pw = fxImage.getPixelWriter();

            Color darkColor = Color.web("#09090F");
            Color lightColor = Color.WHITE;

            for (int y = 0; y < matrixHeight; y++) {
                for (int x = 0; x < matrixWidth; x++) {
                    pw.setColor(x, y, bitMatrix.get(x, y) ? darkColor : lightColor);
                }
            }

            return fxImage;
        } catch (WriterException e) {
            System.err.println("[QrCodeGenerator] Encoding error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Exports a QR code as a PNG file on disk.
     */
    public static void exportToPng(String text, File destinationFile, int size) throws IOException, WriterException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 2);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints);
        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", destinationFile.toPath());
    }

    /**
     * Decodes a QR code token from an image file (e.g. for the Admin Entry Gate image upload feature).
     */
    public static String decodeQrFromFile(File imageFile) throws IOException, NotFoundException {
        if (imageFile == null || !imageFile.exists()) {
            throw new IllegalArgumentException("Image file does not exist.");
        }

        BufferedImage bufferedImage = ImageIO.read(imageFile);
        if (bufferedImage == null) {
            throw new IllegalArgumentException("Could not read image file format.");
        }

        LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(BarcodeFormat.QR_CODE));

        Result result = new MultiFormatReader().decode(bitmap, hints);
        return result.getText();
    }
}
