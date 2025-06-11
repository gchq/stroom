package stroom.state.impl.dao;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface StateFields {

    String KEY = "Key";
    String VALUE_TYPE = "ValueType";
    String VALUE = "Value";
    String INSERT_TIME = "InsertTime";

    QueryField KEY_FIELD = QueryField.createText(KEY);
    QueryField VALUE_TYPE_FIELD = QueryField.createText(VALUE_TYPE, false);
    QueryField VALUE_FIELD = QueryField.createText(VALUE, false);
    QueryField INSERT_TIME_FIELD = QueryField.createText(INSERT_TIME, false);

    List<QueryField> FIELDS = Arrays.asList(
            KEY_FIELD,
            VALUE_TYPE_FIELD,
            VALUE_FIELD,
            INSERT_TIME_FIELD);

    Map<String, QueryField> FIELD_MAP = Map.of(
            KEY, KEY_FIELD,
            VALUE_TYPE, VALUE_TYPE_FIELD,
            VALUE, VALUE_FIELD,
            INSERT_TIME, INSERT_TIME_FIELD);
}
