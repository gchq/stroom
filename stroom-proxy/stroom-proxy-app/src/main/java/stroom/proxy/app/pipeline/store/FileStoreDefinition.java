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

import stroom.proxy.app.handler.Durability;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Configuration for one named file store.
 */
@SuppressWarnings("checkstyle:linelength")
@JsonPropertyOrder(alphabetic = true)
public class FileStoreDefinition extends AbstractConfig implements IsProxyConfig {

    private final FileStoreType type;
    private final String path;
    private final Durability durability;
    private final StroomDuration orphanAge;

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
        this(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public FileStoreDefinition(final String path) {
        this(FileStoreType.LOCAL_FILESYSTEM, path, null, null, null, null, null, null, null, null, null, null);
    }

    public FileStoreDefinition(final FileStoreType type, final String path, final StroomDuration orphanAge) {
        this(type, path, null, orphanAge, null, null, null, null, null, null, null, null);
    }

    @JsonCreator
    public FileStoreDefinition(
            @JsonProperty("type") final FileStoreType type,
            @JsonProperty("path") final String path,
            @JsonProperty("durability") final Durability durability,
            @JsonProperty("orphanAge") final StroomDuration orphanAge,
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
        this.durability = durability;
        this.orphanAge = orphanAge;
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
    @JsonPropertyDescription("Backing storage: LOCAL_FILESYSTEM (default; local mode only), SHARED_FILESYSTEM "
                             + "(a mount every node sees; shared mode only) or S3 (shared mode only).")
    public FileStoreType getType() {
        return type;
    }

    @JsonProperty
    @JsonPropertyDescription("Filesystem path for a LOCAL_FILESYSTEM or SHARED_FILESYSTEM store. If omitted, a "
                             + "default path is derived from the store name.")
    public String getPath() {
        return path;
    }

    @JsonProperty
    @JsonPropertyDescription("What a commit forces to stable storage before it returns. Defaults by type: FULL "
                             + "for LOCAL_FILESYSTEM, FILESYSTEM (rely on the mount) for SHARED_FILESYSTEM. Not "
                             + "used by S3, where the acknowledgement is the durability point.")
    public Durability getDurability() {
        return durability;
    }

    @JsonProperty
    @JsonPropertyDescription("SHARED_FILESYSTEM only, and required for it. Anything under the store older than "
                             + "this - stale staging, a group left unreferenced by a crash, an empty directory - "
                             + "is deleted by the proxy's scheduled sweep, whether or not something still names "
                             + "it. Must exceed the longest a live group can legitimately wait: it is validated "
                             + "to be at least twice the forward retry window and twice the aggregation window, "
                             + "and a queue backlog is the operator's judgement.")
    public StroomDuration getOrphanAge() {
        return orphanAge;
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
    @JsonPropertyDescription("AWS credentials type: 'default' (SDK chain - IRSA, container or instance role), "
                             + "'basic' (static access key/secret, for S3-compatible endpoints such as MinIO) or "
                             + "'environment'. Defaults to 'default'. To select a named profile set AWS_PROFILE "
                             + "in the environment.")
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
    @JsonPropertyDescription("S3 only. Local directory for staging uploads and holding downloads. Defaults to a "
                             + "subdirectory of the proxy data dir if omitted.")
    public String getLocalCachePath() {
        return localCachePath;
    }

    @JsonIgnore
    public Durability getEffectiveDurability() {
        return durability != null
                ? durability
                : type.defaultDurability();
    }

    public String getEffectiveKeyPrefix(final String storeName) {
        return keyPrefix != null
                ? keyPrefix
                : storeName + "/";
    }

    @JsonIgnore
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
