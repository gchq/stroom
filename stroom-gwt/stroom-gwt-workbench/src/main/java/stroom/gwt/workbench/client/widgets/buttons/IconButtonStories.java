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
import stroom.gwt.workbench.client.widgets.StoryArgs;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

import java.util.concurrent.atomic.AtomicInteger;

/// Stories for [InlineSvgButton], matching `Widgets/Buttons/IconButton` in the React Storybook.
///
/// The React `IconButton` is a port of [InlineSvgButton] (and its subclass `SvgButton`), Stroom's
/// icon-only button.
public final class IconButtonStories {

    // Arg names, the same as the React IconButton's props
    private static final String ICON = "icon";
    private static final String TITLE = "title";
    private static final String ENABLED = "enabled";
    private static final String VISIBLE = "visible";
    private static final String CLASS_NAME = "className";
    private static final String ON_CLICK = "onClick";

    private IconButtonStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Buttons/IconButton", IconButtonStories.class)
                .layout(StoryLayout.CENTERED)
                // React's icon arg is the SVG markup; here it is the name of an SvgImage
                .argType(ArgType.select(ICON, "ADD", "EDIT", "DELETE", "FIND", "AUTO_REFRESH")
                        .description("The glyph (GWT InlineSvgButton.setSvg(...)).")
                        .typeName("SvgImage"))
                .argType(ArgType.text(TITLE).description("Native tooltip (GWT setTitle(...))."))
                .argType(ArgType.bool(ENABLED)
                        .description("GWT setEnabled(...): toggles the disabled class and ignores clicks.")
                        .defaultSummary("true"))
                .argType(ArgType.bool(VISIBLE)
                        .description("GWT setVisible(...): hides the button with display:none.")
                        .defaultSummary("true"))
                .argType(ArgType.text(CLASS_NAME).description("Extra CSS classes (GWT addStyleName(...))."))
                .argType(ArgType.action(ON_CLICK).description("Called when the button is clicked."))
                .args(Args.of(ICON, "ADD"))
                // Default icon button (the "add" glyph) with a tooltip
                .story("Default", IconButtonStories::fromArgs)
                .withArgs(Args.of(ICON, "ADD", TITLE, "Add"))
                // The full set of shared glyphs, each with a tooltip
                .story("Icons", context -> StoryPanels.row(8,
                        // Differs from React: React also sets aria-label; InlineSvgButton has no
                        // equivalent, so the accessible name comes from the title (the same text).
                        iconButton(context, SvgImage.ADD, "Add"),
                        iconButton(context, SvgImage.EDIT, "Edit"),
                        iconButton(context, SvgImage.DELETE, "Delete"),
                        iconButton(context, SvgImage.FIND, "Find"),
                        iconButton(context, SvgImage.AUTO_REFRESH, "Refresh")))
                // Disabled - the disabled class is applied and clicks are ignored
                .story("Disabled", context -> {
                    final InlineSvgButton button = iconButton(context, SvgImage.DELETE, "Delete");
                    final InlineLabel counter = counter("Clicks: ", " (should stay 0)");
                    countClicks(button, counter, "Clicks: ", " (should stay 0)");
                    // Differs from React: GWT's setEnabled(false) also sets the native disabled
                    // property (FocusWidget), so the browser shows no tooltip on hover; React only
                    // adds the disabled class, keeping the tooltip.
                    button.setEnabled(false);
                    return StoryPanels.row(8, button, counter);
                })
                // Interactive - click to increment a counter
                .story("Clickable", context -> {
                    final InlineSvgButton button = iconButton(context, SvgImage.ADD, "Increment");
                    final InlineLabel counter = counter("Count: ", "");
                    countClicks(button, counter, "Count: ", "");
                    return StoryPanels.row(8, button, counter);
                })
                // visible={false} collapses the button (rendered here beside a visible one)
                .story("Visibility", context -> {
                    final InlineSvgButton hidden = iconButton(context, SvgImage.EDIT, "Hidden");
                    hidden.setVisible(false);
                    return StoryPanels.row(8,
                            iconButton(context, SvgImage.ADD, "Visible"),
                            hidden,
                            StoryPanels.note("(the edit button is hidden)", "#dce4e5", "12px"));
                });
    }

    /// A button made entirely from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        final Args args = context.getArgs();
        final InlineSvgButton button = iconButton(context,
                StoryArgs.toSvgImage(args.getString(ICON)),
                args.getString(TITLE));
        button.setEnabled(StoryArgs.getBoolean(args, ENABLED, true));
        button.setVisible(StoryArgs.getBoolean(args, VISIBLE, true));
        if (args.has(CLASS_NAME)) {
            button.addStyleName(args.getString(CLASS_NAME));
        }
        return button;
    }

    private static InlineSvgButton iconButton(final StoryContext context,
                                              final SvgImage icon,
                                              final String title) {
        final InlineSvgButton button = new InlineSvgButton();
        if (icon != null) {
            button.setSvg(icon);
        }
        if (title != null) {
            button.setTitle(title);
        }
        final Spy onClick = context.fn(ON_CLICK);
        button.addClickHandler(event -> onClick.call(title));
        return button;
    }

    /// Equivalent of `<span style={{fontSize: 12}}>{prefix}{count}{suffix}</span>`.
    private static InlineLabel counter(final String prefix, final String suffix) {
        return StoryPanels.note(prefix + 0 + suffix, "#dce4e5", "12px");
    }

    private static void countClicks(final InlineSvgButton button,
                                    final InlineLabel counter,
                                    final String prefix,
                                    final String suffix) {
        final AtomicInteger count = new AtomicInteger();
        button.addClickHandler(event -> counter.setText(prefix + count.incrementAndGet() + suffix));
    }
}
