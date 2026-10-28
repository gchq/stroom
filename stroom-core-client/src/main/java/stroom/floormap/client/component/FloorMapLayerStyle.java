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

import stroom.floormap.client.FloorMapAria;
import stroom.floormap.client.FloorMapSwatchHtml;
import stroom.floormap.shared.FloorMapIcon;
import stroom.floormap.shared.TypeStyle;
import stroom.floormap.shared.TypeStyle.Shape;
import stroom.item.client.SelectionBox;
import stroom.widget.button.client.Button;
import stroom.widget.colour.client.ColourBox;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.RadioGroup;
import stroom.widget.text.client.Html;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

/// The layer appearance dialog's content, which its [FloorMapLayerStyleController] shows.
///
/// ```
/// Graphic   (•) Shape   ( ) Icon   ( ) Image
/// Shape     [ Circle            ▾ ]
/// Icon      [ ▦ grid of built-in icons ]
/// Image     [ /assets/…/van.svg   ] [Upload]
/// Colour    [ ■ ]
/// ─────────────────────────────────
/// Preview   [▣]  fixed size on the map
/// ```
///
/// The Shape, Icon and Image rows are mutually exclusive — the `Graphic`
/// radios disable the two the mode does not use, so it is always clear which one
/// the layer will actually draw. Colour stays enabled throughout: a shape and an
/// icon are both filled with it, and even in image mode it still colours areas of
/// the type and the glyph label.
public class FloorMapLayerStyle extends Component {

    /// The shape list's label for "no configured shape" (the default rectangle glyph).
    public static final String DEFAULT_SHAPE_LABEL = "(default)";

    private static final Binder BINDER = GWT.create(Binder.class);

    /// Size of the preview graphic in pixels.
    private static final int PREVIEW_SIZE_PX = 32;

    /// Applied to whichever of the Shape / Icon / Image rows the mode ignores.
    private static final String DIMMED_ROW_STYLE = "floormap-layer-style-row-dimmed";

    @UiField
    TableCellElement graphicLabel;
    @UiField
    RadioGroup<GraphicMode> modeGroup;
    @UiField
    TableRowElement shapeRow;
    @UiField
    TableCellElement shapeLabel;
    @UiField
    SelectionBox<String> shapeBox;
    @UiField
    TableRowElement iconRow;
    @UiField
    TableCellElement iconLabel;
    @UiField
    FloorMapIconPicker iconPicker;
    @UiField
    TableRowElement imageRow;
    @UiField
    TableCellElement imageLabel;
    @UiField
    SimplePanel assetPickerPanel;
    @UiField
    DropDownBox assetBox;
    @UiField
    Button uploadButton;
    @UiField
    TableCellElement colourLabel;
    @UiField
    ColourBox colourBox;
    @UiField
    SimplePanel previewPanel;

    private Runnable onChange = () -> {
    };
    private Runnable onUpload = () -> {
    };

    /// Makes the dialog's content, drawing a shape.
    public FloorMapLayerStyle() {
        initWidget(BINDER.createAndBindUi(this));

        shapeBox.addItem(DEFAULT_SHAPE_LABEL);
        for (final Shape shape : Shape.values()) {
            shapeBox.addItem(shape.name());
        }
        // A radio group takes its items in Java, not as template children
        modeGroup.addItem(GraphicMode.SHAPE, "Shape", "Draw a coloured shape for facts of this type");
        modeGroup.addItem(GraphicMode.ICON, "Icon", "Draw one of the built-in icons, in this layer's colour");
        modeGroup.addItem(GraphicMode.IMAGE, "Image", "Draw an image from this document's assets");

        // Each row's label is <td> text, which names nothing on its own. The Graphic row's radio
        // group is named by its cell too, and each radio button by its own text
        labelledBy(graphicLabel, modeGroup);
        // A selection box: its combobox is the text box inside it
        FloorMapAria.labelledByInnerControl(shapeBox, id(shapeLabel));
        labelledBy(colourLabel, colourBox);
        // The icon grid is a group of choices rather than one control, so the row label names the
        // group, as it does the asset picker's
        labelledBy(iconLabel, iconPicker);
        labelledBy(imageLabel, assetPickerPanel);
        // The picker is a button named by the value it shows, so the row's name hangs off its
        // container as a group
        FloorMapAria.group(assetPickerPanel, "Image");
        uploadButton.setOnClick(() -> onUpload.run());
        //noinspection unused e
        shapeBox.addValueChangeHandler(e -> onChange.run());
        iconPicker.setOnChange(() -> onChange.run());
        //noinspection unused e
        colourBox.addValueChangeHandler(e -> {
            // The icons preview in the layer's colour, so they follow the picker.
            iconPicker.setColour(colourBox.getValue());
            onChange.run();
        });
        //noinspection unused e
        modeGroup.addValueChangeHandler(e -> {
            applyModeEnablement();
            onChange.run();
        });

        modeGroup.setValue(GraphicMode.SHAPE);
        applyModeEnablement();
    }

