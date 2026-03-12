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

package stroom.analytics.shared;

import stroom.docref.DocRef;
import stroom.query.api.datasource.QueryField;

import java.util.ArrayList;
import java.util.List;

public class ExecutionScheduleFields {
    public static final String TYPE = "ExecutionSchedule";
    public static final DocRef DOC_REF = DocRef.builder()
            .type(TYPE)
            .uuid(TYPE)
            .name("Execution Schedule")
            .build();


    private static final List<QueryField> FIELDS = new ArrayList<>();

    public static final String ID = "Id";
    public static final String NAME = "Name";
    public static final String PARENT_DOC = "Parent Document";
    public static final String PARENT_RULE = "Parent Rule";
    public static final String PARENT_REPORT = "Parent Report";
    public static final String PARENT_DOC_TYPE = "Parent Document Type";
    public static final String SCHEDULE_NAME = "Schedule Name";
    public static final String ENABLED = "Enabled";
    public static final String NODE_NAME = "Node Name";
    public static final String SCHEDULE = "Schedule";
    public static final String SCHEDULE_TYPE = "Schedule Type";
    public static final String RUN_AS_USER = "Run As User";
    public static final String BOUNDS = "Bounds";
    public static final String START_TIME = "Start Time";
    public static final String END_TIME = "End Time";

    public static final QueryField FIELD_ID = QueryField.createId(ID);
    public static final QueryField FIELD_NAME = QueryField.createText(NAME);
    public static final QueryField FIELD_ENABLED = QueryField.createBoolean(ENABLED);
    public static final QueryField FIELD_NODE_NAME = QueryField.createText(NODE_NAME);
    public static final QueryField FIELD_SCHEDULE = QueryField.createText(SCHEDULE);
    public static final QueryField FIELD_SCHEDULE_TYPE = QueryField.createText(SCHEDULE_TYPE);
    public static final QueryField FIELD_RUN_AS_USER = QueryField.createUserRef(RUN_AS_USER);
    public static final QueryField FIELD_START_TIME = QueryField.createDate(START_TIME);
    public static final QueryField FIELD_END_TIME = QueryField.createDate(END_TIME);
    public static final QueryField FIELD_PARENT_RULE
            = QueryField.createDocRefByUniqueName(AnalyticRuleDoc.TYPE, PARENT_RULE);
    public static final QueryField FIELD_PARENT_REPORT
            = QueryField.createDocRefByUniqueName(ReportDoc.TYPE, PARENT_REPORT);
    public static final QueryField FIELD_PARENT_DOC_TYPE
            = QueryField.createText(PARENT_DOC_TYPE);

    static {
        FIELDS.add(FIELD_ID);
        FIELDS.add(FIELD_NAME);
        FIELDS.add(FIELD_ENABLED);
        FIELDS.add(FIELD_NODE_NAME);
        FIELDS.add(FIELD_SCHEDULE);
        FIELDS.add(FIELD_SCHEDULE_TYPE);
        FIELDS.add(FIELD_START_TIME);
        FIELDS.add(FIELD_END_TIME);
        FIELDS.add(FIELD_RUN_AS_USER);
        FIELDS.add(FIELD_PARENT_RULE);
        FIELDS.add(FIELD_PARENT_REPORT);
        FIELDS.add(FIELD_PARENT_DOC_TYPE);
    }

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }
}
