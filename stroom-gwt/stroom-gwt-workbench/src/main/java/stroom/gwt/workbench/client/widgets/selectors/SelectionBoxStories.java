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

import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.item.client.BaseSelectionBox;
import stroom.item.client.SelectionBox;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.item.client.SimpleSelectionListModel;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// Stories for Stroom's [SelectionBox]: a read only box that opens a list of items (Stroom's
/// `SelectionPopup`), with a quick filter over 10 items and a pager over 100.
public final class SelectionBoxStories {

    private static final String ON_CHANGE = "onChange";
    private static final String ON_WINDOW_ESCAPE = "onWindowEscape";
    // The render box (which paints the value) is covered by the box's transparent text box, which
    // takes the clicks (as it does for a user) and opens the popup; the render box has no click
    // handler of its own
    private static final String OPENER = ".selection-box__input";
    private static final String POPUP = ".selection-popup";
    private static final int KEY_F9 = 120;
    // The keys that open the list, as the play function types them
    private static final List<String> OPENING_KEYS =
            List.of("{Enter}", "{ArrowDown}", "{ArrowUp}", "{Alt>}{ArrowDown}{/Alt}", " ");
    // A short list (no quick filter), with two items starting with D
    private static final List<String> NAMES = List.of("Alpha", "Bravo", "Charlie", "Delta", "Dingo", "Echo");

