package stroom.meta.shared;

import stroom.query.api.datasource.QueryField;

public final class DataRetentionFields {

    public static final String RETENTION_AGE = "Age";
    public static final String RETENTION_UNTIL = "Until";
    public static final String RETENTION_RULE = "Rule";

    public static final QueryField RETENTION_AGE_FIELD = QueryField.createText(RETENTION_AGE, false);
    public static final QueryField RETENTION_UNTIL_FIELD = QueryField.createText(RETENTION_UNTIL, false);
    public static final QueryField RETENTION_RULE_FIELD = QueryField.createText(RETENTION_RULE, false);

    private DataRetentionFields() {
    }
}
