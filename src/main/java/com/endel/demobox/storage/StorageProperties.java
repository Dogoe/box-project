package com.endel.demobox.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Where uploaded photos go: an S3-compatible bucket (Cloudflare R2 in practice). Filled from the
 * R2_* environment variables, or from a local .env file in development (see application.yml).
 */
@Component
@ConfigurationProperties(prefix = "app.storage")
@Data
public class StorageProperties {
    /** S3 API endpoint, e.g. https://ACCOUNT_ID.r2.cloudflarestorage.com. */
    private String endpoint;
    /** R2 always uses "auto". */
    private String region = "auto";
    private String bucket;
    private String accessKeyId;
    private String secretAccessKey;
    /** Public base URL the photos are served from, e.g. https://pub-xxxx.r2.dev or https://imagenes.tudominio.com. */
    private String publicBaseUrl;

    /** Uploads are only offered when every value needed to store and serve a photo is present. */
    public boolean isConfigured() {
        return StringUtils.hasText(endpoint) && StringUtils.hasText(bucket) && StringUtils.hasText(accessKeyId)
                && StringUtils.hasText(secretAccessKey) && StringUtils.hasText(publicBaseUrl);
    }
}
