/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.store;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Where a committed file group is: the store that holds it and a complete URI.
 * <p>
 * The URI scheme says which kind of store wrote it ({@code file:} or {@code s3://}) and the store
 * name says which instance; a store rejects a location whose name or scheme is not its own. The URI
 * is complete - it includes the writer root - so it is unique across a cluster and resolvable from
 * any node the deployment mode allows to consume it.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
public record FileStoreLocation(
        @JsonProperty(value = "storeName", required = true)
        String storeName,
        @JsonProperty(value = "uri", required = true)
        String uri) {

    private static final String FILE_SCHEME = "file:";
    private static final String S3_SCHEME = "s3://";

    @JsonCreator
    public FileStoreLocation {
        storeName = requireNonBlank(storeName, "storeName");
        uri = requireNonBlank(uri, "uri");
        if (!uri.startsWith(FILE_SCHEME) && !uri.startsWith(S3_SCHEME)) {
            throw new IllegalArgumentException("A file store location URI must start with '"
                                               + FILE_SCHEME + "' or '" + S3_SCHEME + "': " + uri);
        }
    }

    public static FileStoreLocation filesystem(final String storeName, final Path path) {
        Objects.requireNonNull(path, "path");
        return new FileStoreLocation(storeName, path.toAbsolutePath().normalize().toUri().toString());
    }

    /**
     * @param key The group's key prefix, e.g. {@code receive/3f2a.../000000000001/}.
     */
    public static FileStoreLocation s3(final String storeName, final String bucket, final String key) {
        requireNonBlank(bucket, "bucket");
        requireNonBlank(key, "key");
        return new FileStoreLocation(storeName, S3_SCHEME + bucket + "/" + key);
    }

    @JsonIgnore
    public boolean isFilesystem() {
        return uri.startsWith(FILE_SCHEME);
    }

    @JsonIgnore
    public boolean isS3() {
        return uri.startsWith(S3_SCHEME);
    }

    @JsonIgnore
    public Path toPath() {
        if (!isFilesystem()) {
            throw new IllegalStateException("Not a filesystem location: " + uri);
        }
        return Path.of(URI.create(uri)).toAbsolutePath().normalize();
    }

    @JsonIgnore
    public String getS3Bucket() {
        final String rest = s3Remainder();
        final int slash = rest.indexOf('/');
        return slash < 0
                ? rest
                : rest.substring(0, slash);
    }

    /**
     * @return Everything after the bucket, or an empty string if the URI names only a bucket.
     */
    @JsonIgnore
    public String getS3Key() {
        final String rest = s3Remainder();
        final int slash = rest.indexOf('/');
        return slash < 0
                ? ""
                : rest.substring(slash + 1);
    }

    private String s3Remainder() {
        if (!isS3()) {
            throw new IllegalStateException("Not an S3 location: " + uri);
        }
        return uri.substring(S3_SCHEME.length());
    }

    private static String requireNonBlank(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
