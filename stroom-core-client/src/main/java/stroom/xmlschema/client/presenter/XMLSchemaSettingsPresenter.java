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

package stroom.xmlschema.client.presenter;

import stroom.core.client.event.DirtyKeyDownHander;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.View;
import stroom.widget.tickbox.client.view.TickBox;
import stroom.xmlschema.client.presenter.XMLSchemaSettingsPresenter.XMLSchemaSettingsView;
import stroom.xmlschema.shared.XmlSchemaDoc;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class XMLSchemaSettingsPresenter extends DocPresenter<XMLSchemaSettingsView, XmlSchemaDoc> {

    @Inject
    public XMLSchemaSettingsPresenter(final EventBus eventBus, final XMLSchemaSettingsView view) {
        super(eventBus, view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, getView()::onReadOnly);

        // Add listeners for dirty events.
        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                onChange();
            }
        };
        own(getView().getNamespaceURI().addKeyDownHandler(keyDownHander));
        own(getView().getSystemId().addKeyDownHandler(keyDownHander));
        own(getView().getSchemaGroup().addKeyDownHandler(keyDownHander));
        own(getView().getDeprecated().addValueChangeHandler(event -> onChange()));
    }

    @Override
    public void onRead(final DocRef docRef, final XmlSchemaDoc xmlSchema) {
        getView().getNamespaceURI().setText(xmlSchema.getNamespaceURI());
        getView().getSystemId().setText(xmlSchema.getSystemId());
        getView().getSchemaGroup().setText(xmlSchema.getSchemaGroup());
        getView().getDeprecated().setValue(xmlSchema.isDeprecated());
    }

    @Override
    public XmlSchemaDoc onWrite(final XmlSchemaDoc xmlSchema) {
        return xmlSchema.copy()
                .namespaceURI(getView().getNamespaceURI().getText().trim())
                .systemId(getView().getSystemId().getText())
                .schemaGroup(getView().getSchemaGroup().getText())
                .deprecated(getView().getDeprecated().getValue())
                .build();
    }

    public interface XMLSchemaSettingsView extends View {

        /// Shows whether the user may change what this shows.
        ///
        /// @param readOnly Whether it may not be changed.
        void onReadOnly(boolean readOnly);

        TextBox getNamespaceURI();

        TextBox getSystemId();

        TextBox getSchemaGroup();

        TickBox getDeprecated();
    }
}
