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

package stroom.data.client.presenter;

import stroom.cell.expander.client.ExpanderCell;
import stroom.core.client.LocationManager;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;

public class MetaRelationListPresenter extends AbstractMetaListPresenter {

    private final Map<Long, MetaRow> streamMap = new HashMap<>();
    private final Set<Long> hasChildren = new HashSet<>();
    private int maxDepth = -1;

    private Column<MetaRow, Expander> expanderColumn;

    @Inject
    public MetaRelationListPresenter(final EventBus eventBus,
                                     final PagerView view,
                                     final RestFactory restFactory,
                                     final LocationManager locationManager,
                                     final DateTimeFormatter dateTimeFormatter,
                                     final Provider<SelectionSummaryPresenter> selectionSummaryPresenterProvider,
                                     final Provider<ProcessChoicePresenter> processChoicePresenterProvider,
                                     final Provider<DocSelectionPopup> pipelineSelection,
                                     final ExpressionValidator expressionValidator) {
        super(eventBus,
                view,
                restFactory,
                locationManager,
                dateTimeFormatter,
                selectionSummaryPresenterProvider,
                processChoicePresenterProvider,
                pipelineSelection,
                expressionValidator,
                false
        );
    }

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

        // Set the width of the expander column so that all expanders
        // can be seen.
        dataGrid.setColumnWidth(expanderColumn, ExpanderCell.getColumnWidth(maxDepth), Unit.PX);

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
    protected void addColumns(final boolean allowSelectAll) {
        addSelectedColumn(allowSelectAll);

        expanderColumn = DataGridUtil.expanderColumn(this::buildExpander);
        dataGrid.addColumn(expanderColumn, "<br/>", 0);

        addInfoColumn();

        addCreatedColumn();
        addStreamTypeColumn();
        addFeedColumn();
        addPipelineColumn();

        addRightAlignedAttributeColumn(
                "Raw",
                MetaFields.RAW_SIZE,
                v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)),
                ColumnSizeConstants.SMALL_COL);
        addRightAlignedAttributeColumn(
                "Disk",
                MetaFields.FILE_SIZE,
                v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)),
                ColumnSizeConstants.SMALL_COL);
        addRightAlignedAttributeColumn(
                "Read",
                MetaFields.REC_READ,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
                ColumnSizeConstants.SMALL_COL);
        addRightAlignedAttributeColumn(
                "Write",
                MetaFields.REC_WRITE,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
                ColumnSizeConstants.SMALL_COL);
        addRightAlignedAttributeColumn(
                "Fatal",
                MetaFields.REC_FATAL,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
                40);
        addRightAlignedAttributeColumn(
                "Error",
                MetaFields.REC_ERROR,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
                40);
        addRightAlignedAttributeColumn(
                "Warn", MetaFields.REC_WARN,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
                40);
        addRightAlignedAttributeColumn(
                "Info", MetaFields.REC_INFO,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
                40);
        addAttributeColumn(
                "Retention",
                DataRetentionFields.RETENTION_AGE_FIELD,
                ColumnSizeConstants.SMALL_COL);

        addEndColumn();
    }

    private Expander buildExpander(final MetaRow row) {
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
