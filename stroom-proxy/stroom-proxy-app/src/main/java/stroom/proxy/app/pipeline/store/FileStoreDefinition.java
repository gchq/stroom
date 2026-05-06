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

package stroom.proxy.app.pipeline.store;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Definition of a named file store used by pipeline stages.
 * <p>
 * Supports two backing types:
 * <ul>
 *     <li>{@link FileStoreType#LOCAL_FILESYSTEM} — local or shared filesystem (default)</li>
 *     <li>{@link FileStoreType#S3} — AWS S3 or S3-compatible object storage</li>
 * </ul>
 * </p>
 */
@SuppressWarnings("checkstyle:linelength")
@JsonPropertyOrder(alphabetic = true)
public class FileStoreDefinition extends AbstractConfig implements IsProxyConfig {

    private final FileStoreType type;
    private final String path;

    // S3-specific fields
    private final String region;
    private final String bucket;
    private final String keyPrefix;
    private final String endpointOverride;
    private final String credentialsType;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String localCachePath;

    public FileStoreDefinition() {
        this(null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Convenience constructor for local filesystem stores (backwards compatible).
     */
    public FileStoreDefinition(final String path) {
        this(FileStoreType.LOCAL_FILESYSTEM, path, null, null, null, null, null, null, null, null);
    }

    @JsonCreator
    public FileStoreDefinition(
            @JsonProperty("type") final FileStoreType type,
            @JsonProperty("path") final String path,
            @JsonProperty("region") final String region,
            @JsonProperty("bucket") final String bucket,
            @JsonProperty("keyPrefix") final String keyPrefix,
            @JsonProperty("endpointOverride") final String endpointOverride,
            @JsonProperty("credentialsType") final String credentialsType,
            @JsonProperty("accessKeyId") final String accessKeyId,
            @JsonProperty("secretAccessKey") final String secretAccessKey,
            @JsonProperty("localCachePath") final String localCachePath) {
        this.type = Objects.requireNonNullElse(type, FileStoreType.LOCAL_FILESYSTEM);
        this.path = normaliseOptional(path);
        this.region = normaliseOptional(region);
        this.bucket = normaliseOptional(bucket);
        this.keyPrefix = normaliseOptional(keyPrefix);
        this.endpointOverride = normaliseOptional(endpointOverride);
        this.credentialsType = normaliseOptional(credentialsType);
        this.accessKeyId = normaliseOptional(accessKeyId);
        this.secretAccessKey = normaliseOptional(secretAccessKey);
        this.localCachePath = normaliseOptional(localCachePath);
    }

    @JsonProperty
    @JsonPropertyDescription("Backing storage type: LOCAL_FILESYSTEM (default) or S3.")
    public FileStoreType getType() {
        return type;
    }

    @JsonProperty
    @JsonPropertyDescription("Local/shared filesystem path for this file store. Used when type is LOCAL_FILESYSTEM. If omitted, a default path is derived from the store name.")
    public String getPath() {
        return path;
    }

    @JsonProperty
    @JsonPropertyDescription("AWS region for the S3 bucket (e.g. 'eu-west-2'). Required when type is S3.")
    public String getRegion() {
        return region;
    }

    @JsonProperty
    @JsonPropertyDescription("S3 bucket name. Required when type is S3.")
    public String getBucket() {
        return bucket;
    }

    @JsonProperty
    @JsonPropertyDescription("S3 key prefix for file groups in this store. Defaults to the store name if omitted.")
    public String getKeyPrefix() {
        return keyPrefix;
    }

    @JsonProperty
    @JsonPropertyDescription("S3 endpoint override for S3-compatible stores (MinIO, LocalStack, Cloudflare R2). Leave blank for AWS S3.")
    public String getEndpointOverride() {
        return endpointOverride;
    }

    @JsonProperty
    @JsonPropertyDescription("AWS credentials type: 'default' (SDK chain), 'basic' (access key/secret), 'environment', 'profile'. Defaults to 'default'.")
    public String getCredentialsType() {
        return credentialsType;
    }

    @JsonProperty
    @JsonPropertyDescription("AWS access key ID. Only used when credentialsType is 'basic'.")
    public String getAccessKeyId() {
        return accessKeyId;
    }

    @JsonProperty
    @JsonPropertyDescription("AWS secret access key. Only used when credentialsType is 'basic'.")
    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    @JsonProperty
    @JsonPropertyDescription("Local directory for staging uploads and caching downloads. Defaults to a subdirectory of the proxy data dir if omitted.")
    public String getLocalCachePath() {
        return localCachePath;
    }

    /**
     * @return The effective key prefix, falling back to a default if not configured.
     */
    public String getEffectiveKeyPrefix(final String storeName) {
        return keyPrefix != null
                ? keyPrefix
                : storeName + "/";
    }

    /**
     * @return The effective credentials type, falling back to 'default'.
     */
    public String getEffectiveCredentialsType() {
        return credentialsType != null
                ? credentialsType
                : "default";
    }

    private static String normaliseOptional(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
