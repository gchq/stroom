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

import stroom.core.client.LocationManager;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.MetaFields;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.ModelStringUtil;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class MetaListPresenter extends AbstractMetaListPresenter {

    @Inject
    public MetaListPresenter(final EventBus eventBus,
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
                true);
    }

    @Override
    protected void addColumns(final boolean allowSelectAll) {
        addSelectedColumn(allowSelectAll);

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

        addEndColumn();
    }
}
