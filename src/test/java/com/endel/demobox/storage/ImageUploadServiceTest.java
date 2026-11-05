package com.endel.demobox.storage;

import com.endel.demobox.exception.BadRequestException;
import com.endel.demobox.exception.StorageUnavailableException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageUploadServiceTest {

    /** Keeps what would have been uploaded. */
    private static final class MemoryStorage implements ImageStorage {
        final List<String> keys = new ArrayList<>();
        byte[] last;

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String store(String key, byte[] content, String contentType) {
            assertEquals("image/jpeg", contentType);
            keys.add(key);
            last = content;
            return "https://pub-test.r2.dev/" + key;
        }
    }

    private final MemoryStorage storage = new MemoryStorage();
    private final ImageUploadService service = new ImageUploadService(storage);

    private static byte[] png(int width, int height, boolean transparent) throws IOException {
        BufferedImage image = new BufferedImage(width, height, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(20, 120, 60));
        g.fillRect(width / 4, height / 4, width / 2, height / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void bigTransparentPngBecomesA1600PixelJpegOnWhite() throws IOException {
        String url = service.upload("products", png(4000, 2000, true));

        assertTrue(url.matches("https://pub-test\\.r2\\.dev/products/[0-9a-f-]{36}\\.jpg"), url);
        BufferedImage stored = read(storage.last);
        assertEquals(1600, stored.getWidth());
        assertEquals(800, stored.getHeight());
        Color corner = new Color(stored.getRGB(5, 5));
        assertTrue(corner.getRed() > 245 && corner.getGreen() > 245 && corner.getBlue() > 245, "transparent background turns white: " + corner);
        Color center = new Color(stored.getRGB(800, 400));
        assertTrue(center.getGreen() > center.getRed(), "the photo itself is kept: " + center);
    }

    @Test
    void smallPhotosAreNotEnlarged() throws IOException {
        service.upload("kits", png(640, 480, false));

        BufferedImage stored = read(storage.last);
        assertEquals(640, stored.getWidth());
        assertEquals(480, stored.getHeight());
        assertTrue(storage.keys.get(0).startsWith("kits/"));
    }

    @Test
    void everyUploadGetsItsOwnName() throws IOException {
        byte[] photo = png(100, 100, false);
        service.upload("products", photo);
        service.upload("products", photo);
        assertFalse(storage.keys.get(0).equals(storage.keys.get(1)));
    }

    @Test
    void rejectsFilesThatAreNotImagesAndUnknownFolders() throws IOException {
        BadRequestException notImage = assertThrows(BadRequestException.class,
                () -> service.upload("products", "%PDF-1.7 not a photo".getBytes()));
        assertTrue(notImage.getMessage().contains("JPG, PNG o WebP"));
        assertThrows(BadRequestException.class, () -> service.upload("products", new byte[0]));
        byte[] photo = png(10, 10, false);
        assertThrows(BadRequestException.class, () -> service.upload("../secret", photo));
        assertTrue(storage.keys.isEmpty());
    }

    @Test
    void webpUploadsCanBeRead() {
        assertTrue(ImageIO.getImageReadersByFormatName("webp").hasNext(), "imageio-webp plugin on the classpath");
    }

    @Test
    void unconfiguredStorageExplainsWhatIsMissing() throws IOException {
        S3ImageStorage s3 = new S3ImageStorage(new StorageProperties());
        assertFalse(s3.isConfigured());
        ImageUploadService unconfigured = new ImageUploadService(s3);
        byte[] photo = png(10, 10, false);
        StorageUnavailableException ex = assertThrows(StorageUnavailableException.class, () -> unconfigured.upload("products", photo));
        assertTrue(ex.getMessage().contains("R2_ACCESS_KEY_ID"));
    }

    @Test
    void publicUrlJoinsBaseAndKeyWithOneSlash() {
        StorageProperties properties = new StorageProperties();
        properties.setPublicBaseUrl("https://pub-abc.r2.dev/");
        assertEquals("https://pub-abc.r2.dev/products/a.jpg", new S3ImageStorage(properties).publicUrl("products/a.jpg"));
    }
}
