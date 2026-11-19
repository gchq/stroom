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

package stroom.widget.htree.client;

import stroom.widget.util.client.CssProperties;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;

import java.util.HashMap;
import java.util.Map;

/// A canvas of stacked layers, made on demand by name, e.g. a tree's lines on one and its selection
/// on another. Styled by `layered-canvas.css`; its size is set as custom properties on the root and
/// on every layer, as a layer may be moved elsewhere to be drawn (e.g. the pipeline tree's faded
/// layer, drawn in its faded group).
public class LayeredCanvas extends FlowPanel {

    private static final String BLOCK = "layered-canvas";

    private final Map<String, Canvas> layerMap = new HashMap<>();
    private int width = 100;
    private int height = 100;

    private LayeredCanvas() {
        setStyleName(BLOCK);
        setSizeProperties(getElement());
    }

    public static LayeredCanvas createIfSupported() {
        if (Canvas.isSupported()) {
            return new LayeredCanvas();
        }
        return null;
    }

    private Canvas createLayer(final String name) {
        final Canvas canvas = Canvas.createIfSupported();
        if (canvas != null) {
            layerMap.put(name, canvas);

            canvas.setStyleName(BLOCK + "__layer");
            setSizeProperties(canvas.getElement());
            canvas.setCoordinateSpaceWidth(width);
            canvas.setCoordinateSpaceHeight(height);

            add(canvas);
            if (isAttached()) {
                hide(canvas);
            }
        }
        return canvas;
    }

    public Canvas getLayer(final String name) {
        Canvas canvas = layerMap.get(name);
        if (canvas == null) {
            canvas = createLayer(name);
        }
        return canvas;
    }

    public void setSize(final int width, final int height) {
        this.width = width;
        this.height = height;

        setSizeProperties(getElement());

        for (final Canvas canvas : layerMap.values()) {
            setSizeProperties(canvas.getElement());
            canvas.setCoordinateSpaceWidth(width);
            canvas.setCoordinateSpaceHeight(height);
        }
    }

    public void clear() {
        for (final Canvas canvas : layerMap.values()) {
            canvas.getContext2d().clearRect(0, 0, width, height);
        }
    }

    // The layers are drawing only, so they are kept out of the tab order and hidden from assistive
    // technology. This is done once they are attached (or when made, if after), as a canvas (a
    // FocusWidget) makes itself a tab stop then.
    @Override
    protected void onLoad() {
        super.onLoad();
        for (final Canvas canvas : layerMap.values()) {
            hide(canvas);
        }
    }

    // The size, which layered-canvas.css's rules use
    private void setSizeProperties(final Element element) {
        CssProperties.set(element, "--" + BLOCK + "__width", width + "px");
        CssProperties.set(element, "--" + BLOCK + "__height", height + "px");
    }

    private static void hide(final Canvas canvas) {
        canvas.getElement().setTabIndex(-1);
        canvas.getElement().setAttribute("aria-hidden", "true");
    }
}
