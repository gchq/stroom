//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.google.gwt.user.cellview.client;

import stroom.util.shared.NullSafe;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.dom.builder.shared.ElementBuilderBase;
import com.google.gwt.dom.builder.shared.HtmlBuilderFactory;
import com.google.gwt.dom.builder.shared.HtmlTableSectionBuilder;
import com.google.gwt.dom.builder.shared.TableRowBuilder;
import com.google.gwt.dom.builder.shared.TableSectionBuilder;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractHeaderOrFooterBuilder<T> implements HeaderBuilder<T>, FooterBuilder<T> {

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
        final String cellId = this.getColumnId(elem);
        return cellId == null
                ? null
                : this.idToColumnMap.get(cellId);
    }

    public Header<?> getHeader(final Element elem) {
        final String headerId = this.getHeaderId(elem);
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
        final String columnId = "column-" + Document.get().createUniqueId();
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
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        header.render(context, sb);
        out.html(sb.toSafeHtml());
    }

    protected final TableRowBuilder startRow() {
        while (this.section.getDepth() > 1) {
            this.section.end();
        }

        if (this.section.getDepth() < 1) {
            throw new IllegalStateException("Cannot start a row.  Did you call TableRowBuilder.end() too many times?");
        } else {
            final TableRowBuilder row = this.section.startTR();
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
            final String value = elem.getAttribute(attribute);
            return NullSafe.isNonEmptyString(value)
                    ? value
                    : null;
        }
    }

    private String getHeaderId(final Element elem) {
        return this.getElementAttribute(elem, "__gwt_header");
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
