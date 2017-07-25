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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;

public class StreamListPresenter extends AbstractStreamListPresenter {
    @Inject
    public StreamListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                               final TooltipPresenter tooltipPresenter, final ClientSecurityContext securityContext) {
        super(eventBus, dispatcher, tooltipPresenter, securityContext, true);
    }

    @Override
    protected void addColumns(final boolean allowSelectAll) {
        addSelectedColumn(allowSelectAll);

        addInfoColumn();

        addCreatedColumn();
        addStreamTypeColumn();
        addFeedColumn();
        addPipelineColumn();

        addAttributeColumn("Raw", StreamAttributeConstants.STREAM_SIZE, ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Disk", StreamAttributeConstants.FILE_SIZE, ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Read", StreamAttributeConstants.REC_READ, ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Write", StreamAttributeConstants.REC_WRITE, ColumnSizeConstants.SMALL_COL);
        addAttributeColumn("Fatal", StreamAttributeConstants.REC_FATAL, 40);
        addAttributeColumn("Error", StreamAttributeConstants.REC_ERROR, 40);
        addAttributeColumn("Warn", StreamAttributeConstants.REC_WARN, 40);
        addAttributeColumn("Info", StreamAttributeConstants.REC_INFO, 40);
        addAttributeColumn("Retention", StreamAttributeConstants.RETENTION_AGE, ColumnSizeConstants.SMALL_COL);

        getView().addEndColumn(new EndColumn<StreamAttributeMap>());
    }
}
