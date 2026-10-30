/*
 * Copyright 2024 Crown Copyright
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

import stroom.content.client.component.ContentTabController;
import stroom.data.table.client.HasRefresh;
import stroom.job.shared.JobNode;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows the nodes, and the selected node's jobs.
public class NodesController extends ContentTabController
        implements HasRefresh {

    public static final String TAB_TYPE = "Nodes";

    private final NodeStatusListController nodeStatusList;
    private final NodeJobListController nodeJobList;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus       The application's event bus.
    /// @param view           Its component.
    /// @param nodeStatusList The node status list.
    /// @param nodeJobList    The node job list.
    @Inject
    public NodesController(final EventBus eventBus,
                           final Nodes view,
                           final NodeStatusListController nodeStatusList,
                           final NodeJobListController nodeJobList) {
        super(eventBus);
        initWidget(view);
        this.nodeStatusList = own(nodeStatusList).attach(view.getNodeList());
        this.nodeJobList = own(nodeJobList).attach(view.getNodeJobList());

        own(nodeStatusList.getSelectionModel().addSelectionHandler(event -> {
            final NodeStatusResult row = nodeStatusList.getSelectionModel().getSelected();
            final String nodeName = NullSafe.get(row, NodeStatusResult::getNode, Node::getName);
            nodeJobList.read(nodeName);
        }));
    }

    @Override
    public void refresh() {
        nodeStatusList.refresh();
        nodeJobList.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.NODES;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Nodes";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    /// Selects a node and its job.
    ///
    /// @param jobNode The job on its node.
    public void setSelected(final JobNode jobNode) {
        final String nodeName = NullSafe.get(jobNode, JobNode::getNodeName);
        nodeJobList.read(nodeName);
        nodeStatusList.setSelected(nodeName);
        nodeJobList.setSelected(jobNode);
    }
}
