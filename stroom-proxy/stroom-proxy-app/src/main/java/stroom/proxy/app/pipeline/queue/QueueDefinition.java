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
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.constraints.Min;

import java.util.Map;
import java.util.Objects;

/**
 * Definition of a logical file-group queue.
 * <p>
 * Queue definitions are expected to be held in a map keyed by logical queue name, e.g.
 * {@code preAggregateInput}, {@code aggregateInput}, or {@code forwardingInput}. Pipeline stage
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
    private final StroomDuration abandonedLeaseScanInterval;
    private final int maxDeliveryAttempts;

    /**
     * How often an idle LOCAL_FILESYSTEM queue looks for leases abandoned by a
     * consumer that never acknowledged or failed its item.
     */
    public static final StroomDuration DEFAULT_ABANDONED_LEASE_SCAN_INTERVAL =
            StroomDuration.ofSeconds(10);

    /**
     * How many times a LOCAL_FILESYSTEM message may be delivered before it is
     * quarantined instead of re-queued.
     */
    public static final int DEFAULT_MAX_DELIVERY_ATTEMPTS = 100;

    public QueueDefinition() {
        this(
                DEFAULT_TYPE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
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
            @JsonProperty("abandonedLeaseScanInterval") final StroomDuration abandonedLeaseScanInterval,
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
        this.abandonedLeaseScanInterval = Objects.requireNonNullElse(
                abandonedLeaseScanInterval,
                DEFAULT_ABANDONED_LEASE_SCAN_INTERVAL);
        this.maxDeliveryAttempts = Objects.requireNonNullElse(
                maxDeliveryAttempts,
                DEFAULT_MAX_DELIVERY_ATTEMPTS);
    }

    @JsonPropertyDescription("The queue implementation type. Defaults to LOCAL_FILESYSTEM.")
    @JsonProperty
    public QueueType getType() {
        return type;
    }

    @JsonPropertyDescription(
            "LOCAL_FILESYSTEM only. How often an idle queue checks for in-flight messages held by no live " +
            "consumer and returns them to pending. A consumer whose acknowledge() or fail() throws leaves its " +
            "message in-flight; without this it would wait for a proxy restart. Unlike an SQS visibility " +
            "timeout this is not a guess about elapsed time - the queue reclaims only messages that no live " +
            "consumer in this process holds - so it cannot take work from a consumer that is merely slow. " +
            "Defaults to PT10S.")
    @JsonProperty
    public StroomDuration getAbandonedLeaseScanInterval() {
        return abandonedLeaseScanInterval;
    }

    @JsonPropertyDescription(
            "LOCAL_FILESYSTEM only. How many times a message may be delivered before it is moved to the queue's " +
            "failed directory instead of being re-queued. A message whose file group has already been consumed - " +
            "which at-least-once delivery makes possible - can never succeed, and without a limit it would be " +
            "retried forever. Defaults to 100.")
    @JsonProperty
    @Min(1)
    public int getMaxDeliveryAttempts() {
        return maxDeliveryAttempts;
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

    @JsonPropertyDescription("AWS SQS visibility timeout.")
    @JsonProperty
    public StroomDuration getVisibilityTimeout() {
        return visibilityTimeout;
    }

    @JsonPropertyDescription("AWS SQS long-poll wait time.")
    @JsonProperty
    public StroomDuration getWaitTime() {
        return waitTime;
    }



    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "Kafka queue definitions must set both topic and bootstrapServers.")
    public boolean isKafkaConfigValid() {
        if (type != QueueType.KAFKA) {
            return true;
        }
        return isNonBlank(topic)
               && isNonBlank(bootstrapServers);
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "SQS queue definitions must set queueUrl.")
    public boolean isSqsConfigValid() {
        if (type != QueueType.SQS) {
            return true;
        }
        return isNonBlank(queueUrl);
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
               '}';
    }
}
