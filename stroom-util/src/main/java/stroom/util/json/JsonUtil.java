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

package stroom.util.json;

import stroom.util.exception.ThrowingConsumer;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class JsonUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtil.class);

    private static final ObjectMapper OBJECT_MAPPER = createMapper(true);
    private static final ObjectMapper NO_INDENT_MAPPER = createMapper(false);

    public static String writeValueAsString(final Object object) {
        return writeValueAsString(object, true);
    }

    public static String writeValueAsString(final Object object, final boolean indent) {
        String json = null;

        if (object != null) {
            try {
                if (indent) {
                    json = getMapper().writeValueAsString(object);
                } else {
                    json = getNoIndentMapper().writeValueAsString(object);
                }
            } catch (final JsonProcessingException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        return json;
    }

    /**
     * Serialise object to the file described by path
     */
    public static void writeValue(final Path outputFile, final Object object) {
        Preconditions.checkNotNull(object);
        Preconditions.checkNotNull(outputFile);
        try {
            getMapper().writeValue(outputFile.toFile(), object);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(String.format("Error serialising object %s to json",
                    object), e);
        } catch (final IOException e) {
            throw new UncheckedIOException(String.format("Error writing json to file %s",
                    outputFile.toAbsolutePath()), e);
        }
    }

    public static <T> T readValue(final String content, final Class<T> valueType) {
        Preconditions.checkNotNull(content);
        Preconditions.checkNotNull(valueType);
        try {
            return getMapper().readValue(content, valueType);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(String.format("Error deserialising object %s %s",
                    content, e.getMessage()), e);
        }
    }

    public static ObjectMapper getMapper() {
        return OBJECT_MAPPER;
    }

    public static ObjectMapper getNoIndentMapper() {
        return NO_INDENT_MAPPER;
    }

    private static ObjectMapper createMapper(final boolean indent) {
//        final SimpleModule module = new SimpleModule();
//        module.addSerializer(Double.class, new MyDoubleSerialiser());

        final ObjectMapper mapper = new ObjectMapper();
//        mapper.registerModule(module);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);
        return mapper;
    }


    /**
     * Gets the entries from the passed json that are children of the root object.
     * Avoids having to parse the whole object if you only want to get a few keys.
     * Does not descend into child objects/arrays.
     * If the root is an array, returns an empty map.
     *
     * @param json The json to parse.
     * @param keys The fields to find.
     * @return The entries with keys matching keys
     */
    public static Map<String, String> getEntries(final String json,
                                                 final String... keys) {
        if (keys == null || keys.length == 0) {
            return Collections.emptyMap();
        } else {
            return getEntries(json, Set.of(keys));
        }
    }

    /**
     * Gets a value from the passed json that is a child of the root object.
     * Avoids having to parse the whole object if you only want to get one key.
     * Does not descend into child objects/arrays.
     * If the root is an array, returns an empty map.
     *
     * @param json The json to parse.
     * @param key  The field to find the value for.
     * @return The value for the supplied key.
     */
    public static Optional<String> getValue(final String json,
                                            final String key) {
        if (NullSafe.isBlankString(key) || NullSafe.isBlankString(json)) {
            return Optional.empty();
        } else {
            final Map<String, String> entries = getEntries(json, key);
            return Optional.ofNullable(entries.get(key));
        }
    }

    /**
     * Gets the entries from the passed json that are children of the root object.
     * Avoids having to parse the whole object if you only want to get a few keys.
     * Does not descend into child objects/arrays.
     * If the root is an array, returns an empty map.
     *
     * @param json The json to parse.
     * @param keys The fields to find.
     * @return The entries with keys matching keys
     */
    public static Map<String, String> getEntries(final String json,
                                                 final Set<String> keys) {
        if (NullSafe.isBlankString(json) || !NullSafe.hasItems(keys)) {
            return Collections.emptyMap();
        } else {
            final Set<String> remainingFields = new HashSet<>(keys);
            final Map<String, String> results = new HashMap<>();

            final JsonFactory jFactory = new JsonFactory();
            JsonParser jParser = null;
            JsonToken jsonToken;
            JsonToken startRootToken = null;
            JsonToken endRootToken = null;
            try {
                jParser = jFactory.createParser(json);
                while (true) {
                    jsonToken = jParser.nextToken();
                    LOGGER.trace("jsonToken: {}", jsonToken);

                    if (jsonToken == null
                        || (startRootToken == null && !(jsonToken == JsonToken.START_OBJECT))
                        || jsonToken == endRootToken
                        || remainingFields.isEmpty()) {
                        break;
                    }

                    if (jsonToken == JsonToken.FIELD_NAME) {
                        final String fieldName = jParser.getCurrentName();
                        if (remainingFields.contains(fieldName)) {
                            final String value = jParser.nextTextValue();
                            if (value != null) {
                                results.put(fieldName, value);
                                remainingFields.remove(fieldName);
                            }
                        }
                    } else if (jsonToken == JsonToken.START_OBJECT && startRootToken != null) {
                        // Skip over this complex sub-object
                        while (jsonToken != JsonToken.END_OBJECT) {
                            jsonToken = jParser.nextToken();
                        }
                    } else if (jsonToken == JsonToken.START_ARRAY) {
                        // Skip over this array
                        while (jsonToken != JsonToken.END_ARRAY) {
                            jsonToken = jParser.nextToken();
                        }
                    }

                    if (startRootToken == null) {
                        startRootToken = jsonToken;
                        endRootToken = JsonToken.END_OBJECT;
                    }
                }
            } catch (final IOException e) {
                throw new RuntimeException(LogUtil.message(
                        "Error extracting fields '{}' from json:\n{}", keys, json));
            } finally {
                NullSafe.consume(jParser, ThrowingConsumer.unchecked(JsonParser::close));
            }
            return results;
        }
    }
}
