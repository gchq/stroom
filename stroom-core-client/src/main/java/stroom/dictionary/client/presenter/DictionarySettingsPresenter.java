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

package stroom.dictionary.client.presenter;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.pipeline.shared.XSLT;
import stroom.query.api.v2.DocRef;
import stroom.security.client.ClientSecurityContext;

public class DictionarySettingsPresenter extends DocumentSettingsPresenter<DictionarySettingsPresenter.DictionarySettingsView, DictionaryDoc> {
    private final DictionaryListPresenter dictionaryListPresenter;

    @Inject
    public DictionarySettingsPresenter(final EventBus eventBus, final DictionarySettingsView view, final DictionaryListPresenter dictionaryListPresenter, final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);
        this.dictionaryListPresenter = dictionaryListPresenter;
        getView().setImportList(dictionaryListPresenter.getView());

        // Add listeners for dirty events.
        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        };

        registerHandler(view.getDescription().addKeyDownHandler(keyDownHander));
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(dictionaryListPresenter.addDirtyHandler(event -> setDirty(true)));
    }

    @Override
    public String getType() {
        return XSLT.ENTITY_TYPE;
    }

    @Override
    protected void onRead(final DocRef docRef, final DictionaryDoc doc) {
        getView().getDescription().setText(doc.getDescription());
        dictionaryListPresenter.read(docRef, doc);
    }

    @Override
    protected void onWrite(final DictionaryDoc doc) {
        doc.setDescription(getView().getDescription().getText().trim());
        dictionaryListPresenter.write(doc);
    }

    public interface DictionarySettingsView extends View {
        TextArea getDescription();

        void setImportList(View view);
    }
}
