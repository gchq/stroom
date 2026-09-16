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

package stroom.proxy.app.pipeline.queue;

import stroom.proxy.app.pipeline.store.FileStore;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;
import java.util.Objects;

/**
 * Definition of a logical file-group queue.
 * <p>
 * Queue definitions are expected to be held in a map keyed by logical queue name, e.g.
 * {@code splitZipInput}, {@code aggregateInput}, or {@code forwardingInput}. Pipeline stage
 * configuration should reference those logical names rather than hard-coding transport-specific
 * destinations.
 * </p>
 * <p>
 * The queue transport only carries {@link FileGroupQueueMessage} instances. The referenced data
 * must already have been written to a {@link FileStore} before a message is published.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
public class QueueDefinition extends AbstractConfig implements IsProxyConfig {

    public static final QueueType DEFAULT_TYPE = QueueType.LOCAL_FILESYSTEM;

    private final QueueType type;
    private final String path;

    private final String topic;
    private final String bootstrapServers;
    private final Map<String, String> producerConfig;
    private final Map<String, String> consumerConfig;

    private final String queueUrl;
    private final StroomDuration visibilityTimeout;
    private final StroomDuration waitTime;
    private final Integer maxDeliveryAttempts;

    /**
     * How many times a message may be delivered before the queue gives up on it, where the proxy
     * is the one counting: local and Kafka queues.
     */
    public static final int DEFAULT_MAX_DELIVERY_ATTEMPTS = 100;

    public QueueDefinition() {
        this(DEFAULT_TYPE, null, null, null, null, null, null, null, null, null);
    }

    @JsonCreator
    public QueueDefinition(
            @JsonProperty("type") final QueueType type,
            @JsonProperty("path") final String path,
            @JsonProperty("topic") final String topic,
            @JsonProperty("bootstrapServers") final String bootstrapServers,
            @JsonProperty("producer") final Map<String, String> producerConfig,
            @JsonProperty("consumer") final Map<String, String> consumerConfig,
            @JsonProperty("queueUrl") final String queueUrl,
            @JsonProperty("visibilityTimeout") final StroomDuration visibilityTimeout,
            @JsonProperty("waitTime") final StroomDuration waitTime,
            @JsonProperty("maxDeliveryAttempts") final Integer maxDeliveryAttempts) {

        this.type = Objects.requireNonNullElse(type, DEFAULT_TYPE);
        this.path = normaliseOptional(path);

        this.topic = normaliseOptional(topic);
        this.bootstrapServers = normaliseOptional(bootstrapServers);
        this.producerConfig = producerConfig == null || producerConfig.isEmpty()
                ? Map.of()
                : Map.copyOf(producerConfig);
        this.consumerConfig = consumerConfig == null || consumerConfig.isEmpty()
                ? Map.of()
                : Map.copyOf(consumerConfig);

        this.queueUrl = normaliseOptional(queueUrl);
        this.visibilityTimeout = visibilityTimeout;
        this.waitTime = waitTime;
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    @JsonPropertyDescription("The queue implementation type. Defaults to LOCAL_FILESYSTEM.")
    @JsonProperty
    public QueueType getType() {
        return type;
    }

    @JsonPropertyDescription(
            "How many times a message may be delivered before the queue gives up on it. LOCAL_FILESYSTEM moves it "
            + "to the queue's failed directory; KAFKA publishes it to the dead-letter topic <topic>.failed. Not "
            + "applicable to SQS, whose redrive policy is the bound - setting it there is a validation error. "
            + "Defaults to 100.")
    @JsonProperty
    public int getMaxDeliveryAttempts() {
        return maxDeliveryAttempts != null
                ? maxDeliveryAttempts
                : DEFAULT_MAX_DELIVERY_ATTEMPTS;
    }

    @JsonIgnore
    public boolean isMaxDeliveryAttemptsStated() {
        return maxDeliveryAttempts != null;
    }

    @JsonPropertyDescription(
            "Path used by LOCAL_FILESYSTEM queues. If omitted, the queue factory may derive a path from the " +
            "logical queue name and proxy data path.")
    @JsonProperty
    public String getPath() {
        return path;
    }

    @JsonPropertyDescription("Kafka topic name.")
    @JsonProperty
    public String getTopic() {
        return topic;
    }

    @JsonPropertyDescription("Kafka bootstrap servers.")
    @JsonProperty
    public String getBootstrapServers() {
        return bootstrapServers;
    }

    @JsonPropertyDescription("Additional Kafka producer properties.")
    @JsonProperty("producer")
    public Map<String, String> getProducerConfig() {
        return producerConfig;
    }

    @JsonPropertyDescription("Additional Kafka consumer properties.")
    @JsonProperty("consumer")
    public Map<String, String> getConsumerConfig() {
        return consumerConfig;
    }

    @JsonPropertyDescription("AWS SQS queue URL.")
    @JsonProperty
    public String getQueueUrl() {
        return queueUrl;
    }

    @JsonPropertyDescription("SQS only. How long a received message stays invisible to other consumers; the " +
                             "proxy extends it with a heartbeat while it holds the message, up to SQS's twelve-hour " +
                             "ceiling. Defaults to 30 minutes.")
    @JsonProperty
    public StroomDuration getVisibilityTimeout() {
        return visibilityTimeout;
    }

    @JsonPropertyDescription("AWS SQS long-poll wait time.")
    @JsonProperty
    public StroomDuration getWaitTime() {
        return waitTime;
    }





    private static String normaliseOptional(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static boolean isNonBlank(final String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "QueueDefinition{" +
               "type=" + type +
               ", path='" + path + '\'' +
               ", topic='" + topic + '\'' +
               ", bootstrapServers='" + bootstrapServers + '\'' +
               ", producerConfig=" + producerConfig +
               ", consumerConfig=" + consumerConfig +
               ", queueUrl='" + queueUrl + '\'' +
               ", visibilityTimeout=" + visibilityTimeout +
               ", waitTime=" + waitTime +
               ", maxDeliveryAttempts=" + maxDeliveryAttempts +
               '}';
    }
}
