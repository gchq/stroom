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
import stroom.pathways.client.presenter.PathwaysSettingsPresenter.PathwaysSettingsView;
import stroom.pathways.shared.PathwaysDoc;
import stroom.util.shared.time.SimpleDuration;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class PathwaysSettingsPresenter extends DocumentEditPresenter<PathwaysSettingsView, PathwaysDoc>
        implements PathwaysSettingsUiHandlers {

    @Inject
    public PathwaysSettingsPresenter(final EventBus eventBus,
                                     final PathwaysSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    public void onChange() {
        setDirty(true);
    }

    @Override
    protected void onRead(final DocRef docRef, final PathwaysDoc doc, final boolean readOnly) {
        getView().setTemporalOrderingTolerance(doc.getTemporalOrderingTolerance());
    }

    @Override
    protected PathwaysDoc onWrite(final PathwaysDoc doc) {
        doc.setTemporalOrderingTolerance(getView().getTemporalOrderingTolerance());
        return doc;
    }

    public interface PathwaysSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<PathwaysSettingsUiHandlers> {

        SimpleDuration getTemporalOrderingTolerance();

        void setTemporalOrderingTolerance(SimpleDuration temporalOrderingTolerance);
    }
}
