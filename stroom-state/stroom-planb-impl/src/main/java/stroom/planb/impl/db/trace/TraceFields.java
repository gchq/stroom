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
