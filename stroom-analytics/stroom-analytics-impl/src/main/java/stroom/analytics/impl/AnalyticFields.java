package stroom.analytics.impl;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AnalyticFields {

    public static final String ANALYTICS_STORE_TYPE = "Analytics";
    public static final DocRef ANALYTICS_DOC_REF = DocRef.builder()
            .type(ANALYTICS_STORE_TYPE)
            .uuid("0")
            .name(ANALYTICS_STORE_TYPE)
            .build();

    public static final String NAME = "Name";
    public static final String UUID = "UUID";
    public static final String TIME = "Time";
    public static final String VALUE = "Value";
    private static final List<AbstractField> FIELDS = new ArrayList<>();
    private static final Map<String, AbstractField> FIELD_MAP;

    // Times
    public static final DateField TIME_FIELD = new DateField(TIME);

    public static final TextField NAME_FIELD = new TextField(NAME);
    public static final DateField UUID_FIELD = new DateField(UUID);
    public static final DateField VALUE_FIELD = new DateField(VALUE);

    static {
        FIELDS.add(TIME_FIELD);
        FIELDS.add(NAME_FIELD);
        FIELDS.add(UUID_FIELD);
        FIELDS.add(VALUE_FIELD);

        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(AbstractField::getName, Function.identity()));
    }

    public static List<AbstractField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, AbstractField> getFieldMap() {
        return FIELD_MAP;
    }
}
