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

package stroom.floormap.client.view;

import stroom.floormap.client.FloorMapSwatchHtml;
import stroom.floormap.shared.FloorMapIcon;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;

import java.util.EnumMap;
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
public class FloorMapIconPicker extends Composite {

    /// Size of each cell's icon in pixels.
    private static final int ICON_SIZE_PX = 30;

    private static final String CELL_STYLE = "floormap-icon-picker__cell";
    private static final String SELECTED_STYLE = "floormap-icon-picker__cell--selected";

    private final FlowPanel grid = new FlowPanel();
    private final Map<FloorMapIcon, FlowPanel> cells = new EnumMap<>(FloorMapIcon.class);
    private final Map<FloorMapIcon, HTML> swatches = new EnumMap<>(FloorMapIcon.class);

    private FloorMapIcon selected;
    private String colour;
    private boolean enabled = true;
    private Runnable changeHandler;

    public FloorMapIconPicker() {
        grid.addStyleName("floormap-icon-picker");
        for (final FloorMapIcon icon : FloorMapIcon.values()) {
            grid.add(buildCell(icon));
        }
        initWidget(grid);
    }

    private FlowPanel buildCell(final FloorMapIcon icon) {
        final FlowPanel cell = new FlowPanel();
        cell.addStyleName(CELL_STYLE);
        cell.setTitle(icon.getLabel());

        final HTML swatch = new HTML(
                FloorMapSwatchHtml.iconSwatch(icon, colour, null, ICON_SIZE_PX));
        swatch.addStyleName("floormap-icon-picker__glyph");
        cell.add(swatch);

        final Label label = new Label(icon.getLabel());
        label.addStyleName("floormap-icon-picker__label");
        cell.add(label);

        //noinspection unused event
        cell.addDomHandler(event -> {
            // Disabled means "this mode is not in use", not "these icons are
            // gone" — the grid stays legible so the user can see what switching
            // to it would offer, but it must not answer clicks.
            if (enabled) {
                setValue(icon);
                fireChange();
            }
        }, ClickEvent.getType());

        cells.put(icon, cell);
        swatches.put(icon, swatch);
        return cell;
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
        if (selected != null) {
            cells.get(selected).removeStyleName(SELECTED_STYLE);
        }
        selected = icon;
        if (selected != null) {
            cells.get(selected).addStyleName(SELECTED_STYLE);
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
        for (final Map.Entry<FloorMapIcon, HTML> entry : swatches.entrySet()) {
            entry.getValue().setHTML(FloorMapSwatchHtml.iconSwatch(
                    entry.getKey(), colour, null, ICON_SIZE_PX));
        }
    }

    /// Whether clicks select an icon. A disabled grid is dimmed, not emptied.
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        grid.setStyleName("floormap-icon-picker--disabled", !enabled);
    }

    /// Registers the handler run when the user picks an icon.
    public void setChangeHandler(final Runnable changeHandler) {
        this.changeHandler = changeHandler;
    }

    private void fireChange() {
        if (changeHandler != null) {
            changeHandler.run();
        }
    }
}
