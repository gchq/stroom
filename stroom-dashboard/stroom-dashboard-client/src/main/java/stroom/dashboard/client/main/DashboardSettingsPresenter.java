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

package stroom.dashboard.client.main;

import stroom.dashboard.client.main.DashboardSettingsPresenter.DashboardSettingsView;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentSettingsPresenter;

import com.google.gwt.event.dom.client.InputEvent;
import com.google.gwt.event.dom.client.InputHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class DashboardSettingsPresenter extends DocumentSettingsPresenter<DashboardSettingsView, DashboardDoc> {

    @Inject
    public DashboardSettingsPresenter(final EventBus eventBus,
                                      final DashboardSettingsView view) {
        super(eventBus, view);

        // Add listeners for dirty events.
        final InputHandler inputHandler = event -> setDirty(true);
        registerHandler(view.getDescription().addDomHandler(inputHandler, InputEvent.getType()));
    }

    @Override
    protected void onRead(final DocRef docRef, final DashboardDoc doc) {
        getView().getDescription().setText(doc.getDescription());
    }

    @Override
    protected void onWrite(final DashboardDoc doc) {
        doc.setDescription(getView().getDescription().getText().trim());
    }

    @Override
    public String getType() {
        return DashboardDoc.DOCUMENT_TYPE;
    }

    public interface DashboardSettingsView extends View {

        TextArea getDescription();
    }
}
