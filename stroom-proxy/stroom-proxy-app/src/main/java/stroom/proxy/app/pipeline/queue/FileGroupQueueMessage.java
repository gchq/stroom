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

import stroom.proxy.app.pipeline.store.FileStoreLocation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What a queue carries: the location of a committed file group, and enough about it to route and
 * trace it. The data is in the store; this is a few hundred bytes.
 *
 * @param feed The aggregation key with {@code type}, when the group is single-feed; null for a
 *             multi-feed group on its way to be split. A partitioned backend keys on it so every
 *             part of a feed reaches the same consuming node, whose aggregate stage can then build
 *             whole aggregates rather than fragments.
 */
@JsonPropertyOrder(alphabetic = true)
public record FileGroupQueueMessage(
        @JsonProperty(value = "schemaVersion", required = true)
        int schemaVersion,
        @JsonProperty(value = "messageId", required = true)
        String messageId,
        @JsonProperty(value = "fileStoreLocation", required = true)
        FileStoreLocation fileStoreLocation,
        @JsonProperty("feed")
        String feed,
        @JsonProperty("type")
        String type,
        @JsonProperty(value = "producingStage", required = true)
        String producingStage,
        @JsonProperty(value = "producerId", required = true)
        String producerId,
        @JsonProperty(value = "createdTime", required = true)
        Instant createdTime,
        @JsonProperty("traceId")
        String traceId,
        @JsonProperty("attributes")
        Map<String, String> attributes) {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    @JsonCreator
    public FileGroupQueueMessage {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported queue message schemaVersion: " + schemaVersion);
        }
        messageId = requireNonBlank(messageId, "messageId");
        fileStoreLocation = Objects.requireNonNull(fileStoreLocation, "fileStoreLocation");
        feed = normaliseOptional(feed);
        type = normaliseOptional(type);
        producingStage = requireNonBlank(producingStage, "producingStage");
        producerId = requireNonBlank(producerId, "producerId");
        createdTime = Objects.requireNonNull(createdTime, "createdTime");
        traceId = normaliseOptional(traceId);
        attributes = attributes == null || attributes.isEmpty()
                ? Map.of()
                : Map.copyOf(attributes);
    }

    public static FileGroupQueueMessage create(final FileStoreLocation fileStoreLocation,
                                               final String feed,
                                               final String type,
                                               final String producingStage,
                                               final String producerId,
                                               final String traceId,
                                               final Map<String, String> attributes) {
        return new FileGroupQueueMessage(
                CURRENT_SCHEMA_VERSION,
                UUID.randomUUID().toString(),
                fileStoreLocation,
                feed,
                type,
                producingStage,
                producerId,
                Instant.now(),
                traceId,
                attributes);
    }

    /**
     * The same message with one attribute set. Identity and creation time are kept, so a requeued
     * message stays traceable as the message it is rather than looking like a new arrival.
     */
    public FileGroupQueueMessage withAttribute(final String key, final String value) {
        final Map<String, String> updated = new LinkedHashMap<>(attributes);
        updated.put(key, value);
        return new FileGroupQueueMessage(
                schemaVersion, messageId, fileStoreLocation, feed, type,
                producingStage, producerId, createdTime, traceId, updated);
    }

    /**
     * What a partitioned backend keys on: the aggregation key when there is one, so a feed's parts
     * stay together, and the message id otherwise, so unkeyed messages spread.
     */
    @JsonIgnore
    public String partitionKey() {
        return feed != null
                ? feed + ":" + (type != null ? type : "")
                : messageId;
    }

    private static String requireNonBlank(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String normaliseOptional(final String value) {
        return value == null || value.isBlank()
                ? null
                : value;
    }
}
