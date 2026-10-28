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

package stroom.xmlschema.client.component;

import stroom.core.client.event.DirtyKeyDownHander;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.xmlschema.shared.XmlSchemaDoc;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// An XML schema's Settings tab: its namespace, system id and group, and whether it is deprecated.
public class XMLSchemaSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox namespaceURI;
    @UiField
    TextBox systemId;
    @UiField
    TextBox schemaGroup;
    @UiField
    TickBox deprecated;

    private Runnable onChange = () -> {
    };

    /// Makes an empty tab.
    public XMLSchemaSettings() {
        initWidget(BINDER.createAndBindUi(this));
        final DirtyKeyDownHander keyDownHandler = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                onChange.run();
            }
        };
        namespaceURI.addKeyDownHandler(keyDownHandler);
        systemId.addKeyDownHandler(keyDownHandler);
        schemaGroup.addKeyDownHandler(keyDownHandler);
        deprecated.addValueChangeHandler(event -> onChange.run());
    }

    /// Shows a schema's settings.
    ///
    /// @param xmlSchema The schema.
    public void read(final XmlSchemaDoc xmlSchema) {
        namespaceURI.setText(xmlSchema.getNamespaceURI());
        systemId.setText(xmlSchema.getSystemId());
        schemaGroup.setText(xmlSchema.getSchemaGroup());
        deprecated.setValue(xmlSchema.isDeprecated());
    }

    /// Writes the settings shown into a copy of a schema.
    ///
    /// @param xmlSchema The schema.
    /// @return A copy of it with the settings shown.
    public XmlSchemaDoc write(final XmlSchemaDoc xmlSchema) {
        return xmlSchema.copy()
                .namespaceURI(namespaceURI.getText().trim())
                .systemId(systemId.getText())
                .schemaGroup(schemaGroup.getText())
                .deprecated(deprecated.getValue())
                .build();
    }

    /// @param onChange What to run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        namespaceURI.setReadOnly(readOnly);
        systemId.setReadOnly(readOnly);
        schemaGroup.setReadOnly(readOnly);
        deprecated.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, XMLSchemaSettings> {

    }
}
