/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.item.client.SelectionBox;
import stroom.pipeline.client.presenter.TextConverterSettingsPresenter.TextConverterSettingsView;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.View;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class TextConverterSettingsPresenter
        extends DocPresenter<TextConverterSettingsView, TextConverterDoc> {

    @Inject
    public TextConverterSettingsPresenter(final EventBus eventBus, final TextConverterSettingsView view) {
        super(eventBus, view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, getView()::onReadOnly);

        view.getConverterType().addItem(TextConverterType.NONE);
        view.getConverterType().addItem(TextConverterType.DATA_SPLITTER);
        view.getConverterType().addItem(TextConverterType.XML_FRAGMENT);

        own(
                getView().getConverterType().addValueChangeHandler(event -> onChange()));
    }

    @Override
    protected void onRead(final DocRef docRef, final TextConverterDoc textConverter) {
        getView().getConverterType().setValue(textConverter.getConverterType());
    }

    @Override
    protected TextConverterDoc onWrite(final TextConverterDoc textConverter) {
        final TextConverterType converterType = getView().getConverterType().getValue();
        return textConverter.copy().converterType(converterType).build();
    }

    public interface TextConverterSettingsView extends View {

        /// Shows whether the user may change what this shows.
        ///
        /// @param readOnly Whether it may not be changed.
        void onReadOnly(boolean readOnly);

        SelectionBox<TextConverterDoc.TextConverterType> getConverterType();
    }
}
