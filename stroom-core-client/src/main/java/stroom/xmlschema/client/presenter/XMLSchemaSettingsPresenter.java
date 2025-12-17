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

package stroom.xmlschema.client.presenter;

import stroom.core.client.event.DirtyKeyDownHander;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.xmlschema.client.presenter.XMLSchemaSettingsPresenter.XMLSchemaSettingsView;
import stroom.xmlschema.shared.XmlSchemaDoc;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class XMLSchemaSettingsPresenter extends DocumentEditPresenter<XMLSchemaSettingsView, XmlSchemaDoc> {

    @Inject
    public XMLSchemaSettingsPresenter(final EventBus eventBus, final XMLSchemaSettingsView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        super.onBind();
        // Add listeners for dirty events.
        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        };
        registerHandler(getView().getNamespaceURI().addKeyDownHandler(keyDownHander));
        registerHandler(getView().getSystemId().addKeyDownHandler(keyDownHander));
        registerHandler(getView().getSchemaGroup().addKeyDownHandler(keyDownHander));
        registerHandler(getView().getDeprecated().addValueChangeHandler(event -> setDirty(true)));
    }

    @Override
    public void onRead(final DocRef docRef, final XmlSchemaDoc xmlSchema, final boolean readOnly) {
        getView().getNamespaceURI().setText(xmlSchema.getNamespaceURI());
        getView().getSystemId().setText(xmlSchema.getSystemId());
        getView().getSchemaGroup().setText(xmlSchema.getSchemaGroup());
        getView().getDeprecated().setValue(xmlSchema.isDeprecated());
    }

    @Override
    public XmlSchemaDoc onWrite(final XmlSchemaDoc xmlSchema) {
        xmlSchema.setNamespaceURI(getView().getNamespaceURI().getText().trim());
        xmlSchema.setSystemId(getView().getSystemId().getText());
        xmlSchema.setSchemaGroup(getView().getSchemaGroup().getText());
        xmlSchema.setDeprecated(getView().getDeprecated().getValue());
        return xmlSchema;
    }

    public interface XMLSchemaSettingsView extends View {

        TextBox getNamespaceURI();

        TextBox getSystemId();

        TextBox getSchemaGroup();

        CustomCheckBox getDeprecated();
    }
}
