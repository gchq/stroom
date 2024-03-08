package stroom.meta.shared;

import stroom.datasource.api.v2.QueryField;
import stroom.datasource.api.v2.TextField;

public final class DataRetentionFields {

    public static final String RETENTION_AGE = "Age";
    public static final String RETENTION_UNTIL = "Until";
    public static final String RETENTION_RULE = "Rule";

    public static final QueryField RETENTION_AGE_FIELD = new TextField(RETENTION_AGE, false);
    public static final QueryField RETENTION_UNTIL_FIELD = new TextField(RETENTION_UNTIL, false);
    public static final QueryField RETENTION_RULE_FIELD = new TextField(RETENTION_RULE, false);

    private DataRetentionFields() {
    }
}
