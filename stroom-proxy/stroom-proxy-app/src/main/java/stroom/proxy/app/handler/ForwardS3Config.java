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
import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
import stroom.util.io.PathCreator;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

@NotInjectableConfig // Used in lists so not a unique thing
@JsonPropertyOrder(alphabetic = true)
public final class ForwardS3Config
        extends AbstractConfig
        implements IsProxyConfig, ForwarderConfig {

    public static final boolean DEFAULT_IS_ENABLED = true;
    public static final boolean DEFAULT_IS_INSTANT = false;
    /** Five, as the forward thread count was before the retry tier went. */
    public static final ConsumerStageThreadsConfig DEFAULT_THREADS = new ConsumerStageThreadsConfig(5);
    public static final NotificationType DEFAULT_NOTIFICATION_TYPE = NotificationType.S3_EVENT;

    private final boolean enabled;
    private final boolean instant;
    private final NotificationType notificationType;
    private final String name;
    private final S3ClientConfig clientConfig;
    private final ForwardRetryConfig retry;
    private final FailureDestinationConfig failureDestination;
    private final ConsumerStageThreadsConfig threads;
    private final Set<String> additionalMetaKeysAllowSet;

    public ForwardS3Config() {
        enabled = DEFAULT_IS_ENABLED;
        instant = DEFAULT_IS_INSTANT;
        notificationType = DEFAULT_NOTIFICATION_TYPE;
        name = null;
        clientConfig = null;
        retry = new ForwardRetryConfig();
        failureDestination = null;
        threads = DEFAULT_THREADS;
        additionalMetaKeysAllowSet = Collections.emptySet();
    }

    @JsonCreator
    public ForwardS3Config(
            @JsonProperty("enabled") final Boolean enabled,
            @JsonProperty("instant") final Boolean instant,
            @JsonProperty("notificationType") final NotificationType notificationType,
            @JsonProperty("name") final String name,
            @JsonProperty("client") final S3ClientConfig clientConfig,
            @JsonProperty("retry") final ForwardRetryConfig retry,
            @JsonProperty("failureDestination") final FailureDestinationConfig failureDestination,
            @JsonProperty("threads") final ConsumerStageThreadsConfig threads,
            @JsonProperty("additionalMetaKeysAllowSet") final Set<String> additionalMetaKeysAllowSet) {

        if (Boolean.TRUE.equals(instant)) {
            throw new IllegalArgumentException("instant is not supported by the S3 forwarder");
        } else {
            this.instant = DEFAULT_IS_INSTANT;
        }
        this.enabled = Objects.requireNonNullElse(enabled, DEFAULT_IS_ENABLED);
        this.notificationType = Objects.requireNonNullElse(notificationType, DEFAULT_NOTIFICATION_TYPE);
        this.name = name;
        this.clientConfig = clientConfig;
        this.retry = Objects.requireNonNullElseGet(retry, ForwardRetryConfig::new);
        // Null means the default: <data>/50_forwarding/<name>/03_failure in the proxy's data directory.
        this.failureDestination = failureDestination;
        this.threads = Objects.requireNonNullElse(threads, DEFAULT_THREADS);
        this.additionalMetaKeysAllowSet = NullSafe.unmodifialbeSet(additionalMetaKeysAllowSet);
    }

    /**
     * True if received streams should be forwarded to another stroom(-proxy) instance.
     */
    @Override
    @JsonProperty("enabled")
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    @NotNull
    @JsonProperty("instant")
    @JsonPropertyDescription("Not supported for an S3 destination: setting it true is refused at start-up. " +
                             "Instant forwarding, where the sender is answered only once the destination has " +
                             "accepted the data, is available for HTTP and file destinations.")
    public boolean isInstant() {
        return instant;
    }

    @Override
    @NotEmpty
    @JsonProperty("name")
    @JsonPropertyDescription("The unique name of the destination, across file, HTTP and S3 forward destinations. " +
                             "It names the destination's default give-up directory and, when more than one " +
                             "destination is enabled, its forward-<name> queue and file store, so do not change " +
                             "it once the proxy has processed data. Must be provided.")
    public String getName() {
        return name;
    }

    @NotNull
    @JsonProperty("client")
    public S3ClientConfig getClientConfig() {
        return clientConfig;
    }

    @Override
    @JsonProperty("retry")
    @JsonPropertyDescription("How this destination retries a group it could not deliver and when it gives up.")
    public ForwardRetryConfig getRetry() {
        return retry;
    }

    @Override
    @JsonProperty("failureDestination")
    @JsonPropertyDescription("Where this destination writes data it has given up on, with an error.log " +
                             "beside each group. Configured independently of where it forwards to. When " +
                             "unset, a '03_failure' directory under 50_forwarding/<name> in the proxy's " +
                             "data directory is used.")
    public FailureDestinationConfig getFailureDestination() {
        return failureDestination;
    }

    @Override
    @JsonProperty("threads")
    @JsonPropertyDescription("How many threads deliver to this destination.")
    public ConsumerStageThreadsConfig getThreads() {
        return threads;
    }

    @JsonProperty("notificationType")
    @JsonPropertyDescription(
            "The type of notification to use when sending data to S3. Valid values are: " +
            "(S3_EVENT|REST). S3_EVENT means proxy relies on S3 event notifications to inform the " +
            "downstream about the data. REST means proxy will send a REST request to the downstream " +
            "to notify it of the location of the file on S3.")
    public NotificationType getNotificationType() {
        return notificationType;
    }

    @JsonProperty
    @JsonPropertyDescription("Set of meta keys that should be added to the request when proxy forwards data. " +
                             "This set is in addition to the base set of allowed headers.")
    public Set<String> getAdditionalMetaKeysAllowSet() {
        return additionalMetaKeysAllowSet;
    }

    @JsonIgnore
    @Override
    public String getDestinationDescription(final DownstreamHostConfig ignored1,
                                            final PathCreator ignored2) {
        return getDestinationDescription();
    }

    @JsonIgnore
    public @NonNull String getDestinationDescription() {
        String keyPattern = NullSafe.string(clientConfig.getKeyPattern());
        if (!keyPattern.startsWith("/")) {
            keyPattern = "/" + keyPattern;
        }
        return NullSafe.string(clientConfig.getBucketName())
               + ".s3."
               + NullSafe.string(clientConfig.getRegion())
               + ".amazonaws.com."
               + keyPattern;
    }


    // --------------------------------------------------------------------------------


    public enum NotificationType {
        /**
         * The downstream Stroom or Stroom-Proxy will consume S3 Event Notifications
         * from an SQS queue.
         */
        S3_EVENT,
        /**
         * A REST call will be made to the downstream host to notify them about the S3
         * object creation. This is primarily intended for development/test use.
         */
        REST,
        ;
    }
}
