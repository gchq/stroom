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

package stroom.widget.button.client;

import java.util.function.Consumer;

/// A [ToolbarButton] that is pressed (on) or not, e.g. to show or hide something. Its state is
/// `aria-pressed`, which screen readers announce and toolbar-button.css styles. Made by [Toolbar#toggle],
/// or declared in a template as `<b:ToolbarToggleButton svg="FILTER" title="Show Filters"/>`.
public class ToolbarToggleButton extends ToolbarButton implements ToggleButtonView {

    private boolean pressed;

    public ToolbarToggleButton() {
        // Says it is a toggle from the start
        setPressed(false);
        // Added first, so the state has changed before anything else hears of the press
        addClickHandler(event -> setPressed(!pressed));
    }

    /// @param pressed Whether the button is pressed (on).
    public void setPressed(final boolean pressed) {
        this.pressed = pressed;
        getElement().setAttribute("aria-pressed", String.valueOf(pressed));
    }

    /// @return Whether the button is pressed (on).
    public boolean isPressed() {
        return pressed;
    }

    /// As [#setPressed(boolean)], for controllers given it as a [ToggleButtonView].
    ///
    /// @param on Whether the button is pressed (on).
    @Override
    public void setState(final boolean on) {
        setPressed(on);
    }

    /// As [#isPressed()], for controllers given it as a [ToggleButtonView].
    ///
    /// @return Whether the button is pressed (on).
    @Override
    public boolean getState() {
        return pressed;
    }

    /// Sets what pressing the button does, replacing anything set before: it is told the button's
    /// new state.
    ///
    /// @param onToggle Given true when the button is now pressed, or null to do nothing.
    public void setOnToggle(final Consumer<Boolean> onToggle) {
        setOnClick(onToggle == null
                ? null
                : () -> onToggle.accept(pressed));
    }
}
