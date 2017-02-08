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
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import stroom.cache.shared.CacheClearAction;
import stroom.cache.shared.CacheNodeRow;
import stroom.cache.shared.CacheRow;
import stroom.cache.shared.FetchCacheNodeRowAction;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.HasRead;
import stroom.streamstore.client.presenter.ActionDataProvider;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;

public class CacheNodeListPresenter extends MyPresenterWidget<DataGridView<CacheNodeRow>>implements HasRead<CacheRow> {
    private static final int SMALL_COL = 90;
    private static final int MEDIUM_COL = 150;

    private final FetchCacheNodeRowAction action = new FetchCacheNodeRowAction();
    private final ClientDispatchAsync dispatcher;
    private ActionDataProvider<CacheNodeRow> dataProvider;

    @Inject
    public CacheNodeListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
            final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<CacheNodeRow>(false));
        this.dispatcher = dispatcher;

        // Node.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return row.getNode().getName();
            }
        }, "Node", MEDIUM_COL);

        // Cache Hits.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getCacheHits());
            }
        }, "Cache Hits:", SMALL_COL);
        // In Memory Hits.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getInMemoryHits());
            }
        }, "Memory", SMALL_COL);
        // Off Heap Hits.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getOffHeapHits());
            }
        }, "Off Heap", SMALL_COL);
        // On Disk Hits.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getOnDiskHits());
            }
        }, "Disk", SMALL_COL);

        // Misses.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getCacheMisses());
            }
        }, "Cache Misses:", SMALL_COL);
        // In Memory Misses.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getInMemoryMisses());
            }
        }, "Memory", SMALL_COL);
        // Off Heap Misses.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getOffHeapMisses());
            }
        }, "Off Heap", SMALL_COL);
        // On Disk Misses.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getOnDiskMisses());
            }
        }, "Disk", SMALL_COL);

        // Object Count.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getObjectCount());
            }
        }, "Object Count:", SMALL_COL);
        // Memory Store Object Count.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getMemoryStoreObjectCount());
            }
        }, "Memory", SMALL_COL);
        // Off Heap Store Object Count.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getOffHeapStoreObjectCount());
            }
        }, "Off Heap", SMALL_COL);
        // Disk Store Object Count.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getDiskStoreObjectCount());
            }
        }, "Disk", SMALL_COL);

        // Average Get Time.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Float.toString(row.getCacheInfo().getAverageGetTime());
            }
        }, "Average Get Time", MEDIUM_COL);
        // Eviction Count.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getEvictionCount());
            }
        }, "Eviction Count", MEDIUM_COL);

        // Searches Per Second.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getSearchesPerSecond());
            }
        }, "Searches Per Second", MEDIUM_COL);

        // Average Search Time.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getAverageSearchTime());
            }
        }, "Average Search Time", MEDIUM_COL);

        // Writer Queue Size.
        getView().addResizableColumn(new Column<CacheNodeRow, String>(new TextCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return Long.toString(row.getCacheInfo().getWriterQueueSize());
            }
        }, "Writer Queue Size", MEDIUM_COL);

        // Clear.
        final Column<CacheNodeRow, String> clearColumn = new Column<CacheNodeRow, String>(new ButtonCell()) {
            @Override
            public String getValue(final CacheNodeRow row) {
                return "Clear";
            }
        };
        clearColumn.setFieldUpdater(new FieldUpdater<CacheNodeRow, String>() {
            @Override
            public void update(final int index, final CacheNodeRow row, final String value) {
                dispatcher.execute(new CacheClearAction(row.getCacheInfo().getName(), row.getNode()), null);
            }
        });
        getView().addColumn(clearColumn, "</br>");
        getView().addEndColumn(new EndColumn<CacheNodeRow>());
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
