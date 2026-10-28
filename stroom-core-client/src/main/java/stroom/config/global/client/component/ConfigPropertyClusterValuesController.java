/*
 * Copyright 2020 Crown Copyright
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

package stroom.config.global.client.component;

import stroom.config.global.client.NodeSource;
import stroom.config.global.shared.ConfigProperty;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Map;
import java.util.Set;

/// A property's Cluster Values ([ConfigPropertyClusterValues]), in a dialog made for one use.
public class ConfigPropertyClusterValuesController extends Controller {

    private final ConfigPropertyClusterValues view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public ConfigPropertyClusterValuesController(final EventBus eventBus,
                                                 final ConfigPropertyClusterValues view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows a property's effective values in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener                   What shows it.
    /// @param configProperty           The property.
    /// @param effectiveValueToNodesMap Its effective values, each with the nodes that have it.
    /// @param popupPosition            Where the dialog goes.
    public void show(final HasControllerNode opener,
                     final ConfigProperty configProperty,
                     final Map<String, Set<NodeSource>> effectiveValueToNodesMap,
                     final PopupPosition popupPosition) {
        view.setData(effectiveValueToNodesMap);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(900, 700))
                .popupPosition(popupPosition)
                .caption("Cluster values - " + configProperty.getName())
                .onShow(e -> view.focus())
                .fire();
    }
}
