/*
 * Copyright 2026 Crown Copyright
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

package stroom.node.client.component;

import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.node.client.NodeGroupClient;
import stroom.node.shared.FindNodeGroupRequest;
import stroom.node.shared.NodeGroup;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The node groups ([NodeGroupList]), a page at a time in the order chosen; a group is enabled or
/// disabled with its tick box. Its owner acts on New, Edit and Delete.
public class NodeGroupListController extends Controller {

    private final NodeGroupList view;
    private final NodeGroupClient nodeGroupClient;
    private final RestDataProvider<NodeGroup, ResultPage<NodeGroup>> dataProvider;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus        The application's event bus.
    /// @param view            The content.
    /// @param nodeGroupClient Fetches and updates the groups.
    @Inject
    public NodeGroupListController(final EventBus eventBus,
                                   final NodeGroupList view,
                                   final NodeGroupClient nodeGroupClient) {
        super(eventBus);
        this.view = view;
        this.nodeGroupClient = nodeGroupClient;
        initWidget(view);

        final FindNodeGroupRequest request = new FindNodeGroupRequest(
                PageRequest.createDefault(),
                FindNodeGroupRequest.DEFAULT_SORT_LIST,
                null);
        dataProvider = new RestDataProvider<NodeGroup, ResultPage<NodeGroup>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<NodeGroup>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(request, range);
                CriteriaUtil.setSortList(request, view.getSortList());
                nodeGroupClient.find(request, dataConsumer, errorHandler, view.getTaskMonitor());
            }
        };
        view.setDataProvider(dataProvider);
        view.setOnSortChange(this::refresh);
        view.setOnEnabledChange((nodeGroup, enabled) -> nodeGroupClient.update(
                nodeGroup.copy().enabled(enabled).build(),
                result -> refresh(),
                new DefaultErrorHandler(this, null),
                view.getTaskMonitor()));
    }

    /// @return The selection.
    public MultiSelectionModel<NodeGroup> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// Fetches the groups again.
    public void refresh() {
        dataProvider.refresh();
    }

    /// @param onNew Run when New is pressed.
    public void setOnNew(final Runnable onNew) {
        view.setOnNew(onNew);
    }

    /// @param onEdit Run when Edit is pressed, or a row is double clicked.
    public void setOnEdit(final Runnable onEdit) {
        view.setOnEdit(onEdit);
    }

    /// @param onDelete Run when Delete is pressed.
    public void setOnDelete(final Runnable onDelete) {
        view.setOnDelete(onDelete);
    }
}
