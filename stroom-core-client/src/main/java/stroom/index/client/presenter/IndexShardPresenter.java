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
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexResource;
import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneIndexDoc;
import stroom.node.client.NodeManager;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.TableBuilder;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class IndexShardPresenter
        extends DocumentEditPresenter<PagerView, LuceneIndexDoc>
        implements Refreshable {

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
    private LuceneIndexDoc index;
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

        dataGrid = new MyDataGrid<>(this);
        view.setDataWidget(dataGrid);

        this.tooltipPresenter = tooltipPresenter;
        this.restFactory = restFactory;
        this.nodeManager = nodeManager;
        this.securityContext = securityContext;
        this.dateTimeFormatter = dateTimeFormatter;

        if (securityContext.hasAppPermission(AppPermission.MANAGE_INDEX_SHARDS_PERMISSION)) {
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
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
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
            protected void showInfo(final IndexShard indexShard, final PopupPosition popupPosition) {
                final TableBuilder tb = new TableBuilder();

                if (index != null) {
                    tb.row("Index UUID", String.valueOf(index.getUuid()));
                }
                tb
                        .row("Shard Id", String.valueOf(indexShard.getId()))
                        .row("Node", indexShard.getNodeName())
                        .row("Partition", indexShard.getPartition());

                if (indexShard.getPartitionFromTime() != null) {
                    tb.row("Partition From", dateTimeFormatter.format(indexShard.getPartitionFromTime()));
                }
                if (indexShard.getPartitionToTime() != null) {
                    tb.row("Partition To", dateTimeFormatter.format(indexShard.getPartitionToTime()));
                }

                tb
                        .row("Path", indexShard.getVolume().getPath())
                        .row("Status", indexShard.getStatus().getDisplayValue())
                        .row("Document Count", intToString(indexShard.getDocumentCount()))
                        .row("File Size", indexShard.getFileSizeString())
                        .row("Bytes Per Document", intToString(indexShard.getBytesPerDocument()))
                        .row("Commit", dateTimeFormatter.format(indexShard.getCommitMs()))
                        .row("Commit Duration",
                                ModelStringUtil.formatDurationString(indexShard.getCommitDurationMs()))
                        .row("Commit Document Count", intToString(indexShard.getCommitDocumentCount()))
                        .row("Index Version", indexShard.getIndexVersion());

                final HtmlBuilder htmlBuilder = new HtmlBuilder();
                htmlBuilder.div(tb::write, Attribute.className("infoTable"));

                tooltipPresenter.show(htmlBuilder.toSafeHtml(), popupPosition);
            }
        };
        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    private void addNodeColumn() {
        dataGrid.addResizableColumn(new OrderByColumn<IndexShard, String>(
                new TextCell(), FindIndexShardCriteria.FIELD_NODE, true) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getNodeName();
            }
        }, FindIndexShardCriteria.FIELD_NODE, 100);
    }

    private void addPartitionColumn() {
        final OrderByColumn<IndexShard, String> col = new OrderByColumn<IndexShard, String>(
                new TextCell(), FindIndexShardCriteria.FIELD_PARTITION, true) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getPartition();
            }
        };
        dataGrid.addResizableColumn(col, FindIndexShardCriteria.FIELD_PARTITION, 100);

        // Sort by partition ascending by default.
        dataGrid.sort(col);
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
        dataGrid.addResizableColumn(new OrderByColumn<IndexShard, String>(
                new TextCell(), FindIndexShardCriteria.FIELD_STATUS, true) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getStatus().getDisplayValue();
            }
        }, FindIndexShardCriteria.FIELD_STATUS, 100);
    }

    private void addDocCountColumn() {
        dataGrid.addResizableColumn(new OrderByColumn<IndexShard, String>(
                new TextCell(), FindIndexShardCriteria.FIELD_DOC_COUNT, true) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return intToString(indexShard.getDocumentCount());
            }
        }, FindIndexShardCriteria.FIELD_DOC_COUNT, 100);
    }

    private void addFileSizeColumn() {
        dataGrid.addResizableColumn(new OrderByColumn<IndexShard, String>(
                new TextCell(), FindIndexShardCriteria.FIELD_FILE_SIZE, true) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return indexShard.getFileSizeString();
            }
        }, FindIndexShardCriteria.FIELD_FILE_SIZE, 100);
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
        dataGrid.addResizableColumn(new OrderByColumn<IndexShard, String>(
                new TextCell(), FindIndexShardCriteria.FIELD_LAST_COMMIT, true) {
            @Override
            public String getValue(final IndexShard indexShard) {
                return dateTimeFormatter.format(indexShard.getCommitMs());
            }
        }, FindIndexShardCriteria.FIELD_LAST_COMMIT, 170);
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
        final Set<Long> rtn = new HashSet<>();
        if (resultList != null) {
            for (final IndexShard e : resultList.getValues()) {
                rtn.add(e.getId());
            }
        }
        return rtn;

    }

    @Override
    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<IndexShard, ResultPage<IndexShard>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<IndexShard>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    CriteriaUtil.setRange(queryCriteria, range);
                    CriteriaUtil.setSortList(queryCriteria, dataGrid.getColumnSortList());
                    restFactory
                            .create(INDEX_RESOURCE)
                            .method(res -> res.find(queryCriteria))
                            .onSuccess(dataConsumer)
                            .onFailure(errorHandler)
                            .taskMonitorFactory(getView())
                            .exec();
                }

                @Override
                protected void changeData(final ResultPage<IndexShard> data) {
                    super.changeData(data);
                    onChangeData(data);
                }
            };
            dataProvider.addDataDisplay(dataGrid);

        } else {
            dataProvider.refresh();
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final LuceneIndexDoc document, final boolean readOnly) {
        this.readOnly = readOnly;
        enableButtons();

        if (document != null) {
            this.index = document;
            queryCriteria.getIndexUuidSet().add(docRef.getUuid());
            selectionCriteria.getIndexUuidSet().add(docRef.getUuid());
            selectionCriteria.getIndexShardIdSet().clear();

            refresh();

            securityContext.hasDocumentPermission(
                    docRef,
                    DocumentPermission.DELETE,
                    result -> {
                        this.allowDelete = result;
                        enableButtons();
                    },
                    throwable -> AlertEvent.fireErrorFromException(this, throwable, null),
                    this);
        }
    }

    @Override
    protected LuceneIndexDoc onWrite(final LuceneIndexDoc entity) {
        return entity;
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
            restFactory
                    .create(INDEX_RESOURCE)
                    .method(res -> res.flushIndexShards(nodeName, selectionCriteria))
                    .onSuccess(result -> delayedUpdate.update())
                    .taskMonitorFactory(getView())
                    .exec();
        }), throwable -> {
        }, getView());

        AlertEvent.fireInfo(IndexShardPresenter.this,
                "Selected index shards will be flushed. Please be patient as this may take some time.",
                null);
    }

    private void doDelete() {
        delayedUpdate.reset();
        nodeManager.listEnabledNodes(nodeNames -> nodeNames.forEach(nodeName -> {
            restFactory
                    .create(INDEX_RESOURCE)
                    .method(res -> res.deleteIndexShards(nodeName, selectionCriteria))
                    .onSuccess(result -> delayedUpdate.update())
                    .taskMonitorFactory(getView())
                    .exec();
        }), throwable -> {
        }, getView());

        AlertEvent.fireInfo(IndexShardPresenter.this,
                "Selected index shards will be deleted. Please be patient as this may take some time.",
                null);
    }
}
