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

package stroom.search.elastic.shared;

import stroom.query.api.datasource.FieldType;

import java.util.HashMap;
import java.util.Map;

public class ElasticNativeTypes {

    private static final Map<String, FieldType> NATIVE_TYPE_MAP = new HashMap<>();

    static {
        NATIVE_TYPE_MAP.put("boolean", FieldType.BOOLEAN);

        NATIVE_TYPE_MAP.put("integer", FieldType.INTEGER);
        NATIVE_TYPE_MAP.put("short", FieldType.INTEGER);
        NATIVE_TYPE_MAP.put("byte", FieldType.INTEGER);
        NATIVE_TYPE_MAP.put("version", FieldType.INTEGER);

        NATIVE_TYPE_MAP.put("long", FieldType.LONG);
        NATIVE_TYPE_MAP.put("unsigned_long", FieldType.LONG);

        NATIVE_TYPE_MAP.put("float", FieldType.FLOAT);
        NATIVE_TYPE_MAP.put("half_float", FieldType.FLOAT);
        NATIVE_TYPE_MAP.put("scaled_float", FieldType.FLOAT);

        NATIVE_TYPE_MAP.put("double", FieldType.DOUBLE);

        NATIVE_TYPE_MAP.put("date", FieldType.DATE);
        NATIVE_TYPE_MAP.put("date_nanos", FieldType.DATE);

        NATIVE_TYPE_MAP.put("text", FieldType.TEXT);

        NATIVE_TYPE_MAP.put("keyword", FieldType.KEYWORD);
        NATIVE_TYPE_MAP.put("constant_keyword", FieldType.KEYWORD);
        NATIVE_TYPE_MAP.put("wildcard", FieldType.KEYWORD);

        NATIVE_TYPE_MAP.put("ip", FieldType.IPV4_ADDRESS);

        NATIVE_TYPE_MAP.put("dense_vector", FieldType.DENSE_VECTOR);
    }

    /**
     * Given a native Elasticsearch data type, return an equivalent Stroom field type
     */
    public static FieldType fromNativeType(final String fieldName, final String nativeType)
            throws UnsupportedTypeException {
        final FieldType fieldType = NATIVE_TYPE_MAP.get(nativeType);

        if (fieldType == null) {
            throw new UnsupportedTypeException("Field '" + fieldName + "' has an unsupported mapping type '" +
                    nativeType + "'");
        }
        return fieldType;
    }
}