    /// @param shape The name of the shape drawn, or [#DEFAULT_SHAPE_LABEL] for the default glyph.
    public void setShape(final String shape) {
        shapeBox.setValue(shape);
    }

    /// @return The name of the shape drawn, or [#DEFAULT_SHAPE_LABEL] for the default glyph.
    public String getShape() {
        return shapeBox.getValue();
    }

    /// The controller resolves the layer's effective colour before calling this, so
    /// the input is simply shown as given (a non-hex value normalises to black
    /// inside [ColourBox] — the native control has no empty state).
    ///
    /// @param colour The layer's colour.
    public void setColour(final String colour) {
        colourBox.setValue(colour);
        iconPicker.setColour(colourBox.getValue());
    }

    /// @return The layer's colour.
    public String getColour() {
        return colourBox.getValue();
    }

    /// @param mode Which of the three graphics the layer draws; null for a shape.
    public void setMode(final GraphicMode mode) {
        modeGroup.setValue(mode != null
                ? mode
                : GraphicMode.SHAPE);
        applyModeEnablement();
    }

    /// @return Which of the three graphics the layer draws.
    public GraphicMode getMode() {
        return modeGroup.getValue() != null
                ? modeGroup.getValue()
                : GraphicMode.SHAPE;
    }

    /// @param icon The built-in icon chosen, or `null` for none.
    public void setIcon(final FloorMapIcon icon) {
        iconPicker.setValue(icon);
    }

    /// @return The built-in icon chosen, or `null` for none.
    public FloorMapIcon getIcon() {
        return iconPicker.getValue();
    }

    /// @return The box the controller's asset picker is attached to.
    public DropDownBox getAssetBox() {
        return assetBox;
    }

    /// @param onUpload Run when the user presses Upload.
    public void setOnUpload(final Runnable onUpload) {
        this.onUpload = onUpload;
    }

    /// @param onChange Run whenever the mode, shape, icon or colour changes, so the preview can be
    ///                 brought up to date.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param style The style the preview draws.
    public void setPreview(final TypeStyle style) {
        previewPanel.setWidget(new Html(FloorMapSwatchHtml.mapPreview(style, PREVIEW_SIZE_PX)));
    }

    private void applyModeEnablement() {
        final GraphicMode mode = getMode();
        shapeBox.setEnabled(mode == GraphicMode.SHAPE);
        iconPicker.setEnabled(mode == GraphicMode.ICON);
        uploadButton.setEnabled(mode == GraphicMode.IMAGE);
        assetBox.setEnabled(mode == GraphicMode.IMAGE);
        UIObject.setStyleName(shapeRow, DIMMED_ROW_STYLE, mode != GraphicMode.SHAPE);
        UIObject.setStyleName(iconRow, DIMMED_ROW_STYLE, mode != GraphicMode.ICON);
        UIObject.setStyleName(imageRow, DIMMED_ROW_STYLE, mode != GraphicMode.IMAGE);
    }

    // Names a control after the text in its row's label cell
    private static void labelledBy(final TableCellElement label, final UIObject control) {
        FloorMapAria.labelledBy(control, id(label));
    }

    // The label cell's id, given one
    private static String id(final TableCellElement label) {
        if (label.getId() == null || label.getId().isEmpty()) {
            label.setId(FloorMapAria.uniqueId("floormap-label"));
        }
        return label.getId();
    }


    // --------------------------------------------------------------------------------


    /// What a layer draws for facts of its type.
    public enum GraphicMode {
        /// A coloured [Shape].
        SHAPE,
        /// A built-in [FloorMapIcon], filled with the layer's colour.
        ICON,
        /// An image uploaded to the document's asset store.
        IMAGE
    }


    interface Binder extends UiBinder<Widget, FloorMapLayerStyle> {

    }
}
