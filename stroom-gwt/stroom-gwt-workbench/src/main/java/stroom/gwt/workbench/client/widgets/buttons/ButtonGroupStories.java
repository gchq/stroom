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
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonGroupFocus;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories for groups of icon buttons ([Toolbar]), measuring how a keyboard user crosses them.
///
/// Buttons that sit next to each other are one tab stop, a toolbar whose buttons the arrow keys
/// move between (the WAI-ARIA toolbar pattern, [ButtonGroupFocus]). The plays measure the tab
/// stops each run of buttons costs ([ButtonRuns]) and the region's tab stops in order
/// ([TabStops]), and use the keys.
public final class ButtonGroupStories {

    private static final String REGION = "button-group-region";

    private ButtonGroupStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Buttons/ButtonGroup", ButtonGroupStories.class)
                .layout(StoryLayout.CENTERED)
                // One button on its own: a plain button, not a toolbar
                .story("SingleButton", context -> region(add()))
                .withPlay(play -> {
                    final Query region = play.getByTestId(REGION);
                    play.expect(ButtonRuns.of(region)).toEqual(List.of("Add: 1 tab stop"));
                    play.expect(play.querySelector(".toolbar")).not().toHaveAttribute("role");
                    play.tab();
                    play.expect(play.getByRole("button", "Add")).toHaveFocus();
                })
                // Three buttons side by side: one tab stop, a toolbar the arrow keys move around
                .story("ThreeButtons", context -> region(addEditDelete()))
                .withPlay(play -> {
                    final Query region = play.getByTestId(REGION);
                    final Query add = play.getByRole("button", "Add");
                    final Query edit = play.getByRole("button", "Edit");
                    final Query delete = play.getByRole("button", "Delete");
                    play.expect(ButtonRuns.of(region)).toEqual(List.of("Add, Edit, Delete: 1 tab stop"));
                    play.expect(TabStops.names(region)).toEqual(List.of("Add"));
                    play.expect(play.getByRole("toolbar")).toBeInTheDocument();
                    play.tab();
                    play.expect(add).toHaveFocus();
                    play.keyboard("{ArrowRight}");
                    play.expect(edit).toHaveFocus();
                    play.keyboard("{ArrowRight}");
                    play.expect(delete).toHaveFocus();
                    // The ends wrap round
                    play.keyboard("{ArrowRight}");
                    play.expect(add).toHaveFocus();
                    play.keyboard("{ArrowLeft}");
                    play.expect(delete).toHaveFocus();
                    play.keyboard("{Home}");
                    play.expect(add).toHaveFocus();
                    play.keyboard("{End}");
                    play.expect(delete).toHaveFocus();
                    // Tab leaves the group, and coming back returns to the button last used
                    play.tab();
                    play.expect(delete).not().toHaveFocus();
                    play.expect(TabStops.names(region)).toEqual(List.of("Delete"));
                    play.tab(true);
                    play.expect(delete).toHaveFocus();
                })
                // A disabled button (aria-disabled) can still be reached, so its name is found
                .story("WithDisabled", context -> {
                    final Toolbar toolbar = addEditDelete();
                    buttonTitled(toolbar, "Edit").setEnabled(false);
                    return region(toolbar);
                })
                .withPlay(play -> {
                    final Query region = play.getByTestId(REGION);
                    final Query edit = play.getByRole("button", "Edit");
                    play.expect(edit).toHaveAttribute("aria-disabled", "true");
                    play.expect(ButtonRuns.of(region)).toEqual(List.of("Add, Edit, Delete: 1 tab stop"));
                    play.tab();
                    play.keyboard("{ArrowRight}");
                    play.expect(edit).toHaveFocus();
                })
                // A toggle button (ToolbarToggleButton) beside an ordinary one
                .story("WithToggle", context -> {
                    final Toolbar toolbar = add();
                    toolbar.toggle(SvgImage.EXPAND_DOWN, "Expand", null);
                    return region(toolbar);
                })
                .withPlay(play -> {
                    final Query region = play.getByTestId(REGION);
                    play.expect(ButtonRuns.of(region)).toEqual(List.of("Add, Expand: 1 tab stop"));
                    play.expect(play.getByRole("button", "Expand")).toHaveAttribute("aria-pressed", "false");
                })
                // A hidden button is neither seen nor reached by the arrow keys
                .story("WithHidden", context -> {
                    final Toolbar toolbar = addEditDelete();
                    buttonTitled(toolbar, "Edit").setVisible(false);
                    return region(toolbar);
                })
                .withPlay(play -> {
                    final Query region = play.getByTestId(REGION);
                    play.expect(ButtonRuns.of(region)).toEqual(List.of("Add, Delete: 1 tab stop"));
                    play.tab();
                    play.keyboard("{ArrowRight}");
                    play.expect(play.getByRole("button", "Delete")).toHaveFocus();
                })
                // A button column: Up and Down move between the buttons
                .story("Vertical", context -> {
                    final Toolbar toolbar = addDelete();
                    toolbar.setVertical(true);
                    return region(toolbar);
                })
                .withPlay(play -> {
                    play.expect(play.getByRole("toolbar")).toHaveAttribute("aria-orientation", "vertical");
                    play.tab();
                    play.keyboard("{ArrowDown}");
                    play.expect(play.getByRole("button", "Delete")).toHaveFocus();
                    play.keyboard("{ArrowUp}");
                    play.expect(play.getByRole("button", "Add")).toHaveFocus();
                })
                // Two toolbars side by side (as a document tab's Save buttons sit beside its editor's)
                // look like one group, but are still a tab stop each
                .story("AdjacentPanels", context -> region(saveSaveAs(), addDelete()))
                .withPlay(play -> {
                    final Query region = play.getByTestId(REGION);
                    play.expect(ButtonRuns.of(region)).toEqual(List.of("Save, Save As, Add, Delete: 2 tab stops"));
                })
                // The same toolbars made one group, by making the element holding them a group: the
                // toolbars leave their buttons to it, so the arrow keys cross from one to the other
                .story("MergedPanels", context -> {
                    final FlowPanel row = region(saveSaveAs(), addDelete());
                    ButtonGroupFocus.install(row.getElement());
                    return row;
                })
                .withPlay(play -> {
                    final Query region = play.getByTestId(REGION);
                    play.expect(ButtonRuns.of(region)).toEqual(List.of("Save, Save As, Add, Delete: 1 tab stop"));
                    play.expect(play.getAllByRole("toolbar")).toHaveLength(1);
                    play.tab();
                    play.keyboard("{ArrowRight}");
                    play.keyboard("{ArrowRight}");
                    play.expect(play.getByRole("button", "Add")).toHaveFocus();
                })
                // Toolbars at either end of a row are separate groups, a tab stop each
                .story("SeparatedPanels", context -> {
                    final FlowPanel row = region(addDelete(), refresh());
                    row.getElement().getStyle().setProperty("justifyContent", "space-between");
                    row.getElement().getStyle().setProperty("width", "300px");
                    return row;
                })
                .withPlay(play -> {
                    final Query region = play.getByTestId(REGION);
                    play.expect(ButtonRuns.of(region)).toEqual(List.of(
                            "Add, Delete: 1 tab stop",
                            "Refresh: 1 tab stop"));
                });
    }

    // The toolbars the stories show. Their buttons start enabled and do nothing when pressed.

    private static Toolbar add() {
        final Toolbar toolbar = new Toolbar("List actions");
        toolbar.button(SvgImage.ADD, "Add", null);
        return toolbar;
    }

    // A list's Add, Edit and Delete buttons
    private static Toolbar addEditDelete() {
        final Toolbar toolbar = new Toolbar("List actions");
        toolbar.button(SvgImage.ADD, "Add", null);
        toolbar.button(SvgImage.EDIT, "Edit", null);
        toolbar.button(SvgImage.DELETE, "Delete", null);
        return toolbar;
    }

    private static Toolbar addDelete() {
        final Toolbar toolbar = new Toolbar("List actions");
        toolbar.button(SvgImage.ADD, "Add", null);
        toolbar.button(SvgImage.DELETE, "Delete", null);
        return toolbar;
    }

    private static Toolbar saveSaveAs() {
        final Toolbar toolbar = new Toolbar("Document actions");
        toolbar.button(SvgImage.SAVE, "Save", null);
        toolbar.button(SvgImage.SAVEAS, "Save As", null);
        return toolbar;
    }

    private static Toolbar refresh() {
        final Toolbar toolbar = new Toolbar("Paging");
        toolbar.button(SvgImage.REFRESH, "Refresh", null);
        return toolbar;
    }

    private static ToolbarButton buttonTitled(final Toolbar toolbar, final String title) {
        for (final Widget widget : toolbar) {
            if (title.equals(widget.getTitle())) {
                return (ToolbarButton) widget;
            }
        }
        throw new IllegalArgumentException("No button titled " + title);
    }

    // The toolbars in a row, marked so the plays can measure them
    private static FlowPanel region(final Widget... toolbars) {
        final FlowPanel row = new FlowPanel();
        row.getElement().setAttribute("data-testid", REGION);
        row.getElement().getStyle().setProperty("display", "flex");
        for (final Widget toolbar : toolbars) {
            row.add(toolbar);
        }
        return row;
    }
}
