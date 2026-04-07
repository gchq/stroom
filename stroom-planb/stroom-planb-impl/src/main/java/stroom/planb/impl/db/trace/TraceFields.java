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

package stroom.planb.impl.db.trace;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface TraceFields {

    String TRACE_ID = "TraceId";
    String PARENT_SPAN_ID = "ParentSpanId";
    String SPAN_ID = "SpanId";
    String START_TIME = "StartTime";
    String END_TIME = "EndTime";
    String INSERT_TIME = "InsertTime";

    QueryField TRACE_ID_FIELD = QueryField.createText(TRACE_ID);
    QueryField PARENT_SPAN_ID_FIELD = QueryField.createText(PARENT_SPAN_ID);
    QueryField SPAN_ID_FIELD = QueryField.createText(SPAN_ID);
    QueryField START_TIME_FIELD = QueryField.createDate(START_TIME);
    QueryField END_TIME_FIELD = QueryField.createDate(END_TIME);
    QueryField INSERT_TIME_FIELD = QueryField.createDate(INSERT_TIME);

    List<QueryField> FIELDS = Arrays.asList(
            TRACE_ID_FIELD,
            PARENT_SPAN_ID_FIELD,
            SPAN_ID_FIELD,
            START_TIME_FIELD,
            END_TIME_FIELD,
            INSERT_TIME_FIELD);

    Map<String, QueryField> FIELD_MAP = Map.of(
            TRACE_ID, TRACE_ID_FIELD,
            PARENT_SPAN_ID, PARENT_SPAN_ID_FIELD,
            SPAN_ID, SPAN_ID_FIELD,
            START_TIME, START_TIME_FIELD,
            END_TIME, END_TIME_FIELD,
            INSERT_TIME, INSERT_TIME_FIELD);
}
