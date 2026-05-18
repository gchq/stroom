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

import stroom.util.concurrent.LazyValue;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.EncodingUtil;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class JsonUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtil.class);

    private static final JsonMapper OBJECT_MAPPER = createMapper(true);
    private static final JsonMapper NO_INDENT_MAPPER = createMapper(false);

    // Make them lazy as they are likely used in tests only
    private static final LazyValue<JsonMapper> CONSISTENT_ORDER_MAPPER = LazyValue.initialisedBy(() ->
            createConsistentOrderMapper(true));
    private static final LazyValue<JsonMapper> NO_INDENT_CONSISTENT_ORDER_MAPPER = LazyValue.initialisedBy(() ->
            createConsistentOrderMapper(false));

    public static String writeValueAsString(final Object object) {
        return writeValueAsString(object, true);
    }

    public static String writeValueAsString(final Object object, final boolean indent) {
        String json = null;

        if (object != null) {
            try {
                json = getMapper(indent).writeValueAsString(object);
            } catch (final JacksonException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        return json;
    }

    public static byte[] writeValueAsBytes(final Object object) {
        return writeValueAsBytes(object, true);
    }

    public static byte[] writeValueAsBytes(final Object object, final boolean indent) {
        byte[] jsonBytes = null;
        if (object != null) {
            try {
                jsonBytes = getMapper(indent).writeValueAsBytes(object);
            } catch (final JacksonException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return jsonBytes;
    }

    public static String writeValueAsConsistentString(final Object object) {
        return writeValueAsConsistentString(object, true);
    }

    public static String writeValueAsConsistentString(final Object object, final boolean indent) {
        String json = null;

        if (object != null) {
            try {
                json = getConsistentOrderMapper(indent).writeValueAsString(object);
            } catch (final JacksonException e) {
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
        } catch (final JacksonException e) {
            throw new RuntimeException(String.format("Error serialising object %s to json and writing it to file %s",
                    object, outputFile.toAbsolutePath()), e);
        }
    }

    public static <T> T readValue(final String content, final Class<T> valueType) {
        Preconditions.checkNotNull(content);
        Preconditions.checkNotNull(valueType);
        try {
            return getMapper().readValue(content, valueType);
        } catch (final JacksonException e) {
            throw new RuntimeException(String.format("Error deserialising object %s %s",
                    content, e.getMessage()), e);
        }
    }

    public static <T> T readValue(final byte[] content, final Class<T> valueType) {
        Preconditions.checkNotNull(content);
        Preconditions.checkNotNull(valueType);
        try {
            return getMapper().readValue(content, valueType);
        } catch (final JacksonException e) {
            throw new RuntimeException(String.format("Error deserialising object %s %s",
                    EncodingUtil.asString(content), e.getMessage()), e);
        }
    }

    /**
     * @return A {@link JsonMapper} that won't fail on unknown properties, includes only non-null
     * values and is indented.
     */
    public static JsonMapper getMapper() {
        return OBJECT_MAPPER;
    }

    public static JsonMapper getMapper(final boolean indent) {
        return indent
                ? OBJECT_MAPPER
                : NO_INDENT_MAPPER;
    }

    /**
     * @param indent Whether to pretty print or not
     * @return A {@link JsonMapper} that will serialise with a consistent order, i.e.
     * properties are in alphabetic order rather than declaration order.
     * This {@link JsonMapper} has the same behaviour as that returned by {@link JsonUtil#getMapper(boolean)}
     * except for the property order.
     * <p>
     * <Strong>WARNING:</Strong> There is a performance penalty for this ordering, so this is only intended
     * for use in tests, see {@link tools.jackson.databind.MapperFeature#SORT_CREATOR_PROPERTIES_FIRST}.
     * </p>
     */
    public static JsonMapper getConsistentOrderMapper(final boolean indent) {
        return indent
                ? CONSISTENT_ORDER_MAPPER.getValueWithLocks()
                : NO_INDENT_CONSISTENT_ORDER_MAPPER.getValueWithLocks();
    }

    /**
     * @return A {@link JsonMapper} that won't fail on unknown properties, includes only non-null
     * values and is not indented.
     */
    public static JsonMapper getNoIndentMapper() {
        return NO_INDENT_MAPPER;
    }

    private static JsonMapper createMapper(final boolean indent) {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.INDENT_OUTPUT, indent)
                .changeDefaultPropertyInclusion(incl ->
                        incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .changeDefaultPropertyInclusion(incl ->
                        incl.withContentInclusion(JsonInclude.Include.NON_NULL))
                // JacksonV3 changes the default behaviour for enums to use the toString
                // as the serialised form, so turn that off so we use the name.
                .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
                // JacksonV3 changes the default behaviour for enums to use the toString
                // as the serialised form, so turn that off so we use the name.
                .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
                .build();
    }

    private static JsonMapper createConsistentOrderMapper(final boolean indent) {
        // Include all the config from our standard JsonMapper
        return getMapper(indent)
                .rebuild()
                .enable(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                // With this enabled the props in the ctor always come first (for perf reasons)
                .disable(tools.jackson.databind.MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
                .build();
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

                    if (jsonToken == JsonToken.PROPERTY_NAME) {
                        final String fieldName = jParser.currentName();
                        if (remainingFields.contains(fieldName)) {
                            final String value = jParser.nextStringValue();
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
            } catch (final Exception e) {
                throw new RuntimeException(LogUtil.message(
                        "Error extracting fields '{}' from json:\n{}", keys, json));
            } finally {
                NullSafe.consume(jParser, ThrowingConsumer.unchecked(JsonParser::close));
            }
            return results;
        }
    }
}
