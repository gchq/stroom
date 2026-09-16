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

package stroom.proxy.app.handler;

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.proxy.app.handler.ForwardS3Config.NotificationType;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.Set;

/**
 * Where a forward destination puts data it has given up on.
 * <p>
 * Give-up data is the one thing the proxy holds that nobody will come back for on its own, so where
 * it lands is a deployment decision rather than a fixed location. Leaving it on the local disk of the
 * process that failed is fine for a single proxy and useless for a fleet, where the operator wants it
 * somewhere they can actually reach.
 * </p>
 * <p>
 * This is configured independently of where the destination forwards <em>to</em>, so a file forwarder
 * can quarantine to S3 and an S3 forwarder can quarantine to local disk. Omit the block entirely and
 * behaviour is unchanged: a {@code 03_failure} directory beneath the destination's own forwarding
 * directory.
 * </p>
 */
@NotInjectableConfig // One per forward destination
@JsonPropertyOrder(alphabetic = true)
public class FailureDestinationConfig extends AbstractConfig implements IsProxyConfig {

    private static final DestinationType DEFAULT_TYPE = DestinationType.FILE;

    private final DestinationType type;
    private final String path;
    private final PathTemplateConfig subPathTemplate;
    private final S3ClientConfig s3ClientConfig;
    private final NotificationType notificationType;
    private final Set<String> additionalMetaKeysAllowSet;

    public FailureDestinationConfig() {
        this(null, null, null, null, null, null);
    }

    @JsonCreator
    public FailureDestinationConfig(
            @JsonProperty("type") final DestinationType type,
            @JsonProperty("path") final String path,
            @JsonProperty("subPathTemplate") final PathTemplateConfig subPathTemplate,
            @JsonProperty("s3Client") final S3ClientConfig s3ClientConfig,
            @JsonProperty("notificationType") final NotificationType notificationType,
            @JsonProperty("additionalMetaKeysAllowSet") final Set<String> additionalMetaKeysAllowSet) {

        this.type = Objects.requireNonNullElse(type, DEFAULT_TYPE);
        this.path = path;
        this.subPathTemplate = subPathTemplate;
        this.s3ClientConfig = s3ClientConfig;
        this.notificationType = notificationType;
        this.additionalMetaKeysAllowSet = NullSafe.unmodifialbeSet(additionalMetaKeysAllowSet);

        if (this.type != DestinationType.FILE && this.type != DestinationType.S3) {
            throw new IllegalArgumentException(
                    "A failure destination must be of type FILE or S3, not " + this.type
                    + ". HTTP cannot hold data for an operator to recover.");
        }
        if (this.type == DestinationType.S3) {
            if (s3ClientConfig == null) {
                throw new IllegalArgumentException(
                        "A failure destination of type S3 must configure 's3Client'.");
            }
            if (NullSafe.isBlankString(s3ClientConfig.getBucketName())
                || NullSafe.isBlankString(s3ClientConfig.getKeyPattern())) {
                throw new IllegalArgumentException(
                        "A failure destination of type S3 must configure 's3Client.bucketName' and "
                        + "'s3Client.keyPattern', or every give-up would fail and the group cycle to the "
                        + "queue's give-up instead.");
            }
        }
    }

    @JsonProperty
    @JsonPropertyDescription("Where give-up data is written: FILE (the default) or S3. This is " +
                             "independent of where this destination forwards to.")
    public DestinationType getType() {
        return type;
    }

    @JsonProperty
    @JsonPropertyDescription("FILE only. The directory to write give-up data to. When unset, a " +
                             "'03_failure' directory under 50_forwarding/<name> in the proxy's data directory " +
                             "is used, which is the behaviour when this whole block is omitted. In " +
                             "shared pipeline mode this must be on storage every node sees: a " +
                             "directory at least two levels below a SHARED_FILESYSTEM file store's path, " +
                             "such as <store path>/give-up/<destination name>. In shared mode each process " +
                             "writes under its own <uuid>/ directory beneath this path.")
    public String getPath() {
        return path;
    }

    @JsonProperty
    @JsonPropertyDescription("FILE only. Templated sub-path beneath the failure directory. When " +
                             "unset, '${year}${month}${day}/${feed}' is used.")
    public PathTemplateConfig getSubPathTemplate() {
        return subPathTemplate;
    }

    @JsonProperty("s3Client")
    @JsonPropertyDescription("S3 only. The S3 client configuration for the bucket give-up data is " +
                             "written to. Required when type is S3.")
    public S3ClientConfig getS3ClientConfig() {
        return s3ClientConfig;
    }

    @JsonProperty
    @JsonPropertyDescription("S3 only. Whether and how to notify downstream of objects written here.")
    public NotificationType getNotificationType() {
        return notificationType;
    }

    @JsonProperty
    @JsonPropertyDescription("S3 only. Additional meta keys to carry onto the S3 object.")
    public Set<String> getAdditionalMetaKeysAllowSet() {
        return additionalMetaKeysAllowSet;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FailureDestinationConfig that = (FailureDestinationConfig) o;
        return type == that.type
               && Objects.equals(path, that.path)
               && Objects.equals(subPathTemplate, that.subPathTemplate)
               && Objects.equals(s3ClientConfig, that.s3ClientConfig)
               && notificationType == that.notificationType
               && Objects.equals(additionalMetaKeysAllowSet, that.additionalMetaKeysAllowSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, path, subPathTemplate, s3ClientConfig, notificationType,
                additionalMetaKeysAllowSet);
    }

    @Override
    public String toString() {
        return "FailureDestinationConfig{" +
               "type=" + type +
               ", path='" + path + '\'' +
               ", subPathTemplate=" + subPathTemplate +
               ", s3ClientConfig=" + s3ClientConfig +
               ", notificationType=" + notificationType +
               ", additionalMetaKeysAllowSet=" + additionalMetaKeysAllowSet +
               '}';
    }
}
