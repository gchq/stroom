/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.datagrid.client;

import stroom.widget.datagrid.client.model.ColumnModel;
import stroom.widget.datagrid.client.model.TableText;
import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasHorizontalAlignment.HorizontalAlignmentConstant;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment.VerticalAlignmentConstant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/// Builds the HTML of [DataGrid]'s header and rows from its columns, headers and sort list,
/// with the class names `MyDataGrid` uses (design R-H5 and R-R1), and gets the text cells and
/// headers show, for copying, exporting and asking AI (R-M3, R-M4).
///
/// @param <R> The row type.
final class GridHtml<R> {

    /// The attribute holding a row's page-relative index.
    static final String DATA_ROW = "data-row";
    /// The attribute holding a cell's column index.
    static final String DATA_COL = "data-col";
    /// The attribute holding a header cell's column index.
    static final String DATA_HCOL = "data-hcol";

    // A cell spanning columns, as many as its --data-grid__span
    private static final String SPAN_CLASS_NAME = "data-grid__cell--span";

    // A tick box in a cell is a check box to assistive technology
    private static final String CHECK_BOX_ROLE = "role=\"checkbox\"";

    private final ColumnModel<Column<R, ?>> columnModel;
    private final Map<Column<R, ?>, Header<?>> headers;
    private final ColumnSortList sortList;
    private final BiFunction<R, Integer, Object> keys;
    // Each column's name for the unnamed tick boxes in its cells (see nameTickBoxes), worked out
    // when the header is rendered; null for a column whose header gives none
    private String[] tickBoxNames = new String[0];

    /// @param columnModel The grid's columns.
    /// @param headers     The columns' headers.
    /// @param sortList    The grid's sort list.
    /// @param keys        Gives a row's key from the row and its absolute index.
    // A cell's role attribute and a space after it, or nothing (see setCellRole)
    private String cellRoleAttribute = "role=\"gridcell\" ";

    GridHtml(final ColumnModel<Column<R, ?>> columnModel,
             final Map<Column<R, ?>, Header<?>> headers,
             final ColumnSortList sortList,
             final BiFunction<R, Integer, Object> keys) {
        this.columnModel = columnModel;
        this.headers = headers;
        this.sortList = sortList;
        this.keys = keys;
    }

    /// @return The header cells, with the sort indicators of the columns in the sort list.
    SafeHtml header() {
        updateTickBoxNames();
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        final int count = columnModel.size();
        for (int i = 0; i < count; i++) {
            final Column<R, ?> column = columnModel.getColumn(i);
            final Header<?> header = headers.get(column);
            final int sortIndex = sortIndex(column);
            final boolean sortable = column.isSortable();
            final boolean sorted = sortIndex >= 0;
            final boolean ascending = sorted && sortList.get(sortIndex).isAscending();

            final StringBuilder classes = new StringBuilder("data-grid__header-cell");
            if (sortable) {
                classes.append(" data-grid__header-cell--sortable");
            }
            if (header != null && header.getHeaderStyleNames() != null) {
                classes.append(' ').append(header.getHeaderStyleNames());
            }

            sb.appendHtmlConstant("<div class=\"")
                    .appendEscaped(classes.toString())
                    .appendHtmlConstant("\" role=\"columnheader\" " + DATA_HCOL + "=\"" + i + "\"");
            if (sortable) {
                sb.appendHtmlConstant(" tabindex=\"-1\" aria-sort=\"" + (sorted
                        ? (ascending
                        ? "ascending"
                        : "descending")
                        : "none") + "\"");
            }
            sb.appendHtmlConstant(">");
            if (header != null) {
                final Context context = new Context(0, i, header.getKey());
                if (sortable) {
                    sb.appendHtmlConstant("<div class=\"data-grid__sort\">"
                                          + "<div class=\"data-grid__sort-name\">");
                    header.render(context, sb);
                    sb.appendHtmlConstant("</div>");
                    if (sorted) {
                        final HtmlBuilder icon = new HtmlBuilder();
                        SortIcon.append(icon, ascending, sortIndex + 1);
                        sb.append(icon.toSafeHtml());
                    }
                    sb.appendHtmlConstant("</div>");
                } else {
                    header.render(context, sb);
                }
            }
            sb.appendHtmlConstant("</div>");
        }
        return sb.toSafeHtml();
    }

    /// @param cellRole A cell's role (see [DataGridProfile#getCellRole()]), or null for none.
    void setCellRole(final String cellRole) {
        cellRoleAttribute = cellRole == null
                ? ""
                : "role=\"" + cellRole + "\" ";
    }

    /// @param row      The row, or `null` for a placeholder.
    /// @param absIndex The row's absolute index.
    /// @param spans    Column index to the number of columns that cell spans (R-R9).
    /// @return The row's cells. They don't depend on the row's state (selected, striped), which is
    /// the row's classes, so a change of state doesn't rewrite them. The keyboard cell's class is
    /// not included either; the grid sets it on the element, as it depends on the focus.
    SafeHtml cells(final R row,
                   final int absIndex,
                   final Map<Integer, Integer> spans) {
        return SafeHtmlUtils.fromTrustedString(rowCells(row, absIndex, spans).joined());
    }

