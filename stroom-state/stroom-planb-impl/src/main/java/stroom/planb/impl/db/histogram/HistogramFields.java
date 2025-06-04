package stroom.planb.impl.db.histogram;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface HistogramFields {

    String KEY = "Key"; // TODO : Multi tags
    String TIME = "Time";
    String RESOLUTION = "Resolution";
    String VALUE = "Value";

    QueryField KEY_FIELD = QueryField.createText(KEY);
    QueryField TIME_FIELD = QueryField.createDate(TIME);
    QueryField RESOLUTION_FIELD = QueryField.createInteger(RESOLUTION, false);
    QueryField VALUE_FIELD = QueryField.createText(VALUE, false);

    List<QueryField> FIELDS = Arrays.asList(
            KEY_FIELD,
            TIME_FIELD,
            RESOLUTION_FIELD,
            VALUE_FIELD);

    Map<String, QueryField> FIELD_MAP = Map.of(
            KEY, KEY_FIELD,
            TIME, TIME_FIELD,
            RESOLUTION, RESOLUTION_FIELD,
            VALUE, VALUE_FIELD);
}
