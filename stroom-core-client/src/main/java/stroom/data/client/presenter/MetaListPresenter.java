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
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.presenter.EntityChooser;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.MetaFields;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;
import java.util.function.Function;

public class MetaListPresenter extends AbstractMetaListPresenter {
    @Inject
    public MetaListPresenter(final EventBus eventBus,
                             final RestFactory restFactory,
                             final TooltipPresenter tooltipPresenter,
                             final LocationManager locationManager,
                             final Provider<SelectionSummaryPresenter> selectionSummaryPresenterProvider,
                             final Provider<ProcessChoicePresenter> processChoicePresenterProvider,
                             final Provider<EntityChooser> pipelineSelection) {
        super(eventBus,
                restFactory,
                tooltipPresenter,
                locationManager,
                selectionSummaryPresenterProvider,
                processChoicePresenterProvider,
                pipelineSelection,
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
                Function.identity(),
                ColumnSizeConstants.SMALL_COL);

        getView().addEndColumn(new EndColumn<>());
    }


//        try {
//        if (StreamAttributeFieldUse.COUNT_IN_DURATION_FIELD.equals(use)) {
//            valueString = ModelStringUtil.formatCsv(Long.valueOf(valueString));
//
//        } else if (StreamAttributeFieldUse.SIZE_FIELD.equals(use)) {
//            valueString = ModelStringUtil.formatIECByteSizeString(Long.valueOf(valueString));
//
//        } else if (StreamAttributeFieldUse.NUMERIC_FIELD.equals(use)) {
//            valueString = ModelStringUtil.formatCsv(Long.valueOf(valueString));
//
//        } else if (StreamAttributeFieldUse.DURATION_FIELD.equals(use)) {
//            final long valueLong = Long.valueOf(valueString);
//
//            valueString = ModelStringUtil.formatDurationString(valueLong) + " ("
//                    + ModelStringUtil.formatCsv(valueLong) + " ms)";
//        }
//    } catch (final RuntimeException e) {
//    }
//
//        return valueString;
}
