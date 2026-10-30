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

package stroom.widget.component.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.ProvidesResize;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/// Shows the layers of a tabbed screen ([Layer]s, e.g. a [ControllerLayer] per tab), one over
/// another, only the selected one visible. Its block is `layer-container` (`layer-container.css`).
public class LayerContainer extends Composite implements RequiresResize, ProvidesResize {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final FlowPanel panel;
    private final Set<Layer> layers = new HashSet<>();
    private Layer selectedLayer;

    /// Makes the container, with no layers.
    public LayerContainer() {
        panel = BINDER.createAndBindUi(this);
        initWidget(panel);
    }

    /// Shows a layer, adding it if it is new, and hides the others.
    ///
    /// @param layer The layer to show, or null to show none.
    public void show(final Layer layer) {
        if (selectedLayer != layer) {
            this.selectedLayer = layer;

            if (layer != null) {
                layers.add(layer);
                layer.addLayer(this);
                onResize();
            }

            instantTransition();
        }
    }

    private void instantTransition() {
        final Iterator<Layer> iter = layers.iterator();
        while (iter.hasNext()) {
            final Layer layer = iter.next();

            // Change the opacity on the layer.
            layer.setLayerVisible(false, layer == selectedLayer);

            if (layer != selectedLayer) {
                if (layer.removeLayer()) {
                    iter.remove();
                }
            }
        }
    }

    /// Shows no layer.
    public void clear() {
        show(null);
    }

    @Override
    public void onResize() {
        for (final Layer layer : layers) {
            layer.onResize();
        }
    }

    /// Adds a layer's widget, which the layer does when it is first shown.
    ///
    /// @param isWidget The layer's widget.
    public void add(final IsWidget isWidget) {
        final Widget widget = isWidget.asWidget();

        widget.addStyleName("layer-container__layer");
        panel.add(widget);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<FlowPanel, LayerContainer> {

    }
}
