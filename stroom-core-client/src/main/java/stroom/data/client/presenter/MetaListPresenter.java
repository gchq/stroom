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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.MetaFields;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;

import java.util.function.Function;

public class MetaListPresenter extends AbstractMetaListPresenter {
    @Inject
    public MetaListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                             final TooltipPresenter tooltipPresenter) {
        super(eventBus, dispatcher, tooltipPresenter, true);
    }

    @Override
    protected void addColumns(final boolean allowSelectAll) {
        addSelectedColumn(allowSelectAll);

        addInfoColumn();

        addCreatedColumn();
        addStreamTypeColumn();
        addFeedColumn();
        addPipelineColumn();

        addAttributeColumn("Raw", MetaFields.RAW_SIZE, v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Disk", MetaFields.FILE_SIZE, v -> ModelStringUtil.formatIECByteSizeString(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Read", MetaFields.REC_READ, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Write", MetaFields.REC_WRITE, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Fatal", MetaFields.REC_FATAL, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addAttributeColumn("Error", MetaFields.REC_ERROR, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addAttributeColumn("Warn", MetaFields.REC_WARN, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addAttributeColumn("Info", MetaFields.REC_INFO, v -> ModelStringUtil.formatCsv(Long.valueOf(v)), 40);
        addAttributeColumn("Retention", DataRetentionFields.RETENTION_AGE_FIELD, Function.identity(), ColumnSizeConstants.SMALL_COL);

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
