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

package stroom.data.client.presenter;

import stroom.core.client.LocationManager;
import stroom.data.client.component.RefCellsController;
import stroom.data.client.component.SelectionSummaryController;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.component.ExplorerPopupController;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class SteppingMetaListPresenter extends AbstractMetaListPresenter {

    @Inject
    public SteppingMetaListPresenter(final EventBus eventBus,
                                     final PagerPanel view,
                                     final RestFactory restFactory,
                                     final LocationManager locationManager,
                                     final DateTimeFormatter dateTimeFormatter,
                                     final Provider<SelectionSummaryController> selectionSummaryControllerProvider,
                                     final Provider<ProcessChoicePresenter> processChoicePresenterProvider,
                                     final Provider<ExplorerPopupController> pipelineSelection,
                                     final ExpressionValidator expressionValidator,
                                     final RefCellsController refCells) {
        super(eventBus,
                view,
                restFactory,
                locationManager,
                dateTimeFormatter,
                selectionSummaryControllerProvider,
                processChoicePresenterProvider,
                pipelineSelection,
                expressionValidator,
                true,
                refCells);
    }

    @Override
    protected void addColumns(final boolean allowSelectAll) {
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
                "Warn",
                MetaFields.REC_WARN,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
                40);
        addRightAlignedAttributeColumn(
                "Info",
                MetaFields.REC_INFO,
                v -> ModelStringUtil.formatCsv(Long.valueOf(v)),
                40);
        addAttributeColumn(
                "Retention",
                DataRetentionFields.RETENTION_AGE_FIELD,
                ColumnSizeConstants.SMALL_COL);
    }

    public ResultPage<MetaRow> getResultPage() {
        return super.getResultPage();
    }

    public MultiSelectionModel<MetaRow> getSelectionModel() {
        return super.getSelectionModel();
    }

    public MetaRow getSelected() {
        return super.getSelected();
    }
}
