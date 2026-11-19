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

package stroom.cache.client.component;

import stroom.cell.client.ActionCell;
import stroom.cell.client.CellIcon;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.cache.CacheInfo;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/// A cache on each node, a row each: a button to clear it there, the node's name and a column for
/// each statistic the nodes report (hits, misses, sizes and the like), with the hit ratio worked out
/// when there are hits and misses.
public class CacheNodeList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final int ICON_COL = 18;
    private static final int SMALL_COL = 90;
    private static final int MEDIUM_COL = 130;
    private static final RegExp CASE_CONVERSION_REGEX = RegExp.compile("([a-z])([A-Z])", "g");
    private static final String HIT_COUNT_KEY = "HitCount";
    private static final String MISS_COUNT_KEY = "MissCount";
    private static final String HIT_RATIO_KEY = "HitRatio";

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<CacheInfo> dataGrid;

    private final List<Column<CacheInfo, ?>> columns = new ArrayList<>();
    private Consumer<CacheInfo> onClear = row -> {
    };

    /// Makes an empty list.
    public CacheNodeList() {
        initWidget(BINDER.createAndBindUi(this));
        // A row for each node, keyed by its name
        dataGrid.setKeyProvider(CacheInfo::getNodeName);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<CacheInfo> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @param onClear Given the cache on a node when its Clear and rebuild button is pressed.
    public void setOnClear(final Consumer<CacheInfo> onClear) {
        this.onClear = onClear;
    }

    /// Makes the columns again, for the statistics the nodes report.
    ///
    /// @param statKeys The statistics' keys, e.g. "HitCount".
    public void setStatKeys(final Collection<String> statKeys) {
        for (final Column<CacheInfo, ?> column : columns) {
            dataGrid.removeColumn(column);
        }
        columns.clear();
        addClearColumn();
        addNodeColumn();
        addStatColumns(statKeys);
    }

    private void addClearColumn() {
        final ActionCell<CacheInfo> cell = new ActionCell<>(
                CellIcon.of(SvgImage.DELETE, "Clear and rebuild cache"),
                (row, nativeEvent) -> onClear.accept(row));
        final Column<CacheInfo, CacheInfo> column = new Column<>(cell) {
            @Override
            public CacheInfo getValue(final CacheInfo row) {
                return row;
            }
        };
        columns.add(column);
        dataGrid.addColumn(column, "", ICON_COL);
    }

    private void addNodeColumn() {
        final Column<CacheInfo, String> column = new Column<>(new TextCell()) {
            @Override
            public String getValue(final CacheInfo row) {
                return row.getNodeName();
            }
        };
        columns.add(column);
        dataGrid.addColumn(column, "Node", MEDIUM_COL);
    }

    private void addStatColumns(final Collection<String> statKeys) {
        final List<String> sortedKeys = new ArrayList<>(statKeys);
        // The hit ratio is worked out from the hits and misses, so only when there are both
        if (statKeys.contains(HIT_COUNT_KEY) && statKeys.contains(MISS_COUNT_KEY)) {
            sortedKeys.add(HIT_RATIO_KEY);
        }
        sortedKeys.sort(Comparator.naturalOrder());
        for (final String key : sortedKeys) {
            if (HIT_RATIO_KEY.equals(key)) {
                addStatColumn("Hit Ratio", row -> hitRatio(row.getMap()));
            } else {
                addStatColumn(CASE_CONVERSION_REGEX.replace(key, "$1 $2"),
                        row -> formatValue(key, row.getMap().get(key)));
            }
        }
    }

    private void addStatColumn(final String name, final Function<CacheInfo, String> valueExtractor) {
        final Column<CacheInfo, String> column = DataGridUtil.textColumnBuilder(valueExtractor)
                .rightAligned()
                .build();
        columns.add(column);
        dataGrid.addResizableColumn(column, DataGridUtil.createRightAlignedHeader(name), columnWidth(name));
    }

    private static String hitRatio(final Map<String, String> cacheInfo) {
        final long hitCount = Long.parseLong(cacheInfo.get(HIT_COUNT_KEY));
        final long missCount = Long.parseLong(cacheInfo.get(MISS_COUNT_KEY));
        if (hitCount + missCount == 0) {
            return "-";
        }
        final double ratio = ((double) hitCount) / (hitCount + missCount);
        return NumberFormat.getDecimalFormat().format(new BigDecimal(ratio).setScale(3, RoundingMode.HALF_UP));
    }

    /// @param key   A statistic's key.
    /// @param value Its value.
    /// @return The value as shown: a count with thousands separators; a time or an expiry as it is.
    static String formatValue(final String key, final String value) {
        if (key.equals("TotalLoadTime") || key.startsWith("ExpireAfter")) {
            return value;
        }
        try {
            return ModelStringUtil.formatCsv(Long.parseLong(value));
        } catch (final NumberFormatException e) {
            return value;
        }
    }

    /// A column's width for its heading, roughly: the font isn't fixed width, so some letters count
    /// for less and some for more.
    ///
    /// @param text The heading.
    /// @return The width in pixels, no less than a small column's.
    static int columnWidth(final String text) {
        final long thinCharsCount = text.chars()
                .filter(chr -> chr == 'I' || chr == 'i' || chr == 'j' || chr == 'l' || chr == 'r' || chr == 't')
                .count();
        final long wideCharsCount = text.chars()
                .filter(chr -> chr == 'M' || chr == 'W' || chr == 'm' || chr == 'w')
                .count();
        final long normalCharsCount = text.length() - thinCharsCount - wideCharsCount;
        final double adjustedCharsCount = normalCharsCount
                                          + ((double) thinCharsCount * 0.6)
                                          + ((double) wideCharsCount * 1.3);
        return Math.max((int) (adjustedCharsCount * 8.5), SMALL_COL);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CacheNodeList> {

    }
}
