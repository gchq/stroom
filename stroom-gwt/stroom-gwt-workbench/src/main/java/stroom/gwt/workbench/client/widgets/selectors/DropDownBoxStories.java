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


package stroom.gwt.workbench.client.widgets.selectors;

import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.dropdowntree.client.DropDownBox;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [DropDownBox]: a label and an ellipsis button that open a popup (e.g.
/// the explorer tree of a document selection box).
public final class DropDownBoxStories {

    private static final String ON_OPEN = "onOpen";
    private static final String PROBLEM = "'Selected item' can't be found, or you don't have permission to see it.";
    private static final String FULL_PROBLEM = "Item 'Selected item' doesn't exist or you do not have permission to "
                                               + "view it. Select a different item or speak to your administrator.";

    private DropDownBoxStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // Stroom's controllers always set the text ("None" when nothing is selected) and handle
        // the warning button themselves.
        registry.component("Widgets/Selectors/DropDownSelector", DropDownBoxStories.class)
                .layout(StoryLayout.CENTERED)
                // Basic drop-down whose label updates when "opened" (the popup is stubbed)
                .story("Basic", context -> {
                    final DropDownBox box = box(context, "My Pipeline", false, true);
                    return minWidth(box);
                })
                .withPlay(play -> {
                    // A button that opens a dialog, named by the value it shows
                    final Query picker = play.getByRole("button", TextMatch.containing("My Pipeline"));
                    play.expect(picker).toHaveAttribute("aria-haspopup", "dialog");
                    // Space opens it, as for any button
                    play.tab();
                    play.expect(picker).toHaveFocus();
                    play.keyboard(" ");
                    play.expect(play.spy(ON_OPEN)).toHaveBeenCalledTimes(1);
                })
                // Disabled - non-interactive, greyed out
                .story("Disabled", context -> {
                    final DropDownBox box = box(context, "Disabled", false, false);
                    // As Stroom's controllers (e.g. DocSelectionController.setEnabled) do: the box
                    // greys out and tells assistive technology, and the controller ignores its
                    // requests to open. It stays focusable.
                    box.setEnabled(false);
                    return minWidth(box);
                })
                .withPlay(play -> {
                    final Query picker = play.getByRole("button", TextMatch.containing("Disabled"));
                    play.expect(picker).toHaveAttribute("aria-disabled", "true");
                    play.expect(picker).toHaveAttribute("aria-disabled", "true");
                    play.click(picker);
                    play.expect(play.spy(ON_OPEN)).not().toHaveBeenCalled();
                })
                // A problem with the value: the message under the picker (a line of its own, as it isn't
                // in a form group), the full one its tooltip and what a screen reader reads
                .story("Warning", context -> minWidth(box(context, "Selected item", true, true)))
                .withPlay(play -> {
                    final Query picker = play.getByRole("button", TextMatch.startingWith("Selected item"));
                    play.expect(picker).toHaveAttribute("aria-invalid", "true");
                    play.expect(play.querySelector(".drop-down__feedback")).toHaveTextContent(
                            TextMatch.containing(PROBLEM));
                    // (Chrome reads the full message alone, as the short one is hidden from it; the
                    // test library's description also has the hidden text, so this checks it is there)
                    play.expect(picker).toHaveAccessibleDescription(TextMatch.containing(FULL_PROBLEM));
                });
    }

    private static DropDownBox box(final StoryContext context,
                                   final String text,
                                   final boolean hasWarning,
                                   final boolean enabled) {
        final DropDownBox box = new DropDownBox();
        box.setText(text);
        if (hasWarning) {
            box.setProblem(PROBLEM, FULL_PROBLEM);
        }
        final Spy onOpen = context.fn(ON_OPEN);
        box.setOnOpen(() -> {
            if (enabled) {
                onOpen.call();
                box.setText("(popup would open)");
            }
        });
        return box;
    }

    /// Equivalent of `<div style={{minWidth: 240}}>`.
    private static Widget minWidth(final Widget widget) {
        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("minWidth", "240px");
        panel.add(widget);
        return panel;
    }
}
