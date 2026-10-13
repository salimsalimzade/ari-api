package app.ari.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * S3-compatible file storage ({@code app.storage}): MinIO locally.
 *
 * @param endpoint where ari-api reaches it, from {@code STORAGE_ENDPOINT}
 * @param publicEndpoint where browsers reach it, for signed image links; differs from {@code
 *     endpoint} when ari-api runs in Docker ({@code STORAGE_PUBLIC_ENDPOINT})
 * @param region MinIO ignores it; S3 signatures need one
 * @param accessKey from {@code STORAGE_ACCESS_KEY}
 * @param secretKey from {@code STORAGE_SECRET_KEY}
 * @param bucket created at startup when missing, from {@code STORAGE_BUCKET}
 */
@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(
    @NotBlank(message = "set STORAGE_ENDPOINT") String endpoint,
    @NotBlank(message = "set STORAGE_PUBLIC_ENDPOINT") String publicEndpoint,
    @NotBlank String region,
    @NotBlank(message = "set STORAGE_ACCESS_KEY") String accessKey,
    @NotBlank(message = "set STORAGE_SECRET_KEY") String secretKey,
    @NotBlank(message = "set STORAGE_BUCKET") String bucket) {}
