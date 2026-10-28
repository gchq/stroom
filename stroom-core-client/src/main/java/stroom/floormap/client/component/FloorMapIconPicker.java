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

package stroom.floormap.client.component;

import stroom.floormap.client.FloorMapSwatchHtml;
import stroom.floormap.shared.FloorMapIcon;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.LabelElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/// Grid of the built-in [FloorMapIcon]s, one of which can be selected.
///
/// Every icon is drawn **in the layer's own colour** rather than a
/// neutral grey, so the grid previews the decision instead of merely listing the
/// options — pick a colour, and the whole grid restates what that colour looks
/// like. [#setColour] rebuilds for that reason.
///
/// Each cell names its icon underneath, wrapping rather than truncating: a grid
/// of pictograms alone would make the user guess which drawing is meant to be the
/// badge reader.
///
/// A radio group (`role="radiogroup"`, named by its row's label): each cell is a label holding a
/// native radio button, unseen, which its text names, so the grid is one tab stop with the arrow
/// keys choosing between the icons, as any radio group's do: Left and Right the one before or after
/// (the browser's own), and Up and Down the one above or below in the grid as it is laid out. The
/// chosen icon and the focused one are drawn from the radio button's state
/// (`floormap-icon-picker.css`). Its markup is `FloorMapIconPicker.ui.xml`.
public class FloorMapIconPicker extends Widget {

    /// Size of each cell's icon in pixels.
    private static final int ICON_SIZE_PX = 30;

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String BLOCK = "floormap-icon-picker";

    // The radio buttons share a name, which makes them one group to the browser
    private final String name = DOM.createUniqueId();
    private final Map<FloorMapIcon, InputElement> inputs = new EnumMap<>(FloorMapIcon.class);
    private final Map<FloorMapIcon, Element> swatches = new EnumMap<>(FloorMapIcon.class);

    private FloorMapIcon selected;
    private String colour;
    private Runnable onChange;

    public FloorMapIconPicker() {
        setElement(BINDER.createAndBindUi(this));
        for (final FloorMapIcon icon : FloorMapIcon.values()) {
            getElement().appendChild(buildCell(icon));
        }
        // A radio button's change bubbles to the grid: only the one just chosen fires it
        //noinspection unused event
        addDomHandler(event -> {
            final FloorMapIcon icon = chosenIcon();
            if (icon != null && icon != selected) {
                selected = icon;
                fireChange();
            }
        }, ChangeEvent.getType());
        addDomHandler(this::onKeyDown, KeyDownEvent.getType());
    }

    private Element buildCell(final FloorMapIcon icon) {
        final Document document = Document.get();
        final LabelElement cell = document.createLabelElement();
        cell.setClassName(BLOCK + "__cell");
        cell.setTitle(icon.getLabel());

        final InputElement input = document.createRadioInputElement(name);
        input.setClassName(BLOCK + "__input");
        cell.appendChild(input);

        // A picture of what its text says
        final SpanElement swatch = document.createSpanElement();
        swatch.setClassName(BLOCK + "__glyph");
        swatch.setAttribute("aria-hidden", "true");
        swatch.setInnerSafeHtml(FloorMapSwatchHtml.iconSwatch(icon, colour, null, ICON_SIZE_PX));
        cell.appendChild(swatch);

        final SpanElement label = document.createSpanElement();
        label.setClassName(BLOCK + "__label");
        label.setInnerText(icon.getLabel());
        cell.appendChild(label);

        inputs.put(icon, input);
        swatches.put(icon, swatch);
        return cell;
    }

    // Up and Down choose the icon above or below the one with the focus, in the grid as it is laid
    // out (the browser's own radio group keys would go to the one before or after, as Left and
    // Right do). At the top or bottom row the choice stays
    private void onKeyDown(final KeyDownEvent event) {
        final int keyCode = event.getNativeKeyCode();
        if ((keyCode != KeyCodes.KEY_UP && keyCode != KeyCodes.KEY_DOWN)
            || event.isAnyModifierKeyDown()) {
            return;
        }
        event.preventDefault();
        final List<InputElement> order = new ArrayList<>(inputs.values());
        final int index = order.indexOf(Element.as(event.getNativeEvent().getEventTarget()));
        if (index < 0) {
            return;
        }
        final int columns = columns();
        final int next = keyCode == KeyCodes.KEY_DOWN
                ? index + columns
                : index - columns;
        if (next >= 0 && next < order.size()) {
            final InputElement input = order.get(next);
            input.setChecked(true);
            input.focus();
            final FloorMapIcon icon = chosenIcon();
            if (icon != null && icon != selected) {
                selected = icon;
                fireChange();
            }
        }
    }

    // How many icons there are in a row of the grid as it is laid out: those level with the first
    private int columns() {
        final Element first = getElement().getFirstChildElement();
        if (first == null) {
            return 1;
        }
        int columns = 0;
        for (Element cell = first; cell != null; cell = cell.getNextSiblingElement()) {
            if (cell.getOffsetTop() != first.getOffsetTop()) {
                break;
            }
            columns++;
        }
        return Math.max(columns, 1);
    }

    // The icon whose radio button is checked, or null for none
    private FloorMapIcon chosenIcon() {
        for (final Map.Entry<FloorMapIcon, InputElement> entry : inputs.entrySet()) {
            if (entry.getValue().isChecked()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /// The chosen icon, or `null` if none is chosen.
    public FloorMapIcon getValue() {
        return selected;
    }

    /// Selects an icon without firing the change handler, so the presenter can
    /// populate the grid without it looking like a user edit.
    ///
    /// @param icon the icon to select, or `null` to select none
    public void setValue(final FloorMapIcon icon) {
        selected = icon;
        for (final Map.Entry<FloorMapIcon, InputElement> entry : inputs.entrySet()) {
            entry.getValue().setChecked(entry.getKey() == icon);
        }
    }

    /// Redraws every icon in the given colour.
    ///
    /// @param colour a hex colour, or `null` for the built-in default
    public void setColour(final String colour) {
        if (this.colour == null
                ? colour == null
                : this.colour.equals(colour)) {
            // Rebuilding 25 inline SVGs on every keystroke of a colour picker is
            // real DOM churn for no visible change.
            return;
        }
        this.colour = colour;
        for (final Map.Entry<FloorMapIcon, Element> entry : swatches.entrySet()) {
            entry.getValue().setInnerSafeHtml(FloorMapSwatchHtml.iconSwatch(
                    entry.getKey(), colour, null, ICON_SIZE_PX));
        }
    }

    /// Whether an icon can be chosen. Disabled means "this mode is not in use", not "these icons
    /// are gone": the grid stays legible, dimmed, so the user can see what switching to it would
    /// offer.
    ///
    /// @param enabled Whether an icon can be chosen.
    public void setEnabled(final boolean enabled) {
        for (final InputElement input : inputs.values()) {
            input.setDisabled(!enabled);
        }
    }

    /// @param onChange Run when the user picks an icon, or null for nothing.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.run();
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<DivElement, FloorMapIconPicker> {

    }
}
