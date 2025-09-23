package stroom.dashboard.client.table;

import stroom.preferences.client.UserPreferencesManager;
import stroom.query.client.presenter.TableRow;

public class TableRowStyles extends AbstractRowStyles<TableRow> {

    public TableRowStyles(final UserPreferencesManager userPreferencesManager) {
        super(userPreferencesManager,
                row -> row == null
                        ? null
                        : row.getMatchingRule());
    }
}
