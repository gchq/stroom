/*
 * Copyright 2026 Crown Copyright
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

import stroom.widget.presenter.client.Layer;
import stroom.widget.presenter.client.LayerContainer;

import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// Shows a controller's widget in a [LayerContainer], e.g. as the body of one of a document's
/// tabs, as a presenter is shown. Make one per controller and keep it, as the container tells its
/// layers apart by identity. Focusing the layer focuses the controller, if it takes the focus.
public final class ControllerLayer implements Layer, Focus {

    private final IsWidget content;

    /// @param content What to show, e.g. a controller.
    public ControllerLayer(final IsWidget content) {
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override
    public void setLayerVisible(final boolean fade, final boolean visible) {
        Layer.setLayerVisible(getWidget().getElement(), fade, visible);
    }

    @Override
    public void addLayer(final LayerContainer container) {
        container.add(getWidget());
    }

    @Override
    public boolean removeLayer() {
        getWidget().removeFromParent();
        return true;
    }

    @Override
    public void onResize() {
        if (getWidget() instanceof final RequiresResize requiresResize) {
            requiresResize.onResize();
        }
    }

    /// Puts the focus in the content if it takes the focus (it is a [Focus]), as a presenter shown
    /// as a layer does, e.g. when its tab is chosen.
    @Override
    public void focus() {
        if (content instanceof final Focus focus) {
            focus.focus();
        }
    }

    private Widget getWidget() {
        return content.asWidget();
    }
}
