package com.endel.demobox.storage;

import com.endel.demobox.exception.BadRequestException;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Turns an uploaded photo into a web-ready JPEG (white background, at most {@value #MAX_SIDE_PX} px
 * on its longest side) and stores it under a random name, so the original upload never needs to be
 * trusted or kept.
 */
@Service
public class ImageUploadService {
    public static final Set<String> FOLDERS = Set.of("products", "kits");
    static final int MAX_SIDE_PX = 1600;
    // Rejects "decompression bombs": tiny files that would expand to huge bitmaps.
    static final long MAX_SOURCE_PIXELS = 50_000_000L;
    static final float JPEG_QUALITY = 0.85f;

    private final ImageStorage storage;

    public ImageUploadService(ImageStorage storage) {
        this.storage = storage;
    }

    public boolean isEnabled() {
        return storage.isConfigured();
    }

    /** Returns the public URL of the stored photo. */
    public String upload(String folder, byte[] original) {
        if (!FOLDERS.contains(folder)) {
            throw new BadRequestException("Carpeta de imágenes no válida: " + folder);
        }
        if (original == null || original.length == 0) {
            throw new BadRequestException("Selecciona una foto.");
        }
        byte[] jpeg = toWebJpeg(original);
        return storage.store(folder + "/" + UUID.randomUUID() + ".jpg", jpeg, "image/jpeg");
    }

    byte[] toWebJpeg(byte[] original) {
        BufferedImage source = decode(original);
        double scale = Math.min(1.0, (double) MAX_SIDE_PX / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        try {
            // JPEG has no transparency: transparent PNG/WebP backgrounds become white, like a product shot.
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(downscaleInSteps(source, width, height), 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return encodeJpeg(output);
    }

    private static BufferedImage decode(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw notAnImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_SOURCE_PIXELS) {
                    throw new BadRequestException("La foto es demasiado grande (más de 50 megapíxeles).");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException bad) {
                throw bad;
            }
            throw notAnImage();
        }
    }

    private static BadRequestException notAnImage() {
        return new BadRequestException("El archivo no es una imagen válida (usa JPG, PNG o WebP).");
    }

    /** Halving first keeps big downscales sharp (a single bicubic pass from 4000 px to 1600 px looks jagged). */
    private static BufferedImage downscaleInSteps(BufferedImage image, int targetWidth, int targetHeight) {
        BufferedImage current = image;
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            int w = current.getWidth() / 2;
            int h = current.getHeight() / 2;
            BufferedImage half = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = half.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(current, 0, 0, w, h, null);
            } finally {
                g.dispose();
            }
            current = half;
        }
        return current;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode JPEG", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
