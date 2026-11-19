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

package stroom.cell.textfield.client;

import com.google.gwt.cell.client.TextInputCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;

/// A text field in a grid's cell: the text field (`text-field`, text-field.css) as a cell, filling its
/// column (`text-field--cell`), each row's named for its column and row.
///
/// Every row's field is a tab stop, and while one has the focus the grid's own keyboard handling
/// leaves its keys to it (the arrows move the caret, not the row), as the cell is editing. GWT's own
/// `TextInputCell` and `EditTextCell` render `tabindex="-1"`, or only text, so a keyboard user
/// couldn't reach them. Its edits are kept as GWT's `TextInputCell` keeps them, so an
/// edit survives a redraw, and the value is given to the column's field updater when it changes.
public class TextFieldCell extends TextInputCell {

    private static final Template TEMPLATE = GWT.create(Template.class);

    private final IntFunction<String> nameForRow;
    private boolean readOnly;

    /// @param nameForRow Names the field on a row, given the row's index, e.g. "Path, row 2", so that
    ///                   each row's field is told apart from the others.
    public TextFieldCell(final IntFunction<String> nameForRow) {
        this.nameForRow = nameForRow;
    }

    /// Makes every row's field read only: it can be read and reached, but not changed. It looks
    /// like the normal field with its value greyed (text-field.css).
    ///
    /// @param readOnly Whether the fields are read only.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
    }

    // A click in the field is the field's: without this, a grid would take the press as choosing
    // the row, taking the focus from the field
    @Override
    public Set<String> getConsumedEvents() {
        final Set<String> events = new HashSet<>(super.getConsumedEvents());
        events.add(BrowserEvents.CLICK);
        events.add(BrowserEvents.MOUSEDOWN);
        return events;
    }

    @Override
    public void render(final Context context, final String value, final SafeHtmlBuilder sb) {
        final String name = nameForRow == null
                ? ""
                : nameForRow.apply(context.getIndex());
        render(context, value, name, sb);
    }

    /// Renders the field with a name of its own, for a field its row doesn't name, e.g. a column's
    /// filter box in a grid's header, named for its column.
    ///
    /// @param context The cell's context.
    /// @param value   The field's value, or null for none.
    /// @param name    The field's name, e.g. "Filter Name".
    /// @param sb      What the field's HTML is added to.
    public void render(final Context context, final String value, final String name, final SafeHtmlBuilder sb) {
        // As TextInputCell.render, so an edit not yet given to the field updater survives a redraw
        final Object key = context.getKey();
        ViewData viewData = getViewData(key);
        if (viewData != null && viewData.getCurrentValue().equals(value)) {
            clearViewData(key);
            viewData = null;
        }
        final String current = viewData == null
                ? value
                : viewData.getCurrentValue();
        final String text = current == null
                ? ""
                : current;
        sb.append(readOnly
                ? TEMPLATE.readOnlyInput(text, nullToEmpty(name))
                : TEMPLATE.input(text, nullToEmpty(name)));
    }

    private static String nullToEmpty(final String value) {
        return value == null
                ? ""
                : value;
    }


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<input type=\"text\" class=\"text-field text-field--cell\" value=\"{0}\" aria-label=\"{1}\" "
                  + "></input>")
        SafeHtml input(String value, String name);

        @Template("<input type=\"text\" class=\"text-field text-field--cell\" value=\"{0}\" aria-label=\"{1}\" "
                  + "readonly></input>")
        SafeHtml readOnlyInput(String value, String name);
    }
}
