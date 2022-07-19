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

package stroom.index.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexResource;
import stroom.index.shared.IndexShard;
import stroom.node.client.NodeManager;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class IndexShardPresenter extends MyPresenterWidget<PagerView>
        implements Refreshable, HasDocumentRead<IndexDoc>, ReadOnlyChangeHandler {

    private static final IndexResource INDEX_RESOURCE = GWT.create(IndexResource.class);

    private final MyDataGrid<IndexShard> dataGrid;
    private final TooltipPresenter tooltipPresenter;
    private final RestFactory restFactory;
    private final NodeManager nodeManager;
    private final ClientSecurityContext securityContext;
    private final DateTimeFormatter dateTimeFormatter;
    private RestDataProvider<IndexShard, ResultPage<IndexShard>> dataProvider;
    private ResultPage<IndexShard> resultList = null;
    private final FindIndexShardCriteria selectionCriteria = FindIndexShardCriteria.matchAll();
    private final FindIndexShardCriteria queryCriteria = FindIndexShardCriteria.matchAll();
    private final DelayedUpdate delayedUpdate;

    private ButtonView buttonFlush;
    private ButtonView buttonDelete;
    private IndexDoc index;
    private boolean readOnly;
    private boolean allowDelete;

    @Inject
    public IndexShardPresenter(final EventBus eventBus,
                               final PagerView view,
                               final TooltipPresenter tooltipPresenter,
                               final RestFactory restFactory,
                               final NodeManager nodeManager,
                               final ClientSecurityContext securityContext,
                               final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        view.setDataWidget(dataGrid);

        this.tooltipPresenter = tooltipPresenter;
        this.restFactory = restFactory;
        this.nodeManager = nodeManager;
        this.securityContext = securityContext;
        this.dateTimeFormatter = dateTimeFormatter;

        if (securityContext.hasAppPermission(PermissionNames.MANAGE_INDEX_SHARDS_PERMISSION)) {
            buttonFlush = view.addButton(SvgPresets.SHARD_FLUSH);
            buttonDelete = view.addButton(SvgPresets.DELETE);
        }

        addColumns();
        delayedUpdate = new DelayedUpdate(this::refresh);
    }

    @Override
    protected void onBind() {
        super.onBind();
        if (buttonFlush != null) {
            registerHandler(buttonFlush.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    flush();
                }
            }));
        }
        if (buttonDelete != null) {
            registerHandler(buttonDelete.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    delete();
                }
            }));
        }
    }

    private void enableButtons() {
        final boolean enabled = !readOnly && !selectionCriteria.getIndexShardIdSet().isMatchNothing();
        if (buttonFlush != null) {
            if (readOnly) {
                buttonFlush.setTitle("Flush is not available as index is read only");
            } else {
                buttonFlush.setTitle("Flush Selected Shards");
            }
            buttonFlush.setEnabled(enabled);
        }
        if (buttonDelete != null) {
            if (readOnly) {
                buttonDelete.setTitle("Delete is not available as index is read only");
            } else if (!allowDelete) {
                buttonDelete.setTitle("You do not have delete permissions on this index");
            } else {
                buttonDelete.setTitle("Delete Selected Shards");
            }
            buttonDelete.setEnabled(allowDelete && enabled);
        }
    }

    private void addColumns() {
        addSelectedColumn();
        addInfoColumn();
        addNodeColumn();
        addPartitionColumn();
        addPathColumn();
        addStatusColumn();
        addDocCountColumn();
        addFileSizeColumn();
        addBytesPDColumn();
        addCommitColumn();
//        addCommitDurationColumn();
//        addCommitCountColumn();
        addVersionColumn();
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addSelectedColumn() {
        // Select Column
        final Column<IndexShard, TickBoxState> column = new Column<IndexShard, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final IndexShard indexShard) {
                final boolean match = selectionCriteria.getIndexShardIdSet().isMatch(indexShard.getId());
                return TickBoxState.fromBoolean(match);
            }

        };
        final Header<TickBoxState> header = new Header<TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue() {
                if (selectionCriteria.getIndexShardIdSet().isMatchAll()) {
                    return TickBoxState.TICK;
                } else if (selectionCriteria.getIndexShardIdSet().size() > 0) {
                    return TickBoxState.HALF_TICK;
                }
                return TickBoxState.UNTICK;
            }
        };
        dataGrid.addColumn(column, header, ColumnSizeConstants.CHECKBOX_COL);

        // Add Handlers
        header.setUpdater(value -> {
            if (value.equals(TickBoxState.UNTICK)) {
                selectionCriteria.getIndexShardIdSet().clear();
                selectionCriteria.getIndexShardIdSet().setMatchAll(false);
            } else if (value.equals(TickBoxState.TICK)) {
                selectionCriteria.getIndexShardIdSet().clear();
                selectionCriteria.getIndexShardIdSet().setMatchAll(true);
            }

            if (dataProvider != null) {
                dataProvider.updateRowData(dataProvider.getRanges()[0].getStart(), resultList.getValues());
            }

            enableButtons();
        });
        column.setFieldUpdater((index, row, value) -> {
            if (value.toBoolean()) {
                selectionCriteria.getIndexShardIdSet().add(row.getId());
            } else {
                // De-selecting one and currently matching all ?
                if (selectionCriteria.getIndexShardIdSet().isMatchAll()) {
                    selectionCriteria.getIndexShardIdSet().setMatchAll(false);
                    selectionCriteria.getIndexShardIdSet().addAll(getResultStreamIdSet());
                }
                selectionCriteria.getIndexShardIdSet().remove(row.getId());
            }
            dataGrid.redrawHeaders();
            enableButtons();
        });
    }

    private void addInfoColumn() {
        // Info column.
        final InfoColumn<IndexShard> infoColumn = new InfoColumn<IndexShard>() {
            @Override
            protected void showInfo(final IndexShard indexShard, final int x, final int y) {
                final TooltipUtil.Builder builder = TooltipUtil.builder()
                        .addTwoColTable(tableBuilder -> {
                            if (index != null) {
                                tableBuilder.addRow("Index UUID", String.valueOf(index.getUuid()));
                            }
                            tableBuilder
                                    .addRow("Shard Id", String.valueOf(indexShard.getId()))
                                    .addRow("Node", indexShard.getNodeName())
                                    .addRow("Partition", indexShard.getPartition());

                            if (indexShard.getPartitionFromTime() != null) {
                                tableBuilder.addRow("Partition From",
                                        dateTimeFormatter.format(indexShard.getPartitionFromTime()));
                            }
                            if (indexShard.getPartitionToTime() != null) {
                                tableBuilder.addRow("Partition To",
                                        dateTimeFormatter.format(indexShard.getPartitionToTime()));
                            }

                            return tableBuilder
                                    .addRow("Path", indexShard.getVolume().getPath())
                                    .addRow("Status", indexShard.getStatus().getDisplayValue())
                                    .addRow("Document Count", intToString(indexShard.getDocumentCount()))
                                    .addRow("File Size", indexShard.getFileSizeString())
                                    .addRow("Bytes Per Document", intToString(indexShard.getBytesPerDocument()))
                                    .addRow("Commit", dateTimeFormatter.format(indexShard.getCommitMs()))
                                    .addRow("Commit Duration",
                                            ModelStringUtil.formatDurationString(indexShard.getCommitDurationMs()))
                                    .addRow("Commit Document Count", intToString(indexShard.getCommitDocumentCount()))
                                    .addRow("Index Version", indexShard.getIndexVersion())
                                    .build();
                        });
                tooltipPresenter.show(builder.build(), x, y);
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    private void addNodeColumn() {
        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getNodeName();
            }
        }, "Node", 100);
    }

    private void addPartitionColumn() {
        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getPartition();
            }
        }, "Partition", 100);
    }

    private void addPathColumn() {
        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getVolume().getPath();
            }
        }, "Path", 200);
    }

    private void addStatusColumn() {
        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getStatus().getDisplayValue();
            }
        }, "Status", 100);
    }

    private void addDocCountColumn() {
        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return intToString(indexShard.getDocumentCount());
            }
        }, "Doc Count", 100);
    }

    private void addFileSizeColumn() {
        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getFileSizeString();
            }
        }, "File Size", 100);
    }

    private void addBytesPDColumn() {
        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return intToString(indexShard.getBytesPerDocument());
            }
        }, "Bytes pd", 100);
    }

    private void addCommitColumn() {
        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return dateTimeFormatter.format(indexShard.getCommitMs());
            }
        }, "Last Commit", 170);
    }

