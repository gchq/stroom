/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.state.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.security.shared.DocumentPermissionNames;
import stroom.state.client.presenter.StateStoreSettingsPresenter.StateStoreSettingsView;
import stroom.state.shared.ScyllaDbDoc;
import stroom.state.shared.StateDoc;

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

        clusterPresenter.setIncludedTypes(ScyllaDbDoc.DOCUMENT_TYPE);
        clusterPresenter.setRequiredPermissions(DocumentPermissionNames.USE);

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
    protected void onRead(final DocRef docRef, final StateDoc index, final boolean readOnly) {
        clusterPresenter.setSelectedEntityReference(index.getScyllaDbRef());
    }

    @Override
    protected StateDoc onWrite(final StateDoc index) {
        index.setScyllaDbRef(clusterPresenter.getSelectedEntityReference());
        return index;
    }

    public interface StateStoreSettingsView
            extends View, HasUiHandlers<StateStoreSettingsUiHandlers> {

        void setClusterView(final View view);
    }
}
