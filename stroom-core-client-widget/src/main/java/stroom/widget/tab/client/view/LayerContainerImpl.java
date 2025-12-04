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

package stroom.widget.tab.client.view;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.ProvidesResize;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.LayerContainer;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class LayerContainerImpl extends Composite implements LayerContainer, RequiresResize, ProvidesResize {

    private final FlowPanel panel;
    private final Set<Layer> layers = new HashSet<>();
    private boolean fade;
    //    private TransitionTimer transitionTimer;
    private Layer selectedLayer;

    public LayerContainerImpl() {
        panel = new FlowPanel();
        panel.addStyleName("layerContainer-container");
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

//            if (fade) {
//                fadeTransition();
//            } else {
            instantTransition();
//            }
        }
    }

    private void instantTransition() {
        final Iterator<Layer> iter = layers.iterator();
        while (iter.hasNext()) {
            final Layer layer = iter.next();
//            final double opacity;
//            if (layer == selectedLayer) {
//                opacity = 1;
//            } else {
//                opacity = 0;
//            }

            // Change the opacity on the layer.
            layer.setLayerVisible(fade, layer == selectedLayer);

            if (layer != selectedLayer) {
                if (layer.removeLayer()) {
                    iter.remove();
                }
            }
        }
    }
//
//    private void fadeTransition() {
//        if (transitionTimer == null) {
//            transitionTimer = new TransitionTimer(layers, 300);
//        }
//        transitionTimer.setSelectedContent(selectedLayer);
//        transitionTimer.update();
//    }

    @Override
    public void clear() {
        show(null);
    }

    @Override
    public void setFade(final boolean fade) {
//        this.fade = fade;
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

        widget.addStyleName("layerContainer-layer");
        panel.add(widget);
    }

//    private static class TransitionTimer extends Timer {
//
//        private static final int FREQUENCY = 40;
//
//        private final Set<Layer> layers;
//        private final double duration;
//
//        private Layer selectedLayer;
//        private long start;
//
//        public TransitionTimer(final Set<Layer> layers, final double duration) {
//            this.layers = layers;
//            this.duration = duration;
//        }
//
//        @Override
//        public void run() {
//            final long elapsed = System.currentTimeMillis() - start;
//            final double percent = Math.max(0, Math.min(1, elapsed / duration));
//
//            final Iterator<Layer> iter = layers.iterator();
//            while (iter.hasNext()) {
//                final Layer layer = iter.next();
//                if (layer != null) {
//                    double opacity = layer.getOpacity();
//
//                    if (layer == selectedLayer) {
//                        opacity = percent;
//                    } else {
//                        opacity = 1 - percent;
//                    }
//
//                    // Change the opacity on the layer.
//                    layer.setOpacity(opacity);
//
//                    if (opacity == 0) {
//                        if (layer.removeLayer()) {
//                            iter.remove();
//                        }
//                    }
//                }
//            }
//
//            if (percent == 1) {
//                this.cancel();
//            }
//        }
//
//        public void update() {
//            cancel();
//            start = System.currentTimeMillis();
//            scheduleRepeating(FREQUENCY);
//        }
//
//        public void setSelectedContent(final Layer selectedLayer) {
//            this.selectedLayer = selectedLayer;
//        }
//    }
}
