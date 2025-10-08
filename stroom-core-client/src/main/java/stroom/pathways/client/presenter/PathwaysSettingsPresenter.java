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

package stroom.pathways.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.pathways.client.presenter.PathwaysSettingsPresenter.PathwaysSettingsView;
import stroom.pathways.shared.PathwaysDoc;
import stroom.planb.shared.PlanBDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.time.SimpleDuration;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class PathwaysSettingsPresenter extends DocumentEditPresenter<PathwaysSettingsView, PathwaysDoc>
        implements PathwaysSettingsUiHandlers {

    private final DocSelectionBoxPresenter traceStorePresenter;
    private final DocSelectionBoxPresenter feedPresenter;

    @Inject
    public PathwaysSettingsPresenter(final EventBus eventBus,
                                     final PathwaysSettingsView view,
                                     final DocSelectionBoxPresenter traceStorePresenter,
                                     final DocSelectionBoxPresenter feedPresenter) {
        super(eventBus, view);
        this.traceStorePresenter = traceStorePresenter;
        this.feedPresenter = feedPresenter;
        view.setUiHandlers(this);

        traceStorePresenter.setIncludedTypes(PlanBDoc.TYPE);
        traceStorePresenter.setRequiredPermissions(DocumentPermission.VIEW);
        view.setTraceStoreView(traceStorePresenter.getView());

        feedPresenter.setIncludedTypes(FeedDoc.TYPE);
        feedPresenter.setRequiredPermissions(DocumentPermission.VIEW);
        view.setInfoFeedView(feedPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(traceStorePresenter.addDataSelectionHandler(e -> setDirty(true)));
        registerHandler(feedPresenter.addDataSelectionHandler(e -> setDirty(true)));
    }

    @Override
    public void onChange() {
        setDirty(true);
    }

    @Override
    protected void onRead(final DocRef docRef, final PathwaysDoc doc, final boolean readOnly) {
        getView().setTemporalOrderingTolerance(doc.getTemporalOrderingTolerance());
        getView().setAllowPathwayCreation(doc.isAllowPathwayCreation());
        getView().setAllowPathwayMutation(doc.isAllowPathwayMutation());
        getView().setAllowConstraintCreation(doc.isAllowConstraintCreation());
        getView().setAllowConstraintMutation(doc.isAllowConstraintMutation());
        traceStorePresenter.setSelectedEntityReference(doc.getTracesDocRef(), true);
        feedPresenter.setSelectedEntityReference(doc.getInfoFeed(), true);
        getView().setProcessingNode(doc.getProcessingNode());
    }

    @Override
    protected PathwaysDoc onWrite(final PathwaysDoc doc) {
        doc.setTemporalOrderingTolerance(getView().getTemporalOrderingTolerance());
        doc.setAllowPathwayCreation(getView().isAllowPathwayCreation());
        doc.setAllowPathwayMutation(getView().isAllowPathwayMutation());
        doc.setAllowConstraintCreation(getView().isAllowConstraintCreation());
        doc.setAllowConstraintMutation(getView().isAllowConstraintMutation());
        doc.setTracesDocRef(traceStorePresenter.getSelectedEntityReference());
        doc.setInfoFeed(feedPresenter.getSelectedEntityReference());
        doc.setProcessingNode(getView().getProcessingNode());
        return doc;
    }

    public interface PathwaysSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<PathwaysSettingsUiHandlers> {

        void setTraceStoreView(View view);

        void setInfoFeedView(View view);

        SimpleDuration getTemporalOrderingTolerance();

        void setTemporalOrderingTolerance(SimpleDuration temporalOrderingTolerance);

        boolean isAllowPathwayCreation();

        void setAllowPathwayCreation(boolean allowPathwayCreation);

        boolean isAllowPathwayMutation();

        void setAllowPathwayMutation(boolean allowPathwayMutation);

        boolean isAllowConstraintCreation();

        void setAllowConstraintCreation(boolean allowConstraintCreation);

        boolean isAllowConstraintMutation();

        void setAllowConstraintMutation(boolean allowConstraintMutation);

        String getProcessingNode();

        void setProcessingNode(String processingNode);
    }
}
