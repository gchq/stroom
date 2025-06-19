package stroom.cell.info.client;

import stroom.cell.info.client.ToolTipCell.ToolTipCellValue;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

// TODO: 14/03/2023 This was the beginings of a cell for showing the byte values, e.g.
//  volume/stream sizes, however it probably ought to become a ByteValueCell and ByteValueColumn
//  with a standard approach for formatting the byte value and the tooltip. Leaving it here
//  in case such a thing ever happens.

/**
 * A text cell with a tool tip
 */
public class ToolTipCell extends AbstractCell<ToolTipCellValue> {

    @Override
    public void render(final Context context, final ToolTipCellValue value, final SafeHtmlBuilder sb) {
        if (value != null) {
            sb
                    .appendHtmlConstant("<div title=\"")
                    .appendEscaped(value.getToolTipValue())
                    .appendHtmlConstant("\">")
                    .append(SafeHtmlUtils.fromString(value.cellValue));
        }
    }


    // --------------------------------------------------------------------------------


    public static class ToolTipCellValue {

        private final String cellValue;
        private final String toolTipValue;

        public static ToolTipCellValue EMPTY = new ToolTipCellValue("", "");

        public ToolTipCellValue(final String cellValue, final String toolTipValue) {
            this.cellValue = cellValue;
            this.toolTipValue = toolTipValue;
        }

        public String getCellValue() {
            return cellValue;
        }

        public String getToolTipValue() {
            return toolTipValue;
        }
    }
}
