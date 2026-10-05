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

package stroom.gwt.workbench.client.widgets;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

import java.util.concurrent.atomic.AtomicInteger;

/// Stories for [Button], matching `Widgets/Buttons/Button` in the React Storybook.
public final class ButtonStories {

    private static final String PRIMARY = "Button--contained-primary";
    private static final String SECONDARY = "Button--contained-secondary";
    private static final int LOADING_RESET_MILLIS = 2000;

    // Arg names, the same as the React Button's props
    private static final String TEXT = "text";
    private static final String VARIANT = "variant";
    private static final String LOADING = "loading";
    private static final String DISABLED = "disabled";
    private static final String CLASS_NAME = "className";
    private static final String ICON = "icon";
    private static final String TITLE = "title";
    private static final String WIDTH = "width";
    private static final String ON_CLICK = "onClick";

    private ButtonStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Buttons/Button", ButtonStories.class)
                .layout(StoryLayout.CENTERED)
                .argType(ArgType.text(TEXT)
                        .description("Button label text. Defaults to \"Close\" when omitted, mirroring GWT Button.")
                        .defaultSummary("'Close'"))
                .argType(ArgType.radio(VARIANT, "default", "contained-primary", "contained-secondary",
                                "contained-green")
                        .description("Visual variant.")
                        .defaultSummary("'default'"))
                .argType(ArgType.bool(LOADING)
                        .description("Loading state — shows spinner, disables interaction.")
                        .defaultSummary("false"))
                .argType(ArgType.bool(DISABLED)
                        .description("Whether the button is disabled.")
                        .defaultSummary("false"))
                .argType(ArgType.text(CLASS_NAME).description("Extra CSS classes to append."))
                .argType(ArgType.select(ICON, "ADD", "DELETE", "FIND", "AUTO_REFRESH", "EDIT")
                        .description("Optional icon shown before the text (GWT Button.setIcon(...)).")
                        .typeName("SvgImage"))
                .argType(ArgType.text(TITLE).description("Optional native tooltip text (GWT-style setTitle(...))."))
                .argType(ArgType.text(WIDTH).description("Optional CSS width, e.g. '200px'."))
                .argType(ArgType.action(ON_CLICK).description("Called when the button is clicked."))
                .story("Default", ButtonStories::fromArgs)
                .withArgs(Args.of(TEXT, "Click me"))
                // Primary (contained) button with a click counter
                .story("Primary", ButtonStories::primary)
                .withPlay(play -> {
                    // Not by name, as GWT's Button repeats its text (in a transparent span) so its
                    // accessible name is e.g. 'SaveSave'
                    play.click(play.getByRole("button"));
                    play.expect(play.getByText("Clicked: 1")).toBeVisible();
                    play.click(play.getByRole("button"));
                    play.waitFor(() -> play.expect(play.getByText("Clicked: 2")).toBeInTheDocument());
                })
                .story("Secondary", ButtonStories::fromArgs)
                .withArgs(Args.of(TEXT, "Cancel", VARIANT, "contained-secondary"))
                // Loading state, click to toggle (auto-resets after 2 s)
                .story("Loading", ButtonStories::loading)
                .story("Disabled", context -> {
                    final Button disabledDefault = button(context, "Disabled default", null);
                    disabledDefault.setEnabled(false);
                    final Button disabledPrimary = button(context, "Disabled primary", PRIMARY);
                    disabledPrimary.setEnabled(false);
                    return StoryPanels.row(12, disabledDefault, disabledPrimary);
                })
                .story("WithIcons", context -> StoryPanels.row(12,
                        button(context, "Add", PRIMARY, SvgImage.ADD),
                        button(context, "Delete", SECONDARY, SvgImage.DELETE),
                        button(context, "Search", null, SvgImage.FIND),
                        button(context, "Refresh", null, SvgImage.AUTO_REFRESH),
                        button(context, "Edit", null, SvgImage.EDIT)));
    }

    /// A button made entirely from the story's args, so the Controls addon changes it.
    private static Widget fromArgs(final StoryContext context) {
        final Args args = context.getArgs();
        final String variant = args.getString(VARIANT, "default");
        final Button button = button(context, args.getString(TEXT, "Close"), "default".equals(variant)
                ? null
                : "Button--" + variant);
        button.setLoading(args.getBoolean(LOADING));
        button.setEnabled(!args.getBoolean(DISABLED));
        if (args.has(CLASS_NAME)) {
            button.addStyleName(args.getString(CLASS_NAME));
        }
        final SvgImage icon = toSvgImage(args.getString(ICON));
        if (icon != null) {
            button.setIcon(icon);
        }
        if (args.has(TITLE)) {
            button.setTitle(args.getString(TITLE));
        }
        if (args.has(WIDTH)) {
            button.setWidth(args.getString(WIDTH));
        }
        return button;
    }

    /// Converts an icon arg to an [SvgImage].
    ///
    /// @param name The name of an [SvgImage] constant, e.g. `ADD`. Args can be set in the URL so
    ///             this may be any value, or null.
    /// @return The image, or null, so no icon is shown, if there is no image with that name.
    static SvgImage toSvgImage(final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (final SvgImage image : SvgImage.values()) {
            if (image.name().equals(name)) {
                return image;
            }
        }
        return null;
    }

    private static Widget primary(final StoryContext context) {
        final InlineLabel counter = StoryPanels.note("Clicked: 0", "#ccc", "0.85rem");
        final Button button = button(context, "Save", PRIMARY);
        final AtomicInteger count = new AtomicInteger();
        button.addClickHandler(event -> counter.setText("Clicked: " + count.incrementAndGet()));
        return StoryPanels.row(12, button, counter);
    }

    private static Widget loading(final StoryContext context) {
        final Button button = button(context, "Submit", PRIMARY);
        button.addClickHandler(event -> {
            button.setLoading(true);
            new Timer() {
                @Override
                public void run() {
                    button.setLoading(false);
                }
            }.schedule(LOADING_RESET_MILLIS);
        });
        return StoryPanels.row(12, button, StoryPanels.note("(auto-resets after 2 s)", "#aaa", "0.8rem"));
    }

    private static Button button(final StoryContext context, final String text, final String variantClass) {
        return button(context, text, variantClass, null);
    }

    private static Button button(final StoryContext context,
                                 final String text,
                                 final String variantClass,
                                 final SvgImage icon) {
        final Button button = new Button();
        button.setText(text);
        if (variantClass != null) {
            button.addStyleName(variantClass);
        }
        if (icon != null) {
            button.setIcon(icon);
        }
        button.addClickHandler(event -> context.action(ON_CLICK, text));
        return button;
    }
}
