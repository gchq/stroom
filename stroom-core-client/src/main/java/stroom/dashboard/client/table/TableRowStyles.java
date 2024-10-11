package stroom.dashboard.client.table;

import stroom.dashboard.client.table.DynamicStyles.DynamicStyle;
import stroom.query.client.presenter.TableRow;
import stroom.util.shared.GwtNullSafe;

import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.user.cellview.client.RowStyles;

public class TableRowStyles implements RowStyles<TableRow> {

    @Override
    public String getStyleNames(final TableRow row, final int rowIndex) {
        // Row styles.
        if (GwtNullSafe.isNonBlankString(row.getBackgroundColor()) ||
                GwtNullSafe.isNonBlankString(row.getTextColor())) {
            final SafeStylesBuilder rowStyle = new SafeStylesBuilder();
            if (GwtNullSafe.isNonBlankString(row.getBackgroundColor())) {
                rowStyle.trustedBackgroundColor(row.getBackgroundColor());
            }
            if (GwtNullSafe.isNonBlankString(row.getTextColor())) {
                rowStyle.trustedColor(row.getTextColor());
            }
            final DynamicStyle dynamicStyle = DynamicStyles.create(rowStyle.toSafeStyles().asString());
            return dynamicStyle.getName();
        }

        return "";
    }
}
