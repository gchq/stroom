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
import stroom.data.meta.api.FindDataCriteria;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataStatus;
import stroom.data.meta.api.DataRow;
import stroom.data.meta.api.MetaDataSource;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreamRelationListPresenter extends AbstractStreamListPresenter {
    private final Map<Long, DataRow> streamMap = new HashMap<>();
    private int maxDepth = -1;

    private Column<DataRow, Expander> expanderColumn;

    @Inject
    public StreamRelationListPresenter(final EventBus eventBus,
                                       final ClientDispatchAsync dispatcher,
                                       final TooltipPresenter tooltipPresenter) {
        super(eventBus, dispatcher, tooltipPresenter, false);
        dataProvider.setAllowNoConstraint(false);
    }

    public void setSelectedStream(final DataRow streamAttributeMap, final boolean fireEvents,
                                  final boolean showSystemFiles) {
        if (streamAttributeMap == null) {
            setCriteria(null);

        } else {
            final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);
            if (!showSystemFiles) {
                builder.addTerm(MetaDataSource.STATUS, Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue());
            }
            builder.addTerm(MetaDataSource.STREAM_ID, Condition.EQUALS, String.valueOf(streamAttributeMap.getData().getId()));

            final FindDataCriteria criteria = new FindDataCriteria();
            criteria.setExpression(builder.build());
            criteria.setSort(MetaDataSource.CREATE_TIME, Direction.ASCENDING, false);

            setCriteria(criteria);
        }

        getSelectionModel().setSelected(streamAttributeMap);
    }

    @Override
    protected ResultList<DataRow> onProcessData(final ResultList<DataRow> data) {
        // Store streams against id.
        streamMap.clear();
        for (final DataRow row : data) {
            final Data stream = row.getData();
            streamMap.put(stream.getId(), row);
        }

        for (final DataRow row : data) {
            final Data stream = row.getData();
            streamMap.put(stream.getId(), row);
        }

        // Now use the root streams and attach child streams to them.
        maxDepth = -1;
        final List<DataRow> newData = new ArrayList<>();
        addChildren(null, data, newData, 0);

        // Set the width of the expander column so that all expanders
        // can be seen.
        if (maxDepth >= 0) {
            getView().setColumnWidth(expanderColumn, 16 + (maxDepth * 10), Unit.PX);
        } else {
            getView().setColumnWidth(expanderColumn, 0, Unit.PX);
        }

        final ResultList<DataRow> processed = new BaseResultList<>(newData,
                (long) data.getStart(), (long) data.getSize(), data.isExact());
        return super.onProcessData(processed);
    }

    private void addChildren(final DataRow parent, final List<DataRow> data,
                             final List<DataRow> newData, final int depth) {
        for (final DataRow row : data) {
            final Data stream = row.getData();

            if (parent == null) {
                // Add roots.
                if (stream.getParentDataId() == null || streamMap.get(stream.getParentDataId()) == null) {
                    newData.add(row);
                    addChildren(row, data, newData, depth + 1);

                    if (maxDepth < depth) {
                        maxDepth = depth;
                    }
                }
            } else {
                // Add children.
                if (stream.getParentDataId() != null) {
                    final DataRow thisParent = streamMap.get(stream.getParentDataId());
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

        expanderColumn = new Column<DataRow, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final DataRow row) {
                return buildExpander(row);
            }
        };
        getView().addColumn(expanderColumn, "<br/>", 0);

        addInfoColumn();

        addCreatedColumn();
        addStreamTypeColumn();
        addFeedColumn();
        addPipelineColumn();

        addAttributeColumn("Raw", MetaDataSource.STREAM_SIZE, v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Disk", MetaDataSource.FILE_SIZE, v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Read", MetaDataSource.REC_READ, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Write", MetaDataSource.REC_WRITE, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Fatal", MetaDataSource.REC_FATAL, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addAttributeColumn("Error", MetaDataSource.REC_ERROR, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addAttributeColumn("Warn", MetaDataSource.REC_WARN, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addAttributeColumn("Info", MetaDataSource.REC_INFO, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);

        // TODO : @66 Add data retention column back into the table.
//        addAttributeColumn("Retention", StreamAttributeConstants.RETENTION_AGE, ColumnSizeConstants.SMALL_COL);

        getView().addEndColumn(new EndColumn<>());
    }

    private Expander buildExpander(final DataRow row) {
        return new Expander(getDepth(row), true, true);
    }

    private int getDepth(final DataRow row) {
        int depth = 0;
        Long parentId = row.getData().getParentDataId();
        while (parentId != null) {
            depth++;

            final DataRow parentRow = streamMap.get(parentId);
            if (parentRow == null) {
                parentId = null;
            } else {
                parentId = parentRow.getData().getParentDataId();
            }
        }

        return depth;
    }
}
