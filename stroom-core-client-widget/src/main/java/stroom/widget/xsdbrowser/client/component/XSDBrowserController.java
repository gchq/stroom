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

package stroom.widget.xsdbrowser.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.xsdbrowser.client.XSDModel;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// A schema's browser ([XSDBrowser]): moves around the schema's model, and shows the node selected
/// in it.
public class XSDBrowserController extends Controller {

    private final XSDBrowser view;
    private XSDModel model;

    /// Made by GIN, with its browser.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The browser.
    @Inject
    public XSDBrowserController(final EventBus eventBus, final XSDBrowser view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnHome(() -> withModel(XSDModel::home));
        view.setOnBack(() -> withModel(XSDModel::back));
        view.setOnForward(() -> withModel(XSDModel::forward));
    }

    /// Shows a schema, following the node selected in it.
    ///
    /// @param model The schema.
    public void setModel(final XSDModel model) {
        this.model = model;
        own(model.addDataSelectionHandler(event ->
                view.setSelectedNode(event.getSelectedItem(), event.isDoubleSelect())));
        view.setModel(model);
    }

    // Home, Back and Forward do nothing until there is a schema to move around
    private void withModel(final Consumer<XSDModel> action) {
        if (model != null) {
            action.accept(model);
        }
    }
}
