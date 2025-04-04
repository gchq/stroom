package stroom.query.api.datasource;

public class IndexFieldFields extends FieldFields {

    public static final String STORE = "Store";
    public static final String INDEX = "Index";
    public static final String POSITIONS = "Positions";
    public static final String ANALYSER = "Analyser";
    public static final String CASE_SENSITIVE = "CaseSensitive";

    public static final QueryField STORE_FIELD = QueryField.createBoolean(STORE);
    public static final QueryField INDEX_FIELD = QueryField.createBoolean(INDEX);
    public static final QueryField POSITIONS_FIELD = QueryField.createBoolean(POSITIONS);
    public static final QueryField ANALYSER_FIELD = QueryField.createText(ANALYSER);
    public static final QueryField CASE_SENSITIVE_FIELD = QueryField.createBoolean(CASE_SENSITIVE);
}
