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

package stroom.pipeline.client.presenter;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.item.client.ItemListBox;
import stroom.pipeline.client.presenter.TextConverterSettingsPresenter.TextConverterSettingsView;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.query.api.v2.DocRef;
import stroom.security.client.ClientSecurityContext;

public class TextConverterSettingsPresenter
        extends DocumentSettingsPresenter<TextConverterSettingsView, TextConverterDoc> {
    @Inject
    public TextConverterSettingsPresenter(final EventBus eventBus, final TextConverterSettingsView view,
                                          final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);

        view.getConverterType().addItem(TextConverterType.NONE);
        view.getConverterType().addItem(TextConverterType.DATA_SPLITTER);
        view.getConverterType().addItem(TextConverterType.XML_FRAGMENT);

        // Add listeners for dirty events.
        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        };

        registerHandler(view.getDescription().addKeyDownHandler(keyDownHander));

        registerHandler(
                view.getConverterType().addSelectionHandler(event -> setDirty(true)));
    }

    @Override
    public String getType() {
        return TextConverterDoc.DOCUMENT_TYPE;
    }

    @Override
    protected void onRead(final DocRef docRef, final TextConverterDoc textConverter) {
        getView().getDescription().setText(textConverter.getDescription());
        getView().getConverterType().setSelectedItem(textConverter.getConverterType());
    }

    @Override
    protected void onWrite(final TextConverterDoc textConverter) {
        final TextConverterType converterType = getView().getConverterType().getSelectedItem();
        textConverter.setDescription(getView().getDescription().getText().trim());
        textConverter.setConverterType(converterType);
    }

    public interface TextConverterSettingsView extends View {
        TextArea getDescription();

        ItemListBox<TextConverterDoc.TextConverterType> getConverterType();
    }
}
