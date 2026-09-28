package com.qrassistant.scan;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Finds and decodes QR codes in an image.
 *
 * It doesn't know where the image came from (screen or file)
 * or what the decoded text means. Image in, decoded codes out.
 */
public interface QrScanner {

    /**
     * @param image the picture to search
     * @return every QR code found; an EMPTY list if there are none (that is not an error)
     */
    List<DecodedQr> scan(BufferedImage image);
}