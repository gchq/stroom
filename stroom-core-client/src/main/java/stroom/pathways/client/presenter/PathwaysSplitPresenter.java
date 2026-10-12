/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.pathways.client.presenter.PathwaysSplitPresenter.PathwaysSplitView;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.pathway.PathNode;
import stroom.util.shared.NullSafe;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.tab.client.view.LinkTabBar;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.View;

public class PathwaysSplitPresenter extends DocPresenter<PathwaysSplitView, PathwaysDoc> {

    private final PathwayListPresenter pathwayListPresenter;
    private final PathwayTreePresenter pathwayTreePresenter;
    private final NodeInfoPresenter nodeInfoPresenter;
    // One tab rather than none, so the panel reads the same as the pathway tab's and has somewhere to
    // put a second thing about a node if one is ever wanted.
    private final TabData infoTab = new TabDataImpl("Info");

    @Inject
    public PathwaysSplitPresenter(final EventBus eventBus,
                                  final PathwaysSplitView view,
                                  final PathwayListPresenter pathwayListPresenter,
                                  final PathwayTreePresenter pathwayTreePresenter,
                                  final NodeInfoPresenter nodeInfoPresenter) {
        super(eventBus, view);
        this.pathwayListPresenter = pathwayListPresenter;
        this.pathwayTreePresenter = pathwayTreePresenter;
        this.nodeInfoPresenter = nodeInfoPresenter;
        view.setTable(pathwayListPresenter.getView());
        view.setTree(pathwayTreePresenter.getView());
        view.getNodeTabBar().addTab(infoTab);
        view.getNodeTabBar().selectTab(infoTab);
        view.getNodeLayerContainer().show(nodeInfoPresenter);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(pathwayListPresenter.getSelectionModel().addSelectionHandler(e ->
                pathwayListPresenter.withSelectedPathway(pathway ->
                        pathwayTreePresenter.read(pathway))));
        // Which node is picked out on the drawing is what the panel beside it is about.
        registerHandler(pathwayTreePresenter.getSelectionModel()
                .addSelectionChangeHandler(e -> showNode()));
    }

    private void showNode() {
        final PathNode node = pathwayTreePresenter.getSelectionModel().getSelectedObject();
        // The drawing may have been scrolled away from since the node was clicked, so the path says
        // both what it is and where it sits.
        getView().setNodePath(path(node));
        nodeInfoPresenter.setData(node,
                pathwayTreePresenter.getChange(NullSafe.get(node, PathNode::getNodePath)));
    }

    // Where a node sits in the model, root first, written the way the changes list writes it.
    private static String path(final PathNode node) {
        return node == null
                ? ""
                : String.join(" / ", NullSafe.list(node.getNodePath()));
    }

    @Override
    protected void onRead(final DocRef docRef, final PathwaysDoc document, final boolean readOnly) {
        // Nothing here holds the changes behind a model, so the tree is told where to ask for them.
        // It only does so for the graph, which is the one drawing that needs them.
        pathwayTreePresenter.setDocRef(docRef);
        pathwayListPresenter.onRead(docRef, document, readOnly);
    }

    @Override
    protected PathwaysDoc onWrite(final PathwaysDoc document) {
        return pathwayListPresenter.onWrite(document);
    }

    public interface PathwaysSplitView extends View {

        void setTable(View view);

        void setTree(View view);

        /** Which node the panel beside the drawing is about, or nothing where none is picked out. */
        void setNodePath(String path);

        LinkTabBar getNodeTabBar();

        LayerContainer getNodeLayerContainer();
    }
}
