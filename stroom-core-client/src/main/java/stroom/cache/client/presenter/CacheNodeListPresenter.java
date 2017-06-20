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

import com.google.gwt.cell.client.ButtonCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.cache.shared.CacheClearAction;
import stroom.cache.shared.CacheInfo;
import stroom.cache.shared.CacheNodeRow;
import stroom.cache.shared.CacheRow;
import stroom.cache.shared.FetchCacheNodeRowAction;
import stroom.cell.info.client.InfoColumn;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.HasRead;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.streamstore.client.presenter.ColumnSizeConstants;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

public class CacheNodeListPresenter extends MyPresenterWidget<DataGridView<CacheNodeRow>> implements HasRead<CacheRow> {
    private static final int SMALL_COL = 90;
    private static final int MEDIUM_COL = 150;

    private final FetchCacheNodeRowAction action = new FetchCacheNodeRowAction();
    private final ClientDispatchAsync dispatcher;
    private final TooltipPresenter tooltipPresenter;

    private ActionDataProvider<CacheNodeRow> dataProvider;

    @Inject
    public CacheNodeListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                                  final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<CacheNodeRow>(false));
        this.dispatcher = dispatcher;
        this.tooltipPresenter = tooltipPresenter;

        // Info.
        addInfoColumn();

        // Node.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return row.getNode().getName();
            }
        }, "Node", MEDIUM_COL);

        // Hits.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getCacheHits());
            }
        }, "Hits", SMALL_COL);

        // Misses.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getCacheMisses());
            }
        }, "Misses", SMALL_COL);

        // Objects.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getObjectCount());
            }
        }, "Objects", SMALL_COL);

        // Clear.
        final Column<CacheNodeRow, String> clearColumn = new Column<CacheNodeRow, String>(new ButtonCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return "Clear";
            }
        };
        clearColumn.setFieldUpdater((index, row, value) -> dispatcher.exec(new CacheClearAction(row.getCacheInfo().getName(), row.getNode())));
        getView().addColumn(clearColumn, "</br>", 50);

        getView().addEndColumn(new EndColumn<CacheNodeRow>());
    }

    private void addInfoColumn() {
        // Info column.
        final InfoColumn<CacheNodeRow> infoColumn = new InfoColumn<CacheNodeRow>() {
            @Override
            public SvgPreset getValue(final CacheNodeRow object) {
                return SvgPresets.INFO;
            }

            @Override
            protected void showInfo(final CacheNodeRow row, final int x, final int y) {
                final String html = getInfoHtml(row);
                tooltipPresenter.setHTML(html);
                final PopupPosition popupPosition = new PopupPosition(x, y);
                ShowPopupEvent.fire(CacheNodeListPresenter.this, tooltipPresenter, PopupType.POPUP,
                        popupPosition, null);
            }
        };
        getView().addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    private String getInfoHtml(final CacheNodeRow row) {
        final CacheInfo cacheInfo = row.getCacheInfo();
        
        final StringBuilder sb = new StringBuilder();
        TooltipUtil.addHeading(sb, row.getNode().getName());

        TooltipUtil.addBreak(sb);

        TooltipUtil.addHeading(sb, "Cache Hits");
        TooltipUtil.addRowData(sb, "Memory", cacheInfo.getInMemoryHits());
        TooltipUtil.addRowData(sb, "Off Heap", cacheInfo.getOffHeapHits());
        TooltipUtil.addRowData(sb, "Disk", cacheInfo.getOnDiskHits());
        TooltipUtil.addRowData(sb, "Total", cacheInfo.getCacheHits());

        TooltipUtil.addBreak(sb);

        TooltipUtil.addHeading(sb, "Cache Misses");
        TooltipUtil.addRowData(sb, "Memory", cacheInfo.getInMemoryMisses());
        TooltipUtil.addRowData(sb, "Off Heap", cacheInfo.getOffHeapMisses());
        TooltipUtil.addRowData(sb, "Disk", cacheInfo.getOnDiskMisses());
        TooltipUtil.addRowData(sb, "Total", cacheInfo.getCacheMisses());

        TooltipUtil.addBreak(sb);

        TooltipUtil.addHeading(sb, "Objects");
        TooltipUtil.addRowData(sb, "Memory", cacheInfo.getMemoryStoreObjectCount());
        TooltipUtil.addRowData(sb, "Off Heap", cacheInfo.getOffHeapStoreObjectCount());
        TooltipUtil.addRowData(sb, "Disk", cacheInfo.getDiskStoreObjectCount());
        TooltipUtil.addRowData(sb, "Total", cacheInfo.getObjectCount());

        TooltipUtil.addBreak(sb);

        TooltipUtil.addRowData(sb, "Average Get Time", cacheInfo.getAverageGetTime());
        TooltipUtil.addRowData(sb, "Eviction Count", cacheInfo.getEvictionCount());
        TooltipUtil.addRowData(sb, "Searches Per Second", cacheInfo.getSearchesPerSecond());
        TooltipUtil.addRowData(sb, "Average Search Time", cacheInfo.getAverageSearchTime());
        TooltipUtil.addRowData(sb, "Writer Queue Size", cacheInfo.getWriterQueueSize());

        return sb.toString();
    }

    @Override
    public void read(final CacheRow entity) {
        if (entity != null) {
            action.setCacheName(entity.getCacheName());

            if (dataProvider == null) {
                dataProvider = new ActionDataProvider<CacheNodeRow>(dispatcher, action);
                dataProvider.addDataDisplay(getView().getDataDisplay());
            }

            dataProvider.refresh();
        }
    }
}