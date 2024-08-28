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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Holds a map of case-sensitive field names to their respective {@link IndexField} object.
 * ALL field names held in the map are equal when ignoring case.
 * <p>
 * This object acts as the link between case-insensitive field naming in stroom and the potentially
 * case-sensitive field naming in the index provider.
 * </p>
 */
public class IndexFieldMapImpl implements IndexFieldMap {

    private final CIKey caseInsenseFieldName;
    private final Map<String, ? extends IndexField> caseSenseFieldNameToFieldMap;

    IndexFieldMapImpl(final CIKey fieldName,
                      final Map<String, ? extends IndexField> fieldNameToFieldMap) {
        Objects.requireNonNull(fieldName);
        this.caseInsenseFieldName = fieldName;
        this.caseSenseFieldNameToFieldMap = Objects.requireNonNullElseGet(
                fieldNameToFieldMap, Collections::emptyMap);

        this.caseSenseFieldNameToFieldMap.keySet()
                .forEach(exactFieldName -> {
                    if (!fieldName.equalsIgnoreCase(exactFieldName)) {
                        throw new IllegalArgumentException(LogUtil.message(
                                "fieldName '{}' and exactFieldName '{}' do not match (case insensitive)"));
                    }
                });
    }

    @Override
    public CIKey getFieldName() {
        return caseInsenseFieldName;
    }

    @Override
    public List<IndexField> getFields() {
        return caseSenseFieldNameToFieldMap.values()
                .stream()
                .map(indexField -> (IndexField) indexField)
                .toList();
    }

    /**
     * @return The {@link IndexField} associated with ciKey (case-insensitive) or null if there
     * is no associated value. If ciKey matches multiple keys with different case then
     * it will attempt to do a case-sensitive match. If there is no case-sensitive match
     * it will throw a {@link MultipleMatchException}.
     * @throws MultipleMatchException if multiple values are associated with ciKey
     */
    @Override
    public IndexField getMatchingField(final CIKey ciKey) {
        if (caseInsenseFieldName.equals(ciKey)) {
            if (NullSafe.isEmptyMap(caseSenseFieldNameToFieldMap)) {
                return null;
            } else {
                final int count = caseSenseFieldNameToFieldMap.size();
                if (count == 0) {
                    return null;
                } else if (count == 1) {
                    // There is only one, so we don't need to check for an exact match
                    return caseSenseFieldNameToFieldMap.values().iterator().next();
                } else {
                    final IndexField exactMatch = caseSenseFieldNameToFieldMap.get(ciKey.get());
                    if (exactMatch != null) {
                        return exactMatch;
                    } else {
                        throw new MultipleMatchException("Multiple values (" + caseSenseFieldNameToFieldMap.size()
                                + ") exist for case-insensitive field '" + ciKey.get() + "'");
                    }
                }
            }
        } else {
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    static class SingleIndexField implements IndexFieldMap {

        private final CIKey fieldName;
        private final IndexField indexField;

        public SingleIndexField(final CIKey fieldName,
                                final IndexField indexField) {
            this.fieldName = Objects.requireNonNull(fieldName);
            this.indexField = indexField;

            if (indexField != null
                    && !fieldName.equalsIgnoreCase(indexField.getFldName())) {
                throw new IllegalArgumentException(LogUtil.message(
                        "fieldName '{}' and indexField.fldName '{}' do not match (case insensitive)"));
            }
        }

        @Override
        public CIKey getFieldName() {
            return fieldName;
        }

        @Override
        public List<IndexField> getFields() {
            return List.of(indexField);
        }

        @Override
        public IndexField getMatchingField(final CIKey ciKey) {
            if (fieldName.equals(ciKey)) {
                return indexField;
            } else {
                return null;
            }
        }
    }


    // --------------------------------------------------------------------------------


    static class EmptyIndexFieldMap implements IndexFieldMap {

        private final CIKey fieldName;

        EmptyIndexFieldMap(final CIKey fieldName) {
            this.fieldName = fieldName;
        }

        @Override
        public CIKey getFieldName() {
            return fieldName;
        }

        @Override
        public List<IndexField> getFields() {
            return Collections.emptyList();
        }

        @Override
        public IndexField getMatchingField(final CIKey ciKey) {
            return null;
        }
    }
}
