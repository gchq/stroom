/*
 * Copyright 2020 Crown Copyright
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

package stroom.index.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexResource;
import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneIndexDoc;
import stroom.node.client.NodeClient;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.tooltip.client.component.InfoTooltipController;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/// An index's shards ([IndexShardList]), a page at a time; those who may manage index shards can
/// flush or delete the shards chosen on every enabled node, unless the index is read only
/// ([Contexts#READ_ONLY]) or, for delete, they may not delete it. A shard's info is shown in a
/// tooltip.
public class IndexShardListController extends Controller {

    private static final IndexResource INDEX_RESOURCE = GWT.create(IndexResource.class);

    private final IndexShardList view;
    private final InfoTooltipController infoTooltip;
    private final RestFactory restFactory;
    private final NodeClient nodeClient;
    private final ClientSecurityContext securityContext;
    private final DateTimeFormatter dateTimeFormatter;
    private final FindIndexShardCriteria selectionCriteria = FindIndexShardCriteria.matchAll();
    private final FindIndexShardCriteria queryCriteria = FindIndexShardCriteria.matchAll();
    private final DelayedUpdate delayedUpdate;
    private RestDataProvider<IndexShard, ResultPage<IndexShard>> dataProvider;
    private LuceneIndexDoc index;
    private boolean readOnly;
    private boolean allowDelete;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param infoTooltip       Shows a shard's info.
    /// @param restFactory       Finds, flushes and deletes the shards.
    /// @param nodeClient        Lists the nodes to flush or delete the shards on.
    /// @param securityContext   Tells whether the user may manage shards, or delete the index.
    /// @param dateTimeFormatter Formats the times shown.
    @Inject
    public IndexShardListController(final EventBus eventBus,
                                    final IndexShardList view,
                                    final InfoTooltipController infoTooltip,
                                    final RestFactory restFactory,
                                    final NodeClient nodeClient,
                                    final ClientSecurityContext securityContext,
                                    final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        this.infoTooltip = own(infoTooltip);
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;
        this.securityContext = securityContext;
        this.dateTimeFormatter = dateTimeFormatter;
        initWidget(view);

        view.setShardActionsVisible(
                securityContext.hasAppPermission(AppPermission.MANAGE_INDEX_SHARDS_PERMISSION));
        view.addColumns(dateTimeFormatter::format);
        view.setSelection(selectionCriteria.getIndexShardIdSet());
        view.setOnSelectionChange(this::enableButtons);
        view.setOnFlush(this::flush);
        view.setOnDelete(this::delete);
        view.setOnShowInfo(this::showInfo);
        view.setOnSortChange(this::refresh);

        delayedUpdate = new DelayedUpdate(this::refresh);
        onDispose(delayedUpdate::reset);

        watch(Contexts.READ_ONLY, readOnly -> {
            this.readOnly = readOnly;
            enableButtons();
        });
    }

    private void enableButtons() {
        final boolean enabled = !readOnly && !selectionCriteria.getIndexShardIdSet().isMatchNothing();
        view.setFlushEnabled(enabled, readOnly
                ? "index is read only"
                : null);
        final String deleteReason;
        if (readOnly) {
            deleteReason = "index is read only";
        } else if (!allowDelete) {
            deleteReason = "no delete permission";
        } else {
            deleteReason = null;
        }
        view.setDeleteEnabled(allowDelete && enabled, deleteReason);
    }

    private void showInfo(final IndexShard indexShard,
                          final PopupPosition popupPosition,
                          final Element anchor) {
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
                .row("Document Count", IndexShardList.intToString(indexShard.getDocumentCount()))
                .row("File Size", indexShard.getFileSizeString())
                .row("Bytes Per Document", IndexShardList.intToString(indexShard.getBytesPerDocument()))
                .row("Commit", dateTimeFormatter.format(indexShard.getCommitMs()))
                .row("Commit Duration",
                        ModelStringUtil.formatDurationString(indexShard.getCommitDurationMs()))
                .row("Commit Document Count", IndexShardList.intToString(indexShard.getCommitDocumentCount()))
                .row("Index Version", indexShard.getIndexVersion());

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));

        infoTooltip.show(htmlBuilder.toSafeHtml(), popupPosition, anchor);
    }

    /// Finds the shards again; the first time, starts the list.
    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<IndexShard, ResultPage<IndexShard>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<IndexShard>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    CriteriaUtil.setRange(queryCriteria, range);
                    CriteriaUtil.setSortList(queryCriteria, view.getSortList());
                    restFactory
                            .create(INDEX_RESOURCE)
                            .method(res -> res.find(queryCriteria))
                            .onSuccess(dataConsumer)
                            .onFailure(errorHandler)
                            .owner(IndexShardListController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }

                @Override
                protected void changeData(final ResultPage<IndexShard> data) {
                    super.changeData(data);
                    onChangeData(data);
                }
            };
            view.setDataProvider(dataProvider);

        } else {
            dataProvider.refresh();
        }
    }

    /// Shows an index's shards, with none chosen.
    ///
    /// @param docRef   The index.
    /// @param document Its document.
    public void read(final DocRef docRef, final LuceneIndexDoc document) {
        view.setIndexName(docRef.getName());
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
                    view.getTaskMonitor());
        }
    }

    private void onChangeData(final ResultPage<IndexShard> data) {
        if (!selectionCriteria.getIndexShardIdSet().isMatchAll()) {
            final Set<Long> ids = new HashSet<>();
            for (final IndexShard shard : data.getValues()) {
                ids.add(shard.getId());
            }
            if (selectionCriteria.getIndexShardIdSet().getSet().retainAll(ids)) {
                enableButtons();
            }
        }
    }

    private void flush() {
        if (selectionCriteria.getIndexShardIdSet().isMatchAll()) {
            ConfirmEvent.fire(this, "Are you sure you want to flush all index shards?", result -> {
                if (result) {
                    ConfirmEvent.fire(IndexShardListController.this,
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
                            ConfirmEvent.fire(IndexShardListController.this,
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
        nodeClient.listEnabledNodes(nodeNames -> nodeNames.forEach(nodeName -> {
            restFactory
                    .create(INDEX_RESOURCE)
                    .method(res -> res.flushIndexShards(nodeName, selectionCriteria))
                    .onSuccess(result -> delayedUpdate.update())
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        }), throwable -> {
        }, view.getTaskMonitor());

        AlertEvent.fireInfo(this,
                "Selected index shards will be flushed. Please be patient as this may take some time.",
                null);
    }

    private void doDelete() {
        delayedUpdate.reset();
        nodeClient.listEnabledNodes(nodeNames -> nodeNames.forEach(nodeName -> {
            restFactory
                    .create(INDEX_RESOURCE)
                    .method(res -> res.deleteIndexShards(nodeName, selectionCriteria))
                    .onSuccess(result -> delayedUpdate.update())
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        }), throwable -> {
        }, view.getTaskMonitor());

        AlertEvent.fireInfo(this,
                "Selected index shards will be deleted. Please be patient as this may take some time.",
                null);
    }
}
