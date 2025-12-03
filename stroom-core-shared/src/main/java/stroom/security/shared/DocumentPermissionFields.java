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

package stroom.security.shared;

import stroom.docref.DocRef;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DocumentPermissionFields {

    public static final String FIELD_EXPLICIT_DOC_PERMISSION = "explicitdocperm";
    public static final String FIELD_EFFECTIVE_DOC_PERMISSION = "effectivedocperm";

    public static final String DOCUMENT_STORE_TYPE = "DocumentStore";
    public static final DocRef DOCUMENT_STORE_DOC_REF = DocRef.builder()
            .type(DOCUMENT_STORE_TYPE)
            .uuid(DOCUMENT_STORE_TYPE)
            .name(DOCUMENT_STORE_TYPE)
            .build();

    private static final List<QueryField> FIELDS = new ArrayList<>();
    private static final Map<String, QueryField> ALL_FIELD_MAP;

    public static final QueryField DOCUMENT = QueryField
            .builder()
            .fldName("Document")
            .fldType(FieldType.DOC_REF)
            .conditionSet(ConditionSet.DOC_DOC_IS)
            .queryable(true)
            .build();
    public static final QueryField CHILDREN = QueryField
            .builder()
            .fldName("Children")
            .fldType(FieldType.DOC_REF)
            .conditionSet(ConditionSet.DOC_DOC_OF)
            .queryable(true)
            .build();
    public static final QueryField DESCENDANTS = QueryField
            .builder()
            .fldName("Descendants")
            .fldType(FieldType.DOC_REF)
            .conditionSet(ConditionSet.DOC_DOC_OF)
            .queryable(true)
            .build();
    public static final QueryField USER = QueryField
            .builder()
            .fldName("User")
            .fldType(FieldType.USER_REF)
            .conditionSet(ConditionSet.DOC_USER_IS)
            .queryable(true)
            .build();
    public static final QueryField DOCUMENT_TYPE = QueryField
            .builder()
            .fldName("DocumentType")
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();
    public static final QueryField DOCUMENT_NAME = QueryField
            .builder()
            .fldName("DocumentName")
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();
    public static final QueryField DOCUMENT_UUID = QueryField
            .builder()
            .fldName("DocumentUUID")
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();
    public static final QueryField DOCUMENT_TAG = QueryField
            .builder()
            .fldName("DocumentTag")
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();

    public static final QueryField EXPLICIT_DOC_PERMISSION = QueryField.createText(FIELD_EXPLICIT_DOC_PERMISSION);
    public static final QueryField EFFECTIVE_DOC_PERMISSION = QueryField.createText(FIELD_EFFECTIVE_DOC_PERMISSION);

    static {
        FIELDS.add(DOCUMENT);
        FIELDS.add(CHILDREN);
        FIELDS.add(DESCENDANTS);
        FIELDS.add(DOCUMENT_TYPE);
        FIELDS.add(DOCUMENT_NAME);
        FIELDS.add(DOCUMENT_UUID);
        FIELDS.add(DOCUMENT_TAG);
        FIELDS.add(USER);
        FIELDS.add(EXPLICIT_DOC_PERMISSION);
        FIELDS.add(EFFECTIVE_DOC_PERMISSION);

        ALL_FIELD_MAP = FIELDS.stream()
                .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
    }

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, QueryField> getAllFieldMap() {
        return ALL_FIELD_MAP;
    }
}
