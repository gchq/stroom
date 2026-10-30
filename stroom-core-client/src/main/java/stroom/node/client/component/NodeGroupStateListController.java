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

import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.node.client.NodeClient;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.NodeStatusResult;
import stroom.widget.component.client.Controller;
import stroom.widget.datagrid.client.CriteriaUtil;

import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// The nodes ([NodeGroupStateList]), fetched in the order chosen, each ticked to include it in the
/// node group being edited.
public class NodeGroupStateListController extends Controller {

    private final RestDataProvider<NodeStatusResult, FetchNodeStatusResponse> dataProvider;
    private final FindNodeStatusCriteria findNodeStatusCriteria = new FindNodeStatusCriteria();
    private NodeGroupStateList view;

    /// Made by GIN; [#attach(NodeGroupStateList)] gives it its list.
    ///
    /// @param eventBus   The application's event bus.
    /// @param nodeClient Fetches the nodes.
    @Inject
    public NodeGroupStateListController(final EventBus eventBus,
                                        final NodeClient nodeClient) {
        super(eventBus);
        dataProvider = new RestDataProvider<NodeStatusResult, FetchNodeStatusResponse>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<FetchNodeStatusResponse> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setSortList(findNodeStatusCriteria, view.getSortList());
                nodeClient.fetchNodeStatus(dataConsumer, errorHandler, findNodeStatusCriteria,
                        view.getTaskMonitor());
            }
        };
    }

    /// Attaches this to a list, e.g. one a template declares, which it fills from the server. The list is then this
    /// controller's widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public NodeGroupStateListController attach(final NodeGroupStateList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.setDataProvider(dataProvider);
        view.setOnSortChange(this::refresh);
        return this;
    }

    /// @param allNodes The ids of every node, for the header's tick box.
    public void setAllNodes(final Set<Integer> allNodes) {
        view.setAllNodes(allNodes);
    }

    /// @param selectedNodes The ids of the nodes ticked, which ticking changes.
    public void setSelectedNodes(final Set<Integer> selectedNodes) {
        view.setSelectedNodes(selectedNodes);
    }

    /// @param invertSelection Whether the group is the nodes not ticked.
    public void setInvertSelection(final boolean invertSelection) {
        view.setInvertSelection(invertSelection);
    }

    /// Fetches the nodes again.
    public void refresh() {
        dataProvider.refresh();
        view.redrawHeaders();
    }
}
