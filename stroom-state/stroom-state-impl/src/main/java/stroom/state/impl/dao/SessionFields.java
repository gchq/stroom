package stroom.state.impl.dao;

import stroom.datasource.api.v2.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface SessionFields {
    String KEY = "Key";
    String START = "Start";
    String END = "End";
    String TERMINAL = "Terminal";

    QueryField KEY_FIELD = QueryField.createText(KEY);
    QueryField START_FIELD = QueryField.createDate(START);
    QueryField END_FIELD = QueryField.createDate(END);
    QueryField TERMINAL_FIELD = QueryField.createText(TERMINAL, false);

    List<QueryField> QUERYABLE_FIELDS = Arrays.asList(
            KEY_FIELD,
            START_FIELD,
            END_FIELD);

    Map<String, QueryField> FIELD_MAP = Map.of(
            KEY, KEY_FIELD,
            START, START_FIELD,
            END, END_FIELD,
            TERMINAL, TERMINAL_FIELD);
}
