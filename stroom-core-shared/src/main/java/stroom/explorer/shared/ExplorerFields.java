package stroom.explorer.shared;

import stroom.query.api.datasource.QueryField;

public class ExplorerFields {

    public static final String EXPLORER_TYPE = "Explorer";

    public static final QueryField CONTENT = QueryField.createText("Content");
    public static final QueryField TAG = QueryField.createText("Tags");
}