//    private void addCommitDurationColumn() {
//        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
//            @Override
//            public String getValue(final IndexShard indexShard) {
//                return ModelStringUtil.formatDurationString(indexShard.getCommitDurationMs());
//            }
//        }, "Commit In", 100);
//    }
//
//    private void addCommitCountColumn() {
//        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
//            @Override
//            public String getValue(final IndexShard indexShard) {
//                return intToString(indexShard.getCommitDocumentCount());
//            }
//        }, "Commit Count", 100);
//    }

    private void addVersionColumn() {
        dataGrid.addResizableColumn(new Column<IndexShard, String>(new TextCell()) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getIndexVersion();
            }
        }, "Index Version", 100);
    }

    private String intToString(final Integer integer) {
        if (integer == null) {
            return null;
        }
        return integer.toString();
    }

    private Set<Long> getResultStreamIdSet() {
        final HashSet<Long> rtn = new HashSet<>();
        if (resultList != null) {
            for (final IndexShard e : resultList.getValues()) {
                rtn.add(e.getId());
            }
        }
        return rtn;

    }

    @Override
    public void refresh() {
        dataProvider.refresh();
    }

    @Override
    public void read(final DocRef docRef, final IndexDoc index) {
        if (index != null) {
            this.index = index;
            queryCriteria.getIndexUuidSet().add(docRef.getUuid());
            selectionCriteria.getIndexUuidSet().add(docRef.getUuid());
            selectionCriteria.getIndexShardIdSet().clear();

            if (dataProvider == null) {
                dataProvider = new RestDataProvider<IndexShard, ResultPage<IndexShard>>(getEventBus()) {
                    @Override
                    protected void exec(final Range range,
                                        final Consumer<ResultPage<IndexShard>> dataConsumer,
                                        final Consumer<Throwable> throwableConsumer) {
                        CriteriaUtil.setRange(queryCriteria, range);
                        final Rest<ResultPage<IndexShard>> rest = restFactory.create();
                        rest
                                .onSuccess(dataConsumer)
                                .onFailure(throwableConsumer)
                                .call(INDEX_RESOURCE)
                                .find(queryCriteria);
                    }

                    @Override
                    protected void changeData(final ResultPage<IndexShard> data) {
                        super.changeData(data);
                        onChangeData(data);
                    }
                };
                dataProvider.addDataDisplay(dataGrid);
            }

            securityContext.hasDocumentPermission(index.getUuid(), DocumentPermissionNames.DELETE).onSuccess(result -> {
                this.allowDelete = result;
                enableButtons();
            });
        }
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        enableButtons();
    }

    private void onChangeData(final ResultPage<IndexShard> data) {
        resultList = data;

        if (!selectionCriteria.getIndexShardIdSet().isMatchAll()) {
            if (selectionCriteria.getIndexShardIdSet().getSet().retainAll(getResultStreamIdSet())) {
                enableButtons();
            }
        }
    }

    private void flush() {
        if (selectionCriteria.getIndexShardIdSet().isMatchAll()) {
            ConfirmEvent.fire(this, "Are you sure you want to flush all index shards?", result -> {
                if (result) {
                    ConfirmEvent.fire(IndexShardPresenter.this,
                            "You have selected to flush all filtered index shards! Are you absolutely " +
                                    "sure you want to do this?",
                            result1 -> {
                                if (result1) {
                                    doFlush();
                                }
                            });
                }
            });
        } else if (selectionCriteria.getIndexShardIdSet().size() > 0) {
            ConfirmEvent.fire(
                    this,
                    "Are you sure you want to flush the selected index shards?",
                    result -> {
                        if (result) {
                            doFlush();
                        }
                    });

        } else {
            AlertEvent.fireWarn(
                    this,
                    "No index shards have been selected for flushing!",
                    null);
        }
    }

    private void delete() {
        if (selectionCriteria.getIndexShardIdSet().isMatchAll()) {
            ConfirmEvent.fire(this, "Are you sure you want to delete all index shards?",
                    result -> {
                        if (result) {
                            ConfirmEvent.fire(IndexShardPresenter.this,
                                    "You have selected to delete all filtered index shards! Are you " +
                                            "absolutely sure you want to do this?",
                                    result1 -> {
                                        if (result1) {
                                            doDelete();
                                        }
                                    });
                        }
                    });
        } else if (selectionCriteria.getIndexShardIdSet().size() > 0) {
            ConfirmEvent.fire(this, "Are you sure you want to delete the selected index shards?",
                    result -> {
                        if (result) {
                            doDelete();
                        }
                    });

        } else {
            AlertEvent.fireWarn(
                    this,
                    "No index shards have been selected for deletion!",
                    null);
        }
    }

    private void doFlush() {
        delayedUpdate.reset();
        nodeManager.listEnabledNodes(nodeNames -> nodeNames.forEach(nodeName -> {
            final Rest<Long> rest = restFactory.create();
            rest
                    .onSuccess(result -> delayedUpdate.update())
                    .call(INDEX_RESOURCE)
                    .flushIndexShards(nodeName, selectionCriteria);
        }), throwable -> {
        });

        AlertEvent.fireInfo(IndexShardPresenter.this,
                "Selected index shards will be flushed. Please be patient as this may take some time.",
                null);
    }

    private void doDelete() {
        delayedUpdate.reset();
        nodeManager.listEnabledNodes(nodeNames -> nodeNames.forEach(nodeName -> {
            final Rest<Long> rest = restFactory.create();
            rest
                    .onSuccess(result -> delayedUpdate.update())
                    .call(INDEX_RESOURCE)
                    .deleteIndexShards(nodeName, selectionCriteria);
        }), throwable -> {
        });

        AlertEvent.fireInfo(IndexShardPresenter.this,
                "Selected index shards will be deleted. Please be patient as this may take some time.",
                null);
    }
}