    /// @param row      The row, or `null` for a placeholder.
    /// @param absIndex The row's absolute index.
    /// @param spans    Column index to the number of columns that cell spans (R-R9).
    /// @return The row's cells, each on its own, so that a row update can compare and write them one
    /// by one (see [RowCells]). As [#cells], they don't depend on the row's state.
    RowCells rowCells(final R row,
                      final int absIndex,
                      final Map<Integer, Integer> spans) {
        final int count = columnModel.size();
        final List<String> cellHtml = new ArrayList<>(count);
        final List<String> cellClasses = new ArrayList<>(count);
        final List<String> cellContents = new ArrayList<>(count);
        final int[] columns = new int[count];
        int cellCount = 0;
        int skipUntil = -1;
        for (int i = 0; i < count; i++) {
            if (i < skipUntil) {
                continue;
            }
            final Column<R, ?> column = columnModel.getColumn(i);
            final Context context = new Context(absIndex, i, keys.apply(row, absIndex));

            final StringBuilder classes = new StringBuilder("data-grid__cell");
            appendAlignment(classes, column.getHorizontalAlignment(), column.getVerticalAlignment());
            if (row != null) {
                final String cellStyles = column.getCellStyleNames(context, row);
                if (cellStyles != null && !cellStyles.isEmpty()) {
                    classes.append(' ').append(cellStyles);
                }
            }

            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            content.appendHtmlConstant("<div>");
            if (row != null) {
                final SafeHtmlBuilder cell = new SafeHtmlBuilder();
                column.render(context, row, cell);
                content.append(nameTickBoxes(cell.toSafeHtml(), i));
            }
            content.appendHtmlConstant("</div>");

            final Integer span = spans.get(i);
            final int clamped = span != null && span > 1
                    ? Math.min(span, count - i)
                    : 1;
            if (clamped > 1) {
                // The span is a custom property its class uses (data-grid.css)
                classes.append(' ').append(SPAN_CLASS_NAME);
            }
            final SafeHtmlBuilder sb = new SafeHtmlBuilder();
            sb.appendHtmlConstant("<div class=\"")
                    .appendEscaped(classes.toString())
                    .appendHtmlConstant("\" " + cellRoleAttribute + DATA_COL + "=\"" + i + "\"");
            if (clamped > 1) {
                sb.appendHtmlConstant(" style=\"--data-grid__span: " + clamped + "\" aria-colspan=\""
                                      + clamped + "\"");
                skipUntil = i + clamped;
            }
            sb.appendHtmlConstant(">");
            sb.append(content.toSafeHtml());
            sb.appendHtmlConstant("</div>");

            cellHtml.add(sb.toSafeHtml().asString());
            cellClasses.add(classes.toString());
            cellContents.add(content.toSafeHtml().asString());
            columns[cellCount++] = i;
        }
        return new RowCells(cellHtml, Arrays.copyOf(columns, cellCount), cellClasses, cellContents);
    }

    // A tick box in a cell with no name of its own (e.g. its row's, from
    // TickBoxCell.withNameForRow) is named by its column's header, e.g. "Enabled", so that a screen
    // reader says what it sets ("Enabled, check box, checked") rather than only its title, which
    // repeats its state ("Ticked") (#5411)
    private SafeHtml nameTickBoxes(final SafeHtml cellHtml, final int index) {
        if (tickBoxNames.length != columnModel.size()) {
            updateTickBoxNames();
        }
        final String name = tickBoxNames[index];
        final String html = cellHtml.asString();
        if (name == null || !html.contains(CHECK_BOX_ROLE) || html.contains("aria-label")) {
            return cellHtml;
        }
        return SafeHtmlUtils.fromTrustedString(html.replace(CHECK_BOX_ROLE,
                CHECK_BOX_ROLE + " aria-label=\"" + SafeHtmlUtils.htmlEscape(name) + "\""));
    }

