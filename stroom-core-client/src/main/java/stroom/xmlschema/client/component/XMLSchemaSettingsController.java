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

import stroom.docref.DocRef;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.xmlschema.shared.XmlSchemaDoc;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// An XML schema's Settings tab ([XMLSchemaSettings]), read only in a document the user can't
/// change.
public class XMLSchemaSettingsController extends Controller {

    private final XMLSchemaSettings view;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The tab.
    @Inject
    public XMLSchemaSettingsController(final EventBus eventBus, final XMLSchemaSettings view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnChange(this::markChanged);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    /// Shows a schema's settings.
    ///
    /// @param docRef    The schema.
    /// @param xmlSchema Its settings.
    public void read(final DocRef docRef, final XmlSchemaDoc xmlSchema) {
        view.read(xmlSchema);
    }

    /// Writes the settings shown into a copy of a schema.
    ///
    /// @param xmlSchema The schema.
    /// @return A copy of it with the settings shown.
    public XmlSchemaDoc write(final XmlSchemaDoc xmlSchema) {
        return view.write(xmlSchema);
    }
}
