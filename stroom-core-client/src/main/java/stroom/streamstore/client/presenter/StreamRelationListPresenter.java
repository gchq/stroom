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

package stroom.streamstore.client.presenter;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.cell.expander.client.ExpanderCell;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.entity.shared.Sort.Direction;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.data.meta.api.FindStreamCriteria;
import stroom.data.meta.api.Stream;
import stroom.data.meta.api.StreamStatus;
import stroom.data.meta.api.StreamDataRow;
import stroom.data.meta.api.StreamDataSource;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreamRelationListPresenter extends AbstractStreamListPresenter {
    private final Map<Long, StreamDataRow> streamMap = new HashMap<>();
    private int maxDepth = -1;

    private Column<StreamDataRow, Expander> expanderColumn;

    @Inject
    public StreamRelationListPresenter(final EventBus eventBus,
                                       final ClientDispatchAsync dispatcher,
                                       final TooltipPresenter tooltipPresenter) {
        super(eventBus, dispatcher, tooltipPresenter, false);
        dataProvider.setAllowNoConstraint(false);
    }

    public void setSelectedStream(final StreamDataRow streamAttributeMap, final boolean fireEvents,
                                  final boolean showSystemFiles) {
        if (streamAttributeMap == null) {
            setCriteria(null);

        } else {
            final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);
            if (!showSystemFiles) {
                builder.addTerm(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue());
            }
            builder.addTerm(StreamDataSource.STREAM_ID, Condition.EQUALS, String.valueOf(streamAttributeMap.getStream().getId()));

            final FindStreamCriteria criteria = new FindStreamCriteria();
            criteria.setExpression(builder.build());
            criteria.setSort(StreamDataSource.CREATE_TIME, Direction.ASCENDING, false);

            setCriteria(criteria);
        }

        getSelectionModel().setSelected(streamAttributeMap);
    }

    @Override
    protected ResultList<StreamDataRow> onProcessData(final ResultList<StreamDataRow> data) {
        // Store streams against id.
        streamMap.clear();
        for (final StreamDataRow row : data) {
            final Stream stream = row.getStream();
            streamMap.put(stream.getId(), row);
        }

        for (final StreamDataRow row : data) {
            final Stream stream = row.getStream();
            streamMap.put(stream.getId(), row);
        }

        // Now use the root streams and attach child streams to them.
        maxDepth = -1;
        final List<StreamDataRow> newData = new ArrayList<>();
        addChildren(null, data, newData, 0);

        // Set the width of the expander column so that all expanders
        // can be seen.
        if (maxDepth >= 0) {
            getView().setColumnWidth(expanderColumn, 16 + (maxDepth * 10), Unit.PX);
        } else {
            getView().setColumnWidth(expanderColumn, 0, Unit.PX);
        }

        final ResultList<StreamDataRow> processed = new BaseResultList<>(newData,
                (long) data.getStart(), (long) data.getSize(), data.isExact());
        return super.onProcessData(processed);
    }

    private void addChildren(final StreamDataRow parent, final List<StreamDataRow> data,
                             final List<StreamDataRow> newData, final int depth) {
        for (final StreamDataRow row : data) {
            final Stream stream = row.getStream();

            if (parent == null) {
                // Add roots.
                if (stream.getParentStreamId() == null || streamMap.get(stream.getParentStreamId()) == null) {
                    newData.add(row);
                    addChildren(row, data, newData, depth + 1);

                    if (maxDepth < depth) {
                        maxDepth = depth;
                    }
                }
            } else {
                // Add children.
                if (stream.getParentStreamId() != null) {
                    final StreamDataRow thisParent = streamMap.get(stream.getParentStreamId());
                    if (thisParent != null && thisParent.equals(parent)) {
                        newData.add(row);
                        addChildren(row, data, newData, depth + 1);

                        if (maxDepth < depth) {
                            maxDepth = depth;
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void addColumns(final boolean allowSelectAll) {
        addSelectedColumn(allowSelectAll);

        expanderColumn = new Column<StreamDataRow, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final StreamDataRow row) {
                return buildExpander(row);
            }
        };
        getView().addColumn(expanderColumn, "<br/>", 0);

        addInfoColumn();

        addCreatedColumn();
        addStreamTypeColumn();
        addFeedColumn();
        addPipelineColumn();

        addAttributeColumn("Raw", StreamDataSource.STREAM_SIZE, v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Disk", StreamDataSource.FILE_SIZE, v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Read", StreamDataSource.REC_READ, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Write", StreamDataSource.REC_WRITE, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Fatal", StreamDataSource.REC_FATAL, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addAttributeColumn("Error", StreamDataSource.REC_ERROR, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addAttributeColumn("Warn", StreamDataSource.REC_WARN, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addAttributeColumn("Info", StreamDataSource.REC_INFO, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);

        // TODO : @66 Add data retention column back into the table.
//        addAttributeColumn("Retention", StreamAttributeConstants.RETENTION_AGE, ColumnSizeConstants.SMALL_COL);

        getView().addEndColumn(new EndColumn<>());
    }

    private Expander buildExpander(final StreamDataRow row) {
        return new Expander(getDepth(row), true, true);
    }

    private int getDepth(final StreamDataRow row) {
        int depth = 0;
        Long parentId = row.getStream().getParentStreamId();
        while (parentId != null) {
            depth++;

            final StreamDataRow parentRow = streamMap.get(parentId);
            if (parentRow == null) {
                parentId = null;
            } else {
                parentId = parentRow.getStream().getParentStreamId();
            }
        }

        return depth;
    }
}
