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

package stroom.processor.shared;

import stroom.docref.DocRef;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.QueryField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProcessorFilterFields {

    public static final String PROCESSOR_FILTERS_TYPE = "ProcessorFilters";
    public static final DocRef PROCESSOR_FILTERS_DOC_REF = DocRef.builder()
            .type(PROCESSOR_FILTERS_TYPE)
            .uuid(PROCESSOR_FILTERS_TYPE)
            .name(PROCESSOR_FILTERS_TYPE)
            .build();

    public static final String FIELD_ID = "Id";

    private static final List<QueryField> FIELDS = new ArrayList<>();
    private static final Map<String, QueryField> FIELD_MAP;

    public static final QueryField ID = QueryField.createId("Processor Filter Id");
    //    public static final QueryField CREATE_USER = QueryField.createText("Processor Filter Create User");
    public static final QueryField OWNER_UUID = QueryField.createText("Processor Filter Owner User UUID");
    public static final QueryField LAST_POLL_MS = QueryField.createLong("Processor Filter Last Poll Ms");
    public static final QueryField PRIORITY = QueryField.createInteger("Processor Filter Priority");
    public static final QueryField ENABLED = QueryField.createBoolean("Processor Filter Enabled");
    public static final QueryField DELETED = QueryField.createBoolean("Processor Filter Deleted");
    public static final QueryField EXPORT = QueryField.createBoolean("Processor Filter Export");
    public static final QueryField UUID = QueryField.createText("Processor Filter UUID");
    public static final QueryField RUN_AS_USER = QueryField
            .builder()
            .fldName("Run As User")
            .conditionSet(ConditionSet.RUN_AS_USER)
            .queryable(true)
            .build();

    static {
        FIELDS.add(ID);
        FIELDS.add(OWNER_UUID);
        FIELDS.add(LAST_POLL_MS);
        FIELDS.add(PRIORITY);
        FIELDS.add(ENABLED);
        FIELDS.add(DELETED);
        FIELDS.add(EXPORT);
        FIELDS.add(UUID);
        FIELDS.add(RUN_AS_USER);

        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
    }

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, QueryField> getFieldMap() {
        return FIELD_MAP;
    }
}
