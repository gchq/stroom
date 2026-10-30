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

import stroom.gwt.workbench.client.a11y.ButtonRuns;
import stroom.gwt.workbench.client.a11y.TabStops;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarRow;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories for [Toolbar], a named group of icon buttons that is one tab stop, and [ToolbarRow],
/// which spreads toolbars across a row, made in Java and declared in a UiBinder template.
public final class ToolbarStories {

    // Spy names
    private static final String ON_SAVE = "onSave";
    private static final String ON_SAVE_AS = "onSaveAs";
    private static final String ON_FILTER = "onFilter";
    private static final String ON_FIRST = "onFirst";
    private static final String ON_REPLACED = "onReplaced";

    private static final String REGION = "toolbar-region";

    private ToolbarStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Buttons/Toolbar", ToolbarStories.class)
                .layout(StoryLayout.CENTERED)
                // Buttons, a separator and a toggle, made in Java
                .story("Default", context -> region(documentActions(context)))
                .withPlay(play -> {
                    final Query save = play.getByRole("button", "Save");
                    final Query saveAs = play.getByRole("button", "Save As");
                    final Query filter = play.getByRole("button", "Show Filters");
                    // A named toolbar, one tab stop
                    play.expect(play.getByRole("toolbar", "Document actions")).toBeInTheDocument();
                    play.expect(TabStops.names(play.getByTestId(REGION))).toEqual(List.of("Save"));
                    // Enter and Space press the button with the focus
                    play.tab();
                    play.expect(save).toHaveFocus();
                    play.keyboard("{Enter}");
                    play.expect(play.spy(ON_SAVE)).toHaveBeenCalledTimes(1);
                    play.keyboard("{ArrowRight}");
                    play.expect(saveAs).toHaveFocus();
                    play.keyboard(" ");
                    play.expect(play.spy(ON_SAVE_AS)).toHaveBeenCalledTimes(1);
                    // The arrow keys cross the separator
                    play.keyboard("{ArrowRight}");
                    play.expect(filter).toHaveFocus();
                    // The toggle says it is a toggle before it is pressed, and is told its new state
                    play.expect(filter).toHaveAttribute("aria-pressed", "false");
                    play.keyboard("{Enter}");
                    play.expect(play.spy(ON_FILTER)).toHaveBeenCalledWith(true);
                    play.expect(filter).toHaveAttribute("aria-pressed", "true");
                    play.click(filter);
                    play.expect(play.spy(ON_FILTER)).toHaveBeenCalledWith(false);
                    play.click(save);
                    play.expect(play.spy(ON_SAVE)).toHaveBeenCalledTimes(2);
                })
                // A disabled button can still be reached, but does nothing
                .story("DisabledButton", context -> {
                    final Toolbar toolbar = documentActions(context);
                    findButton(toolbar, "Save").setEnabled(false);
                    return region(toolbar);
                })
                .withPlay(play -> {
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    play.tab();
                    play.expect(save).toHaveFocus();
                    play.keyboard("{Enter}");
                    play.click(save);
                    play.expect(play.spy(ON_SAVE)).not().toHaveBeenCalled();
                })
                // One button on its own is a plain button: no toolbar role, so no name on the group
                .story("SingleButton", context -> {
                    final Toolbar toolbar = new Toolbar("Document actions");
                    toolbar.button(SvgImage.SAVE, "Save", context.fn(ON_SAVE)::call);
                    return region(toolbar);
                })
                .withPlay(play -> {
                    final Query toolbar = play.querySelector(".toolbar");
                    play.expect(toolbar).not().toHaveAttribute("role");
                    play.expect(toolbar).not().toHaveAttribute("aria-label");
                    play.expect(ButtonRuns.of(play.getByTestId(REGION))).toEqual(List.of("Save: 1 tab stop"));
                })
                // Setting a button's callback again replaces it, rather than adding a second
                .story("ReplacedCallback", context -> {
                    final Toolbar toolbar = new Toolbar("Document actions");
                    final ToolbarButton save = toolbar.button(SvgImage.SAVE, "Save", context.fn(ON_SAVE)::call);
                    save.setOnClick(context.fn(ON_REPLACED)::call);
                    return region(toolbar);
                })
                .withPlay(play -> {
                    play.click(play.getByRole("button", "Save"));
                    play.expect(play.spy(ON_SAVE)).not().toHaveBeenCalled();
                    play.expect(play.spy(ON_REPLACED)).toHaveBeenCalledTimes(1);
                })
                // The focused button's name is shown beside it, as a title tooltip only shows on hover
                .story("FocusTooltip", context -> region(documentActions(context)))
                .withPlay(play -> {
                    final Query tooltip = play.screen().querySelector(".focus-tooltip");
                    play.tab();
                    play.expect(tooltip).toBeVisible();
                    play.expect(tooltip).toHaveTextContent("Save");
                    // It is for sight only: screen readers hear the button's name already
                    play.expect(tooltip).toHaveAttribute("aria-hidden", "true");
                    play.keyboard("{ArrowRight}");
                    play.expect(tooltip).toHaveTextContent("Save As");
                    // Escape dismisses it, leaving the focus where it is
                    play.keyboard("{Escape}");
                    play.expect(tooltip).not().toBeVisible();
                    play.expect(play.getByRole("button", "Save As")).toHaveFocus();
                    // It goes when the focus leaves
                    play.keyboard("{ArrowRight}");
                    play.expect(tooltip).toBeVisible();
                    play.tab();
                    play.expect(tooltip).not().toBeVisible();
                })
                // A button that hides itself when pressed hands the focus to the button now in its place
                .story("HiddenWhileFocused", context -> {
                    final Toolbar toolbar = new Toolbar("List actions");
                    toolbar.button(SvgImage.ADD, "Add", () -> {
                    });
                    final ToolbarButton edit = toolbar.button(SvgImage.EDIT, "Edit", () -> {
                    });
                    edit.setOnClick(() -> edit.setVisible(false));
                    final ToolbarButton delete = toolbar.button(SvgImage.DELETE, "Delete", () -> {
                    });
                    delete.setOnClick(() -> delete.setVisible(false));
                    return region(toolbar);
                })
                .withPlay(play -> {
                    final Query region = play.getByTestId(REGION);
                    play.tab();
                    play.keyboard("{ArrowRight}");
                    play.keyboard("{Enter}");
                    // Edit has gone: the next button has the focus and is the tab stop
                    play.waitFor(() -> play.expect(play.getByRole("button", "Delete")).toHaveFocus());
                    play.expect(TabStops.names(region)).toEqual(List.of("Delete"));
                    // The last button goes: the one before it takes the focus
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(play.getByRole("button", "Add")).toHaveFocus());
                })
                // A button removed while it has the focus also hands it on
                .story("RemovedWhileFocused", context -> {
                    final Toolbar toolbar = new Toolbar("List actions");
                    toolbar.button(SvgImage.ADD, "Add", () -> {
                    });
                    final ToolbarButton edit = toolbar.button(SvgImage.EDIT, "Edit", () -> {
                    });
                    edit.setOnClick(() -> toolbar.remove(edit));
                    toolbar.button(SvgImage.DELETE, "Delete", () -> {
                    });
                    return region(toolbar);
                })
                .withPlay(play -> {
                    play.tab();
                    play.keyboard("{ArrowRight}");
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(play.getByRole("button", "Delete")).toHaveFocus());
                })
                // A button that hides itself and moves the focus on purpose (as dashboard Maximise
                // focuses Restore) keeps that choice
                .story("HiddenFocusMovedOnPurpose", context -> {
                    final Toolbar toolbar = new Toolbar("List actions");
                    final ToolbarButton add = toolbar.button(SvgImage.ADD, "Add", () -> {
                    });
                    final ToolbarButton edit = toolbar.button(SvgImage.EDIT, "Edit", () -> {
                    });
                    edit.setOnClick(() -> {
                        edit.setVisible(false);
                        add.setFocus(true);
                    });
                    toolbar.button(SvgImage.DELETE, "Delete", () -> {
                    });
                    return region(toolbar);
                })
                .withPlay(play -> {
                    play.tab();
                    play.keyboard("{ArrowRight}");
                    play.keyboard("{Enter}");
                    play.expect(play.getByRole("button", "Add")).toHaveFocus();
                    // Still after the group has caught up with the change
                    play.sleep(100);
                    play.expect(play.getByRole("button", "Add")).toHaveFocus();
                })
                // Driven by the test runner with real key presses and mouse clicks (its real input
                // check, test-runner/lib/real-input/buttons.mjs), which a play can't make
                .story("RealInput", context -> region(documentActions(context)))
                // A row of two toolbars, at either end: a tab stop each
                .story("Row", context -> {
                    final Toolbar paging = new Toolbar("Paging");
                    paging.button(SvgImage.FAST_BACKWARD, "First", context.fn(ON_FIRST)::call);
                    paging.button(SvgImage.FAST_FORWARD, "Last", () -> {
                    });
                    final ToolbarRow row = new ToolbarRow();
                    row.add(documentActions(context));
                    row.add(paging);
                    return region(row);
                })
                .withPlay(ToolbarStories::playRow)
                // The same row declared in a UiBinder template (ToolbarTemplate.ui.xml), its icons set
                // by the svg attribute
                .story("Template", context -> {
                    final ToolbarTemplate template = new ToolbarTemplate();
                    template.setOnSave(context.fn(ON_SAVE)::call);
                    template.setOnFilter(context.fn(ON_FILTER)::call);
                    template.setOnFirst(context.fn(ON_FIRST)::call);
                    return region(template);
                })
                .withPlay(play -> {
                    // svg="SAVE" set the icon, and the template's separator is in the first toolbar
                    play.expect(play.within(play.getByRole("button", "Save")).querySelector("svg")).toBeInTheDocument();
                    play.expect(play.within(play.getByRole("toolbar", "Document actions")).getByRole("separator"))
                            .toBeInTheDocument();
                    playRow(play);
                    play.click(play.getByRole("button", "Save"));
                    play.expect(play.spy(ON_SAVE)).toHaveBeenCalledTimes(1);
                    play.click(play.getByRole("button", "Show Filters"));
                    play.expect(play.spy(ON_FILTER)).toHaveBeenCalledWith(true);
                });
    }

    // Two named toolbars, a tab stop each; Tab moves from one to the other
    private static void playRow(final Play play) {
        final Query region = play.getByTestId(REGION);
        play.expect(play.getByRole("toolbar", "Document actions")).toBeInTheDocument();
        play.expect(play.getByRole("toolbar", "Paging")).toBeInTheDocument();
        play.expect(ButtonRuns.of(region)).toEqual(List.of(
                "Save, Save As, Show Filters: 1 tab stop",
                "First, Last: 1 tab stop"));
        play.expect(TabStops.names(region)).toEqual(List.of("Save", "First"));
        play.tab();
        play.tab();
        play.expect(play.getByRole("button", "First")).toHaveFocus();
        play.keyboard("{Enter}");
        play.expect(play.spy(ON_FIRST)).toHaveBeenCalledTimes(1);
    }

    // Save, Save As, a separator and a toggle, as a document's toolbar might be
    private static Toolbar documentActions(final StoryContext context) {
        final Spy onSave = context.fn(ON_SAVE);
        final Spy onSaveAs = context.fn(ON_SAVE_AS);
        final Spy onFilter = context.fn(ON_FILTER);
        final Toolbar toolbar = new Toolbar("Document actions");
        toolbar.button(SvgImage.SAVE, "Save", onSave::call);
        toolbar.button(SvgImage.SAVEAS, "Save As", onSaveAs::call);
        toolbar.separator();
        toolbar.toggle(SvgImage.FILTER, "Show Filters", onFilter::call);
        return toolbar;
    }

    private static ToolbarButton findButton(final Toolbar toolbar, final String name) {
        for (final Widget widget : toolbar) {
            if (name.equals(widget.getTitle())) {
                return (ToolbarButton) widget;
            }
        }
        throw new IllegalArgumentException("No button named " + name);
    }

    // The story's widget, 400px wide (so a row's toolbars are apart), marked for the plays
    private static FlowPanel region(final Widget widget) {
        final FlowPanel region = new FlowPanel();
        region.getElement().setAttribute("data-testid", REGION);
        region.getElement().getStyle().setProperty("width", "400px");
        region.add(widget);
        return region;
    }
}
