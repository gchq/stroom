/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
