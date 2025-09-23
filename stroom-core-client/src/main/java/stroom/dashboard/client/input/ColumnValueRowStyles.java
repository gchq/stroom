package stroom.dashboard.client.input;

import stroom.dashboard.client.table.AbstractRowStyles;
import stroom.dashboard.shared.ColumnValue;
import stroom.preferences.client.UserPreferencesManager;

public class ColumnValueRowStyles extends AbstractRowStyles<ColumnValue> {

    public ColumnValueRowStyles(final UserPreferencesManager userPreferencesManager) {
        super(userPreferencesManager,
                row -> row == null
                        ? null
                        : row.getMatchingRule());
    }
}
