//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.google.gwt.user.cellview.client;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.dom.builder.shared.DivBuilder;
import com.google.gwt.dom.builder.shared.ElementBuilderBase;
import com.google.gwt.dom.builder.shared.HtmlBuilderFactory;
import com.google.gwt.dom.builder.shared.HtmlTableSectionBuilder;
import com.google.gwt.dom.builder.shared.TableRowBuilder;
import com.google.gwt.dom.builder.shared.TableSectionBuilder;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractHeaderOrFooterBuilder<T> implements HeaderBuilder<T>, FooterBuilder<T> {

    private static final SafeHtml ARROW_UP_SAFE_HTML = SvgImageUtil.toSafeHtml(SvgImage.ARROW_UP);
    private static final SafeHtml ARROW_DOWN_SAFE_HTML = SvgImageUtil.toSafeHtml(SvgImage.ARROW_DOWN);

    private final boolean isFooter;
    private boolean isSortIconStartOfLine = true;
    private final AbstractCellTable<T> table;
    private int rowIndex;
    private HtmlTableSectionBuilder section;
    private final Map<String, Column<T, ?>> idToColumnMap = new HashMap<>();
    private final TwoWayHashMap<String, Header<?>> idToHeaderMap = new TwoWayHashMap<>();

    public AbstractHeaderOrFooterBuilder(final AbstractCellTable<T> table, final boolean isFooter) {
        this.isFooter = isFooter;
        this.table = table;
    }

    public final TableSectionBuilder buildFooter() {
        if (!this.isFooter) {
            throw new UnsupportedOperationException(
                    "Cannot build footer because this builder is designated to build a header");
        } else {
            return this.buildHeaderOrFooter();
        }
    }

    public final TableSectionBuilder buildHeader() {
        if (this.isFooter) {
            throw new UnsupportedOperationException(
                    "Cannot build header because this builder is designated to build a footer");
        } else {
            return this.buildHeaderOrFooter();
        }
    }

    public Column<T, ?> getColumn(final Element elem) {
        String cellId = this.getColumnId(elem);
        return cellId == null
                ? null
                : this.idToColumnMap.get(cellId);
    }

    public Header<?> getHeader(final Element elem) {
        String headerId = this.getHeaderId(elem);
        return headerId == null
                ? null
                : this.idToHeaderMap.getValue(headerId);
    }

    public int getRowIndex(final TableRowElement row) {
        return Integer.parseInt(row.getAttribute("__gwt_header_row"));
    }

    public boolean isBuildingFooter() {
        return this.isFooter;
    }

    public boolean isColumn(final Element elem) {
        return this.getColumnId(elem) != null;
    }

    public boolean isHeader(final Element elem) {
        return this.getHeaderId(elem) != null;
    }

    public boolean isSortIconStartOfLine() {
        return this.isSortIconStartOfLine;
    }

    public void setSortIconStartOfLine(final boolean isStartOfLine) {
        this.isSortIconStartOfLine = isStartOfLine;
    }

    protected abstract boolean buildHeaderOrFooterImpl();

    protected final void enableColumnHandlers(final ElementBuilderBase<?> builder,
                                              final Column<T, ?> column) {
        String columnId = "column-" + Document.get().createUniqueId();
        this.idToColumnMap.put(columnId, column);
        builder.attribute("__gwt_column", columnId);
    }

    protected final Header<?> getHeader(final int index) {
        return this.isFooter
                ? this.getTable().getFooter(index)
                : this.getTable().getHeader(index);
    }

    protected AbstractCellTable<T> getTable() {
        return this.table;
    }

    protected final <H> void renderHeader(final ElementBuilderBase<?> out,
                                          final Cell.Context context,
                                          final Header<H> header) {
        String headerId = this.idToHeaderMap.getKey(header);
        if (headerId == null) {
            headerId = "header-" + Document.get().createUniqueId();
            this.idToHeaderMap.put(headerId, header);
        }

        out.attribute("__gwt_header", headerId);
        SafeHtmlBuilder sb = new SafeHtmlBuilder();
        header.render(context, sb);
        out.html(sb.toSafeHtml());
    }

    protected final void renderSortableHeader(final ElementBuilderBase<?> out,
                                              final Cell.Context context,
                                              final Header<?> header,
                                              final boolean isSorted,
                                              final boolean isSortAscending) {
        if (isSorted && !this.isFooter) {
            DivBuilder outerDiv = out.startDiv();
            outerDiv.className("dataGridSortableHeaderOuterDiv");

            DivBuilder nameHolder = outerDiv.startDiv();
            nameHolder.className("dataGridSortableHeaderNameHolder");
            this.renderHeader(nameHolder, context, header);
            nameHolder.endDiv();

            DivBuilder imageHolder = outerDiv.startDiv();
            imageHolder.className("dataGridSortableHeaderImageHolder");

            imageHolder.html(this.getSortIcon(isSortAscending));
            imageHolder.endDiv();

            outerDiv.endDiv();

        } else {
            this.renderHeader(out, context, header);
        }
    }

    protected final TableRowBuilder startRow() {
        while (this.section.getDepth() > 1) {
            this.section.end();
        }

        if (this.section.getDepth() < 1) {
            throw new IllegalStateException("Cannot start a row.  Did you call TableRowBuilder.end() too many times?");
        } else {
            TableRowBuilder row = this.section.startTR();
            row.attribute("__gwt_header_row", this.rowIndex);
            ++this.rowIndex;
            return row;
        }
    }

    private TableSectionBuilder buildHeaderOrFooter() {
        this.section = this.isFooter
                ? HtmlBuilderFactory.get().createTFootBuilder()
                : HtmlBuilderFactory.get().createTHeadBuilder();
        this.idToHeaderMap.clear();
        this.idToColumnMap.clear();
        this.rowIndex = 0;
        if (!this.buildHeaderOrFooterImpl()) {
            return null;
        } else {
            while (this.section.getDepth() > 0) {
                this.section.end();
            }

            return this.section;
        }
    }

    private String getColumnId(final Element elem) {
        return this.getElementAttribute(elem, "__gwt_column");
    }

    private String getElementAttribute(final Element elem, final String attribute) {
        if (elem == null) {
            return null;
        } else {
            String value = elem.getAttribute(attribute);
            return GwtNullSafe.isNonEmptyString(value)
                    ? value
                    : null;
        }
    }

    private String getHeaderId(final Element elem) {
        return this.getElementAttribute(elem, "__gwt_header");
    }

    private SafeHtml getSortIcon(final boolean isAscending) {
        return isAscending
                ? ARROW_UP_SAFE_HTML
                : ARROW_DOWN_SAFE_HTML;
    }


    // --------------------------------------------------------------------------------


    private static class TwoWayHashMap<K, V> {

        private final Map<K, V> keyToValue;
        private final Map<V, K> valueToKey;

        private TwoWayHashMap() {
            this.keyToValue = new HashMap<>();
            this.valueToKey = new HashMap<>();
        }

        void clear() {
            this.keyToValue.clear();
            this.valueToKey.clear();
        }

        K getKey(final V value) {
            return this.valueToKey.get(value);
        }

        V getValue(final K key) {
            return this.keyToValue.get(key);
        }

        void put(final K key, final V value) {
            this.keyToValue.put(key, value);
            this.valueToKey.put(value, key);
        }
    }
}
