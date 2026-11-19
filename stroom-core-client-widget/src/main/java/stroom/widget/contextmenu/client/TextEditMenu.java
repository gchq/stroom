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

import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.Separator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// The items of the edit menu Stroom shows on a right-click in a text field or code editor, in place
/// of the browser's own: Undo, Redo, Cut, Copy, Paste, Delete and Select All.
///
/// An item that can't apply is greyed rather than left out, so the menu keeps its shape, except in a
/// read-only field, which shows only Copy and Select All. A password field can't be copied from.
public final class TextEditMenu {

    /// The menu's accessible name.
    public static final String NAME = "Edit";

    static final String PASTE_INSECURE_TOOLTIP =
            "Stroom can only paste from this menu over HTTPS. Use Ctrl+V to paste.";
    static final String PASTE_REFUSED_TOOLTIP =
            "Your browser didn't let Stroom read the clipboard. Use Ctrl+V to paste.";

    private TextEditMenu() {
        // Static utility
    }

    /// Creates the menu's items for a field.
    ///
    /// @param state    The field's state when the menu was opened.
    /// @param executor Runs the command an item stands for when it is chosen.
    /// @return The items, in the order to show them.
    public static List<Item> createItems(final TextFieldState state,
                                         final Consumer<TextEditCommand> executor) {
        final List<Item> items = new ArrayList<>();
        int priority = 0;
        if (state.editable()) {
            items.add(item(priority++, TextEditCommand.UNDO, state, state.canUndo(), executor));
            items.add(item(priority++, TextEditCommand.REDO, state, state.canRedo(), executor));
            items.add(new Separator(priority++));
            items.add(item(priority++, TextEditCommand.CUT, state, state.hasSelection() && !state.password(),
                    executor));
        }
        items.add(item(priority++, TextEditCommand.COPY, state, state.hasSelection() && !state.password(),
                executor));
        if (state.editable()) {
            items.add(pasteItem(priority++, state, executor));
            items.add(item(priority++, TextEditCommand.DELETE, state, state.hasSelection(), executor));
        }
        items.add(new Separator(priority++));
        items.add(item(priority, TextEditCommand.SELECT_ALL, state, state.hasText(), executor));
        return items;
    }

    /// @param command The command.
    /// @param mac     Whether the user is on a Mac, whose shortcuts use Cmd rather than Ctrl.
    /// @return The shortcut that does the same as the command's item, as shown beside it, e.g.
    /// "Ctrl+C".
    static String getShortcut(final TextEditCommand command, final boolean mac) {
        final String modifier = mac
                ? "Cmd+"
                : "Ctrl+";
        return switch (command) {
            case UNDO -> modifier + "Z";
            case REDO -> mac
                    ? "Cmd+Shift+Z"
                    : "Ctrl+Y";
            case CUT -> modifier + "X";
            case COPY -> modifier + "C";
            case PASTE -> modifier + "V";
            case DELETE -> "Del";
            case SELECT_ALL -> modifier + "A";
        };
    }

    private static Item item(final int priority,
                             final TextEditCommand command,
                             final TextFieldState state,
                             final boolean enabled,
                             final Consumer<TextEditCommand> executor) {
        return new IconMenuItem.Builder()
                .priority(priority)
                .icon(command.getIcon())
                .text(command.getText())
                .shortcut(getShortcut(command, state.mac()))
                .enabled(enabled)
                .command(() -> executor.accept(command))
                .build();
    }

    // Paste is greyed, saying why and what to do instead, where the page can't read the clipboard
    private static Item pasteItem(final int priority,
                                  final TextFieldState state,
                                  final Consumer<TextEditCommand> executor) {
        final IconMenuItem.Builder builder = new IconMenuItem.Builder()
                .priority(priority)
                .icon(TextEditCommand.PASTE.getIcon())
                .text(TextEditCommand.PASTE.getText())
                .shortcut(getShortcut(TextEditCommand.PASTE, state.mac()))
                .command(() -> executor.accept(TextEditCommand.PASTE));
        switch (state.paste()) {
            case INSECURE -> builder.enabled(false).tooltip(PASTE_INSECURE_TOOLTIP);
            case REFUSED -> builder.enabled(false).tooltip(PASTE_REFUSED_TOOLTIP);
            default -> builder.enabled(true);
        }
        return builder.build();
    }


    // --------------------------------------------------------------------------------


    /// Whether the edit menu can paste from the clipboard.
    public enum PasteSupport {
        /// The page can ask to read the clipboard.
        AVAILABLE,
        /// The page is served over plain HTTP, where browsers don't let it read the clipboard.
        INSECURE,
        /// The browser or the user refused to let the page read the clipboard.
        REFUSED
    }


    // --------------------------------------------------------------------------------


    /// A field's state when the edit menu was opened, which decides which items are shown and which
    /// are greyed.
    ///
    /// @param editable     Whether the field can be typed in (it isn't read-only).
    /// @param password     Whether the field hides what is typed, so can't be copied from.
    /// @param hasSelection Whether some of the field's text is selected.
    /// @param hasText      Whether the field has any text.
    /// @param canUndo      Whether there may be something to undo; plain fields can't tell, so say
    ///                     true.
    /// @param canRedo      Whether there may be something to redo, as `canUndo`.
    /// @param paste        Whether Paste can read the clipboard.
    /// @param mac          Whether the user is on a Mac, whose shortcuts use Cmd rather than Ctrl.
    public record TextFieldState(boolean editable,
                                 boolean password,
                                 boolean hasSelection,
                                 boolean hasText,
                                 boolean canUndo,
                                 boolean canRedo,
                                 PasteSupport paste,
                                 boolean mac) {

    }
}
