/*
 * Copyright 2016-2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * A stable reference to a file-group location in a named {@link FileStore}.
 * <p>
 * Queue messages should carry this value rather than moving the underlying data.
 * The location is intentionally URI based so the contract can support non-file
 * stores while initially supporting local/shared filesystem and S3 storage.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
public record FileStoreLocation(
        @JsonProperty(value = "storeName", required = true)
        String storeName,
        @JsonProperty(value = "locationType", required = true)
        LocationType locationType,
        @JsonProperty(value = "uri", required = true)
        String uri,
        @JsonProperty("attributes")
        Map<String, String> attributes) {

    private static final String FILE_URI_SCHEME = "file:";
    private static final String S3_URI_SCHEME = "s3://";

    @JsonCreator
    public FileStoreLocation {
        storeName = requireNonBlank(storeName, "storeName");
        locationType = Objects.requireNonNull(locationType, "locationType");
        uri = requireNonBlank(uri, "uri");
        attributes = attributes == null || attributes.isEmpty()
                ? Map.of()
                : Map.copyOf(attributes);

        if (LocationType.LOCAL_FILESYSTEM.equals(locationType)
            && !uri.regionMatches(true, 0, FILE_URI_SCHEME, 0, FILE_URI_SCHEME.length())) {
            throw new IllegalArgumentException("LOCAL_FILESYSTEM locations must use a file URI");
        }
        if (LocationType.S3.equals(locationType)
            && !uri.regionMatches(true, 0, S3_URI_SCHEME, 0, S3_URI_SCHEME.length())) {
            throw new IllegalArgumentException("S3 locations must use an s3:// URI");
        }
    }

    public static FileStoreLocation localFileSystem(final String storeName,
                                                    final Path path) {
        Objects.requireNonNull(path, "path");
        return new FileStoreLocation(
                storeName,
                LocationType.LOCAL_FILESYSTEM,
                path.toAbsolutePath().normalize().toUri().toString(),
                Map.of());
    }

    /**
     * Create an S3-backed file store location.
     *
     * @param storeName The logical file store name.
     * @param bucket    The S3 bucket name.
     * @param keyPrefix The S3 key prefix for this file group (e.g. "receiveStore/0000000001").
     * @return A new S3 location.
     */
    public static FileStoreLocation s3(final String storeName,
                                       final String bucket,
                                       final String keyPrefix) {
        requireNonBlank(bucket, "bucket");
        requireNonBlank(keyPrefix, "keyPrefix");
        return new FileStoreLocation(
                storeName,
                LocationType.S3,
                S3_URI_SCHEME + bucket + "/" + keyPrefix,
                Map.of());
    }

    @JsonIgnore
    public boolean isLocalFileSystem() {
        return LocationType.LOCAL_FILESYSTEM.equals(locationType);
    }

    @JsonIgnore
    public boolean isS3() {
        return LocationType.S3.equals(locationType);
    }

    /**
     * Extract the S3 bucket name from an S3 URI.
     *
     * @return The bucket name.
     * @throws IllegalStateException If this is not an S3 location.
     */
    @JsonIgnore
    public String getS3Bucket() {
        if (!isS3()) {
            throw new IllegalStateException("Not an S3 location: " + locationType);
        }
        // URI format: s3://bucket/key/prefix
        final String withoutScheme = uri.substring(S3_URI_SCHEME.length());
        final int slashIndex = withoutScheme.indexOf('/');
        if (slashIndex < 0) {
            return withoutScheme;
        }
        return withoutScheme.substring(0, slashIndex);
    }

    /**
     * Extract the S3 key prefix from an S3 URI.
     *
     * @return The key prefix.
     * @throws IllegalStateException If this is not an S3 location.
     */
    @JsonIgnore
    public String getS3KeyPrefix() {
        if (!isS3()) {
            throw new IllegalStateException("Not an S3 location: " + locationType);
        }
        // URI format: s3://bucket/key/prefix
        final String withoutScheme = uri.substring(S3_URI_SCHEME.length());
        final int slashIndex = withoutScheme.indexOf('/');
        if (slashIndex < 0) {
            return "";
        }
        return withoutScheme.substring(slashIndex + 1);
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public enum LocationType {
        LOCAL_FILESYSTEM,
        S3
    }
}
