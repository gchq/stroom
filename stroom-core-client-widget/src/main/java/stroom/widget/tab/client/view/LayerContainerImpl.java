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

package stroom.widget.tab.client.view;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.ProvidesResize;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;

import stroom.widget.tab.client.presenter.Layer;
import stroom.widget.tab.client.presenter.LayerContainer;

public class LayerContainerImpl extends Composite implements LayerContainer, RequiresResize, ProvidesResize {
    public interface Style extends CssResource {
        String container();

        String layer();
    }

    public interface Resources extends ClientBundle {
        @Source("LayerContainer.css")
        Style style();
    }

    private static class TransitionTimer extends Timer {
        private static final int FREQUENCY = 40;

        private final Set<Layer> layers;
        private final double duration;

        private Layer selectedLayer;
        private long start;

        public TransitionTimer(final Set<Layer> layers, final double duration) {
            this.layers = layers;
            this.duration = duration;
        }

        @Override
        public void run() {
            final long elapsed = System.currentTimeMillis() - start;
            final double percent = Math.max(0, Math.min(1, elapsed / duration));

            final Iterator<Layer> iter = layers.iterator();
            while (iter.hasNext()) {
                final Layer layer = iter.next();
                if (layer != null) {
                    double opacity = layer.getOpacity();

                    if (layer == selectedLayer) {
                        opacity = percent;
                    } else {
                        opacity = 1 - percent;
                    }

                    // Change the opacity on the layer.
                    layer.setOpacity(opacity);

                    if (opacity == 0) {
                        if (layer.removeLayer()) {
                            iter.remove();
                        }
                    }
                }
            }

            if (percent == 1) {
                this.cancel();
            }
        }

        public void update() {
            cancel();
            start = System.currentTimeMillis();
            scheduleRepeating(FREQUENCY);
        }

        public void setSelectedContent(final Layer selectedLayer) {
            this.selectedLayer = selectedLayer;
        }
    }

    private static Resources resources;

    private final FlowPanel panel;
    private boolean fade;
    private TransitionTimer transitionTimer;

    private final Set<Layer> layers = new HashSet<>();
    private Layer selectedLayer;

    public LayerContainerImpl() {
        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        panel = new FlowPanel();
        panel.setStyleName(resources.style().container());

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

            if (fade) {
                fadeTransition();
            } else {
                instantTransition();
            }
        }
    }

    private void instantTransition() {
        final Iterator<Layer> iter = layers.iterator();
        while (iter.hasNext()) {
            final Layer layer = iter.next();
            double opacity = layer.getOpacity();

            if (layer == selectedLayer) {
                opacity = 1;
            } else {
                opacity = 0;
            }

            // Change the opacity on the layer.
            layer.setOpacity(opacity);

            if (opacity == 0) {
                if (layer.removeLayer()) {
                    iter.remove();
                }
            }
        }
    }

    private void fadeTransition() {
        if (transitionTimer == null) {
            transitionTimer = new TransitionTimer(layers, 300);
        }
        transitionTimer.setSelectedContent(selectedLayer);
        transitionTimer.update();
    }

    @Override
    public void clear() {
        show(null);
    }

    @Override
    public void setFade(final boolean fade) {
        this.fade = fade;
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

        widget.addStyleName(resources.style().layer());
        panel.add(widget);
    }
}
