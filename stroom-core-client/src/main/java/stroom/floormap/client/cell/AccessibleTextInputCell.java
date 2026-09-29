/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.cell;

import stroom.floormap.client.cell.AccessibleSelectionCell.RowLabelProvider;

import com.google.gwt.cell.client.TextInputCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

/// A [TextInputCell] that a keyboard user can reach, and that a screen reader can name.
///
/// Same problem and same reasoning as [AccessibleSelectionCell]: GWT renders
/// `<input type="text" tabindex="-1">` on the assumption that the containing cell table
/// handles arrow-key navigation, which Stroom's `MyDataGrid` disables.
///
/// Note this replaces `EditTextCell` at its call sites rather than subclassing it.
/// `EditTextCell` renders static text and swaps to an input on click — or on Enter, but
/// only for a user who has already reached the cell through the table's own keyboard
/// navigation. There is nothing in the tab order to focus directly, so in a grid whose
/// navigation is unavailable it is effectively pointer-only. An always-present input is
/// reachable by Tab alone and, for a small settings grid, less surprising. The visible
/// consequence is that the cells now look like the input fields they are.
public class AccessibleTextInputCell extends TextInputCell {

    interface Template extends SafeHtmlTemplates {

        @Template("<input type=\"text\" value=\"{0}\" aria-label=\"{1}\"></input>")
        SafeHtml input(String value, String label);

        @Template("<input type=\"text\" value=\"{0}\" aria-label=\"{1}\" disabled=\"disabled\"></input>")
        SafeHtml inputDisabled(String value, String label);
    }

    private static final Template TEMPLATE = GWT.create(Template.class);

    private final RowLabelProvider labelProvider;

    private boolean readOnly;

    public AccessibleTextInputCell(final RowLabelProvider labelProvider) {
        this.labelProvider = labelProvider;
    }

    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public void render(final Context context, final String value, final SafeHtmlBuilder sb) {
        // Mirrors TextInputCell.render, including the view-data handling, so an in-flight
        // edit survives a redraw exactly as it does in the superclass.
        final Object key = context.getKey();
        ViewData viewData = getViewData(key);
        if (viewData != null && viewData.getCurrentValue().equals(value)) {
            clearViewData(key);
            viewData = null;
        }

        final String current = viewData == null
                ? value
                : viewData.getCurrentValue();
        // Null-coalesced, not just null-checked for the provider: a provider that returns
        // null would otherwise reach the generated SafeHtml escaper and throw.
        final String label = labelProvider == null
                ? ""
                : nullToEmpty(labelProvider.getLabel(context.getIndex()));
        sb.append(readOnly
                ? TEMPLATE.inputDisabled(current == null ? "" : current, label)
                : TEMPLATE.input(current == null ? "" : current, label));
    }

    private static String nullToEmpty(final String value) {
        return value == null
                ? ""
                : value;
    }
}
