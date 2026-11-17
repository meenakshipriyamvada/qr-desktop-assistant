package com.qrassistant.capture;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Opens an image file so it can be scanned. The file is UNTRUSTED, just like a QR code.
 *
 * Order of checks, cheapest first:
 *  1. the file isn't absurdly large on disk
 *  2. the first bytes don't say "WebP / HEIC / AVIF / SVG", which Java can't read
 *     (so we can say exactly what's wrong instead of "not an image")
 *  3. the image's WIDTH and HEIGHT are read from its header BEFORE decoding.
 *     A small file can claim to be 50000 x 50000 pixels ("decompression bomb") and
 *     decoding it would use gigabytes of memory, so huge sizes are refused here:
 *     both too long on one side, and too many pixels in total.
 *  4. only then the pixels are decoded
 *
 * Supported: whatever Java's ImageIO reads: PNG, JPEG, BMP, GIF, TIFF.
 * Errors are logged by TYPE only. (X8)
 */
public class ImageFileLoader {

    private static final Logger LOG = Logger.getLogger(ImageFileLoader.class.getName());

    /** Longest side we accept, in pixels. */
    public static final int MAX_SIDE = 12_000;
    /**
     * Most pixels we decode in total: 50 million is about 200 MB of memory.
     * Big enough for a 48-megapixel phone photo; a 4K screenshot is only about 8 million.
     */
    public static final long MAX_PIXELS = 50_000_000L;
    /** Largest file we even look at. A QR screenshot or photo is far smaller. */
    static final long MAX_FILE_BYTES = 100L * 1024 * 1024;

    private static final int HEADER_BYTES = 32;

    public BufferedImage load(Path file) throws ImageLoadException {
        try {
            if (!Files.isRegularFile(file)) {
                throw new ImageLoadException("That file can't be found.");
            }
            if (Files.size(file) > MAX_FILE_BYTES) {
                throw new ImageLoadException("That file is too large (over 100 MB) to be a QR code image.");
            }
            Optional<String> unsupported = unsupportedFormat(readHeader(file));
            if (unsupported.isPresent()) {
                throw new ImageLoadException(unsupported.get());
            }
            return decode(file);
        } catch (IOException | RuntimeException e) {
            // RuntimeException too: image decoders have had bugs with damaged files.
            LOG.warning("Image file couldn't be read: " + e.getClass().getName());
            throw new ImageLoadException("That file couldn't be read. It may be damaged, or not an image.");
        }
    }

    private static BufferedImage decode(Path file) throws IOException, ImageLoadException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw new ImageLoadException(
                        "That isn't an image type the app can read. Use PNG, JPEG, BMP, GIF or TIFF.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);   // first image only; skip metadata
                int width = reader.getWidth(0);       // reads the HEADER only, no pixels yet
                int height = reader.getHeight(0);
                if (width > MAX_SIDE || height > MAX_SIDE) {
                    throw new ImageLoadException("That image is too large (" + width + " × " + height
                            + " pixels). The limit is " + MAX_SIDE + " pixels on each side.");
                }
                // The side limit alone still allows 12000 x 12000 = 144 million pixels (~576 MB),
                // so the TOTAL is limited too. "long": width * height can overflow an int.
                long pixels = (long) width * height;
                if (pixels > MAX_PIXELS) {
                    throw new ImageLoadException("That image is too large (" + (pixels / 1_000_000)
                            + " megapixels). The limit is " + (MAX_PIXELS / 1_000_000) + " megapixels. "
                            + "Use a smaller copy, or open it and use \"Scan screen\".");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new ImageLoadException("That file couldn't be read. It may be damaged, or not an image.");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static byte[] readHeader(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(HEADER_BYTES);
        }
    }

    /**
     * Recognizes common formats Java can't read, from the file's first bytes ("magic numbers").
     * The file NAME isn't trusted: a .png can really be anything.
     *
     * @return a message for the user, or empty if the format isn't one of these
     */
    static Optional<String> unsupportedFormat(byte[] header) {
        String ascii = new String(header, StandardCharsets.ISO_8859_1);   // 1 byte = 1 char, never fails
        if (ascii.startsWith("RIFF") && ascii.length() >= 12 && ascii.startsWith("WEBP", 8)) {
            return Optional.of("WebP images aren't supported. Save it as PNG or JPEG, or take a screenshot "
                    + "of it and use \"Scan screen\".");
        }
        if (ascii.length() >= 12 && ascii.startsWith("ftyp", 4)) {
            String brand = ascii.substring(8, 12).toLowerCase(Locale.ROOT);
            if (Arrays.asList("heic", "heix", "hevc", "hevx", "mif1", "msf1", "heif").contains(brand)) {
                return Optional.of("HEIC photos (iPhone format) aren't supported. Save it as JPEG, "
                        + "or open it and use \"Scan screen\".");
            }
            if (brand.equals("avif") || brand.equals("avis")) {
                return Optional.of("AVIF images aren't supported. Save it as PNG or JPEG, or open it "
                        + "and use \"Scan screen\".");
            }
        }
        String start = ascii.stripLeading().toLowerCase(Locale.ROOT);
        if (start.startsWith("<svg") || start.startsWith("<?xml")) {
            return Optional.of("SVG drawings aren't supported. Open it and use \"Scan screen\" instead.");
        }
        return Optional.empty();
    }
}
