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

package com.gwtplatform.mvp.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.RequiresResize;

public interface Layer extends RequiresResize {

    String BASE = "layerContainer-layer--base";
    String VISIBLE = "layerContainer-layer--visible";
    String FADE = "layerContainer-layer--fade";

//    double getOpacity();
//
//    void setOpacity(double opacity);

    void setLayerVisible(boolean fade, boolean visible);

    void addLayer(LayerContainer container);

    boolean removeLayer();

    static void setLayerVisible(final Element element,
                                final boolean fade,
                                final boolean visible) {
        element.addClassName(Layer.BASE);
        if (fade) {
            element.addClassName(Layer.FADE);
        } else {
            element.removeClassName(Layer.FADE);
        }
        if (visible) {
            element.addClassName(Layer.VISIBLE);
        } else {
            element.removeClassName(Layer.VISIBLE);
        }
    }
}
