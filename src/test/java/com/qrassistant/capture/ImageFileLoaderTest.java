package com.qrassistant.capture;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.qrassistant.scan.DecodedQr;
import com.qrassistant.scan.ZxingQrScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real files in a temporary folder: JUnit deletes it afterwards. */
class ImageFileLoaderTest {

    @TempDir
    Path folder;

    private final ImageFileLoader loader = new ImageFileLoader();

    // --- Files that work ---

    @Test
    void loadsAPng() throws Exception {
        Path file = folder.resolve("small.png");
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
        BufferedImage image = loader.load(file);
        assertEquals(40, image.getWidth());
        assertEquals(30, image.getHeight());
    }

    @Test
    void qrCodesInAFileAreFound() throws Exception {
        // Two real QR codes side by side, saved as JPEG, then loaded and scanned.
        BufferedImage page = new BufferedImage(700, 340, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 700, 340);
        g.drawImage(qr("https://example.com/one"), 20, 20, null);
        g.drawImage(qr("Second code"), 380, 20, null);
        g.dispose();
        Path file = folder.resolve("two.jpg");
        ImageIO.write(page, "jpg", file.toFile());

        List<DecodedQr> codes = new ZxingQrScanner().scan(loader.load(file));
        assertEquals(List.of("https://example.com/one", "Second code"),
                codes.stream().map(DecodedQr::text).toList());   // left to right
    }

    // --- Files that are refused, with a clear message ---

    @Test
    void hugeImageIsRefusedBeforeDecoding() throws Exception {
        // A tiny file whose header CLAIMS 20000 x 20000 pixels and has no pixel data at all.
        // If the loader tried to decode it, it would fail differently; it must stop at the size.
        Path file = folder.resolve("bomb.png");
        Files.write(file, pngHeaderOnly(20_000, 20_000));
        ImageLoadException e = assertThrows(ImageLoadException.class, () -> loader.load(file));
        assertTrue(e.getMessage().contains("too large"), e.getMessage());
    }

    @Test
    void tooManyPixelsInTotalIsRefused() throws Exception {
        // 10000 x 10000: each side is under the 12000 limit, but it's 100 megapixels (~400 MB).
        Path file = folder.resolve("square-bomb.png");
        Files.write(file, pngHeaderOnly(10_000, 10_000));
        ImageLoadException e = assertThrows(ImageLoadException.class, () -> loader.load(file));
        assertTrue(e.getMessage().contains("100 megapixels"), e.getMessage());
    }

    @Test
    void webpIsExplained() throws Exception {
        Path file = folder.resolve("photo.png");   // the name lies; the bytes tell the truth
        Files.write(file, bytes("RIFF\0\0\0\0WEBPVP8 "));
        assertMessageContains(file, "WebP");
    }

    @Test
    void heicIsExplained() throws Exception {
        Path file = folder.resolve("IMG_0001.HEIC");
        Files.write(file, bytes("\0\0\0\u0018ftypheic\0\0\0\0mif1heic"));
        assertMessageContains(file, "HEIC");
    }

    @Test
    void svgIsExplained() throws Exception {
        Path file = folder.resolve("code.svg");
        Files.writeString(file, "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>");
        assertMessageContains(file, "SVG");
    }

    @Test
    void notAnImage() throws Exception {
        Path file = folder.resolve("notes.png");
        Files.writeString(file, "just some text, not an image at all");
        assertMessageContains(file, "isn't an image type");
    }

    @Test
    void damagedPng() throws Exception {
        Path file = folder.resolve("broken.png");
        byte[] good = pngBytes(new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB));
        Files.write(file, java.util.Arrays.copyOf(good, 40));   // cut off in the middle
        assertMessageContains(file, "couldn't be read");
    }

    @Test
    void missingFile() {
        assertMessageContains(folder.resolve("gone.png"), "can't be found");
    }

    // --- Helpers ---

    private void assertMessageContains(Path file, String expected) {
        ImageLoadException e = assertThrows(ImageLoadException.class, () -> loader.load(file));
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static BufferedImage qr(String text) throws WriterException {
        return MatrixToImageWriter.toBufferedImage(new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300));
    }

    private static byte[] pngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** The PNG signature plus a valid IHDR block for the given size, and nothing else. */
    private static byte[] pngHeaderOnly(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        DataOutputStream c = new DataOutputStream(chunk);
        c.writeBytes("IHDR");
        c.writeInt(width);
        c.writeInt(height);
        c.write(new byte[]{8, 2, 0, 0, 0});   // 8-bit RGB, default compression/filter, no interlace
        byte[] typeAndData = chunk.toByteArray();

        CRC32 crc = new CRC32();
        crc.update(typeAndData);
        data.writeInt(typeAndData.length - 4);
        data.write(typeAndData);
        data.writeInt((int) crc.getValue());
        return out.toByteArray();
    }
}
