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

package stroom.item.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SelectionType;
import stroom.widget.util.client.TypedKey;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntFunction;

/// A selection box in a grid's cell: the same WAI-ARIA combobox as [BaseSelectionBox] (one that
/// can't be typed in), drawn by the same classes (selection-box.css, icon-box.css) and filling
/// its column (`selection-box--cell`), opening the same list ([SelectionPopup]) below it.
///
/// Its keys are the box's: Enter, Space, Down, Up and Alt+Down open the list, Home and End open it at
/// its first or last option, and typing a character opens it at the first option starting with it;
/// in the list, Enter or Alt+Up chooses, Escape closes it, and Tab closes it and goes on. A click
/// opens it too. Choosing gives the value to the column's field updater, and the focus comes back to
/// the box, which the grid draws again.
///
/// Every row's box is a tab stop, and while one has the focus the grid's own keyboard handling
/// leaves its keys to it, as the cell is editing (see [#isEditing]). A cell gets its events after the
/// browser has handled them, so a key can't be cancelled (the page may scroll on Space).
///
/// @param <T> The type of the values chosen from.
/// @param <I> The type of the list's items.
public class SelectionBoxCell<T, I extends SelectionItem> extends AbstractCell<T> {

    // The attribute that finds a row's box again once the grid has drawn it again
    private static final String CELL_ATTRIBUTE = "data-selection-box-cell";
    private static final Template TEMPLATE = GWT.create(Template.class);
    // How often, and how many times, to look for a row's box drawn again after a choice
    private static final int REFOCUS_DELAY_MILLIS = 20;
    private static final int REFOCUS_TRIES = 25;
    private static int nextCellId;

    private final SelectionListModel<T, I> model;
    private final IntFunction<String> nameForRow;
    private final String cellId = "selection-box-cell-" + nextCellId++;
    private boolean readOnly;
    private SelectionPopup<T, I> popup;

    /// @param model      The values to choose from.
    /// @param nameForRow Names the box on a row, given the row's index, e.g. "Role, row 2", so that
    ///                   each row's box is told apart from the others (its list is named for it too).
    public SelectionBoxCell(final SelectionListModel<T, I> model, final IntFunction<String> nameForRow) {
        // Focus and blur too, so that the grid knows whether the box has the focus (see isEditing)
        // before it sees a key: it learns that from the last event it gave the cell
        super(BrowserEvents.CLICK, BrowserEvents.KEYDOWN, BrowserEvents.FOCUS, BrowserEvents.BLUR);
        this.model = model;
        this.nameForRow = nameForRow;
    }

