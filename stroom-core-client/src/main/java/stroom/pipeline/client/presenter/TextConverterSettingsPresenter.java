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

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.entity.client.presenter.EntitySettingsPresenter;
import stroom.item.client.ItemListBox;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.security.client.ClientSecurityContext;

public class TextConverterSettingsPresenter
        extends EntitySettingsPresenter<TextConverterSettingsPresenter.TextConverterSettingsView, TextConverter> {
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
                view.getConverterType().addSelectionHandler(new SelectionHandler<TextConverter.TextConverterType>() {
                    @Override
                    public void onSelection(final SelectionEvent<TextConverterType> event) {
                        setDirty(true);
                    }
                }));
    }

    @Override
    public String getType() {
        return TextConverter.ENTITY_TYPE;
    }

    @Override
    protected void onRead(final TextConverter textConverter) {
        getView().getDescription().setText(textConverter.getDescription());
        getView().getConverterType().setSelectedItem(textConverter.getConverterType());
    }

    @Override
    protected void onWrite(final TextConverter textConverter) {
        final TextConverterType converterType = getView().getConverterType().getSelectedItem();
        textConverter.setDescription(getView().getDescription().getText().trim());
        textConverter.setConverterType(converterType);
    }

    public interface TextConverterSettingsView extends View {
        TextArea getDescription();

        ItemListBox<TextConverter.TextConverterType> getConverterType();
    }
}
