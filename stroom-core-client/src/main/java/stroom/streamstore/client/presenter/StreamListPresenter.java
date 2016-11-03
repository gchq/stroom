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

import stroom.security.client.ClientSecurityContext;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
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
        addEffectiveColumn();
        addFeedColumn();
        addStreamTypeColumn();

        addAttributeColumn(StreamAttributeConstants.STREAM_SIZE, ColumnSizeConstants.MEDIUM_COL);
        addAttributeColumn(StreamAttributeConstants.FILE_SIZE, ColumnSizeConstants.MEDIUM_COL);
        addAttributeColumn(StreamAttributeConstants.REC_READ, ColumnSizeConstants.MEDIUM_COL);
        addAttributeColumn(StreamAttributeConstants.REC_WRITE, ColumnSizeConstants.MEDIUM_COL);
        addAttributeColumn(StreamAttributeConstants.REC_INFO, ColumnSizeConstants.MEDIUM_COL);
        addAttributeColumn(StreamAttributeConstants.REC_WARN, ColumnSizeConstants.MEDIUM_COL);
        addAttributeColumn(StreamAttributeConstants.REC_ERROR, ColumnSizeConstants.MEDIUM_COL);
        addAttributeColumn(StreamAttributeConstants.REC_FATAL, ColumnSizeConstants.MEDIUM_COL);

        getView().addEndColumn(new EndColumn<StreamAttributeMap>());
    }
}
