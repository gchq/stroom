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

package stroom.widget.button.client;

import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.dom.client.HasMouseDownHandlers;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;

/// A Stroom button, as a controller sees it: a [Button] or a [ToolbarButton] (including a
/// [ToolbarToggleButton]).
public interface IsButton extends HasClickHandlers, HasMouseDownHandlers, Focus {

    boolean isEnabled();

    void setEnabled(boolean enabled);

    boolean isVisible();

    void setVisible(boolean visible);

    void setTitle(String title);

    /// @return The button as a [Widget], so that it can be inserted in the DOM.
    Widget asWidget();

    /// Null-safe setting of the enabled state.
    ///
    /// @param button  The button, or null to do nothing.
    /// @param enabled Whether it is enabled.
    static void setEnabled(final IsButton button, final boolean enabled) {
        if (button != null) {
            button.setEnabled(enabled);
        }
    }
}
