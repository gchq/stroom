package stroom.task.impl;

import stroom.query.api.datasource.QueryField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TaskManagerFields {

    public static final String FIELD_NODE = "Node";
    public static final String FIELD_NAME = "Name";
    public static final String FIELD_USER = "User";
    public static final String FIELD_SUBMIT_TIME = "Submit Time";
    public static final String FIELD_AGE = "Age";
    public static final String FIELD_INFO = "Info";

    private static final List<QueryField> FIELDS = new ArrayList<>();
    private static final Map<String, QueryField> FIELD_MAP;

    public static final QueryField NODE = QueryField.createText(FIELD_NODE);
    public static final QueryField NAME = QueryField.createText(FIELD_NAME);
    public static final QueryField USER = QueryField.createText(FIELD_USER);
    public static final QueryField SUBMIT_TIME = QueryField.createDate(FIELD_SUBMIT_TIME);
    public static final QueryField AGE = QueryField.createLong(FIELD_AGE);
    public static final QueryField INFO = QueryField.createText(FIELD_INFO);

    static {
        FIELDS.add(NODE);
        FIELDS.add(NAME);
        FIELDS.add(USER);
        FIELDS.add(SUBMIT_TIME);
        FIELDS.add(AGE);
        FIELDS.add(INFO);

        FIELD_MAP = FIELDS.stream()
                .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
    }

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, QueryField> getFieldMap() {
        return FIELD_MAP;
    }
}
