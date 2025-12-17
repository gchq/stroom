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

package stroom.state.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.security.shared.DocumentPermission;
import stroom.state.client.presenter.StateStoreSettingsPresenter.StateStoreSettingsView;
import stroom.state.shared.ScyllaDbDoc;
import stroom.state.shared.StateDoc;
import stroom.state.shared.StateType;
import stroom.util.shared.time.TimeUnit;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class StateStoreSettingsPresenter
        extends DocumentEditPresenter<StateStoreSettingsView, StateDoc>
        implements StateStoreSettingsUiHandlers {

    private final DocSelectionBoxPresenter clusterPresenter;

    @Inject
    public StateStoreSettingsPresenter(
            final EventBus eventBus,
            final StateStoreSettingsView view,
            final DocSelectionBoxPresenter clusterPresenter) {
        super(eventBus, view);

        this.clusterPresenter = clusterPresenter;

        clusterPresenter.setIncludedTypes(ScyllaDbDoc.TYPE);
        clusterPresenter.setRequiredPermissions(DocumentPermission.USE);

        view.setUiHandlers(this);
        view.setClusterView(clusterPresenter.getView());
    }

    @Override
    protected void onBind() {
        registerHandler(clusterPresenter.addDataSelectionHandler(event -> setDirty(true)));
    }

    @Override
    public void onChange() {
        setDirty(true);
    }

    @Override
    protected void onRead(final DocRef docRef, final StateDoc doc, final boolean readOnly) {
        getView().onReadOnly(readOnly);

        clusterPresenter.setSelectedEntityReference(doc.getScyllaDbRef(), true);
        getView().setStateType(doc.getStateType());
        getView().setCondense(doc.isCondense());
        getView().setCondenseAge(doc.getCondenseAge());
        getView().setCondenseTimeUnit(doc.getCondenseTimeUnit());
        getView().setRetainForever(doc.isRetainForever());
        getView().setRetainAge(doc.getRetainAge());
        getView().setRetainTimeUnit(doc.getRetainTimeUnit());
    }

    @Override
    protected StateDoc onWrite(final StateDoc doc) {
        doc.setScyllaDbRef(clusterPresenter.getSelectedEntityReference());
        doc.setStateType(getView().getStateType());
        doc.setCondense(getView().isCondense());
        doc.setCondenseAge(getView().getCondenseAge());
        doc.setCondenseTimeUnit(getView().getCondenseTimeUnit());
        doc.setRetainForever(getView().isRetainForever());
        doc.setRetainAge(getView().getRetainAge());
        doc.setRetainTimeUnit(getView().getRetainTimeUnit());
        return doc;
    }


    // --------------------------------------------------------------------------------


    public interface StateStoreSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<StateStoreSettingsUiHandlers> {

        void setClusterView(final View view);

        StateType getStateType();

        void setStateType(StateType stateType);

        boolean isCondense();

        void setCondense(boolean condense);

        int getCondenseAge();

        void setCondenseAge(int age);

        TimeUnit getCondenseTimeUnit();

        void setCondenseTimeUnit(TimeUnit condenseTimeUnit);

        boolean isRetainForever();

        void setRetainForever(boolean retainForever);

        int getRetainAge();

        void setRetainAge(int age);

        TimeUnit getRetainTimeUnit();

        void setRetainTimeUnit(TimeUnit retainTimeUnit);
    }
}
