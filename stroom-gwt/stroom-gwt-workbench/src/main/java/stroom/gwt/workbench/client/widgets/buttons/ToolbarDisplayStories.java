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

import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.shared.ThemeCssUtil;
import stroom.ui.config.shared.UserPreferences.ToolbarDisplay;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.text.client.Label;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories of the toolbar display preference (`UserPreferences.ToolbarDisplay`): a list's action
/// toolbar, and a pager's toolbar kept to icons (`toolbar--icons`), shown as icons, as icons and
/// text, and as text, and in a toolbar too narrow for its buttons, whose last ones go into its More
/// menu. The preference is a page class in Stroom (`ThemeCssUtil`); here it is a class on the
/// story's own panel, which the stylesheets read the same way.
public final class ToolbarDisplayStories {

    private static final String ON_PRESS = "onPress";
    private static final String EDIT = "Edit Selected Index Field";
    private static final String DOWNLOAD = "Download Selected Streams";
    private static final String FILTERS = "Show Column Filters";
    private static final String FIRST = "First";

    private ToolbarDisplayStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Buttons/ToolbarDisplay", ToolbarDisplayStories.class)
                .layout(StoryLayout.PADDED)
                // Icons only, the default: each button's name is its tooltip
                .story("Icons", context -> render(context, ToolbarDisplay.ICONS, null))
                .withPlay(play -> {
                    expectIconOnly(play, EDIT);
                    expectBorder(play, EDIT, false);
                    expectIconOnly(play, FIRST);
                })
                // Icons and text: the action toolbar's buttons show their labels beside their icons
                // (a short label where the name is long); the pager's stay icons
                .story("IconsAndText", context -> render(context, ToolbarDisplay.ICONS_AND_TEXT, null))
                .withPlay(play -> {
                    expectLabel(play, EDIT, "Edit", true);
                    expectBorder(play, EDIT, false);
                    expectLabel(play, FILTERS, FILTERS, true);
                    expectIconOnly(play, FIRST);
                })
                // Text: the action toolbar's buttons show only their labels
                .story("Text", context -> render(context, ToolbarDisplay.TEXT, null))
                .withPlay(play -> {
                    expectLabel(play, EDIT, "Edit", false);
                    // Bordered, so that text reads as a button; the pager's icons aren't
                    expectBorder(play, EDIT, true);
                    expectBorder(play, FIRST, false);
                    expectLabel(play, DOWNLOAD, "Download", false);
                    expectIconOnly(play, FIRST);
                })
                // Too narrow for its buttons with their labels: the last go into its More menu,
                // which presses them as they would be pressed
                .story("Overflow", context -> render(context, ToolbarDisplay.ICONS_AND_TEXT, "420px"))
                .withPlay(ToolbarDisplayStories::playOverflow)
                // As text, too narrow too
                .story("TextOverflow", context -> render(context, ToolbarDisplay.TEXT, "320px"))
                .withPlay(play -> play.expect(play.getByRole("button", "More")).toBeVisible());
    }

    private static Widget render(final StoryContext context, final ToolbarDisplay display, final String width) {
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.builder().build())
                .withoutStartupFixtures()
                .build();
        final Spy onPress = context.fn(ON_PRESS);

        final Toolbar actions = new Toolbar("Index field actions");
        actions.button(SvgImage.ADD, "New Index Field", () -> onPress.call("New Index Field"));
        actions.button(SvgImage.EDIT, EDIT, "Edit", () -> onPress.call(EDIT));
        actions.button(SvgImage.DELETE, "Delete Selected Index Fields", "Delete",
                () -> onPress.call("Delete Selected Index Fields"));
        actions.separator();
        actions.button(SvgImage.DOWNLOAD, DOWNLOAD, "Download", () -> onPress.call(DOWNLOAD));
        actions.toggle(SvgImage.FILTER, FILTERS, pressed -> onPress.call(FILTERS));
        final ToolbarButton run = actions.button(SvgImage.PLAY, "Execute Query", () -> onPress.call("Execute Query"));
        run.addStyleName("toolbar-button--play");

        final Toolbar paging = new Toolbar("Paging");
        paging.addStyleName("toolbar--icons");
        paging.button(SvgImage.FAST_BACKWARD, FIRST, () -> onPress.call(FIRST));
        paging.button(SvgImage.STEP_BACKWARD, "Backward", () -> onPress.call("Backward"));
        paging.button(SvgImage.STEP_FORWARD, "Forward", () -> onPress.call("Forward"));
        paging.button(SvgImage.FAST_FORWARD, "Last", () -> onPress.call("Last"));

        final FlowPanel row = new FlowPanel();
        row.setStyleName("toolbar-row");
        row.add(actions);
        row.add(paging);
        if (width != null) {
            row.getElement().getStyle().setProperty("width", width);
            row.getElement().getStyle().setProperty("border", "1px dashed var(--border-color)");
        }

        final FlowPanel page = new FlowPanel();
        final String displayClass = ThemeCssUtil.getToolbarDisplayClass(display);
        if (displayClass != null) {
            page.addStyleName(displayClass);
        }
        page.add(new Label("Toolbar Buttons: " + display.getDisplayValue()));
        page.add(row);
        harness.add(page);
        return harness.asWidget();
    }

    // The button shows its icon alone, 24px wide, its label hidden
    private static void expectIconOnly(final Play play, final String name) {
        final Query button = play.getByRole("button", name);
        play.expect("\"" + name + "\" is 24px wide", () -> button.width().get()).toEqual(24.0);
        play.expect(play.within(button).querySelector(".toolbar-button__label")).toHaveStyle("display", "none");
    }

    // The button shows its label (and its icon, or not), and is still named by its whole name; the
    // label is drawn from an attribute, not held as the page's text
    private static void expectLabel(final Play play, final String name, final String label, final boolean icon) {
        final Query button = play.getByRole("button", name);
        final Query labelElement = play.within(button).querySelector(".toolbar-button__label");
        play.expect(labelElement).toHaveAttribute("data-label", label);
        play.expect(button).toHaveTextContent("");
        play.expect(labelElement).toBeVisible();
        play.expect(labelElement).toHaveAttribute("aria-hidden", "true");
        play.expect(play.within(button).querySelector("svg")).toHaveStyle("display", icon
                ? "block"
                : "none");
        play.expect("\"" + name + "\" is wider than its icon", () -> button.width().get() > 24).toEqual(true);
    }

    // Whether the button has a border at rest (drawn by its ::before, toolbar-button.css)
    private static void expectBorder(final Play play, final String name, final boolean bordered) {
        final Query button = play.getByRole("button", name);
        play.expect("\"" + name + "\" has a border", () -> "solid".equals(beforeBorderStyle(button.element().get())))
                .toEqual(bordered);
    }

    private static native String beforeBorderStyle(Element element) /*-{
        return element.ownerDocument.defaultView.getComputedStyle(element, '::before').borderTopStyle;
    }-*/;

    private static void playOverflow(final Play play) {
        final Query more = play.getByRole("button", "More");
        play.waitFor(() -> play.expect(more).toBeVisible());
        // The last buttons are in the menu, not the toolbar; the first still show
        play.expect(play.getByRole("button", "New Index Field")).toBeVisible();
        play.expect(play.queryByRole("button", "Execute Query")).toBeNull();
        play.expect(play.queryByRole("button", FILTERS)).toBeNull();
        play.expect("the toolbar ends in its row", () -> play.querySelector(".toolbar").right().get()
                <= play.querySelector(".toolbar-row").right().get()).toEqual(true);

        // The menu lists them by their names, and presses one as the toolbar would
        play.click(more);
        final Play screen = play.screen();
        play.waitFor(() -> play.expect(screen.getByRole("menuitem", "Execute Query")).toBeInTheDocument());
        play.expect(screen.getByRole("menuitem", FILTERS)).toBeInTheDocument();
        play.click(screen.getByRole("menuitem", "Execute Query"));
        play.expect(play.spy(ON_PRESS)).toHaveBeenCalledWith("Execute Query");
    }
}
