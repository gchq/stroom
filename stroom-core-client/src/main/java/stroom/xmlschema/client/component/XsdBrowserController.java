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

import stroom.widget.component.client.Controller;
import stroom.xmlschema.client.xsdbrowser.XsdModel;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// A schema's browser ([XsdBrowser]): moves around the schema's model, and shows the node selected
/// in it.
public class XsdBrowserController extends Controller {

    private final XsdBrowser view;
    private XsdModel model;

    /// Made by GIN, with its browser.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The browser.
    @Inject
    public XsdBrowserController(final EventBus eventBus, final XsdBrowser view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnHome(() -> withModel(XsdModel::home));
        view.setOnBack(() -> withModel(XsdModel::back));
        view.setOnForward(() -> withModel(XsdModel::forward));
    }

    /// Shows a schema, following the node selected in it.
    ///
    /// @param model The schema.
    public void setModel(final XsdModel model) {
        this.model = model;
        model.setOnSelect(view::setSelectedNode);
        view.setModel(model);
    }

    // Home, Back and Forward do nothing until there is a schema to move around
    private void withModel(final Consumer<XsdModel> action) {
        if (model != null) {
            action.accept(model);
        }
    }
}
