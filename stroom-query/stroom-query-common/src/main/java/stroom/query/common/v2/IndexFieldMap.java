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
import stroom.query.common.v2.IndexFieldMapImpl.EmptyIndexFieldMap;
import stroom.query.common.v2.IndexFieldMapImpl.SingleIndexField;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.string.CIKey;
import stroom.util.string.MultiCaseMap.MultipleMatchException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Holds one or more {@link IndexField}s that ALL share the same name when ignoring case.
 * Provides a common means to get a field for a given field name, initially ignoring case,
 * but taking case into account if the map contains multiple fields (with the same
 * case-insensitive name).
 * <p>
 * E.g. it may hold fields 'foo' and 'FOO'. Calling {@link IndexFieldMap#getClosestMatchingField(String)}
 * with 'foo' would return field 'foo', but calling that with 'Foo' would throw an exception as there
 * is no exact match.
 * </p>
 */
public interface IndexFieldMap {

    /**
     * @param fieldName           A case-insensitive field name. The string it wraps may or may not exactly match
     *                            one of the field names in the map, but it must match all when ignoring case.
     * @param fieldNameToFieldMap A map of case-sensitive field names to their respective {@link IndexField} object.
     *                            All keys must be equal ignoring case to each other and to fieldName.
     */
    static IndexFieldMap fromFieldsMap(final CIKey fieldName,
                                       final Map<String, ? extends IndexField> fieldNameToFieldMap) {
        final int size = NullSafe.size(fieldNameToFieldMap);
        if (size == 0) {
            return new EmptyIndexFieldMap(fieldName);
        } else if (size == 1) {
            return IndexFieldMap.forSingleField(fieldName, fieldNameToFieldMap.values().iterator().next());
        } else {
            return new IndexFieldMapImpl(fieldName, fieldNameToFieldMap);
        }
    }

    /**
     * @param fieldName   A case-insensitive field name. The string it wraps may or may not exactly match
     *                    one of the field names in the map, but it must match all when ignoring case.
     * @param indexFields A collection of {@link IndexField} whose field names are ALL equal (ignoring case).
     */
    static IndexFieldMap fromFieldList(final CIKey fieldName,
                                       final Collection<? extends IndexField> indexFields) {
        final int size = NullSafe.size(indexFields);
        if (size == 0) {
            return new EmptyIndexFieldMap(fieldName);
        } else if (size == 1) {
            return IndexFieldMap.forSingleField(fieldName, indexFields.iterator().next());
        } else {
            return new IndexFieldMapImpl(fieldName, indexFields.stream()
                    .collect(Collectors.toMap(IndexField::getFldName, Function.identity())));
        }
    }

    /**
     * Constructor for use when you know there is only one {@link IndexField} corresponding
     * to fieldName (ignoring case).
     */
    static IndexFieldMap forSingleField(final CIKey fieldName,
                                        final IndexField indexField) {
        if (indexField == null) {
            return new EmptyIndexFieldMap(fieldName);
        } else {
            return new SingleIndexField(fieldName, indexField);
        }
    }

    static IndexFieldMap empty(final CIKey fieldName) {
        return new EmptyIndexFieldMap(fieldName);
    }

    static IndexFieldMap merge(final IndexFieldMap map1, final IndexFieldMap map2) {
        if (map1 == null && map2 == null) {
            return null;
        } else if (map1 == null) {
            return map2;
        } else if (map2 == null) {
            return map1;
        } else {
            if (!Objects.equals(map1.getFieldName(), map2.getFieldName())) {
                throw new IllegalArgumentException(LogUtil.message(
                        "Names do not match. map1: '{}', map2: '{}'",
                        map1.getFieldName(), map2.getFieldName()));
            }

            final Map<String, IndexField> combinedMap = Stream.of(map1, map2)
                    .map(IndexFieldMap::getFields)
                    .flatMap(List::stream)
                    .collect(Collectors.toMap(
                            IndexField::getFldName, Function.identity()));
            return IndexFieldMap.fromFieldsMap(map1.getFieldName(), combinedMap);
        }
    }

    /**
     * @return The case-insensitive field name.
     */
    CIKey getFieldName();

    /**
     * @return The list of {@link IndexField} matching the case-insensitive {@link CIKey} fieldName.
     */
    List<IndexField> getFields();

    /**
     * @return The {@link IndexField} associated with ciKey (case-insensitive) or null if there
     * is no associated value. If ciKey matches multiple keys with different case then
     * it will attempt to do a case-sensitive match. If there is no case-sensitive match
     * it will throw a {@link MultipleMatchException}.
     * @throws MultipleMatchException if multiple values are associated with ciKey
     */
    IndexField getClosestMatchingField(final String caseSensitiveFieldName);

    /**
     * @return The {@link IndexField} with a name exactly matching caseSensitiveFieldName, else null.
     */
    IndexField getExactMatchingField(final String caseSensitiveFieldName);
}
