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

package stroom.gwt.workbench.client.widgets.menu;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.item.client.SelectionBox;
import stroom.widget.button.client.Button;
import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.Html;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories for the edit menu Stroom shows on a right-click in a text field, in place of the
/// browser's (`ContextMenuManager` and `TextEditMenu`, gchq/stroom#5897): Undo, Redo, Cut, Copy,
/// Paste, Delete and Select All, greyed where they can't apply.
///
/// The stories check which items are shown and which are greyed, and the commands that need no
/// clipboard (Delete, Undo, Select All). Copying, cutting and pasting need a real click, so are
/// checked by the real input checks (`text-context-menu.mjs`).
public final class TextContextMenuStories {

    private static final String NAME = "Name";
    private static final String VALUE = "hello world";
    private static final String MENU_ITEM = "menuitem";
    private static final String DISABLED = "aria-disabled";
    private static final List<String> EDIT_ITEMS = List.of(
            "Undo", "Redo", "Cut", "Copy", "Paste", "Delete", "Select All");

    private TextContextMenuStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Menu/TextContextMenu", TextContextMenuStories.class)
                .layout(StoryLayout.CENTERED)
                // A text box with nothing selected: every item, with those that need a selection greyed
                .story("Editable", context -> withMenus(context, textBox(VALUE, false)))
                .withPlay(play -> {
                    play.rightClick(play.getByRole("textbox", NAME));
                    final Query menu = play.screen().findByRole("menu", "Edit");
                    for (final String item : EDIT_ITEMS) {
                        play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing(item)))
                                .toBeInTheDocument();
                    }
                    expectEnabled(play, menu, "Undo", "Redo", "Paste", "Select All");
                    expectDisabled(play, menu, "Cut", "Copy", "Delete");
                    // Each item says the key that does the same, for screen readers too
                    play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing("Copy")))
                            .toHaveTextContent(TextMatch.containing("Ctrl+C"));
                    play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing("Copy")))
                            .toHaveAttribute("aria-keyshortcuts", "Control+C");
                })
                // With some text selected, Cut, Copy and Delete can be chosen; Delete and Undo act
                // on the field
                .story("WithSelection", context -> withMenus(context, textBox(VALUE, false)))
                .withPlay(play -> {
                    final Query box = play.getByRole("textbox", NAME);
                    openWithSelection(play, box, 0, 6);
                    final Query menu = play.screen().findByRole("menu", "Edit");
                    expectEnabled(play, menu, "Undo", "Redo", "Cut", "Copy", "Paste", "Delete", "Select All");

                    play.click(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing("Delete")));
                    play.waitFor(() -> play.expect(box).toHaveValue("world"));
                    // The focus is back in the field
                    play.expect(box).toHaveFocus();

                    play.rightClick(box);
                    play.click(play.screen().findByRole(MENU_ITEM, TextMatch.containing("Undo")));
                    play.waitFor(() -> play.expect(box).toHaveValue(VALUE));
                })
                // Select All selects the field's text
                .story("SelectAll", context -> withMenus(context, textBox(VALUE, false)))
                .withPlay(play -> {
                    final Query box = play.getByRole("textbox", NAME);
                    play.rightClick(box);
                    play.click(play.screen().findByRole(MENU_ITEM, TextMatch.containing("Select All")));
                    play.waitFor(() -> play.expect("selected text", () -> getSelectedText(box.element().get()))
                            .toBe(VALUE));
                    play.expect(box).toHaveFocus();
                })
                // A read-only field can only be copied from
                .story("ReadOnly", context -> withMenus(context, textBox(VALUE, true)))
                .withPlay(play -> {
                    openWithSelection(play, play.getByRole("textbox", NAME), 0, 5);
                    final Query menu = play.screen().findByRole("menu", "Edit");
                    play.expect(play.within(menu).getAllByRole(MENU_ITEM)).toHaveLength(2);
                    expectEnabled(play, menu, "Copy", "Select All");
                })
                // A password can't be copied or cut, even when selected
                .story("Password", context -> {
                    final PasswordBox passwordBox = new PasswordBox();
                    passwordBox.setValue("secret");
                    AccessibleName.set(passwordBox, "Password");
                    return withMenus(context, passwordBox);
                })
                .withPlay(play -> {
                    // A password field has no role, so is found by its type
                    final Query field = play.querySelector("input[type='password']");
                    openWithSelection(play, field, 0, 6);
                    final Query menu = play.screen().findByRole("menu", "Edit");
                    expectDisabled(play, menu, "Cut", "Copy");
                    expectEnabled(play, menu, "Paste", "Delete", "Select All");
                })
                // An empty text area: nothing to select
                .story("Empty", context -> {
                    final TextArea textArea = new TextArea();
                    AccessibleName.set(textArea, "Description");
                    return withMenus(context, textArea);
                })
                .withPlay(play -> {
                    play.rightClick(play.getByRole("textbox", "Description"));
                    final Query menu = play.screen().findByRole("menu", "Edit");
                    expectDisabled(play, menu, "Cut", "Copy", "Delete", "Select All");
                })
                // Opened from the keyboard (Shift+F10 or the context menu key, which send contextmenu
                // without the secondary button): Escape puts the focus and selection back
                .story("Keyboard", context -> withMenus(context, textBox(VALUE, false)))
                .withPlay(play -> {
                    final Query box = play.getByRole("textbox", NAME);
                    play.run("select 'hello'", () -> select(box.element().get(), 0, 5));
                    play.fireEvent().contextMenu(box, EventInit.create().button(0));
                    final Query menu = play.screen().findByRole("menu", "Edit");
                    play.waitFor(() -> play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing("Undo")))
                            .toHaveFocus());
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(box).toHaveFocus());
                    play.expect("selected text", () -> getSelectedText(box.element().get())).toBe("hello");
                })
                // Shift+right-click is left to the browser, which shows its own menu
                .story("ShiftClick", context -> withMenus(context, textBox(VALUE, false)))
                .withPlay(play -> {
                    final Query box = play.getByRole("textbox", NAME);
                    play.fireEvent().contextMenu(box, EventInit.create().shiftKey());
                    play.expect(play.screen().queryByRole("menu")).toBeNull();
                    play.expect("browser's menu allowed", () -> isLastContextMenuAllowed()).toBe(true);
                })
                // With the Text Context Menu preference set to Browser, a right-click in a text field
                // is left to the browser
                .story("BrowserPreference", context -> {
                    ContextMenuManager.setBrowserMenuForText(true);
                    context.addCleanUp(() -> ContextMenuManager.setBrowserMenuForText(false));
                    return withMenus(context, textBox(VALUE, false));
                })
                .withPlay(play -> {
                    play.rightClick(play.getByRole("textbox", NAME));
                    play.expect(play.screen().queryByRole("menu")).toBeNull();
                    play.expect("browser's menu allowed", () -> isLastContextMenuAllowed()).toBe(true);
                })
                // Selected text that isn't in a field (e.g. help, an alert's message) can be copied:
                // a right-click on the selection shows Copy, one elsewhere shows nothing
                .story("SelectedText", context -> {
                    final Html text = new Html("<p class=\"help-popup\" data-testid=\"text\">"
                                               + "Stroom stores and processes event data.</p>"
                                               + "<p data-testid=\"other\">Not selectable.</p>");
                    return withMenus(context, text);
                })
                .withPlay(play -> {
                    final Query text = play.getByTestId("text");
                    play.run("select the text", () -> selectContents(text.element().get()));
                    play.fireEvent().contextMenu(text, EventInit.create().atCentreOf(text).button(2));
                    final Query menu = play.screen().findByRole("menu", "Selected text");
                    play.expect(play.within(menu).getAllByRole(MENU_ITEM)).toHaveLength(1);
                    play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing("Copy")))
                            .toHaveTextContent(TextMatch.containing("Ctrl+C"));
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.screen().queryByRole("menu")).toBeNull());

                    final Query other = play.getByTestId("other");
                    play.fireEvent().contextMenu(other, EventInit.create().atCentreOf(other).button(2));
                    play.expect(play.screen().queryByRole("menu")).toBeNull();
                })
                // A link (e.g. in help or markdown) can be opened in a new tab or its address copied
                .story("Link", context -> withMenus(context, new Html(
                        "<a href=\"https://gchq.github.io/stroom-docs/\" target=\"_blank\""
                        + " style=\"display: inline-block; padding: 4px 0\">Stroom's documentation</a>")))
                .withPlay(play -> {
                    play.rightClick(play.getByRole("link", "Stroom's documentation"));
                    final Query menu = play.screen().findByRole("menu", "Link");
                    play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing("Open Link in New Tab")))
                            .toBeInTheDocument();
                    play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing("Copy Link Address")))
                            .toBeInTheDocument();
                })
                // Not a text field: no menu, and not the browser's either
                .story("NotATextField", context -> {
                    final Button button = new Button();
                    button.setText("Save");
                    final SelectionBox<String> selectionBox = new SelectionBox<>();
                    selectionBox.addItems(List.of("Raw Events", "Events"));
                    selectionBox.setValue("Events");
                    AccessibleName.set(selectionBox, "Type");
                    return withMenus(context, StoryPanels.column(8, button, selectionBox));
                })
                .withPlay(play -> {
                    play.rightClick(play.getByRole("button", "Save"));
                    play.expect(play.screen().queryByRole("menu")).toBeNull();
                    play.expect("browser's menu allowed", () -> isLastContextMenuAllowed()).toBe(false);
                    // A selection box that can't be typed in is a list to choose from, not text
                    play.rightClick(play.getByRole("combobox", "Type"));
                    play.expect(play.screen().queryByRole("menu")).toBeNull();
                });
    }

    private static Widget withMenus(final StoryContext context, final Widget widget) {
        StoryPopups.create(context).withMenus();
        watchContextMenus(context);
        return StoryPanels.column(8, widget);
    }

    private static TextBox textBox(final String value, final boolean readOnly) {
        final TextBox textBox = new TextBox();
        textBox.setValue(value);
        textBox.setReadOnly(readOnly);
        AccessibleName.set(textBox, NAME);
        return textBox;
    }

    private static void openWithSelection(final Play play, final Query field, final int start, final int end) {
        play.run("select characters " + start + " to " + end, () -> select(field.element().get(), start, end));
        // A right press puts the caret where it is pressed, so the menu is opened as the browser opens
        // it on a selection, without the press
        play.fireEvent().contextMenu(field);
    }

    private static void expectEnabled(final Play play, final Query menu, final String... items) {
        for (final String item : items) {
            play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing(item)))
                    .not().toHaveAttribute(DISABLED);
        }
    }

    private static void expectDisabled(final Play play, final Query menu, final String... items) {
        for (final String item : items) {
            play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing(item)))
                    .toHaveAttribute(DISABLED, "true");
        }
    }

    // Notes whether each contextmenu event reached the document unprevented, i.e. whether the browser
    // would show its own menu, for the length of the story
    private static void watchContextMenus(final StoryContext context) {
        addWatcher();
        context.addCleanUp(TextContextMenuStories::removeWatcher);
    }

    private static native void addWatcher() /*-{
        var watcher = function (event) {
            // After every other listener, on the way out of the document
            setTimeout(function () {
                $wnd.__textContextMenuAllowed = !event.defaultPrevented;
            }, 0);
        };
        $wnd.__textContextMenuWatcher = watcher;
        $wnd.addEventListener('contextmenu', watcher);
    }-*/;

    private static native void removeWatcher() /*-{
        if ($wnd.__textContextMenuWatcher) {
            $wnd.removeEventListener('contextmenu', $wnd.__textContextMenuWatcher);
            $wnd.__textContextMenuWatcher = null;
        }
    }-*/;

    private static native boolean isLastContextMenuAllowed() /*-{
        return $wnd.__textContextMenuAllowed === true;
    }-*/;

    private static native void select(final Element element, final int start, final int end) /*-{
        element.focus();
        element.setSelectionRange(start, end);
    }-*/;

    private static native void selectContents(final Element element) /*-{
        var range = $doc.createRange();
        range.selectNodeContents(element);
        var selection = $wnd.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
    }-*/;

    private static native String getSelectedText(final Element element) /*-{
        return element.value.substring(element.selectionStart, element.selectionEnd);
    }-*/;
}
