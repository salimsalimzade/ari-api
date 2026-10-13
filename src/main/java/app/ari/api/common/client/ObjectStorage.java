package app.ari.api.common.client;

import app.ari.api.config.StorageProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Files in the S3-compatible storage (MinIO locally), in one bucket. Keys are listed in
 * docs/database.md section 5; every file belongs to exactly one row.
 */
@Component
public class ObjectStorage {

  private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);

  private final S3Client s3;
  private final S3Presigner presigner;
  private final String bucket;

  public ObjectStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
    this.s3 = s3;
    this.presigner = presigner;
    this.bucket = properties.bucket();
  }

  public void put(String key, byte[] content, String contentType) {
    s3.putObject(
        request -> request.bucket(bucket).key(key).contentType(contentType),
        RequestBody.fromBytes(content));
  }

  /** Deleting a missing file is not an error. */
  public void delete(String key) {
    s3.deleteObject(request -> request.bucket(bucket).key(key));
  }

  /** Deletes after the change that dropped the file; a failure only leaves it orphaned. */
  public void deleteQuietly(String key) {
    try {
      delete(key);
    } catch (RuntimeException e) {
      log.warn("Storage: could not delete {}, it stays orphaned: {}", key, e.getMessage());
    }
  }

  /** A link the browser can load the file from until it expires, without signing in. */
  public String signedUrl(String key, Duration lifetime) {
    return presigner
        .presignGetObject(
            request ->
                request
                    .signatureDuration(lifetime)
                    .getObjectRequest(object -> object.bucket(bucket).key(key)))
        .url()
        .toString();
  }

  public void createBucketIfMissing() {
    try {
      s3.headBucket(request -> request.bucket(bucket));
    } catch (NoSuchBucketException e) {
      s3.createBucket(request -> request.bucket(bucket));
      log.info("Storage: created bucket {}", bucket);
    }
  }
}
