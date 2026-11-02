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
import stroom.gwt.workbench.framework.client.story.ComponentStories;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;

import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// The look of a text button ([Button]) of each colour in each of its states, for screenshots, so
/// that changes to their DOM or CSS can be compared with a baseline (`workbenchScreenshotDiff`). Each
/// button has `allow-focus`, as a dialog's buttons do.
///
/// The hover, pressed and focus stories are hovered over, pressed or tabbed to with the real mouse
/// and keyboard by the test runner's real input checks (`test-runner/lib/real-input/buttons.mjs`).
public final class ButtonStateStories {

    private static final String TARGET = "target";

    // Each colour: its name in the stories' names, and its classes (none for the default)
    private static final List<Colour> COLOURS = List.of(
            new Colour("Default", null),
            new Colour("Primary", "button--contained button--contained-primary"),
            new Colour("Secondary", "button--contained button--contained-secondary"),
            new Colour("Green", "button--contained button--contained-green"));

    private ButtonStateStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        final ComponentStories stories = registry.component("Widgets/Buttons/ButtonStates", ButtonStateStories.class)
                .layout(StoryLayout.CENTERED)
                // Each colour, a row each: enabled, disabled and loading
                .story("States", context -> StoryPanels.column(12, COLOURS.stream()
                        .map(colour -> StoryPanels.row(12,
                                button(colour, "Enabled"),
                                disabled(button(colour, "Disabled")),
                                loading(button(colour, "Loading"))))
                        .toArray(Widget[]::new)));
        // Driven by the real input checks: hovered with the mouse, pressed with it, and focused with
        // the keyboard, a story for each colour
        for (final Colour colour : COLOURS) {
            stories.story("Hovered" + colour.name(), context -> target(button(colour, "Hovered")))
                    .story("Pressed" + colour.name(), context -> target(button(colour, "Pressed")))
                    .story("FocusVisible" + colour.name(), context -> target(button(colour, "Focused")));
        }
    }

    private static Button button(final Colour colour, final String text) {
        final Button button = new Button();
        button.setText(text);
        button.setIcon(SvgImage.OK);
        button.addStyleName("allow-focus");
        if (colour.classes() != null) {
            button.addStyleName(colour.classes());
        }
        return button;
    }

    private static Button disabled(final Button button) {
        button.setEnabled(false);
        return button;
    }

    private static Button loading(final Button button) {
        button.setLoading(true);
        return button;
    }

    // Marks the button for the real input check
    private static Widget target(final Widget button) {
        button.getElement().setAttribute("data-testid", TARGET);
        return button;
    }


    // --------------------------------------------------------------------------------


    // A button colour: its name in the stories' names, and its classes, or null for the default
    private record Colour(String name, String classes) {

    }
}
