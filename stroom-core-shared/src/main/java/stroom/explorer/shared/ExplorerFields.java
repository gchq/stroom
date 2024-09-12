package stroom.explorer.shared;

import stroom.datasource.api.v2.QueryField;
import stroom.util.shared.string.CIKey;

public class ExplorerFields {

    public static final String EXPLORER_TYPE = "Explorer";

    public static final QueryField CONTENT = QueryField.createText(CIKey.ofStaticKey("Content"), true);
    public static final QueryField TAG = QueryField.createText(CIKey.ofStaticKey("Tags"), true);
}
