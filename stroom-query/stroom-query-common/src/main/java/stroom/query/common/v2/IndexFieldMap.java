/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.common.v2;

import stroom.datasource.api.v2.IndexField;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.string.CIKey;
import stroom.util.string.MultiCaseMap.MultipleMatchException;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Holds a map of case-sensitive field names to their respective {@link IndexField} object.
 * All field names held in the map are equal when ignoring case.
 * <p>
 * This object acts as the link between case-insensitive field naming in stroom and the potentially
 * case-sensitive field naming in the index provider.
 * </p>
 */
public class IndexFieldMap {

    private final CIKey fieldName;
    public final Map<String, IndexField> fieldNameToFieldMap;

    /**
     * @param fieldName           A case-insensitive field name. The string it wraps may or may not exactly match
     *                            one of the field names in the map, but it will match all when ignoring case.
     * @param fieldNameToFieldMap A map of
     */
    public IndexFieldMap(final CIKey fieldName,
                         final Map<String, IndexField> fieldNameToFieldMap) {
        Objects.requireNonNull(fieldName);
        this.fieldName = fieldName;
        this.fieldNameToFieldMap = Objects.requireNonNullElseGet(fieldNameToFieldMap, Collections::emptyMap);

        fieldNameToFieldMap.keySet()
                .forEach(exactFieldName -> {
                    if (!fieldName.equalsIgnoreCase(exactFieldName)) {
                        throw new IllegalArgumentException(LogUtil.message(
                                "fieldName '{}' and exactFieldName '{}' do not match (case insensitive)"));
                    }
                });
    }

    public static IndexFieldMap empty(final CIKey fieldName) {
        return new IndexFieldMap(fieldName, Collections.emptyMap());
    }

    public CIKey getFieldName() {
        return fieldName;
    }

    /**
     * @return The value associated with ciKey (case-insensitive) or null if there
     * is no associated value. If ciKey matches multiple keys with different case then
     * it will attempt to do a case-sensitive match. If there is no case-sensitive match
     * it will throw a {@link MultipleMatchException}.
     * @throws MultipleMatchException if multiple values are associated with ciKey
     */
    public IndexField getCaseSensitive(final String fieldName) {
        if (this.fieldName.equalsIgnoreCase()) {
            if (NullSafe.isEmptyMap(fieldNameToFieldMap)) {
                return null;
            } else {
                final int count = fieldNameToFieldMap.size();
                if (count == 0) {
                    return null;
                } else if (count == 1) {
                    // There is only one, so we don't need to check for an exact match
                    return fieldNameToFieldMap.values().iterator().next();
                } else {
                    final IndexField exactMatch = fieldNameToFieldMap.get(fieldName);
                    if (exactMatch != null) {
                        return exactMatch;
                    } else {
                        throw new MultipleMatchException("Multiple fields (" + fieldNameToFieldMap.keySet()
                                + ") exist with the same name (ignoring case). You must use the exact case " +
                                "for the field that you require.");
                    }
                }
            }
        }
    }

    /**
     * @return The value associated with ciKey (case-insensitive) or null if there
     * is no associated value. If ciKey matches multiple keys with different case then
     * it will attempt to do a case-sensitive match. If there is no case-sensitive match
     * it will throw a {@link MultipleMatchException}.
     * @throws MultipleMatchException if multiple values are associated with ciKey
     */
    public IndexField getCaseSensitive(final CIKey ciKey) {
        if (NullSafe.isEmptyMap(fieldNameToFieldMap)) {
            return null;
        } else {
            final int count = fieldNameToFieldMap.size();
            if (count == 0) {
                // Should never happen.  Shouldn't have an empty subMap
                throw new RuntimeException("Empty subMap");
            } else if (count == 1) {
                // There is only one, so we don't need to check for an exact match
                return fieldNameToFieldMap.values().iterator().next();
            } else {
                final IndexField exactMatch = fieldNameToFieldMap.get(ciKey.get());
                if (exactMatch != null) {
                    return exactMatch;
                } else {
                    throw new MultipleMatchException("Multiple values (" + fieldNameToFieldMap.size()
                            + ") exist for case-insensitive key '" + ciKey.get() + "'");
                }
            }
        }
    }
}
