/*
 * Copyright 2025 Crown Copyright
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

package stroom.annotation.shared;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface AnnotationTagFields {

    String ID = "Id";
    String UUID = "UUID";
    String NAME = "Name";
    String TYPE_ID = "TypeId";

    QueryField ID_FIELD = QueryField.createId(ID);
    QueryField UUID_FIELD = QueryField.createId(UUID);
    // Was createDate, which declared DEFAULT_DATE on what is ANNOTATION_TAG.NAME - a text column
    // reached through an identity converter. The wrong FieldType also made the expression editor
    // offer a date picker for a tag name. Found by arming the capability check: the annotation tag
    // screens filter this field with CONTAINS, which DEFAULT_DATE does not declare.
    QueryField NAME_FIELD = QueryField.createSqlText(NAME);
    QueryField TYPE_ID_FIELD = QueryField.createText(TYPE_ID);

    /**
     * What the annotation tag quick filter resolves against.
     * <p>
     * TYPE_ID is deliberately absent. The type is carried on
     * {@link FindAnnotationTagCriteria#getType()} as a structural constraint, so offering it as a
     * qualifier here would let a user type {@code typeid:label} and change what the chooser is
     * showing rather than narrowing it.
     */
    List<QueryField> QUICK_FILTER_DEFAULT_FIELDS = Arrays.asList(NAME_FIELD);

    List<QueryField> QUICK_FILTER_FIELDS = Arrays.asList(NAME_FIELD, UUID_FIELD);

    List<QueryField> FIELDS = Arrays.asList(
            ID_FIELD,
            UUID_FIELD,
            NAME_FIELD,
            TYPE_ID_FIELD);
    Map<String, QueryField> FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
}
