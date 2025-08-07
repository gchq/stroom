package stroom.planb.impl.db.trace;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface TraceFields {

    String TRACE_ID = "TraceId";
    String PARENT_SPAN_ID = "ParentSpanId";
    String SPAN_ID = "SpanId";

    QueryField TRACE_ID_FIELD = QueryField.createText(TRACE_ID);
    QueryField PARENT_SPAN_ID_FIELD = QueryField.createText(PARENT_SPAN_ID);
    QueryField SPAN_ID_FIELD = QueryField.createText(SPAN_ID);

    List<QueryField> FIELDS = Arrays.asList(
            TRACE_ID_FIELD,
            PARENT_SPAN_ID_FIELD,
            SPAN_ID_FIELD);

    Map<String, QueryField> FIELD_MAP = Map.of(
            TRACE_ID, TRACE_ID_FIELD,
            PARENT_SPAN_ID, PARENT_SPAN_ID_FIELD,
            SPAN_ID, SPAN_ID_FIELD);
}
