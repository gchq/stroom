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
import stroom.pathways.client.component.PathwayListController;
import stroom.pathways.client.component.PathwayTreeController;
import stroom.pathways.client.presenter.PathwaysSplitPresenter.PathwaysSplitView;
import stroom.pathways.shared.PathwaysDoc;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class PathwaysSplitPresenter extends DocPresenter<PathwaysSplitView, PathwaysDoc> {

    private final PathwayListController pathwayList;
    private final PathwayTreeController pathwayTree;

    @Inject
    public PathwaysSplitPresenter(final EventBus eventBus,
                                  final PathwaysSplitView view,
                                  final PathwayListController pathwayList,
                                  final PathwayTreeController pathwayTree) {
        super(eventBus, view);
        own(pathwayList);
        own(pathwayTree);
        this.pathwayList = pathwayList;
        this.pathwayTree = pathwayTree;
        view.setTable(pathwayList);
        view.setTree(pathwayTree);

        pathwayList.setOnSelectionChange(selected -> pathwayTree.read(getEntity(), selected));
    }

    @Override
    protected void onRead(final DocRef docRef, final PathwaysDoc document) {
        pathwayList.read(docRef, document);
    }

    @Override
    protected PathwaysDoc onWrite(final PathwaysDoc document) {
        return document;
    }

    public interface PathwaysSplitView extends View {

        void setTable(IsWidget view);

        void setTree(IsWidget view);
    }
}
