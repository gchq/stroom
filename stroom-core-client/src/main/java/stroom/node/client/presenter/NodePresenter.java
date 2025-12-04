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

package stroom.node.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.job.shared.JobNode;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class NodePresenter
        extends ContentTabPresenter<NodePresenter.NodeView>
        implements Refreshable {

    public static final String TAB_TYPE = "Nodes";
    public static final String NODE_LIST = "NODE_LIST";
    public static final String NODE_JOB_LIST = "NODE_JOB_LIST";

    private final NodeListPresenter nodeListPresenter;
    private final NodeJobListPresenter nodeJobListPresenter;

    @Inject
    public NodePresenter(final EventBus eventBus,
                         final NodeView view,
                         final NodeListPresenter nodeListPresenter,
                         final NodeJobListPresenter nodeJobListPresenter) {
        super(eventBus, view);
        this.nodeListPresenter = nodeListPresenter;
        this.nodeJobListPresenter = nodeJobListPresenter;

        setInSlot(NODE_LIST, nodeListPresenter);
        setInSlot(NODE_JOB_LIST, nodeJobListPresenter);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(nodeListPresenter.getSelectionModel().addSelectionHandler(event -> {
            final NodeStatusResult row = nodeListPresenter.getSelectionModel().getSelected();
            final String nodeName = NullSafe.get(row, NodeStatusResult::getNode, Node::getName);
            nodeJobListPresenter.read(nodeName);
        }));
    }

    @Override
    public void refresh() {
        nodeListPresenter.refresh();
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
        nodeListPresenter.setSelected(nodeName);
        nodeJobListPresenter.read(nodeName);
    }

    public void setSelected(final JobNode jobNode) {
        final String nodeName = NullSafe.get(jobNode, JobNode::getNodeName);
        nodeJobListPresenter.read(nodeName);
        nodeListPresenter.setSelected(nodeName);
        nodeJobListPresenter.setSelected(jobNode);
    }


    // --------------------------------------------------------------------------------


    public interface NodeView extends View {

    }
}
