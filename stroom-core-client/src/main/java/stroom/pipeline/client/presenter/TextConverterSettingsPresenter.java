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

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.item.client.ItemListBox;
import stroom.pipeline.client.presenter.TextConverterSettingsPresenter.TextConverterSettingsView;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class TextConverterSettingsPresenter
        extends DocumentEditPresenter<TextConverterSettingsView, TextConverterDoc> {

    @Inject
    public TextConverterSettingsPresenter(final EventBus eventBus, final TextConverterSettingsView view) {
        super(eventBus, view);

        view.getConverterType().addItem(TextConverterType.NONE);
        view.getConverterType().addItem(TextConverterType.DATA_SPLITTER);
        view.getConverterType().addItem(TextConverterType.XML_FRAGMENT);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(
                getView().getConverterType().addSelectionHandler(event -> setDirty(true)));
    }

    @Override
    protected void onRead(final DocRef docRef, final TextConverterDoc textConverter, final boolean readOnly) {
        getView().getConverterType().setSelectedItem(textConverter.getConverterType());
    }

    @Override
    protected TextConverterDoc onWrite(final TextConverterDoc textConverter) {
        final TextConverterType converterType = getView().getConverterType().getSelectedItem();
        textConverter.setConverterType(converterType);
        return textConverter;
    }

    public interface TextConverterSettingsView extends View {

        ItemListBox<TextConverterDoc.TextConverterType> getConverterType();
    }
}
