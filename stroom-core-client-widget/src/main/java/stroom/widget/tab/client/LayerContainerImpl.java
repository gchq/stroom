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

package stroom.widget.tab.client;

import stroom.widget.component.client.Layer;
import stroom.widget.component.client.LayerContainer;

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

public class LayerContainerImpl extends Composite implements LayerContainer, RequiresResize, ProvidesResize {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final FlowPanel panel;
    private final Set<Layer> layers = new HashSet<>();
    private Layer selectedLayer;

    public LayerContainerImpl() {
        panel = BINDER.createAndBindUi(this);
        initWidget(panel);
    }

    @Override
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

    @Override
    public void clear() {
        show(null);
    }

    @Override
    public void onResize() {
        for (final Layer layer : layers) {
            layer.onResize();
        }
    }

    @Override
    public void add(final IsWidget isWidget) {
        final Widget widget = isWidget.asWidget();

        widget.addStyleName("layer-container__layer");
        panel.add(widget);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<FlowPanel, LayerContainerImpl> {

    }
}
