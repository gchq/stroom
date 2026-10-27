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

package stroom.data.client.component;

import stroom.core.client.LocationManager;
import stroom.data.client.component.MetaList.Columns;
import stroom.data.client.presenter.ExpressionValidator;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.component.ExplorerPopupController;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.Expander;
import stroom.util.shared.ResultPage;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The streams related to the one selected in the data browser ([MetaList], with the tree of streams
/// each came from): it, the streams it came from and those made from it.
public class MetaRelationListController extends MetaListController {

    private final Map<Long, MetaRow> streamMap = new HashMap<>();
    private final Set<Long> hasChildren = new HashSet<>();
    private int maxDepth = -1;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                           The application's event bus.
    /// @param view                               The content.
    /// @param restFactory                        Finds and changes the streams.
    /// @param locationManager                    Opens a download.
    /// @param dateTimeFormatter                  Formats the times shown.
    /// @param selectionSummaryControllerProvider Makes the summary of the streams chosen.
    /// @param processChoiceDialogProvider        Makes the dialog that chooses how to process them.
    /// @param pipelineSelection                  Makes the popup that picks a pipeline.
    /// @param expressionValidator                Checks an expression with the server.
    /// @param refCells                           Lets the feed and pipeline cells open them.
    @Inject
    public MetaRelationListController(final EventBus eventBus,
                                      final MetaList view,
                                      final RestFactory restFactory,
                                      final LocationManager locationManager,
                                      final DateTimeFormatter dateTimeFormatter,
                                      final Provider<SelectionSummaryController> selectionSummaryControllerProvider,
                                      final Provider<ProcessChoiceDialogController> processChoiceDialogProvider,
                                      final Provider<ExplorerPopupController> pipelineSelection,
                                      final ExpressionValidator expressionValidator,
                                      final RefCellsController refCells) {
        super(eventBus,
                view,
                restFactory,
                locationManager,
                dateTimeFormatter,
                selectionSummaryControllerProvider,
                processChoiceDialogProvider,
                pipelineSelection,
                expressionValidator,
                refCells,
                Columns.LINEAGE);
        setTableName("Related Streams");
    }

    /// Shows the streams related to one.
    ///
    /// @param metaRow         The stream, or `null` for none.
    /// @param fireEvents      Not used.
    /// @param showSystemFiles Whether to show locked and deleted streams too.
    public void setSelectedStream(final MetaRow metaRow,
                                  final boolean fireEvents,
                                  final boolean showSystemFiles) {
        if (metaRow == null) {
            getCriteria().setExpression(null);
            getCriteria().setSort(MetaFields.CREATE_TIME.getFldName(), false, false);
            refresh();

        } else {
            final ExpressionOperator.Builder builder = ExpressionOperator.builder();
            if (!showSystemFiles) {
                builder.addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue());
            }
            builder.addIdTerm(MetaFields.ID, Condition.EQUALS, metaRow.getMeta().getId());

            getCriteria().setExpression(builder.build());
            getCriteria().setSort(MetaFields.CREATE_TIME.getFldName(), false, false);
            getCriteria().setFetchRelationships(true);
            refresh();
        }

        getSelectionModel().setSelected(metaRow);
    }

    @Override
    protected ResultPage<MetaRow> onProcessData(final ResultPage<MetaRow> data) {
        // Store streams against id.
        streamMap.clear();
        for (final MetaRow row : data.getValues()) {
            final Meta meta = row.getMeta();
            streamMap.put(meta.getId(), row);
        }

        // Now use the root streams and attach child streams to them.
        maxDepth = -1;
        final List<MetaRow> newData = new ArrayList<>();
        addChildren(null, data, newData, 0);

        // Set the width of the expander column so that all expanders can be seen.
        getView().setExpanderDepth(maxDepth);

        return super.onProcessData(new ResultPage<>(newData, data.getPageResponse()));
    }

    private void addChildren(final MetaRow parent, final ResultPage<MetaRow> data,
                             final List<MetaRow> newData, final int depth) {
        for (final MetaRow row : data.getValues()) {
            final Meta meta = row.getMeta();

            if (parent == null) {
                // Add roots.
                if (meta.getParentMetaId() == null || streamMap.get(meta.getParentMetaId()) == null) {
                    newData.add(row);
                    addChildren(row, data, newData, depth + 1);
                }
            } else {
                // Add children.
                if (meta.getParentMetaId() != null) {
                    final MetaRow thisParent = streamMap.get(meta.getParentMetaId());
                    if (thisParent != null && thisParent.equals(parent)) {
                        hasChildren.add(meta.getParentMetaId());
                        newData.add(row);
                        addChildren(row, data, newData, depth + 1);
                        maxDepth = Math.max(maxDepth, depth);
                    }
                }
            }
        }
    }

    @Override
    protected Expander buildExpander(final MetaRow row) {
        return new Expander(getDepth(row), true, !hasChildren.contains(row.getMeta().getId()));
    }

    private int getDepth(final MetaRow row) {
        int depth = 0;
        Long parentId = row.getMeta().getParentMetaId();
        while (parentId != null) {
            depth++;

            final MetaRow parentRow = streamMap.get(parentId);
            if (parentRow == null) {
                parentId = null;
            } else {
                parentId = parentRow.getMeta().getParentMetaId();
            }
        }

        return depth;
    }
}
