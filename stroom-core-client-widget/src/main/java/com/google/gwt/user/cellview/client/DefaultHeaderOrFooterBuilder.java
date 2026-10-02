//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.google.gwt.user.cellview.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.dom.builder.shared.DivBuilder;
import com.google.gwt.dom.builder.shared.ElementBuilderBase;
import com.google.gwt.dom.builder.shared.TableCellBuilder;
import com.google.gwt.dom.builder.shared.TableRowBuilder;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;

import java.util.Objects;

public class DefaultHeaderOrFooterBuilder<T> extends AbstractHeaderOrFooterBuilder<T> {

    public DefaultHeaderOrFooterBuilder(final AbstractCellTable<T> table, final boolean isFooter) {
        super(table, isFooter);
    }

    protected boolean buildHeaderOrFooterImpl() {
        final AbstractCellTable<T> table = this.getTable();
        final boolean isFooter = this.isBuildingFooter();
        final int columnCount = table.getColumnCount();
        if (columnCount == 0) {
            return false;
        } else {
            boolean hasHeader = false;

            for (int i = 0; i < columnCount; ++i) {
                if (this.getHeader(i) != null) {
                    hasHeader = true;
                    break;
                }
            }

            if (!hasHeader) {
                return false;

            } else {
                // Get styles.
                final AbstractCellTable.Style style = this.getTable().getResources().style();
                final String className = this.isBuildingFooter()
                        ? style.footer()
                        : style.header();

                // Get sort info.
                final ColumnSortList sortList = table.getColumnSortList();

                // Get styles for the first column.
                Column<T, ?> column = this.getTable().getColumn(0);
                int sortIndex = getSortIndex(column);
                ColumnSortList.ColumnSortInfo sortedInfo = sortIndex == -1
                        ? null
                        : sortList.get(sortIndex);
                boolean isSortAscending = sortedInfo != null && sortedInfo.isAscending();
                String sortableStyle = " " + style.sortableHeader();
                String sortedStyle = " " + (isSortAscending
                        ? style.sortedHeaderAscending()
                        : style.sortedHeaderDescending());
                boolean isSortable = !isFooter && column.isSortable();
                boolean isSorted = !isFooter && sortedInfo != null;


                Header<?> prevHeader = this.getHeader(0);
                int prevColspan = 1;

                StringBuilder classesBuilder = new StringBuilder(className);
                classesBuilder
                        .append(" ")
                        .append((isFooter
                                ? style.firstColumnFooter()
                                : style.firstColumnHeader()));

                final TableRowBuilder tr = this.startRow();

                int curColumn;
                for (curColumn = 1; curColumn < columnCount; ++curColumn) {
                    final Header<?> header = this.getHeader(curColumn);
                    if (header != prevHeader) {
                        this.buildTableHeader(
                                tr,
                                column,
                                prevHeader,
                                isSortable,
                                isSorted,
                                isSortAscending,
                                sortIndex,
                                classesBuilder,
                                sortableStyle,
                                sortedStyle,
                                prevColspan,
                                curColumn);
                        prevHeader = header;
                        prevColspan = 1;
                        classesBuilder = new StringBuilder(className);
                    } else {
                        ++prevColspan;
                    }

                    column = table.getColumn(curColumn);
                    sortIndex = getSortIndex(column);
                    sortedInfo = sortIndex == -1
                            ? null
                            : sortList.get(sortIndex);
                    isSortAscending = sortedInfo != null && sortedInfo.isAscending();
                    sortableStyle = " " + style.sortableHeader();
                    sortedStyle = " " + (isSortAscending
                            ? style.sortedHeaderAscending()
                            : style.sortedHeaderDescending());
                    isSortable = !isFooter && column.isSortable();
                    isSorted = !isFooter && sortedInfo != null;
                }

                classesBuilder.append(" ").append(isFooter
                        ? style.lastColumnFooter()
                        : style.lastColumnHeader());
                // Insert the last header.
                this.buildTableHeader(
                        tr,
                        column,
                        prevHeader,
                        isSortable,
                        isSorted,
                        isSortAscending,
                        sortIndex,
                        classesBuilder,
                        sortableStyle,
                        sortedStyle,
                        prevColspan,
                        curColumn);
                tr.endTR();
                return true;
            }
        }
    }

    private int getSortIndex(final Column<?, ?> column) {
        final AbstractCellTable<T> table = this.getTable();
        final ColumnSortList sortList = table.getColumnSortList();
        for (int i = 0; i < sortList.size(); i++) {
            final ColumnSortInfo columnSortInfo = sortList.get(i);
            if (Objects.equals(column, columnSortInfo.getColumn())) {
                return i;
            }
        }
        return -1;
    }

    private void buildTableHeader(final TableRowBuilder tr, final Column<T, ?> column, final Header<?> header,
                                  final boolean isSortable,
                                  final boolean isSorted,
                                  final boolean isSortAscending,
                                  final int sortIndex,
                                  final StringBuilder classesBuilder,
                                  final String sortableStyle,
                                  final String sortedStyle,
                                  final int prevColspan,
                                  final int curColumn) {
        if (isSortable) {
            classesBuilder.append(sortableStyle);
        }

        if (isSorted) {
            classesBuilder.append(sortedStyle);
        }

        this.appendExtraStyles(header, classesBuilder);
        final TableCellBuilder th = tr.startTH().colSpan(prevColspan).className(classesBuilder.toString());
        this.enableColumnHandlers(th, column);
        if (header != null) {
            final Cell.Context context = new Cell.Context(0, curColumn - prevColspan, header.getKey());
            if (isSortable) {
                th.attribute("role", "button");
                th.tabIndex(-1);
            }

            this.renderHeader(th, context, header, isSortable, isSorted, isSortAscending, sortIndex);
        }

        th.endTH();
    }

    private void renderHeader(final ElementBuilderBase<?> out,
                              final Cell.Context context,
                              final Header<?> header,
                              final boolean isSortable,
                              final boolean isSorted,
                              final boolean isSortAscending,
                              final int sortIndex) {
        final boolean isFooter = this.isBuildingFooter();
        if (isSortable && !isFooter) {
            final DivBuilder outerDiv = out.startDiv();
            outerDiv.className("dataGridSortableHeaderOuterDiv");

            final DivBuilder nameHolder = outerDiv.startDiv();
            nameHolder.className("dataGridSortableHeaderNameHolder");
            this.renderHeader(nameHolder, context, header);
            nameHolder.endDiv();

            if (isSorted) {
                SortIcon.append(outerDiv, isSortAscending, sortIndex + 1);
            }

            outerDiv.endDiv();

        } else {
            this.renderHeader(out, context, header);
        }
    }

    private static SafeHtml getSafeHtml(final SvgImage svgImage) {
        return SvgImageUtil.toSafeHtml(svgImage, "svgIcon");
    }

    private <H> void appendExtraStyles(final Header<H> header,
                                       final StringBuilder classesBuilder) {
        if (header != null) {
            final String headerStyleNames = header.getHeaderStyleNames();
            if (headerStyleNames != null) {
                classesBuilder.append(" ");
                classesBuilder.append(headerStyleNames);
            }
        }
    }
}
