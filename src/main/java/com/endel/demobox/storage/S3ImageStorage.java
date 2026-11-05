package com.endel.demobox.storage;

import com.endel.demobox.exception.StorageUnavailableException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.time.Duration;

/** Uploads to an S3-compatible bucket (Cloudflare R2). The client is created on first use. */
@Component
@Log4j2
public class S3ImageStorage implements ImageStorage {
    // Files are never overwritten (every upload gets a new random name), so browsers can cache them for good.
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final StorageProperties properties;
    private volatile S3Client client;

    public S3ImageStorage(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public String store(String key, byte[] content, String contentType) {
        if (!isConfigured()) {
            throw new StorageUnavailableException(
                    "La subida de fotos no está configurada: faltan los datos de R2 (R2_ENDPOINT, R2_ACCESS_KEY_ID, "
                            + "R2_SECRET_ACCESS_KEY, R2_PUBLIC_URL). Mientras tanto puedes pegar el link de la imagen.");
        }
        try {
            client().putObject(PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .cacheControl(CACHE_CONTROL)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            log.error("Upload of {} to bucket {} failed", key, properties.getBucket(), e);
            throw new StorageUnavailableException("No se pudo subir la foto a R2. Revisa las claves y el nombre del bucket, o intenta de nuevo.");
        }
        return publicUrl(key);
    }

    String publicUrl(String key) {
        String base = properties.getPublicBaseUrl().trim();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/" + key;
    }

    private S3Client client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = S3Client.builder()
                            .endpointOverride(URI.create(properties.getEndpoint().trim()))
                            .region(Region.of(properties.getRegion()))
                            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                                    properties.getAccessKeyId().trim(), properties.getSecretAccessKey().trim())))
                            .forcePathStyle(true)
                            // Only send checksums the API requires; S3-compatible services don't all accept the newer defaults.
                            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                            .httpClient(UrlConnectionHttpClient.builder()
                                    .connectionTimeout(Duration.ofSeconds(10))
                                    .socketTimeout(Duration.ofSeconds(30))
                                    .build())
                            .build();
                }
            }
        }
        return client;
    }
}
