/*
 * Copyright 2016 Crown Copyright
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

import stroom.cache.shared.CacheInfo;
import stroom.cache.shared.CacheInfoResponse;
import stroom.cache.shared.CacheResource;
import stroom.cell.info.client.InfoColumn;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeManager;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.PageResponse;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

import com.google.gwt.cell.client.ButtonCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CacheNodeListPresenter extends MyPresenterWidget<DataGridView<CacheInfo>> {

    private static final CacheResource CACHE_RESOURCE = GWT.create(CacheResource.class);

    private static final int SMALL_COL = 90;
    private static final int MEDIUM_COL = 150;
    private static final RegExp CASE_CONVERSION_REGEX = RegExp.compile("([a-z])([A-Z])", "g");

    private final RestFactory restFactory;
    private final TooltipPresenter tooltipPresenter;
    private final NodeManager nodeManager;

    private final Map<String, List<CacheInfo>> responseMap = new HashMap<>();

    private RestDataProvider<CacheInfo, CacheInfoResponse> dataProvider;
    private String cacheName;

    private final DelayedUpdate delayedUpdate;

    private Range range;
    private Consumer<CacheInfoResponse> dataConsumer;
    private Set<String> cacheInfoKeys = new HashSet<>();
    private List<Column<CacheInfo, ?>> columns = new ArrayList<>();

    @Inject
    public CacheNodeListPresenter(final EventBus eventBus,
                                  final RestFactory restFactory,
                                  final TooltipPresenter tooltipPresenter,
                                  final NodeManager nodeManager) {
        super(eventBus, new DataGridViewImpl<>(false));
        this.restFactory = restFactory;
        this.tooltipPresenter = tooltipPresenter;
        this.nodeManager = nodeManager;
        this.delayedUpdate = new DelayedUpdate(this::update);

//        addColumns();
    }

    private void addColumns() {
        for (final Column<CacheInfo, ?> column : columns) {
            getView().removeColumn(column);
        }
        columns.clear();

        // Info.
        addInfoColumn();

        // Node.
        addColumn(new Column<CacheInfo, String>(new TextCell()) {
            @Override
            public String getValue(final CacheInfo row) {
                return row.getNodeName();
            }
        }, "Node", MEDIUM_COL);

        // Entries.
//        addColumn(new Column<CacheInfo, String>(new TextCell()) {
//            @Override
//            public String getValue(final CacheInfo row) {
//                return row.getMap().get("Entries");
//            }
//        }, "Entries", SMALL_COL);

        // Max Entries.
//        addColumn(new Column<CacheInfo, String>(new TextCell()) {
//            @Override
//            public String getValue(final CacheInfo row) {
//                return row.getMap().get("MaximumSize");
//            }
//        }, "Max Entries", SMALL_COL);

        // Expiry.
//        addColumn(new Column<CacheInfo, String>(new TextCell()) {
//            @Override
//            public String getValue(final CacheInfo row) {
//                String expiry = row.getMap().get("ExpireAfterAccess");
//                if (expiry == null) {
//                    expiry = row.getMap().get("ExpireAfterWrite");
//                }
//                return expiry;
//            }
//        }, "Expiry", SMALL_COL);

        for (final String cacheInfoKey : cacheInfoKeys.stream().sorted().collect(Collectors.toList())) {
            final String name = convertUpperCamelToHuman(cacheInfoKey);
            final Column<CacheInfo, String> column = new Column<CacheInfo, String>(new TextCell()) {
                @Override
                public String getValue(final CacheInfo row) {
                    String value = row.getMap().get(cacheInfoKey);
                    return value;
                }
            };
            addColumn(column, name, -1);
        }

        // Clear.
        final Column<CacheInfo, String> clearColumn = new Column<CacheInfo, String>(new ButtonCell()) {
            @Override
            public String getValue(final CacheInfo row) {
                return "Clear";
            }
        };
        clearColumn.setFieldUpdater((index, row, value) -> {
            final Rest<Boolean> rest = restFactory.create();
            rest.call(CACHE_RESOURCE).clear(row.getName(), row.getNodeName());
        });
        addColumn(clearColumn, "</br>", 50);

        final EndColumn<CacheInfo> endColumn = new EndColumn<>();
        columns.add(endColumn);
        getView().addEndColumn(endColumn);
    }

    private String convertUpperCamelToHuman(final String str) {
        return CASE_CONVERSION_REGEX.replace(str, "$1 $2");
    }

    private void addColumn(Column<CacheInfo, ?> column, String name, int width) {
        columns.add(column);
        final int newWidth = width == -1
        ? (int) (((double) name.length() / 11) * SMALL_COL)
                : width;
        getView().addResizableColumn(column, name, newWidth);
    }

    private void addInfoColumn() {
        // Info column.
        final InfoColumn<CacheInfo> infoColumn = new InfoColumn<CacheInfo>() {
            @Override
            public SvgPreset getValue(final CacheInfo object) {
                return SvgPresets.INFO;
            }

            @Override
            protected void showInfo(final CacheInfo row, final int x, final int y) {
                final SafeHtml html = getInfoHtml(row);
                tooltipPresenter.setHTML(html);
                final PopupPosition popupPosition = new PopupPosition(x, y);
                ShowPopupEvent.fire(CacheNodeListPresenter.this, tooltipPresenter, PopupType.POPUP,
                        popupPosition, null);
            }
        };
        getView().addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
        columns.add(infoColumn);
    }

    private SafeHtml getInfoHtml(final CacheInfo cacheInfo) {

        return TooltipUtil.builder()
                .addTwoColTable(tableBuilder -> {
                    tableBuilder.addHeaderRow(cacheInfo.getNodeName());
                    final Map<String, String> map = cacheInfo.getMap();
                    map.keySet().stream()
                            .sorted(Comparator.naturalOrder())
                            .forEachOrdered(k -> {
                                final String v = map.get(k);
                                tableBuilder.addRow(k, v);
                            });
                    return tableBuilder.build();
                })
                .build();
    }

    public void read(final String cacheName) {
        if (cacheName != null) {
            this.cacheName = cacheName;
            responseMap.clear();

            if (dataProvider == null) {
                dataProvider = new RestDataProvider<CacheInfo, CacheInfoResponse>(getEventBus()) {
                    @Override
                    protected void exec(final Range range,
                                        final Consumer<CacheInfoResponse> dataConsumer,
                                        final Consumer<Throwable> throwableConsumer) {
                        CacheNodeListPresenter.this.range = range;
                        CacheNodeListPresenter.this.dataConsumer = dataConsumer;
                        delayedUpdate.reset();
                        nodeManager.listAllNodes(nodeNames ->
                                fetchTasksForNodes(dataConsumer, throwableConsumer, nodeNames), throwableConsumer);
                    }
                };
                dataProvider.addDataDisplay(getView().getDataDisplay());
            }

            dataProvider.refresh();
        }
    }

    private void fetchTasksForNodes(final Consumer<CacheInfoResponse> dataConsumer,
                                    final Consumer<Throwable> throwableConsumer,
                                    final List<String> nodeNames) {
        cacheInfoKeys.clear();
        for (final String nodeName : nodeNames) {
            final Rest<CacheInfoResponse> rest = restFactory.create();
            rest
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
                    .call(CACHE_RESOURCE).info(cacheName, nodeName);
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
