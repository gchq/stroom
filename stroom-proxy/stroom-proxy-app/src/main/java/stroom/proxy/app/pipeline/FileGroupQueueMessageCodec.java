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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * JSON codec for the universal proxy file-group queue message contract.
 * <p>
 * Queue implementations should use this codec to serialise and deserialise
 * {@link FileGroupQueueMessage} instances so the on-wire/on-disk message format
 * is consistent across local filesystem, Kafka, and SQS queue types.
 * </p>
 */
public class FileGroupQueueMessageCodec {

    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final ObjectMapper objectMapper;

    public FileGroupQueueMessageCodec() {
        this(DEFAULT_OBJECT_MAPPER);
    }

    public FileGroupQueueMessageCodec(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String toJson(final FileGroupQueueMessage message) throws IOException {
        Objects.requireNonNull(message, "message");
        return objectMapper.writeValueAsString(message);
    }

    public byte[] toBytes(final FileGroupQueueMessage message) throws IOException {
        return toJson(message).getBytes(StandardCharsets.UTF_8);
    }

    public FileGroupQueueMessage fromJson(final String json) throws IOException {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("json must not be blank");
        }
        return objectMapper.readValue(json, FileGroupQueueMessage.class);
    }

    public FileGroupQueueMessage fromBytes(final byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
        return fromJson(new String(bytes, StandardCharsets.UTF_8));
    }

    public String toJsonUnchecked(final FileGroupQueueMessage message) {
        try {
            return toJson(message);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialise file group queue message", e);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to serialise file group queue message", e);
        }
    }

    public byte[] toBytesUnchecked(final FileGroupQueueMessage message) {
        try {
            return toBytes(message);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialise file group queue message", e);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to serialise file group queue message", e);
        }
    }

    public FileGroupQueueMessage fromJsonUnchecked(final String json) {
        try {
            return fromJson(json);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to deserialise file group queue message", e);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to deserialise file group queue message", e);
        }
    }

    public FileGroupQueueMessage fromBytesUnchecked(final byte[] bytes) {
        try {
            return fromBytes(bytes);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to deserialise file group queue message", e);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to deserialise file group queue message", e);
        }
    }
}
