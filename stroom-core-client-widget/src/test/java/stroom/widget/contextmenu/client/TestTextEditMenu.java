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

package stroom.widget.contextmenu.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.contextmenu.client.TextEditMenu.PasteSupport;
import stroom.widget.contextmenu.client.TextEditMenu.TextFieldState;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuItem;
import stroom.widget.menu.client.Separator;
import stroom.widget.util.client.SafeHtmlUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestTextEditMenu {

    private static final String SEPARATOR = "-";

    @Test
    void editableField_showsEveryItemInTheBrowsersOrder() {
        final List<Item> items = createItems(editable(true));

        assertThat(describe(items))
                .containsExactly("Undo", "Redo", SEPARATOR, "Cut", "Copy", "Paste", "Delete", SEPARATOR,
                        "Select All");
    }

    @Test
    void editableField_withNoSelection_greysCutCopyAndDelete() {
        final List<Item> items = createItems(editable(false));

        assertThat(enabledItems(items))
                .containsExactly("Undo", "Redo", "Paste", "Select All");
    }

    @Test
    void editableField_withSelection_enablesEverything() {
        final List<Item> items = createItems(editable(true));

        assertThat(enabledItems(items))
                .containsExactly("Undo", "Redo", "Cut", "Copy", "Paste", "Delete", "Select All");
    }

    @Test
    void readOnlyField_showsOnlyCopyAndSelectAll() {
        final TextFieldState state = new TextFieldState(false, false, true, true, true, true,
                PasteSupport.AVAILABLE, false);

        assertThat(describe(createItems(state)))
                .containsExactly("Copy", SEPARATOR, "Select All");
        assertThat(enabledItems(createItems(state)))
                .containsExactly("Copy", "Select All");
    }

    @Test
    void passwordField_greysCutAndCopyEvenWithASelection() {
        final TextFieldState state = new TextFieldState(true, true, true, true, true, true,
                PasteSupport.AVAILABLE, false);

        assertThat(enabledItems(createItems(state)))
                .containsExactly("Undo", "Redo", "Paste", "Delete", "Select All");
    }

    @Test
    void emptyField_greysSelectAll() {
        final TextFieldState state = new TextFieldState(true, false, false, false, true, true,
                PasteSupport.AVAILABLE, false);

        assertThat(enabledItems(createItems(state)))
                .containsExactly("Undo", "Redo", "Paste");
    }

    @Test
    void nothingToUndoOrRedo_greysThem() {
        // As a code editor reports, which can tell
        final TextFieldState state = new TextFieldState(true, false, false, true, false, false,
                PasteSupport.AVAILABLE, false);

        assertThat(enabledItems(createItems(state)))
                .containsExactly("Paste", "Select All");
    }

    @Test
    void pasteOverHttp_isGreyedSayingToUseTheKeyboard() {
        final TextFieldState state = new TextFieldState(true, false, false, true, true, true,
                PasteSupport.INSECURE, false);

        final MenuItem paste = find(createItems(state), "Paste");
        assertThat(paste.isEnabled()).isFalse();
        assertThat(SafeHtmlUtil.toPlainText(paste.getTooltip()))
                .isEqualTo("Stroom can only paste from this menu over HTTPS. Use Ctrl+V to paste.");
    }

    @Test
    void pasteRefused_isGreyedSayingWhy() {
        final TextFieldState state = new TextFieldState(true, false, false, true, true, true,
                PasteSupport.REFUSED, false);

        final MenuItem paste = find(createItems(state), "Paste");
        assertThat(paste.isEnabled()).isFalse();
        assertThat(SafeHtmlUtil.toPlainText(paste.getTooltip()))
                .isEqualTo("Your browser didn't let Stroom read the clipboard. Use Ctrl+V to paste.");
    }

    @Test
    void items_showTheBrowsersShortcuts() {
        final List<Item> items = createItems(editable(true));

        assertThat(shortcuts(items))
                .containsExactly("Ctrl+Z", "Ctrl+Y", "Ctrl+X", "Ctrl+C", "Ctrl+V", "Del", "Ctrl+A");
    }

    @Test
    void items_onAMac_showCmdShortcuts() {
        final TextFieldState state = new TextFieldState(true, false, true, true, true, true,
                PasteSupport.AVAILABLE, true);

        assertThat(shortcuts(createItems(state)))
                .containsExactly("Cmd+Z", "Cmd+Shift+Z", "Cmd+X", "Cmd+C", "Cmd+V", "Del", "Cmd+A");
    }

    @Test
    void items_haveTheirCommandsIcons() {
        final List<Item> items = createItems(editable(true));

        assertThat(items.stream()
                .filter(IconMenuItem.class::isInstance)
                .map(item -> ((IconMenuItem) item).getEnabledIcon())
                .collect(Collectors.toList()))
                .containsExactly(SvgImage.UNDO, SvgImage.REDO, SvgImage.CUT, SvgImage.COPY, SvgImage.PASTE,
                        SvgImage.DELETE, SvgImage.SELECT_ALL);
    }

    @Test
    void choosingAnItem_runsItsCommand() {
        final List<TextEditCommand> run = new ArrayList<>();
        final List<Item> items = TextEditMenu.createItems(editable(true), run::add);

        find(items, "Cut").getCommand().execute();
        find(items, "Select All").getCommand().execute();

        assertThat(run).containsExactly(TextEditCommand.CUT, TextEditCommand.SELECT_ALL);
    }

    private static TextFieldState editable(final boolean hasSelection) {
        return new TextFieldState(true, false, hasSelection, true, true, true, PasteSupport.AVAILABLE, false);
    }

    private static List<Item> createItems(final TextFieldState state) {
        return TextEditMenu.createItems(state, command -> {
        });
    }

    private static List<String> describe(final List<Item> items) {
        return items.stream()
                .map(item -> item instanceof Separator
                        ? SEPARATOR
                        : ((MenuItem) item).getText().asString())
                .collect(Collectors.toList());
    }

    private static List<String> enabledItems(final List<Item> items) {
        return items.stream()
                .filter(MenuItem.class::isInstance)
                .map(MenuItem.class::cast)
                .filter(MenuItem::isEnabled)
                .map(item -> item.getText().asString())
                .collect(Collectors.toList());
    }

    private static List<String> shortcuts(final List<Item> items) {
        return items.stream()
                .filter(MenuItem.class::isInstance)
                .map(item -> ((MenuItem) item).getShortcut())
                .collect(Collectors.toList());
    }

    private static MenuItem find(final List<Item> items, final String text) {
        return items.stream()
                .filter(MenuItem.class::isInstance)
                .map(MenuItem.class::cast)
                .filter(item -> text.equals(item.getText().asString()))
                .findFirst()
                .orElseThrow();
    }
}
