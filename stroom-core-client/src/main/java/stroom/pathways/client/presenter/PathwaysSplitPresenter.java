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

package stroom.pathways.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.pathways.client.presenter.PathwaysSplitPresenter.PathwaysSplitView;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.pathway.Pathway;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class PathwaysSplitPresenter extends DocumentEditPresenter<PathwaysSplitView, PathwaysDoc> {

    private final PathwayListPresenter pathwayListPresenter;
    private final PathwayTreePresenter pathwayTreePresenter;

    @Inject
    public PathwaysSplitPresenter(final EventBus eventBus,
                                  final PathwaysSplitView view,
                                  final PathwayListPresenter pathwayListPresenter,
                                  final PathwayTreePresenter pathwayTreePresenter) {
        super(eventBus, view);
        this.pathwayListPresenter = pathwayListPresenter;
        this.pathwayTreePresenter = pathwayTreePresenter;
        view.setTable(pathwayListPresenter.getView());
        view.setTree(pathwayTreePresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(pathwayListPresenter.getSelectionModel().addSelectionHandler(e -> {
            final Pathway selected = pathwayListPresenter.getSelectionModel().getSelected();
            pathwayTreePresenter.read(getEntity(), selected, isReadOnly());
        }));
    }

    @Override
    protected void onRead(final DocRef docRef, final PathwaysDoc document, final boolean readOnly) {
        pathwayListPresenter.onRead(docRef, document, readOnly);
    }

    @Override
    protected PathwaysDoc onWrite(final PathwaysDoc document) {
        return pathwayListPresenter.onWrite(document);
    }

    public interface PathwaysSplitView extends View {

        void setTable(View view);

        void setTree(View view);
    }
}
