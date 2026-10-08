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


package stroom.gwt.workbench.client.app.screen;

import stroom.gwt.workbench.framework.client.play.TextMatch;

import java.util.Objects;

/// How Stroom's GWT screens mark things up, for play functions to find them where the React port
/// uses roles or class names of its own (e.g. `[role="dialog"]`, `[role="row"]`), so every screen
/// story queries them the same way. Plain Java, so usable (and tested) on the JVM.
public final class StroomDom {

    /// A dialog of any popup type (Stroom's `Dialog` and `ResizableDialog`, which have no
    /// `role="dialog"`), e.g. `screen.within(screen.findByText("Caption").closest(StroomDom.DIALOG))`.
    public static final String DIALOG = ".dialog-container";
    /// A dialog's caption.
    public static final String DIALOG_TITLE = ".dialog-titleText";
    /// The data rows of a `MyDataGrid`/`CellTable` (GWT marks each with `__gwt_row`; there is no
    /// `role="row"`). A cell's row is `play.getByText(x).closest("tr")`.
    public static final String GRID_ROW = "tr[__gwt_row]";
    /// The class of a selected `MyDataGrid` row (React's `aria-selected="true"`).
    public static final String SELECTED_ROW = "dataGridSelectedRow";
    /// The class of the header of a column that can be sorted.
    public static final String SORTABLE_HEADER = "dataGridSortableHeader";
    /// The 'open' icon of a `CommandLinkCell` or user cell: GWT runs the link's command when it is
    /// pressed (a mousedown), not when its text is clicked. Its parent's title says what it opens.
    public static final String COMMAND_LINK_OPEN = ".commandLinkOpen";
    /// The text box of Stroom's `SelectionBox` (a drop-down), which opens its list when clicked.
    public static final String SELECTION_BOX = ".SelectionBox-textBox";
    /// The placeholder of a `QuickFilter`'s text box, which has no label (React's 'Filter').
    public static final String QUICK_FILTER_PLACEHOLDER = "Quick Filter";
    /// The label of a link tab (e.g. a document editor's 'Words', 'Permissions' sub-tabs), for
    /// `getByText(label, StroomDom.LINK_TAB_LABEL)`. Stroom's link tabs have no `role="tab"`, and
    /// each label is repeated in a hidden sizer, so a plain `getByText` finds two.
    public static final String LINK_TAB_LABEL = ".linkTab-label";
    /// The text of an item of a menu (`MenuItemCell`), shown on the page's body. The item itself
    /// (the cell's focusable div) has `role="menuitem"`.
    public static final String MENU_ITEM_TEXT = ".menuItem-text";
    /// The file input of a `CustomFileUpload` (Stroom's file chooser), which it hides behind its
    /// 'Choose File' button. `play.upload(...)` chooses a file in it all the same.
    public static final String FILE_INPUT = "input[type='file']";
    /// The title of the shared `ActionMenuCell` ('...' button) of a grid row.
    public static final String ACTIONS_TITLE = "Actions...";

    private StroomDom() {
        // Constants
    }

    /// The accessible name of a Stroom `Button` (e.g. a dialog's OK), for
    /// `getByRole("button", StroomDom.button("OK"))`: exactly its text. (A Stroom button's name
    /// once had its text twice, `OK OK`, as the copy of its text that sizes it wasn't hidden.)
    ///
    /// @param text The button's text, e.g. `OK`, `Cancel`, `Close`.
    /// @return A match of the button's name.
    public static TextMatch button(final String text) {
        final String quoted = quote(Objects.requireNonNull(text));
        return TextMatch.regex("^" + quoted + "$", "");
    }

    private static String quote(final String text) {
        final StringBuilder sb = new StringBuilder();
        for (final char chr : text.toCharArray()) {
            if ("\\^$.|?*+()[]{}".indexOf(chr) >= 0) {
                sb.append('\\');
            }
            sb.append(chr);
        }
        return sb.toString();
    }
}