    /// Makes every row's box read only: it can be read and reached, but its list doesn't open. It
    /// looks like the normal box with its value greyed (selection-box.css).
    ///
    /// @param readOnly Whether the boxes are read only.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        if (readOnly && popup != null) {
            popup.hide();
        }
    }

    @Override
    public void render(final Context context, final T value, final SafeHtmlBuilder sb) {
        final I item = value == null
                ? null
                : model.wrap(value);
        final String text = item == null
                ? ""
                : item.getLabel();
        final SafeHtml rendered = item == null
                ? SafeHtmlUtils.EMPTY_SAFE_HTML
                : item.getRenderedLabel();
        final String name = name(context.getIndex());
        final String rowId = cellId + "-" + context.getIndex();
        final SafeHtml arrow = SafeHtmlUtils.fromTrustedString(SvgImage.ARROW_DOWN.getSvg()
                .replaceFirst("<svg", "<svg class=\"icon-box__image\""));
        sb.append(readOnly
                ? TEMPLATE.readOnlyBox(rendered, text, name, rowId, arrow)
                : TEMPLATE.box(rendered, text, name, rowId, arrow));
    }

    // While the box has the focus the grid leaves its keys alone (as GWT's own keyboard handler
    // does for a cell that is editing)
    @Override
    public boolean isEditing(final Context context, final Element parent, final T value) {
        final Element active = activeElement();
        return active != null && parent.isOrHasChild(active);
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final T value,
                               final NativeEvent event,
                               final ValueUpdater<T> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);
        final Element combobox = combobox(parent);
        if (combobox == null || readOnly) {
            return;
        }
        final String type = event.getType();
        if (BrowserEvents.CLICK.equals(type)) {
            // A second click closes the list, as on the widget
            if (popup != null) {
                popup.hide();
            } else if (MouseUtil.isPrimary(event)) {
                open(context, combobox, value, valueUpdater, null, null);
            }
        } else if (popup == null && BrowserEvents.KEYDOWN.equals(type)) {
            final int keyCode = event.getKeyCode();
            final String key = TypedKey.keyOf(event);
            if (keyCode == KeyCodes.KEY_ENTER
                || keyCode == KeyCodes.KEY_SPACE
                || keyCode == KeyCodes.KEY_DOWN
                || keyCode == KeyCodes.KEY_UP) {
                // (Alt+Down included)
                event.preventDefault();
                open(context, combobox, value, valueUpdater, null, null);
            } else if (keyCode == KeyCodes.KEY_HOME || keyCode == KeyCodes.KEY_END) {
                event.preventDefault();
                open(context, combobox, value, valueUpdater, null, keyCode == KeyCodes.KEY_END);
            } else if (TypedKey.isTypedCharacter(event, key)) {
                event.preventDefault();
                open(context, combobox, value, valueUpdater, key, null);
            }
        }
    }

    // Opens the list below the box, at the value, at what was typed to open it, or at its last option
    // (atLast true) or first (false), as End and Home open it
    private void open(final Context context,
                      final Element combobox,
                      final T value,
                      final ValueUpdater<T> valueUpdater,
                      final String typed,
                      final Boolean atLast) {
        final String rowId = combobox.getAttribute(CELL_ATTRIBUTE);
        final Element grid = closestGrid(combobox);
        final SelectionPopup<T, I> opened = new SelectionPopup<>();
        popup = opened;
        opened.setModel(model);
        opened.addAutoHidePartner(combobox.getParentElement().getParentElement());
        opened.setAccessibleName(name(context.getIndex()));
        combobox.setAttribute("aria-expanded", "true");
        combobox.setAttribute("aria-controls", opened.getListBoxId());

        final I selectionItem = value == null
                ? null
                : model.wrap(value);
        if (selectionItem != null) {
            opened.getSelectionModel().setSelected(selectionItem, true, new SelectionType(), false);
        }
        final List<HandlerRegistration> registrations = new ArrayList<>();
        opened.setOnClose(() -> {
            // The box takes the focus back (Tab goes on from it), as the widget's does
            combobox.setAttribute("aria-expanded", "false");
            combobox.removeAttribute("aria-controls");
            combobox.focus();
            registrations.forEach(HandlerRegistration::removeHandler);
            registrations.clear();
            popup = null;
        });
        registrations.add(opened.getSelectionModel().addSelectionHandler(event -> {
            final I selected = opened.getSelectionModel().getSelected();
            final T chosen = selected == null
                    ? null
                    : model.unwrap(selected);
            opened.hide();
            if (!Objects.equals(chosen, value) && valueUpdater != null) {
                // The box shows the choice at once: it has the focus again, so the grid leaves its
                // cell as it is until the focus leaves it (as a tick box shows its new tick)
                showChoice(combobox, chosen == null
                        ? null
                        : model.wrap(chosen));
                valueUpdater.update(chosen);
                // The grid draws the row again, which replaces the box that had the focus
                refocusAfterRedraw(combobox, grid, rowId);
            }
        }));
        if (atLast == null) {
            opened.show(combobox, typed);
        } else {
            opened.showAtFirstOrLast(combobox, atLast);
        }
    }

    // Shows a choice in a row's box, as render draws it: its picture, and its text for screen readers
    private static void showChoice(final Element combobox, final SelectionItem item) {
        combobox.setPropertyString("value", item == null
                ? ""
                : item.getLabel());
        final Element control = combobox.getParentElement();
        final Element shown = control != null
                ? control.getFirstChildElement()
                : null;
        if (shown != null && shown.hasClassName("selection-box__value")) {
            shown.setInnerSafeHtml(item == null
                    ? SafeHtmlUtils.EMPTY_SAFE_HTML
                    : item.getRenderedLabel());
        }
    }

    // Puts the focus back on a row's box once the grid has drawn it again (which it may do a moment
    // later): the redraw leaves the focus on the page, or the grid gives it to its keyboard selected
    // cell. The focus is left alone if it has gone to a control on purpose meanwhile. Gives up after
    // half a second
    private static void refocusAfterRedraw(final Element replaced, final Element grid, final String rowId) {
        final int[] tries = {0};
        Scheduler.get().scheduleFixedDelay(() -> {
            final Element box = findByCellAttribute(rowId);
            if (box != null && box != replaced) {
                final Element active = activeElement();
                final boolean lost = active == null
                                     || active == replaced
                                     || "body".equalsIgnoreCase(active.getTagName())
                                     || (grid != null
                                         && grid.isOrHasChild(active)
                                         && !isControl(active));
                if (lost) {
                    box.focus();
                }
                return false;
            }
            tries[0]++;
            return tries[0] < REFOCUS_TRIES;
        }, REFOCUS_DELAY_MILLIS);
    }

    private String name(final int rowIndex) {
        final String name = nameForRow == null
                ? null
                : nameForRow.apply(rowIndex);
        return name == null
                ? ""
                : name;
    }

    // The box's combobox (its input), in the cell's element
    private static Element combobox(final Element parent) {
        final NodeList<Element> inputs = parent.getElementsByTagName("input");
        return inputs.getLength() == 0
                ? null
                : inputs.getItem(0);
    }

    // Whether an element is a control the user may have moved to, rather than the grid's own cell
    private static boolean isControl(final Element element) {
        final String tag = element.getTagName().toLowerCase(Locale.ROOT);
        return "input".equals(tag) || "select".equals(tag) || "textarea".equals(tag) || "button".equals(tag);
    }

    // The grid the box is in: a data grid (drawn with divs), or a cell table
    private static native Element closestGrid(Element element) /*-{
        return element.closest('.data-grid, table');
    }-*/;

    private static native Element activeElement() /*-{
        return $doc.activeElement;
    }-*/;

    private static native Element findByCellAttribute(String rowId) /*-{
        return $doc.querySelector('[data-selection-box-cell="' + rowId + '"]');
    }-*/;


    // --------------------------------------------------------------------------------


    // The markup of BaseSelectionBox (an IconBox holding the value's picture and the combobox,
    // then the drop-down arrow), with the cell modifier. The combobox holds the value as text for
    // screen readers, unseen over its picture, as the widget's does in a box that can't be typed in
    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"icon-box icon-box--drop-down icon-box--fluid selection-box selection-box--select-only "
                  + "selection-box--cell\"><div class=\"selection-box__control\">"
                  + "<div class=\"selection-box__value stroom-control\" aria-hidden=\"true\">{0}</div>"
                  + "<input type=\"text\" class=\"text-field selection-box__input stroom-control "
                  + "icon-box__field\" role=\"combobox\" aria-haspopup=\"listbox\" "
                  + "aria-expanded=\"false\" value=\"{1}\" aria-label=\"{2}\" readonly "
                  + "data-selection-box-cell=\"{3}\"></input></div>"
                  + "<div class=\"icon-box__icon\" aria-hidden=\"true\">{4}</div></div>")
        SafeHtml box(SafeHtml rendered, String text, String name, String rowId, SafeHtml arrow);

        @Template("<div class=\"icon-box icon-box--drop-down icon-box--read-only icon-box--fluid selection-box "
                  + "selection-box--select-only selection-box--read-only selection-box--cell\">"
                  + "<div class=\"selection-box__control\">"
                  + "<div class=\"selection-box__value stroom-control\" aria-hidden=\"true\">{0}</div>"
                  + "<input type=\"text\" class=\"text-field selection-box__input stroom-control "
                  + "icon-box__field\" role=\"combobox\" aria-haspopup=\"listbox\" "
                  + "aria-expanded=\"false\" aria-readonly=\"true\" value=\"{1}\" aria-label=\"{2}\" readonly "
                  + "data-selection-box-cell=\"{3}\"></input></div>"
                  + "<div class=\"icon-box__icon\" aria-hidden=\"true\">{4}</div></div>")
        SafeHtml readOnlyBox(SafeHtml rendered, String text, String name, String rowId, SafeHtml arrow);
    }
}
