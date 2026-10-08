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
import stroom.widget.dropdowntree.client.view.DropDownUiHandlers;
import stroom.widget.dropdowntree.client.view.DropDownViewImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [DropDownViewImpl], matching `Widgets/Selectors/DropDownSelector` in the
/// React Storybook: a label and an ellipsis button that open a popup (e.g. the explorer tree of a
/// document selection box).
public final class DropDownSelectorStories {

    private static final String ON_OPEN = "onOpen";

    private DropDownSelectorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's placeholder (shown for empty text) and onWarningClick have no equivalent on the
        // view; Stroom's presenters always set the text ("None" when nothing is selected) and
        // handle the warning button themselves.
        registry.component("Widgets/Selectors/DropDownSelector", DropDownSelectorStories.class)
                .layout(StoryLayout.CENTERED)
                // Basic drop-down whose label updates when "opened" (the popup is stubbed)
                .story("Basic", context -> {
                    final DropDownViewImpl view = view(context, "My Pipeline", false, true);
                    return minWidth(view.asWidget());
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
                    final DropDownViewImpl view = view(context, "Disabled", false, false);
                    // As Stroom's presenters (e.g. DocSelectionBoxPresenter.setEnabled) do: the view
                    // greys out and tells assistive technology, and the presenter ignores the view's
                    // showPopup. Differs from React: it stays focusable.
                    view.setEnabled(false);
                    return minWidth(view.asWidget());
                })
                .withPlay(play -> {
                    final Query picker = play.getByRole("button", TextMatch.containing("Disabled"));
                    play.expect(picker).toHaveAttribute("aria-disabled", "true");
                    play.expect(picker).toHaveClass("disabled");
                    play.click(picker);
                    play.expect(play.spy(ON_OPEN)).not().toHaveBeenCalled();
                })
                // Warning indicator - shows the alert triangle
                .story("Warning", context -> minWidth(view(context, "Selected item", true, true).asWidget()));
    }

    private static DropDownViewImpl view(final StoryContext context,
                                         final String text,
                                         final boolean hasWarning,
                                         final boolean enabled) {
        final DropDownViewImpl view = new DropDownViewImpl(GWT.create(DropDownViewImpl.Binder.class));
        view.setText(text, hasWarning);
        final Spy onOpen = context.fn(ON_OPEN);
        final DropDownUiHandlers handlers = () -> {
            if (enabled) {
                onOpen.call();
                view.setText("(popup would open)", hasWarning);
            }
        };
        view.setUiHandlers(handlers);
        return view;
    }

    /// Equivalent of `<div style={{minWidth: 240}}>`.
    private static Widget minWidth(final Widget widget) {
        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("minWidth", "240px");
        panel.add(widget);
        return panel;
    }
}
