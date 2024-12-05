package stroom.dashboard.client.table;

import stroom.dashboard.client.table.cf.ConditionalFormattingDynamicStyles;
import stroom.query.api.v2.ConditionalFormattingType;
import stroom.query.client.presenter.TableRow;

import com.google.gwt.user.cellview.client.RowStyles;

public class TableRowStyles implements RowStyles<TableRow> {

    @Override
    public String getStyleNames(final TableRow row, final int rowIndex) {
        // Fixed styles.
        if (row.getFormattingStyle() == null || ConditionalFormattingType.CUSTOM.equals(row.getFormattingStyle())) {
            return ConditionalFormattingDynamicStyles.create(row.getCustomStyle());
        } else  if (ConditionalFormattingType.TEXT.equals(row.getFormattingType())) {
            return "textOnly " + row.getFormattingStyle().getCssClassName();
        } else if (ConditionalFormattingType.BACKGROUND.equals(row.getFormattingType())) {
            return row.getFormattingStyle().getCssClassName();
        }

        return "";
    }
}
