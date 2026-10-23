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

package stroom.node.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.job.shared.JobNode;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.presenter.client.View;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class NodePresenter
        extends ContentTabPresenter<NodePresenter.NodeView>
        implements Refreshable {

    public static final String TAB_TYPE = "Nodes";

    private final NodeStatusListPresenter nodeStatusListPresenter;
    private final NodeJobListPresenter nodeJobListPresenter;

    @Inject
    public NodePresenter(final EventBus eventBus,
                         final NodeView view,
                         final NodeStatusListPresenter nodeStatusListPresenter,
                         final NodeJobListPresenter nodeJobListPresenter) {
        super(eventBus, view);
        own(nodeStatusListPresenter);
        own(nodeJobListPresenter);
        this.nodeStatusListPresenter = nodeStatusListPresenter;
        this.nodeJobListPresenter = nodeJobListPresenter;

        getView().setNodeListView(nodeStatusListPresenter.getView());
        getView().setNodeJobListView(nodeJobListPresenter.getView());

        own(nodeStatusListPresenter.getSelectionModel().addSelectionHandler(event -> {
            final NodeStatusResult row = nodeStatusListPresenter.getSelectionModel().getSelected();
            final String nodeName = NullSafe.get(row, NodeStatusResult::getNode, Node::getName);
            nodeJobListPresenter.read(nodeName);
        }));
    }

    @Override
    public void refresh() {
        nodeStatusListPresenter.refresh();
        nodeJobListPresenter.refresh();
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

    public void setSelected(final String nodeName) {
        nodeStatusListPresenter.setSelected(nodeName);
        nodeJobListPresenter.read(nodeName);
    }

    public void setSelected(final JobNode jobNode) {
        final String nodeName = NullSafe.get(jobNode, JobNode::getNodeName);
        nodeJobListPresenter.read(nodeName);
        nodeStatusListPresenter.setSelected(nodeName);
        nodeJobListPresenter.setSelected(jobNode);
    }


    // --------------------------------------------------------------------------------


    public interface NodeView extends View {

        /// Shows the list of nodes.
        ///
        /// @param view Its view.
        void setNodeListView(View view);

        /// Shows the selected node's jobs.
        ///
        /// @param view Its view.
        void setNodeJobListView(View view);
    }
}
