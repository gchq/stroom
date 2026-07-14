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

package stroom.proxy.app.handler;


import stroom.aws.s3.shared.S3ClientConfig;
import stroom.proxy.app.DownstreamHostConfig;
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

import java.util.Objects;

@NotInjectableConfig // Used in lists so not a unique thing
@JsonPropertyOrder(alphabetic = true)
public final class ForwardS3Config
        extends AbstractConfig
        implements IsProxyConfig, ForwarderConfig {

    public static final boolean DEFAULT_IS_ENABLED = true;
    public static final boolean DEFAULT_IS_INSTANT = false;
    public static final NotificationType DEFAULT_NOTIFICATION_TYPE = NotificationType.S3_EVENT;

    private final boolean enabled;
    private final boolean instant;
    private final NotificationType notificationType;
    private final String name;
    private final S3ClientConfig clientConfig;
    private final ForwardS3QueueConfig forwardQueueConfig;

    public ForwardS3Config() {
        enabled = DEFAULT_IS_ENABLED;
        instant = DEFAULT_IS_INSTANT;
        notificationType = DEFAULT_NOTIFICATION_TYPE;
        name = null;
        clientConfig = null;
        forwardQueueConfig = new ForwardS3QueueConfig();
    }

    @JsonCreator
    public ForwardS3Config(@JsonProperty("enabled") final boolean enabled,
                           @JsonProperty("instant") final boolean instant,
                           @JsonProperty("notificationType") final NotificationType notificationType,
                           @JsonProperty("name") final String name,
                           @JsonProperty("client") final S3ClientConfig clientConfig,
                           @JsonProperty("queue") final ForwardS3QueueConfig forwardQueueConfig) {
        if (instant) {
            throw new IllegalArgumentException("instant is not supported by the S3 forwarder");
        } else {
            this.instant = DEFAULT_IS_INSTANT;
        }
        this.enabled = enabled;
        this.notificationType = Objects.requireNonNullElse(notificationType, DEFAULT_NOTIFICATION_TYPE);
        this.name = name;
        this.clientConfig = clientConfig;
        this.forwardQueueConfig = Objects.requireNonNullElseGet(forwardQueueConfig, ForwardS3QueueConfig::new);
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
    @JsonPropertyDescription("Should data be forwarded instantly during the receipt process, i.e. must we" +
                             " successfully forward before returning a success response to the sender.")
    public boolean isInstant() {
        return instant;
    }

    @Override
    @NotEmpty
    @JsonProperty("name")
    @JsonPropertyDescription("The unique name of the destination (across all file/http forward destinations. " +
                             "The name is used in the directories on the file system, so do not change the name " +
                             "once proxy has processed data. Must be provided.")
    public String getName() {
        return name;
    }

    @NotNull
    @JsonProperty("client")
    public S3ClientConfig getClientConfig() {
        return clientConfig;
    }

    @Override
    @JsonProperty("queue")
    public ForwardS3QueueConfig getForwardQueueConfig() {
        return forwardQueueConfig;
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
