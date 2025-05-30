package stroom.planb.impl.db.histogram;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface HistogramFields {

    String KEY = "Key"; // TODO : Multi tags
    String TIME = "Time";
    String DURATION = "Duration";
    String VALUE = "Value";

    QueryField KEY_FIELD = QueryField.createText(KEY);
    QueryField TIME_FIELD = QueryField.createDate(TIME);
    QueryField DURATION_FIELD = QueryField.createInteger(DURATION, false);
    QueryField VALUE_FIELD = QueryField.createText(VALUE, false);

    List<QueryField> FIELDS = Arrays.asList(
            KEY_FIELD,
            TIME_FIELD,
            DURATION_FIELD,
            VALUE_FIELD);

    Map<String, QueryField> FIELD_MAP = Map.of(
            KEY, KEY_FIELD,
            TIME, TIME_FIELD,
            DURATION, DURATION_FIELD,
            VALUE, VALUE_FIELD);
}
