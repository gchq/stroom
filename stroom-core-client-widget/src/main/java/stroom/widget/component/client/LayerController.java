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


import com.google.gwt.user.client.ui.RequiresResize;
import com.google.web.bindery.event.shared.EventBus;

/// A controller that is itself a [Layer] of a [LayerContainer]: a dashboard pane
/// (a tab of the dashboard's layout), a tab of a pane's settings dialog, or a screen shown as one of
/// Stroom's tabs, which their hosts tell apart by identity and ask about (e.g. its type, its label),
/// so they can't be wrapped in a `ControllerLayer`. Its widget is the one given to [#initWidget].
public abstract class LayerController extends Controller implements Layer {

    /// @param eventBus The application's event bus.
    protected LayerController(final EventBus eventBus) {
        super(eventBus);
    }

    @Override
    public void setLayerVisible(final boolean fade, final boolean visible) {
        Layer.setLayerVisible(asWidget().getElement(), fade, visible);
    }

    @Override
    public void addLayer(final LayerContainer container) {
        container.add(asWidget());
    }

    @Override
    public boolean removeLayer() {
        asWidget().removeFromParent();
        return true;
    }

    @Override
    public void onResize() {
        if (asWidget() instanceof final RequiresResize requiresResize) {
            requiresResize.onResize();
        }
    }
}