    // The headers' text, as names for the tick boxes in their cells. A header holding a control
    // (e.g. a tick box that ticks every row) gives none, as its text wouldn't say what a row's tick
    // box sets, nor does a blank one.
    private void updateTickBoxNames() {
        final int count = columnModel.size();
        final String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            final Header<?> header = headers.get(columnModel.getColumn(i));
            if (header != null) {
                final SafeHtmlBuilder sb = new SafeHtmlBuilder();
                header.render(new Context(0, i, header.getKey()), sb);
                final String html = sb.toSafeHtml().asString();
                if (!html.contains("role=") && !html.contains("<input") && !html.contains("<button")) {
                    final String text = TableText.normaliseWhitespace(DataGridDom.textOfHtml(html));
                    names[i] = text.isEmpty()
                            ? null
                            : text;
                }
            }
        }
        tickBoxNames = names;
    }

    /// Append a copy of a cell for measuring its content (R-CR4). It has the cell's classes and
    /// content but none of the row state, e.g. selection.
    ///
    /// @param row      The row.
    /// @param absIndex The row's absolute index.
    /// @param index    The column index.
    /// @param sb       The builder to append the copy to.
    void cellCopy(final R row, final int absIndex, final int index, final SafeHtmlBuilder sb) {
        final Column<R, ?> column = columnModel.getColumn(index);
        final Context context = new Context(absIndex, index, keys.apply(row, absIndex));
        final StringBuilder classes = new StringBuilder("data-grid__cell");
        appendAlignment(classes, column.getHorizontalAlignment(), column.getVerticalAlignment());
        final String cellStyles = column.getCellStyleNames(context, row);
        if (cellStyles != null && !cellStyles.isEmpty()) {
            classes.append(' ').append(cellStyles);
        }
        sb.appendHtmlConstant("<div class=\"").appendEscaped(classes.toString()).appendHtmlConstant("\"><div>");
        column.render(context, row, sb);
        sb.appendHtmlConstant("</div></div>");
    }

    /// @param row      The row.
    /// @param absIndex The row's absolute index.
    /// @param index    The column index.
    /// @return The text the cell shows, rendered from the row, with whitespace normalised.
    String cellText(final R row, final int absIndex, final int index) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        columnModel.getColumn(index).render(new Context(absIndex, index, keys.apply(row, absIndex)), row, sb);
        return TableText.normaliseWhitespace(DataGridDom.textOfHtml(sb.toSafeHtml().asString()));
    }

    /// @param index The column index.
    /// @return The text the column's header shows, with whitespace normalised.
    String headerText(final int index) {
        final Header<?> header = headers.get(columnModel.getColumn(index));
        if (header == null) {
            return "";
        }
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        header.render(new Context(0, index, header.getKey()), sb);
        return TableText.normaliseWhitespace(DataGridDom.textOfHtml(sb.toSafeHtml().asString()));
    }

    private int sortIndex(final Column<?, ?> column) {
        for (int i = 0; i < sortList.size(); i++) {
            if (Objects.equals(column, sortList.get(i).getColumn())) {
                return i;
            }
        }
        return -1;
    }

    private static void appendAlignment(final StringBuilder classes,
                                        final HorizontalAlignmentConstant horizontal,
                                        final VerticalAlignmentConstant vertical) {
        if (HasHorizontalAlignment.ALIGN_RIGHT.equals(horizontal)) {
            classes.append(" data-grid__cell--align-right");
        } else if (HasHorizontalAlignment.ALIGN_CENTER.equals(horizontal)) {
            classes.append(" data-grid__cell--align-center");
        }
        if (HasVerticalAlignment.ALIGN_TOP.equals(vertical)) {
            classes.append(" data-grid__cell--valign-top");
        } else if (HasVerticalAlignment.ALIGN_BOTTOM.equals(vertical)) {
            classes.append(" data-grid__cell--valign-bottom");
        } else if (HasVerticalAlignment.ALIGN_MIDDLE.equals(vertical)) {
            classes.append(" data-grid__cell--valign-middle");
        }
    }

    // --------------------------------------------------------------------------------

    /// A row's cells as drawn, one entry per cell element (a cell spanning columns is one): its
    /// HTML, the column it starts at, its classes and its content (the wrapper inside it). A cell's
    /// HTML is null once forgotten, so it is written again (see [GridRows#forgetCell]).
    static final class RowCells {

        private final List<String> html;
        private final int[] columns;
        private final List<String> classes;
        private final List<String> contents;

        RowCells(final List<String> html,
                 final int[] columns,
                 final List<String> classes,
                 final List<String> contents) {
            this.html = html;
            this.columns = columns;
            this.classes = classes;
            this.contents = contents;
        }

        /// @return Each cell's HTML, in order; an entry is null if forgotten.
        List<String> getHtml() {
            return html;
        }

        /// @return The column each cell starts at, in order.
        int[] getColumns() {
            return columns;
        }

        /// @param position A cell's position in the row.
        /// @return The cell's classes.
        String getClasses(final int position) {
            return classes.get(position);
        }

        /// @param position A cell's position in the row.
        /// @return The cell's content: the wrapper inside its element, and what the column drew in it.
        String getContent(final int position) {
            return contents.get(position);
        }

        /// @param column A column index.
        /// @return The position of the cell that starts at the column, or -1 if none does (it is
        /// spanned by another).
        int positionOf(final int column) {
            for (int i = 0; i < columns.length; i++) {
                if (columns[i] == column) {
                    return i;
                }
            }
            return -1;
        }

        /// @param position A cell's position in the row.
        /// @param cellHtml The HTML to record for it, e.g. what it was last written with, or null to
        ///                 forget it.
        /// @return A copy with that cell's HTML replaced.
        RowCells withHtml(final int position, final String cellHtml) {
            final List<String> copy = new ArrayList<>(html);
            copy.set(position, cellHtml);
            return new RowCells(copy, columns, classes, contents);
        }

        /// @return The cells' HTML joined, as the row's content.
        String joined() {
            final StringBuilder sb = new StringBuilder();
            for (final String cell : html) {
                sb.append(cell);
            }
            return sb.toString();
        }
    }
}