    private SelectionBoxStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Selectors/SelectionBox", SelectionBoxStories.class)
                .layout(StoryLayout.CENTERED)
                // Basic controlled selector with a non-select ("clear") row
                .story("Basic", context -> {
                    final SelectionBox<String> box = namedBox(context, List.of("Alpha", "Beta", "Gamma", "Delta"));
                    box.setNonSelectString("(none)");
                    return withEcho(box);
                })
                // Disabled - non-interactive with a preset value
                .story("Disabled", context -> {
                    final SelectionBox<String> box = namedBox(context, List.of("One", "Two"));
                    box.setValue("One");
                    box.setEnabled(false);
                    return minWidth(box);
                })
                // Disabling the box while its list is open closes the list (it once stayed open, so
                // the value could still be changed)
                .story("DisabledWhileOpen", context -> {
                    final SelectionBox<String> box = namedBox(context, List.of("One", "Two"));
                    // Stands in for a screen that disables the box while its list is open (e.g.
                    // when it learns that the document is read only): F9 disables it
                    final HandlerRegistration registration = Event.addNativePreviewHandler(event -> {
                        if (event.getTypeInt() == Event.ONKEYDOWN
                                && event.getNativeEvent().getKeyCode() == KEY_F9) {
                            box.setEnabled(false);
                        }
                    });
                    context.addCleanUp(registration::removeHandler);
                    return minWidth(box);
                })
                .withPlay(play -> {
                    play.click(play.querySelector(OPENER));
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
                    play.keyboard("{F9}");
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).toBeNull());
                    play.expect(play.querySelector(OPENER)).toBeDisabled();
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })
                // The WAI-ARIA combobox's keys open the list: Enter, Down, Up, Alt+Down and (as the box
                // can't be typed in) Space; Escape closes it and gives the focus back to the box
                .story("KeysOpenTheList", context -> named(selectionBox(context, NAMES)))
                .withPlay(play -> {
                    final Query box = play.getByRole("combobox");
                    play.tab();
                    play.expect(box).toHaveFocus();
                    for (final String keys : OPENING_KEYS) {
                        play.keyboard(keys);
                        play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
                        play.expect(box).toHaveAttribute("aria-expanded", "true");
                        play.keyboard("{Escape}");
                        play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).toBeNull());
                        play.expect(box).toHaveAttribute("aria-expanded", "false");
                        play.expect(box).toHaveFocus();
                    }
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })
                // A right-click on an option shows no menu: the list's options are to choose, not a
                // table. The grid under them had a private bus, which hid its menu (Copy, Export
                // Table, Ask Stroom AI About); since the grid asks the page for it, the list turns it
                // off
                .story("NoMenuOnTheOptions", context -> {
                    StoryPopups.create(context).withPageRequests();
                    return named(selectionBox(context, NAMES));
                })
                .withPlay(play -> {
                    play.tab();
                    play.keyboard("{ArrowDown}");
                    final Query option = play.screen().getByRole("option", "Alpha");
                    play.waitFor(() -> play.expect(option).toHaveFocus());
                    play.rightClick(option);
                    // A menu would be shown straight away
                    play.sleep(300);
                    play.expect(play.screen().queryByRole("menu")).toBeNull();
                    play.expect(option).toBeInTheDocument();
                })
                // In the list, Alt+Up chooses the option with the focus and closes the list
                .story("AltUpChoosesTheOption", context -> named(selectionBox(context, NAMES)))
                .withPlay(play -> {
                    final Query box = play.getByRole("combobox");
                    play.tab();
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(play.screen().getByRole("option", "Alpha")).toHaveFocus());
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(play.screen().getByRole("option", "Bravo")).toHaveFocus());
                    play.keyboard("{Alt>}{ArrowUp}{/Alt}");
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).toBeNull());
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledWith("Bravo");
                    play.expect(box).toHaveValue("Bravo");
                    play.expect(box).toHaveFocus();
                })
                // In a box that can't be typed in, Home and End open the list at its first and last
                // option (WAI-ARIA combobox), whatever the value
                .story("HomeAndEndOpenAtTheEnds", context -> {
                    final SelectionBox<String> box = selectionBox(context, NAMES);
                    box.setValue("Charlie");
                    return named(box);
                })
                .withPlay(play -> {
                    final Query box = play.getByRole("combobox");
                    play.tab();
                    play.keyboard("{End}");
                    play.waitFor(() -> play.expect(play.screen().getByRole("option", "Echo")).toHaveFocus());
                    play.expect(box).toHaveAttribute("aria-expanded", "true");
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).toBeNull());
                    play.waitFor(() -> play.expect(box).toHaveFocus());
                    play.keyboard("{Home}");
                    play.waitFor(() -> play.expect(play.screen().getByRole("option", "Alpha")).toHaveFocus());
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).toBeNull());
                    play.expect(box).toHaveValue("Charlie");
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })
                // In a list with no quick filter, typing a character opens the list and goes to the
                // first option that starts with it; typing it again goes to the next
                .story("TypingJumpsToAnOption", context -> named(selectionBox(context, NAMES)))
                .withPlay(play -> {
                    play.tab();
                    play.keyboard("d");
                    play.waitFor(() -> play.expect(play.screen().getByRole("option", "Delta")).toHaveFocus());
                    play.keyboard("d");
                    play.waitFor(() -> play.expect(play.screen().getByRole("option", "Dingo")).toHaveFocus());
                    // And round to the first again
                    play.keyboard("d");
                    play.waitFor(() -> play.expect(play.screen().getByRole("option", "Delta")).toHaveFocus());
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).toBeNull());
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalledWith("Delta");
                })
                // In a list with a quick filter (over 10 items), what is typed in the closed box is
                // the filter, applied at once, and typing carries on in it
                .story("TypingFiltersALongList", context -> named(selectionBox(context, numbered(150))))
                .withPlay(play -> {
                    play.tab();
                    play.keyboard("15");
                    final Query filter = play.screen().querySelector(".selection-list__filter .quick-filter__input");
                    play.waitFor(() -> play.expect(filter).toHaveValue("15"));
                    play.expect(filter).toHaveFocus();
                    // Named for the box it filters, not only by its placeholder
                    play.expect(filter).toHaveAttribute("aria-label", "Filter Name");
                    play.waitFor(() -> play.expect(play.screen().getByRole("option", "Item 150")).toBeInTheDocument());
                    play.expect(play.screen().queryByRole("option", "Item 2")).toBeNull();
                })
                // Tab in the open list closes it, leaving the value, and goes on to what follows the
                // box; Shift+Tab closes it and goes back to the box
                .story("TabClosesTheList", context -> {
                    final FlowPanel panel = named(selectionBox(context, NAMES));
                    final TextBox after = new TextBox();
                    AccessibleName.set(after, "After");
                    panel.add(after);
                    return panel;
                })
                .withPlay(play -> {
                    final Query box = play.getByRole("combobox", "Name");
                    play.tab();
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(play.screen().getByRole("option", "Alpha")).toHaveFocus());
                    play.tab();
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).toBeNull());
                    play.expect(play.getByRole("textbox", "After")).toHaveFocus();
                    play.expect(box).toHaveAttribute("aria-expanded", "false");
                    play.tab(true);
                    play.expect(box).toHaveFocus();
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(play.screen().getByRole("option", "Alpha")).toHaveFocus());
                    play.tab(true);
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).toBeNull());
                    play.expect(box).toHaveFocus();
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })
                // The list is named for the box that opened it, here by the box's form group label
                .story("TheListIsNamedForTheBox", context -> {
                    final FormGroup formGroup = new FormGroup();
                    formGroup.setIdentity("colour");
                    formGroup.setLabel("Colour");
                    formGroup.add(selectionBox(context, NAMES));
                    return minWidth(formGroup);
                })
                .withPlay(play -> {
                    final Query box = play.getByRole("combobox", "Colour");
                    play.tab();
                    play.expect(box).toHaveFocus();
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(play.screen().getByRole("listbox", "Colour")).toBeInTheDocument());
                    play.keyboard("{Escape}");
                })
                // A value too long for the box stops clear of its drop-down arrow
                .story("LongValue", context -> {
                    final String longValue = "A value much too long to fit in the box beside its arrow";
                    final SelectionBox<String> box = selectionBox(context, List.of("Short", longValue));
                    box.setValue(longValue);
                    final FlowPanel panel = named(box);
                    panel.setWidth("220px");
                    return panel;
                })
                // Long list - shows the quick filter and (over 100 items) the pager
                .story("LongList", context -> {
                    final List<String> items = new ArrayList<>();
                    for (int i = 1; i <= 150; i++) {
                        items.add("Item " + i);
                    }
                    return withEcho(namedBox(context, items));
                })
                // A box backed by a server-side model (e.g. Stroom's DynamicFieldSelectionListModel)
                // always shows the quick filter and pager, however few items there are
                .story("ModelBackedAlwaysShowsFilterAndPager", context -> {
                    final BaseSelectionBox<String, SimpleSelectionItemWrapper<String>> box =
                            new BaseSelectionBox<>();
                    final SimpleSelectionListModel<String> model = new ModelBackedListModel();
                    model.addItem("Only one");
                    box.setModel(model);
                    AccessibleName.set(box, "Name");
                    final Spy onChange = context.fn(ON_CHANGE);
                    box.addValueChangeHandler(event -> onChange.call(event.getValue()));
                    return minWidth(box);
                })
                .withPlay(play -> {
                    play.click(play.querySelector(OPENER));
                    // Stroom's popup is on the page's body, not in the canvas
                    play.waitFor(() -> play.expect(play.screen().querySelector(".selection-list .pager"))
                            .toBeVisible());
                    play.expect(play.screen().querySelector(".selection-list__filter .quick-filter__input"))
                            .not().toBeNull();
                    // The pager is Stroom's PagerPanel, so it reads "N to M of T"
                    play.expect(play.screen().getByText(TextMatch.containingIgnoreCase("to"), ".pager *"))
                            .toBeInTheDocument();
                })
                // The default: a short list shows no pager
                .story("ShortListHasNoPager", context -> namedBox(context, List.of("One", "Two")))
                .withPlay(play -> {
                    play.click(play.querySelector(OPENER));
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
                    // Stroom hides the pager container rather than omitting it
                    play.expect(play.screen().querySelector(".selection-list .pager")).not().toBeVisible();
                })
                // The popup's markup is GWT's (SelectionPopup > SimplePopupLayout > SelectionList)
                .story("PopupUsesGwtMarkup", context -> namedBox(context, List.of("One", "Two")))
                .withPlay(SelectionBoxStories::playPopupMarkup)
                // Escape in an open list closes the list
                .story("EscapeClosesOnlyTheList", context -> {
                    final Spy onWindowEscape = context.fn(ON_WINDOW_ESCAPE);
                    final JavaScriptObject listener = addWindowKeyDownListener(onWindowEscape);
                    context.addCleanUp(() -> removeWindowKeyDownListener(listener));
                    return namedBox(context, List.of("One", "Two"));
                })
                .withPlay(SelectionBoxStories::playEscape);
    }

    private static void playPopupMarkup(final Play play) {
        play.click(play.querySelector(OPENER));
        play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
        final Play popup = play.screen().within(play.screen().querySelector(POPUP));
        // The look Stroom gives every popup (SimplePopupLayout): one element, in GWT's popupContent
        play.expect(popup.querySelector(".popupContent > .popup > .selection-list")).not().toBeNull();
        // SelectionList's own three children, in order
        final Query list = popup.querySelector(".selection-list.dock-container-vertical");
        play.expect(list).not().toBeNull();
        final Play inList = play.screen().within(list);
        play.expect(inList.querySelector(":scope > .selection-list__filter")).not().toBeNull();
        play.expect(inList.querySelector(":scope > .selection-list__path")).not().toBeNull();
        play.expect(inList.querySelector(":scope > .selection-list__options")).not().toBeNull();
        // PagerPanel: the pager container above the list container
        play.expect(popup.querySelector(".selection-list__options .pager")).not().toBeNull();
        play.expect(popup.querySelector(".listContainer")).not().toBeNull();
        // The rows are the data grid's, as a list's: a listbox of options, each holding its cell
        play.expect(popup.querySelector(".data-grid.data-grid--list [role='listbox']")).not().toBeNull();
        final Query row = popup.querySelector("[role='option'].data-grid__row");
        play.expect(row).not().toBeNull();
        final Play inRow = play.screen().within(row);
        play.expect(inRow.querySelector(".data-grid__cell")).not().toBeNull();
        play.expect(inRow.querySelector(".explorer-cell .selectionItemCell-text")).toHaveTextContent("One");
    }

    private static void playEscape(final Play play) {
        play.click(play.querySelector(OPENER));
        play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
        // Focus deliberately outside the popup
        final Query textBox = play.querySelector(".selection-box__input");
        play.run("focus the box's text box", () -> textBox.element().get().focus());
        play.keyboard("{Escape}");
        // Escape closes the list wherever the focus is (it once only did with the focus in the
        // list's quick filter or cell table)
        play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).toBeNull());

        // With the focus in the list, Escape closes the list too
        play.click(play.querySelector(OPENER));
        play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
        final Query focusable = play.screen().querySelector(POPUP + " [tabindex]");
        play.run("focus the list", () -> focusable.element().get().focus());
        play.keyboard("{Escape}");
        play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).toBeNull());
        // And the key is stopped, so the window doesn't see it (it once did)
        play.expect(play.spy(ON_WINDOW_ESCAPE)).not().toHaveBeenCalled();
    }

    private static SelectionBox<String> selectionBox(final StoryContext context, final List<String> items) {
        final SelectionBox<String> box = new SelectionBox<>();
        box.addItems(items);
        final Spy onChange = context.fn(ON_CHANGE);
        box.addValueChangeHandler(event -> onChange.call(event.getValue()));
        return box;
    }

    /// A box named as a form group's label names one on Stroom's screens, for a story that shows it
    /// on its own.
    private static SelectionBox<String> namedBox(final StoryContext context, final List<String> items) {
        final SelectionBox<String> box = selectionBox(context, items);
        AccessibleName.set(box, "Choice");
        return box;
    }

    /// The box, at least 220px wide, named as a label would name it on Stroom's screens.
    private static FlowPanel named(final SelectionBox<String> box) {
        AccessibleName.set(box, "Name");
        return minWidth(box);
    }

    /// @return `Item 1` to `Item n`.
    private static List<String> numbered(final int count) {
        final List<String> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            items.add("Item " + i);
        }
        return items;
    }

    /// The box, at least 220px wide, with a `Selected: ...` echo below.
    private static Widget withEcho(final SelectionBox<String> box) {
        final HTML echo = new HTML();
        echo.getElement().getStyle().setProperty("marginTop", "8px");
        echo.getElement().getStyle().setProperty("fontSize", "12px");
        echo.setText("Selected: none");
        box.addValueChangeHandler(event -> echo.setText("Selected: " + (event.getValue() == null
                ? "none"
                : event.getValue())));
        final FlowPanel panel = minWidth(box);
        panel.add(echo);
        return panel;
    }

    /// Equivalent of `<div style={{minWidth: 220}}>`.
    private static FlowPanel minWidth(final Widget widget) {
        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("minWidth", "220px");
        panel.add(widget);
        return panel;
    }

    /// Records every key pressed that reaches the window, with a `keydown` listener on it.
    private static native JavaScriptObject addWindowKeyDownListener(Spy spy) /*-{
        var listener = function (event) {
            @stroom.gwt.workbench.client.widgets.selectors.SelectionBoxStories::onWindowKeyDown(*)(spy, event.key);
        };
        $wnd.addEventListener('keydown', listener);
        return listener;
    }-*/;

    private static void onWindowKeyDown(final Spy spy, final String key) {
        spy.call(key);
    }

    private static native void removeWindowKeyDownListener(JavaScriptObject listener) /*-{
        $wnd.removeEventListener('keydown', listener);
    }-*/;


    // --------------------------------------------------------------------------------


    /// A list model whose quick filter and pager are always shown, as a server-side model's are
    /// (e.g. `DynamicFieldSelectionListModel`).
    private static final class ModelBackedListModel extends SimpleSelectionListModel<String> {

        @Override
        public boolean displayFilter() {
            return true;
        }

        @Override
        public boolean displayPager() {
            return true;
        }
    }
}
