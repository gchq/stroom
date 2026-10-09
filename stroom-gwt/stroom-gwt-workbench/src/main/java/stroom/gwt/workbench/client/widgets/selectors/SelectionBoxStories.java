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
    private static final String OPENER = ".SelectionBox-textBox";
    private static final String POPUP = ".SelectionPopup";
    private static final int KEY_F9 = 120;

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
                    final SelectionBox<String> box = selectionBox(context, List.of("Alpha", "Beta", "Gamma", "Delta"));
                    box.setNonSelectString("(none)");
                    return withEcho(box);
                })
                // Disabled - non-interactive with a preset value
                .story("Disabled", context -> {
                    final SelectionBox<String> box = selectionBox(context, List.of("One", "Two"));
                    box.setValue("One");
                    box.setEnabled(false);
                    return minWidth(box);
                })
                // Disabling the box while its list is open closes the list (it once stayed open, so
                // the value could still be changed)
                .story("DisabledWhileOpen", context -> {
                    final SelectionBox<String> box = selectionBox(context, List.of("One", "Two"));
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
                // Long list - shows the quick filter and (over 100 items) the pager
                .story("LongList", context -> {
                    final List<String> items = new ArrayList<>();
                    for (int i = 1; i <= 150; i++) {
                        items.add("Item " + i);
                    }
                    return withEcho(selectionBox(context, items));
                })
                // A box backed by a server-side model (e.g. Stroom's DynamicFieldSelectionListModel)
                // always shows the quick filter and pager, however few items there are
                .story("ModelBackedAlwaysShowsFilterAndPager", context -> {
                    final BaseSelectionBox<String, SimpleSelectionItemWrapper<String>> box =
                            new BaseSelectionBox<>();
                    final SimpleSelectionListModel<String> model = new ModelBackedListModel();
                    model.addItem("Only one");
                    box.setModel(model);
                    final Spy onChange = context.fn(ON_CHANGE);
                    box.addValueChangeHandler(event -> onChange.call(event.getValue()));
                    return minWidth(box);
                })
                .withPlay(play -> {
                    play.click(play.querySelector(OPENER));
                    // Stroom's popup is on the page's body, not in the canvas
                    play.waitFor(() -> play.expect(play.screen().querySelector(".selectionList .pager")).toBeVisible());
                    play.expect(play.screen().querySelector(".selectionList-quickFilter .quickFilter-textBox"))
                            .not().toBeNull();
                    // The pager is Stroom's PagerViewImpl, so it reads "N to M of T"
                    play.expect(play.screen().getByText(TextMatch.containingIgnoreCase("to"), ".pager *"))
                            .toBeInTheDocument();
                })
                // The default: a short list shows no pager
                .story("ShortListHasNoPager", context -> selectionBox(context, List.of("One", "Two")))
                .withPlay(play -> {
                    play.click(play.querySelector(OPENER));
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
                    // Stroom hides the pager container rather than omitting it
                    play.expect(play.screen().querySelector(".selectionList .pager")).not().toBeVisible();
                })
                // The popup's markup is GWT's (SelectionPopup > SimplePopupLayout > SelectionList)
                .story("PopupUsesGwtMarkup", context -> selectionBox(context, List.of("One", "Two")))
                .withPlay(SelectionBoxStories::playPopupMarkup)
                // Escape in an open list closes the list
                .story("EscapeClosesOnlyTheList", context -> {
                    final Spy onWindowEscape = context.fn(ON_WINDOW_ESCAPE);
                    final JavaScriptObject listener = addWindowKeyDownListener(onWindowEscape);
                    context.addCleanUp(() -> removeWindowKeyDownListener(listener));
                    return selectionBox(context, List.of("One", "Two"));
                })
                .withPlay(SelectionBoxStories::playEscape);
    }

    private static void playPopupMarkup(final Play play) {
        play.click(play.querySelector(OPENER));
        play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
        final Play popup = play.screen().within(play.screen().querySelector(POPUP));
        // The chrome Stroom wraps every popup in (SimplePopupLayout)
        for (final String selector : new String[]{
                ".popupContent",
                ".simplePopup-container",
                ".simplePopup-background-behind",
                ".simplePopup-background",
                ".simplePopup-content"}) {
            play.expect(popup.querySelector(selector)).not().toBeNull();
        }
        // SelectionList's own three children, in order
        final Query list = popup.querySelector(".selectionList.dock-container-vertical");
        play.expect(list).not().toBeNull();
        final Play inList = play.screen().within(list);
        play.expect(inList.querySelector(":scope > .selectionList-quickFilter")).not().toBeNull();
        play.expect(inList.querySelector(":scope > .selectionList-links")).not().toBeNull();
        play.expect(inList.querySelector(":scope > .selectionList-elementChooser")).not().toBeNull();
        // PagerViewImpl: the pager container above the list container
        play.expect(popup.querySelector(".selectionList-elementChooser .pager")).not().toBeNull();
        play.expect(popup.querySelector(".listContainer")).not().toBeNull();
        // The rows are a real cell table
        play.expect(popup.querySelector("table.cellTableWidget")).not().toBeNull();
        final Query row = popup.querySelector("tr.cellTableEvenRow");
        play.expect(row).not().toBeNull();
        final Play inRow = play.screen().within(row);
        play.expect(inRow.querySelector("td.cellTableCell.cellTableFirstColumn.cellTableLastColumn")).not().toBeNull();
        play.expect(inRow.querySelector(".explorerCell .selectionItemCell-text")).toHaveTextContent("One");
    }

    private static void playEscape(final Play play) {
        play.click(play.querySelector(OPENER));
        play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
        // Focus deliberately outside the popup
        final Query textBox = play.querySelector(".SelectionBox-textBox");
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
