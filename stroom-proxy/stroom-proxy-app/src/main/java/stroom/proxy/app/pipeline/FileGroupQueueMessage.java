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

package stroom.proxy.app.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Universal reference-message contract for proxy file-group queues.
 * <p>
 * Queue implementations must transport this message rather than moving the
 * referenced data. The referenced file group must already have been written to
 * the named {@link FileStoreLocation} before this message is published.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
public record FileGroupQueueMessage(
        @JsonProperty(value = "schemaVersion", required = true)
        int schemaVersion,
        @JsonProperty(value = "messageId", required = true)
        String messageId,
        @JsonProperty(value = "queueName", required = true)
        String queueName,
        @JsonProperty(value = "fileGroupId", required = true)
        String fileGroupId,
        @JsonProperty(value = "fileStoreLocation", required = true)
        FileStoreLocation fileStoreLocation,
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

    public static final int CURRENT_SCHEMA_VERSION = 1;

    @JsonCreator
    public FileGroupQueueMessage {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported queue message schemaVersion: " + schemaVersion);
        }

        messageId = requireNonBlank(messageId, "messageId");
        queueName = requireNonBlank(queueName, "queueName");
        fileGroupId = requireNonBlank(fileGroupId, "fileGroupId");
        fileStoreLocation = Objects.requireNonNull(fileStoreLocation, "fileStoreLocation");
        producingStage = requireNonBlank(producingStage, "producingStage");
        producerId = requireNonBlank(producerId, "producerId");
        createdTime = Objects.requireNonNull(createdTime, "createdTime");
        traceId = normaliseOptional(traceId);
        attributes = attributes == null || attributes.isEmpty()
                ? Map.of()
                : Map.copyOf(attributes);
    }

    public static FileGroupQueueMessage create(final String queueName,
                                               final String fileGroupId,
                                               final FileStoreLocation fileStoreLocation,
                                               final String producingStage,
                                               final String producerId,
                                               final String traceId,
                                               final Map<String, String> attributes) {
        return new FileGroupQueueMessage(
                CURRENT_SCHEMA_VERSION,
                UUID.randomUUID().toString(),
                queueName,
                fileGroupId,
                fileStoreLocation,
                producingStage,
                producerId,
                Instant.now(),
                traceId,
                attributes);
    }

    public static FileGroupQueueMessage create(final String messageId,
                                               final String queueName,
                                               final String fileGroupId,
                                               final FileStoreLocation fileStoreLocation,
                                               final String producingStage,
                                               final String producerId,
                                               final Instant createdTime,
                                               final String traceId,
                                               final Map<String, String> attributes) {
        return new FileGroupQueueMessage(
                CURRENT_SCHEMA_VERSION,
                messageId,
                queueName,
                fileGroupId,
                fileStoreLocation,
                producingStage,
                producerId,
                createdTime,
                traceId,
                attributes);
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String normaliseOptional(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
