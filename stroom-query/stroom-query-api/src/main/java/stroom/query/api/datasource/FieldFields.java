package stroom.query.api.datasource;

public class FieldFields {
    public static final String NAME = "Name";
    public static final String TYPE = "Type";

    public static final QueryField NAME_FIELD = QueryField.createText(NAME);
    public static final QueryField TYPE_FIELD = QueryField.createText(TYPE);
}
