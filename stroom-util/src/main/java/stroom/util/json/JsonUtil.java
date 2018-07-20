/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public final class JsonUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtil.class);

    public static String writeValueAsString(final Object object) {
        String json = null;

        if (object != null) {
            try {
                json = getMapper().writeValueAsString(object);
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
                    object.toString()), e);
        } catch (final IOException e) {
            throw new UncheckedIOException(String.format("Error writing json to file %s",
                    outputFile.toAbsolutePath().toString()), e);
        }
    }

    private static ObjectMapper getMapper() {
        final SimpleModule module = new SimpleModule();
        module.addSerializer(Double.class, new MyDoubleSerialiser());

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(Include.NON_NULL);
        return mapper;
    }
}
