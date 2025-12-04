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

package stroom.widget.htree.client;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.OutlineStyle;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.FlowPanel;

import java.util.HashMap;
import java.util.Map;

public class LayeredCanvas extends FlowPanel {

    private final Map<String, Canvas> layerMap = new HashMap<>();
    private int width = 100;
    private int height = 100;

    private LayeredCanvas() {
        final Style style = getElement().getStyle();
        style.setWidth(width, Unit.PX);
        style.setHeight(height, Unit.PX);
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

            final Style style = canvas.getElement().getStyle();
            style.setPosition(Position.ABSOLUTE);
            style.setLeft(0, Unit.PX);
            style.setTop(0, Unit.PX);
            style.setWidth(width, Unit.PX);
            style.setHeight(height, Unit.PX);
            style.setOutlineStyle(OutlineStyle.NONE);
            canvas.setCoordinateSpaceWidth(width);
            canvas.setCoordinateSpaceHeight(height);

            add(canvas);
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

        Style style = getElement().getStyle();
        style.setWidth(width, Unit.PX);
        style.setHeight(height, Unit.PX);

        for (final Canvas canvas : layerMap.values()) {
            style = canvas.getElement().getStyle();
            style.setWidth(width, Unit.PX);
            style.setHeight(height, Unit.PX);
            canvas.setCoordinateSpaceWidth(width);
            canvas.setCoordinateSpaceHeight(height);
        }
    }

    public void clear() {
        for (final Canvas canvas : layerMap.values()) {
            canvas.getContext2d().clearRect(0, 0, width, height);
        }
    }
}
