package stroom.planb.impl.db.metric;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface MetricFields {

    String KEY = "Key"; // TODO : Multi tags
    String TIME = "Time";
    String RESOLUTION = "Resolution";
    String VALUE = "Value";
    String MIN = "Min";
    String MAX = "Max";
    String COUNT = "Count";
    String SUM = "Sum";
    String AVERAGE = "Average";

    QueryField KEY_FIELD = QueryField.createText(KEY);
    QueryField TIME_FIELD = QueryField.createDate(TIME);
    QueryField RESOLUTION_FIELD = QueryField.createInteger(RESOLUTION, false);
    QueryField VALUE_FIELD = QueryField.createText(VALUE, false);
    QueryField MIN_FIELD = QueryField.createText(MIN, false);
    QueryField MAX_FIELD = QueryField.createText(MAX, false);
    QueryField COUNT_FIELD = QueryField.createText(COUNT, false);
    QueryField SUM_FIELD = QueryField.createText(SUM, false);
    QueryField AVERAGE_FIELD = QueryField.createText(AVERAGE, false);

    List<QueryField> FIELDS = Arrays.asList(
            KEY_FIELD,
            TIME_FIELD,
            RESOLUTION_FIELD,
            VALUE_FIELD,
            MIN_FIELD,
            MAX_FIELD,
            COUNT_FIELD,
            SUM_FIELD,
            AVERAGE_FIELD);

    List<QueryField> CORE_FIELDS = Arrays.asList(
            KEY_FIELD,
            TIME_FIELD,
            RESOLUTION_FIELD);

    Map<String, QueryField> FIELD_MAP = Map.of(
            KEY, KEY_FIELD,
            TIME, TIME_FIELD,
            RESOLUTION, RESOLUTION_FIELD,
            VALUE, VALUE_FIELD,
            MIN, MIN_FIELD,
            MAX, MAX_FIELD,
            COUNT, COUNT_FIELD,
            SUM, SUM_FIELD,
            AVERAGE, AVERAGE_FIELD);
}
