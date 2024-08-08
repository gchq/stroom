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

package stroom.pipeline.shared;

import stroom.datasource.api.v2.ConditionSet;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.util.shared.string.CIKeys;

import java.util.Arrays;
import java.util.List;

public class ReferenceDataFields {

    public static final DocRef REF_STORE_PSEUDO_DOC_REF = new DocRef(
            "Searchable",
            "Reference Data Store",
            "Reference Data Store (This Node Only)");

    public static final QueryField FEED_NAME_FIELD = QueryField
            .builder()
            .fldName(CIKeys.FEED__NAME)
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.REF_DATA_TEXT)
            .queryable(true)
            .build();
    public static final QueryField KEY_FIELD = QueryField
            .builder()
            .fldName(CIKeys.KEY)
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.REF_DATA_TEXT)
            .queryable(true)
            .build();
    public static final QueryField VALUE_FIELD = QueryField
            .builder()
            .fldName(CIKeys.VALUE)
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.REF_DATA_TEXT)
            .queryable(true)
            .build();
    public static final QueryField VALUE_REF_COUNT_FIELD = QueryField.createInteger(
            CIKeys.VALUE__REFERENCE__COUNT, false);
    public static final QueryField MAP_NAME_FIELD = QueryField
            .builder()
            .fldName(CIKeys.MAP__NAME)
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.REF_DATA_TEXT)
            .queryable(true)
            .build();
    public static final QueryField CREATE_TIME_FIELD = QueryField
            .createDate(CIKeys.CREATE__TIME, true);
    public static final QueryField EFFECTIVE_TIME_FIELD = QueryField
            .createDate(CIKeys.EFFECTIVE__TIME, true);
    public static final QueryField LAST_ACCESSED_TIME_FIELD = QueryField
            .createDate(CIKeys.LAST__ACCESSED__TIME, true);
    public static final QueryField PIPELINE_FIELD = QueryField
            .builder()
            .fldName(CIKeys.REFERENCE__LOADER__PIPELINE)
            .fldType(FieldType.DOC_REF)
            .conditionSet(ConditionSet.REF_DATA_DOC_REF)
            .docRefType(PipelineDoc.DOCUMENT_TYPE)
            .queryable(true)
            .build();
    public static final QueryField PROCESSING_STATE_FIELD = QueryField
            .createText(CIKeys.PROCESSING__STATE, false);
    public static final QueryField STREAM_ID_FIELD = QueryField.createId(
            CIKeys.STREAM__ID, false);
    public static final QueryField PART_NO_FIELD = QueryField.createLong(
            CIKeys.PART__NUMBER, false);
    public static final QueryField PIPELINE_VERSION_FIELD = QueryField.createText(
            CIKeys.PIPELINE__VERSION, false);

    public static final List<QueryField> FIELDS = Arrays.asList(
            FEED_NAME_FIELD,
            KEY_FIELD,
            VALUE_FIELD,
            VALUE_REF_COUNT_FIELD,
            MAP_NAME_FIELD,
            CREATE_TIME_FIELD,
            EFFECTIVE_TIME_FIELD,
            LAST_ACCESSED_TIME_FIELD,
            PIPELINE_FIELD,
            PROCESSING_STATE_FIELD,
            STREAM_ID_FIELD,
            PART_NO_FIELD,
            PIPELINE_VERSION_FIELD);

    public static List<QueryField> getFields() {
        return FIELDS;
    }
}
