/*
 * Copyright 2016-2025 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Transport/runtime metadata for a consumed {@link FileGroupQueueMessage}.
 * <p>
 * This metadata describes the queue item wrapper rather than the file-group
 * reference message itself. Queue implementations can populate it with details
 * such as the item identifier, delivery attempt, receive time, and
 * implementation-specific attributes.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
public record FileGroupQueueItemMetadata(
        @JsonProperty(value = "queueName", required = true)
        String queueName,
        @JsonProperty(value = "itemId", required = true)
        String itemId,
        @JsonProperty("attemptNumber")
        int attemptNumber,
        @JsonProperty("publishedTime")
        Instant publishedTime,
        @JsonProperty("firstReceivedTime")
        Instant firstReceivedTime,
        @JsonProperty("lastReceivedTime")
        Instant lastReceivedTime,
        @JsonProperty("attributes")
        Map<String, String> attributes) {

    @JsonCreator
    public FileGroupQueueItemMetadata {
        queueName = requireNonBlank(queueName, "queueName");
        itemId = requireNonBlank(itemId, "itemId");

        if (attemptNumber < 0) {
            throw new IllegalArgumentException("attemptNumber must be >= 0");
        }

        attributes = attributes == null || attributes.isEmpty()
                ? Map.of()
                : Map.copyOf(attributes);
    }

    public static FileGroupQueueItemMetadata create(final String queueName,
                                                    final String itemId) {
        return new FileGroupQueueItemMetadata(
                queueName,
                itemId,
                0,
                null,
                null,
                null,
                Map.of());
    }

    public static FileGroupQueueItemMetadata published(final String queueName,
                                                       final String itemId,
                                                       final Instant publishedTime) {
        return new FileGroupQueueItemMetadata(
                queueName,
                itemId,
                0,
                Objects.requireNonNull(publishedTime, "publishedTime"),
                null,
                null,
                Map.of());
    }

    public FileGroupQueueItemMetadata withReceiveAttempt(final Instant receiveTime) {
        final Instant nonNullReceiveTime = Objects.requireNonNull(receiveTime, "receiveTime");
        return new FileGroupQueueItemMetadata(
                queueName,
                itemId,
                attemptNumber + 1,
                publishedTime,
                firstReceivedTime == null
                        ? nonNullReceiveTime
                        : firstReceivedTime,
                nonNullReceiveTime,
                attributes);
    }

    public FileGroupQueueItemMetadata withAttribute(final String key,
                                                    final String value) {
        final String nonBlankKey = requireNonBlank(key, "key");
        Objects.requireNonNull(value, "value");

        final Map<String, String> newAttributes = new HashMap<>(attributes);
        newAttributes.put(nonBlankKey, value);

        return new FileGroupQueueItemMetadata(
                queueName,
                itemId,
                attemptNumber,
                publishedTime,
                firstReceivedTime,
                lastReceivedTime,
                newAttributes);
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
