/*
 * Copyright 2018 Crown Copyright
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

package stroom.storedquery.impl.db;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.shared.StoredQuery;
import stroom.query.api.v2.Query;

import java.io.IOException;

public class StoredQuerySerialiser {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoredQuerySerialiser.class);

    public static StoredQuery serialise(final StoredQuery storedQuery) {
        try {
            if (storedQuery != null) {
                if (storedQuery.getQuery() == null) {
                    storedQuery.setData(null);
                } else {
                    final ObjectMapper mapper = createMapper(true);
                    final String json = mapper.writeValueAsString(storedQuery.getQuery());
                    storedQuery.setData(json);
                }
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return storedQuery;
    }

    static StoredQuery deserialise(final StoredQuery storedQuery) {
        try {
            if (storedQuery != null) {
                if (storedQuery.getData() == null) {
                    storedQuery.setQuery(null);
                } else {
                    final ObjectMapper mapper = createMapper(true);
                    final Query data = mapper.readValue(storedQuery.getData(), Query.class);
                    storedQuery.setQuery(data);
                }
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return storedQuery;
    }

    private static ObjectMapper createMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }
}
