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
import stroom.util.shared.string.CIKey;
import stroom.util.string.MultiCaseMap.MultipleMatchException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    IndexField getMatchingField(final CIKey ciKey);
}
