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

package stroom.gwt.workbench.client.widgets.buttons;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarToggleButton;

import com.google.gwt.user.client.ui.Widget;

/// The look of a toolbar's buttons ([ToolbarButton], [ToolbarToggleButton]) in each of their states,
/// for screenshots, so that changes to their DOM or CSS can be compared with a baseline
/// (`workbenchScreenshotDiff`).
///
/// The hover and focus stories are hovered over or tabbed to with the real mouse and keyboard by
/// the test runner's real input checks (`test-runner/lib/real-input/buttons.mjs`).
public final class ToolbarButtonStateStories {

    private static final String TARGET = "target";

    private ToolbarButtonStateStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Buttons/ToolbarButtonStates", ToolbarButtonStateStories.class)
                .layout(StoryLayout.CENTERED)
                // Enabled, disabled, not pressed, pressed, and pressed and disabled
                .story("States", context -> StoryPanels.row(8,
                        button(SvgImage.ADD, "Enabled"),
                        disabled(button(SvgImage.DELETE, "Disabled")),
                        toggle(SvgImage.FILTER, "Toggle off", false),
                        toggle(SvgImage.FILTER, "Toggle on", true),
                        disabled(toggle(SvgImage.FILTER, "Toggle on, disabled", true))))
                // Driven by the real input check: hovered with the mouse
                .story("Hovered", context -> target(button(SvgImage.ADD, "Hovered")))
                .story("HoveredOn", context -> target(toggle(SvgImage.FILTER, "Hovered toggle on", true)))
                .story("HoveredDisabled", context -> target(disabled(button(SvgImage.DELETE, "Hovered disabled"))))
                // Driven by the real input check: focused with the keyboard
                .story("FocusVisible", context -> target(button(SvgImage.ADD, "Focused")))
                .story("FocusVisibleOn", context -> target(toggle(SvgImage.FILTER, "Focused toggle on", true)));
    }

    private static ToolbarButton button(final SvgImage icon, final String name) {
        final ToolbarButton button = new ToolbarButton();
        button.setSvg(icon);
        button.setTitle(name);
        return button;
    }

    private static ToolbarToggleButton toggle(final SvgImage icon, final String name, final boolean pressed) {
        final ToolbarToggleButton button = new ToolbarToggleButton();
        button.setSvg(icon);
        button.setTitle(name);
        button.setPressed(pressed);
        return button;
    }

    private static <T extends ToolbarButton> T disabled(final T button) {
        button.setEnabled(false);
        return button;
    }

    // Marks the button for the real input check
    private static Widget target(final Widget button) {
        button.getElement().setAttribute("data-testid", TARGET);
        return button;
    }
}
