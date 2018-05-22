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

package stroom.xmlschema.client.presenter;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.docref.DocRef;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.tickbox.client.view.TickBox;
import stroom.xmlschema.client.presenter.XMLSchemaSettingsPresenter.XMLSchemaSettingsView;
import stroom.xmlschema.shared.XmlSchemaDoc;

public class XMLSchemaSettingsPresenter
        extends DocumentSettingsPresenter<XMLSchemaSettingsView, XmlSchemaDoc> {
    @Inject
    public XMLSchemaSettingsPresenter(final EventBus eventBus, final XMLSchemaSettingsView view,
                                      final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);

        // Add listeners for dirty events.
        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        };

        registerHandler(view.getDescription().addKeyDownHandler(keyDownHander));
        registerHandler(view.getNamespaceURI().addKeyDownHandler(keyDownHander));
        registerHandler(view.getSystemId().addKeyDownHandler(keyDownHander));
        registerHandler(view.getSchemaGroup().addKeyDownHandler(keyDownHander));
        registerHandler(view.getDeprecated().addValueChangeHandler(event -> setDirty(true)));
    }

    @Override
    public String getType() {
        return XmlSchemaDoc.DOCUMENT_TYPE;
    }

    @Override
    public void onRead(final DocRef docRef, final XmlSchemaDoc xmlSchema) {
        getView().getDescription().setText(xmlSchema.getDescription());
        getView().getNamespaceURI().setText(xmlSchema.getNamespaceURI());
        getView().getSystemId().setText(xmlSchema.getSystemId());
        getView().getSchemaGroup().setText(xmlSchema.getSchemaGroup());
        getView().getDeprecated().setBooleanValue(xmlSchema.isDeprecated());
    }

    @Override
    public void onWrite(final XmlSchemaDoc xmlSchema) {
        xmlSchema.setDescription(getView().getDescription().getText().trim());
        xmlSchema.setNamespaceURI(getView().getNamespaceURI().getText().trim());
        xmlSchema.setSystemId(getView().getSystemId().getText());
        xmlSchema.setSchemaGroup(getView().getSchemaGroup().getText());
        xmlSchema.setDeprecated(getView().getDeprecated().getBooleanValue());
    }

    public interface XMLSchemaSettingsView extends View {
        TextArea getDescription();

        TextBox getNamespaceURI();

        TextBox getSystemId();

        TextBox getSchemaGroup();

        TickBox getDeprecated();
    }
}
