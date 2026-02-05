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

package stroom.datagen.client.presenter;

import stroom.datagen.client.presenter.DataGenSettingsPresenter.DataGenSettingsView;
import stroom.datagen.shared.DataGenDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;

import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class DataGenSettingsPresenter extends DocumentEditPresenter<DataGenSettingsView, DataGenDoc> {

    @Inject
    public DataGenSettingsPresenter(final EventBus eventBus, final DataGenSettingsView view) {
        super(eventBus, view);
    }

    @Override
    protected void onRead(final DocRef docRef, final DataGenDoc doc, final boolean readOnly) {
        getView().getTemplate().setText(doc.getTemplate());
    }

    @Override
    protected DataGenDoc onWrite(final DataGenDoc doc) {
        return doc.copy().template(getView().getTemplate().getText()).build();
    }

    public interface DataGenSettingsView extends View {

        TextBox getTemplate();
    }
}
