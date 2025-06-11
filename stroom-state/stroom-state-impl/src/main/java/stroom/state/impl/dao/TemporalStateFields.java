package stroom.state.impl.dao;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface TemporalStateFields {
    String KEY = "Key";
    String EFFECTIVE_TIME = "EffectiveTime";
    String VALUE_TYPE = "ValueType";
    String VALUE = "Value";

    QueryField KEY_FIELD = QueryField.createText(KEY);
    QueryField EFFECTIVE_TIME_FIELD = QueryField.createDate(EFFECTIVE_TIME);
    QueryField VALUE_TYPE_FIELD = QueryField.createText(VALUE_TYPE, false);
    QueryField VALUE_FIELD = QueryField.createText(VALUE, false);

    List<QueryField> FIELDS = Arrays.asList(
            KEY_FIELD,
            EFFECTIVE_TIME_FIELD,
            VALUE_TYPE_FIELD,
            VALUE_FIELD);

    Map<String, QueryField> FIELD_MAP = Map.of(
            KEY, KEY_FIELD,
            EFFECTIVE_TIME, EFFECTIVE_TIME_FIELD,
            VALUE_TYPE, VALUE_TYPE_FIELD,
            VALUE, VALUE_FIELD);
}
