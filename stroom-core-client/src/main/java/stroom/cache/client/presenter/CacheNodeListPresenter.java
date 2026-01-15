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

package stroom.cache.client.presenter;

import stroom.cache.shared.CacheInfoResponse;
import stroom.cache.shared.CacheResource;
import stroom.cell.info.client.ActionCell;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeManager;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.PageResponse;
import stroom.util.shared.cache.CacheInfo;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CacheNodeListPresenter extends MyPresenterWidget<PagerView> {

    private static final CacheResource CACHE_RESOURCE = GWT.create(CacheResource.class);

    private static final int ICON_COL = 18;
    private static final int SMALL_COL = 90;
    private static final int MEDIUM_COL = 130;
    private static final RegExp CASE_CONVERSION_REGEX = RegExp.compile("([a-z])([A-Z])", "g");
    protected static final String CACHE_INFO_KEY_HIT_COUNT = "HitCount";
    protected static final String CACHE_INFO_KEY_MISS_COUNT = "MissCount";
    protected static final String HIT_RATIO_KEY = "HitRatio";

    private final MyDataGrid<CacheInfo> dataGrid;

    private final RestFactory restFactory;
    private final NodeManager nodeManager;

    private final Map<String, List<CacheInfo>> responseMap = new HashMap<>();

    private RestDataProvider<CacheInfo, CacheInfoResponse> dataProvider;
    private String cacheName;

    private final DelayedUpdate delayedUpdate;

    private Range range;
    private Consumer<CacheInfoResponse> dataConsumer;
    private final Set<String> cacheInfoKeys = new HashSet<>();
    private final List<Column<CacheInfo, ?>> columns = new ArrayList<>();

    @Inject
    public CacheNodeListPresenter(final EventBus eventBus,
                                  final PagerView view,
                                  final RestFactory restFactory,
                                  final NodeManager nodeManager) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>(this);
        view.setDataWidget(dataGrid);

        this.restFactory = restFactory;
        this.nodeManager = nodeManager;
        this.delayedUpdate = new DelayedUpdate(this::update);
    }

    private void addColumns() {
        for (final Column<CacheInfo, ?> column : columns) {
            dataGrid.removeColumn(column);
        }
        columns.clear();

        // Clear.
        addClearColumn();
        // Node.
        addNodeColumn();
        addStatColumns();

        final EndColumn<CacheInfo> endColumn = new EndColumn<>();
        columns.add(endColumn);
        dataGrid.addEndColumn(endColumn);
    }

    private void addClearColumn() {
        addIconButtonColumn(
                SvgPresets.of(SvgPresets.DELETE, "Clear and rebuild cache", true),
                (row, nativeEvent) -> {
                    restFactory
                            .create(CACHE_RESOURCE)
                            .method(res -> res.clear(row.getName(), row.getNodeName()))
                            .onSuccess(result -> {
                                dataProvider.refresh();
                            })
                            .taskMonitorFactory(getView())
                            .exec();
                });
    }

    private void addStatColumns() {
        final List<String> sortedCacheKeys = new ArrayList<>(cacheInfoKeys);
        sortedCacheKeys.add(HIT_RATIO_KEY);
        sortedCacheKeys.sort(Comparator.naturalOrder());

        for (final String cacheInfoKey : sortedCacheKeys) {
            final String name = convertUpperCamelToHuman(cacheInfoKey);

            if (HIT_RATIO_KEY.equals(cacheInfoKey)
                && cacheInfoKeys.contains(CACHE_INFO_KEY_HIT_COUNT)
                && cacheInfoKeys.contains(CACHE_INFO_KEY_MISS_COUNT)) {
                addStatColumn("Hit Ratio", -1, row ->
                        getCacheHitRatio(row.getMap()));
            } else {
                addStatColumn(name, -1, row -> {
                    final String value = row.getMap().get(cacheInfoKey);
                    return formatValue(cacheInfoKey, value);
                });
            }
        }
    }

    private void addNodeColumn() {
        //noinspection Convert2Diamond
        final Column<CacheInfo, String> nodeColumn = new Column<CacheInfo, String>(new TextCell()) {
            @Override
            public String getValue(final CacheInfo row) {
                return row.getNodeName();
            }
        };
        dataGrid.addColumn(nodeColumn, "Node", MEDIUM_COL);
        columns.add(nodeColumn);
    }

    private void addIconButtonColumn(final Preset svgPreset,
                                     final BiConsumer<CacheInfo, NativeEvent> action) {
        //noinspection Convert2Diamond
        final ActionCell<CacheInfo> cell = new stroom.cell.info.client.ActionCell<CacheInfo>(
                svgPreset, action);
        //noinspection Convert2Diamond
        final Column<CacheInfo, CacheInfo> col =
                new Column<CacheInfo, CacheInfo>(cell) {
                    @Override
                    public CacheInfo getValue(final CacheInfo row) {
                        return row;
                    }

                    @Override
                    public void onBrowserEvent(final Context context,
                                               final Element elem,
                                               final CacheInfo rule,
                                               final NativeEvent event) {
                        super.onBrowserEvent(context, elem, rule, event);
                    }
                };
        columns.add(col);
        dataGrid.addColumn(col, "", ICON_COL);
    }

    private void addStatColumn(final String name,
                               final int width,
                               final Function<CacheInfo, String> valueExtractor) {
        final Column<CacheInfo, String> col = DataGridUtil.textColumnBuilder(valueExtractor).rightAligned().build();
        columns.add(col);
        final int newWidth = width == -1
                ? determineColumnWidth(name)
                : width;
        dataGrid.addResizableColumn(
                col,
                DataGridUtil.createRightAlignedHeader(name),
                newWidth);
    }

    private String getCacheHitRatio(final Map<String, String> cacheInfo) {
        final long hitCount = Long.parseLong(cacheInfo.get(CACHE_INFO_KEY_HIT_COUNT));
        final long missCount = Long.parseLong(cacheInfo.get(CACHE_INFO_KEY_MISS_COUNT));
        if (hitCount + missCount == 0) {
            return "-";
        } else {
            final double ratio = ((double) hitCount) / (hitCount + missCount);

            final BigDecimal scaledVal = new BigDecimal(ratio).setScale(3, RoundingMode.HALF_UP);
            return NumberFormat.getDecimalFormat()
                    .format(scaledVal);
        }
    }

    private String formatValue(final String cacheKey, final String value) {
        if (cacheKey.equals("TotalLoadTime")) {
            return value;
        } else if (cacheKey.startsWith("ExpireAfter")) {
            return value;
        } else {
            try {
                return ModelStringUtil.formatCsv(Long.parseLong(value));
            } catch (final NumberFormatException e) {
                return value;
            }
        }
    }

    private String convertUpperCamelToHuman(final String str) {
        return CASE_CONVERSION_REGEX.replace(str, "$1 $2");
    }

    private void addColumn(final Column<CacheInfo, ?> column, final String name, final int width) {
        columns.add(column);
        final int newWidth = width == -1
                ? determineColumnWidth(name)
                : width;
        dataGrid.addResizableColumn(column, name, newWidth);
    }

    /**
     * Crude method to size a column by its header
     */
    private int determineColumnWidth(final String text) {
        // Not a fixed width font so some chars are narrower
        final long thinCharsCount = text.chars()
                .filter(chr ->
                        chr == 'I'
                        || chr == 'i'
                        || chr == 'j'
                        || chr == 'l'
                        || chr == 'r'
                        || chr == 't')
                .count();
        final long wideCharsCount = text.chars()
                .filter(chr ->
                        chr == 'M'
                        || chr == 'W'
                        || chr == 'm'
                        || chr == 'w')
                .count();
        final long normalCharsCount = text.length() - thinCharsCount - wideCharsCount;
        // Adjust for the narrow chars present
        final double adjustedCharsCount = normalCharsCount
                                          + ((double) thinCharsCount * 0.6)
                                          + ((double) wideCharsCount * 1.3);
        // Now scale the to pixels
        final int colWidth = (int) (adjustedCharsCount * 8.5);
//        GWT.log("text: " + text
//                + " length: " + text.length()
//                + " eff width: " + effectiveWidth
//                + " col width: " + colWidth);
        return Math.max(colWidth, SMALL_COL);
    }

    public void read(final String cacheName) {
        if (cacheName != null) {
            this.cacheName = cacheName;
            responseMap.clear();

            if (dataProvider == null) {
                //noinspection Convert2Diamond
                dataProvider = new RestDataProvider<CacheInfo, CacheInfoResponse>(getEventBus()) {
                    @Override
                    protected void exec(final Range range,
                                        final Consumer<CacheInfoResponse> dataConsumer,
                                        final RestErrorHandler errorHandler) {
                        CacheNodeListPresenter.this.range = range;
                        CacheNodeListPresenter.this.dataConsumer = dataConsumer;
                        delayedUpdate.reset();
                        nodeManager.listAllNodes(nodeNames ->
                                fetchTasksForNodes(dataConsumer, errorHandler, nodeNames), errorHandler, getView());
                    }
                };
                dataProvider.addDataDisplay(dataGrid);
            }

            dataProvider.refresh();
        }
    }

    private void fetchTasksForNodes(final Consumer<CacheInfoResponse> dataConsumer,
                                    final RestErrorHandler errorHandler,
                                    final List<String> nodeNames) {
        cacheInfoKeys.clear();
        for (final String nodeName : nodeNames) {
            restFactory
                    .create(CACHE_RESOURCE)
                    .method(res -> res.info(cacheName, nodeName))
                    .onSuccess(response -> {
                        responseMap.put(nodeName, response.getValues());

                        cacheInfoKeys.addAll(response.getValues()
                                .stream()
                                .flatMap(resp -> resp.getMap().keySet().stream())
                                .collect(Collectors.toSet()));
                        delayedUpdate.update();
                    })
                    .onFailure(throwable -> {
                        responseMap.remove(nodeName);
                        delayedUpdate.update();
                    })
                    .taskMonitorFactory(getView())
                    .exec();
        }
    }

    private void update() {
        // Combine data from all nodes.
        addColumns();
        final List<CacheInfo> list = new ArrayList<>();
        responseMap.values().forEach(list::addAll);
        list.sort(Comparator.comparing(CacheInfo::getName));

        final long total = list.size();
        final List<CacheInfo> trimmed = new ArrayList<>();
        for (int i = range.getStart(); i < range.getStart() + range.getLength() && i < list.size(); i++) {
            trimmed.add(list.get(i));
        }
        final CacheInfoResponse response = new CacheInfoResponse(trimmed,
                new PageResponse(range.getStart(), trimmed.size(), total, true));
        dataConsumer.accept(response);
    }
}
