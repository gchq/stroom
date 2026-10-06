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

import stroom.floormap.client.FloorMapAria;
import stroom.floormap.client.FloorMapSwatchHtml;
import stroom.floormap.client.presenter.FloorMapLayerStylePresenter.FloorMapLayerStyleView;
import stroom.floormap.shared.FloorMapIcon;
import stroom.floormap.shared.TypeStyle;
import stroom.floormap.shared.TypeStyle.Shape;
import stroom.item.client.SelectionBox;
import stroom.widget.button.client.Button;
import stroom.widget.colour.client.ColourBox;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

/// View for the layer appearance dialog.
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
public class FloorMapLayerStyleViewImpl extends ViewImpl implements FloorMapLayerStyleView {

    /// Size of the preview graphic in pixels.
    private static final int PREVIEW_SIZE_PX = 32;

    /// Shared name grouping the mode radios into one selection.
    private static final String MODE_GROUP = "floormap-layer-graphic-mode";

    /// Applied to whichever of the Shape / Icon / Image rows the mode ignores.
    private static final String DIMMED_ROW_STYLE = "floormap-layer-style-row-dimmed";

    private static final int ROW_GRAPHIC = 0;
    private static final int ROW_SHAPE = 1;
    private static final int ROW_ICON = 2;
    private static final int ROW_IMAGE = 3;
    private static final int ROW_COLOUR = 4;
    private static final int ROW_PREVIEW = 5;

    private final Grid grid;
    private final RadioButton shapeMode = new RadioButton(MODE_GROUP, "Shape");
    private final RadioButton iconMode = new RadioButton(MODE_GROUP, "Icon");
    private final RadioButton imageMode = new RadioButton(MODE_GROUP, "Image");
    private final SelectionBox<String> shapeBox = new SelectionBox<>();
    private final FloorMapIconPicker iconPicker = new FloorMapIconPicker();
    private final ColourBox colourBox = new ColourBox();
    private final SimplePanel assetPickerPanel = new SimplePanel();
    private final Button uploadButton = new Button();
    private final SimplePanel previewPanel = new SimplePanel();

    private Runnable changeHandler;
    private Runnable uploadHandler;

    @Inject
    public FloorMapLayerStyleViewImpl() {
        shapeBox.addItem(FloorMapLayerStyleView.DEFAULT_SHAPE_LABEL);
        for (final Shape shape : Shape.values()) {
            shapeBox.addItem(shape.name());
        }

        uploadButton.setText("Upload");
        uploadButton.setTitle("Upload a new image into this document's assets");
        //noinspection unused event
        uploadButton.addClickHandler(event -> {
            if (uploadHandler != null) {
                uploadHandler.run();
            }
        });

        previewPanel.addStyleName("floormap-layer-style-preview");

        grid = new Grid(6, 2);
        grid.addStyleName("floormap-layer-style-dialog");

        grid.setText(ROW_GRAPHIC, 0, "Graphic");
        grid.setWidget(ROW_GRAPHIC, 1, buildModePanel());

        grid.setText(ROW_SHAPE, 0, "Shape");
        grid.setWidget(ROW_SHAPE, 1, shapeBox);

        grid.setText(ROW_ICON, 0, "Icon");
        grid.setWidget(ROW_ICON, 1, iconPicker);

        grid.setText(ROW_IMAGE, 0, "Image");
        grid.setWidget(ROW_IMAGE, 1, buildImagePanel());

        grid.setText(ROW_COLOUR, 0, "Colour");
        grid.setWidget(ROW_COLOUR, 1, colourBox);

        grid.setText(ROW_PREVIEW, 0, "Preview");
        grid.setWidget(ROW_PREVIEW, 1, buildPreviewPanel());

        // Each row's label is <td> text, which names nothing — see
        // FloorMapAria.labelledByCell. The Graphic row is exempt: its two radios
        // carry their own visible labels, and the radio group is named by the
        // fieldset-less grouping below.
        FloorMapAria.labelledByCell(grid, ROW_SHAPE, 0, shapeBox);
        FloorMapAria.labelledByCell(grid, ROW_COLOUR, 0, colourBox);
        FloorMapAria.labelledByCell(grid, ROW_IMAGE, 0, assetPickerPanel);
        // The icon grid is a group of choices rather than one control, so the row
        // label names the group — the same treatment the asset picker gets below.
        FloorMapAria.labelledByCell(grid, ROW_ICON, 0, iconPicker);
        // The picker is an injected view rather than a control of its own, so the
        // name has to hang off the container as a group.
        FloorMapAria.group(assetPickerPanel, "Image");
        // "Upload" alone does not say what is being uploaded or where it goes.
        uploadButton.getElement().setAttribute("aria-label",
                "Upload a new image into this document's assets");

        //noinspection unused event
        shapeBox.addValueChangeHandler(event -> fireChange());
        iconPicker.setChangeHandler(this::fireChange);
        //noinspection unused event
        colourBox.addValueChangeHandler(event -> {
            // The icons preview in the layer's colour, so they follow the picker.
            iconPicker.setColour(colourBox.getValue());
            fireChange();
        });

        shapeMode.setValue(true);
        applyModeEnablement();
    }

    private Widget buildModePanel() {
        final FlowPanel panel = new FlowPanel();
        panel.addStyleName("floormap-layer-style-modes");
        shapeMode.setTitle("Draw a coloured shape for facts of this type");
        iconMode.setTitle("Draw one of the built-in icons, in this layer's colour");
        imageMode.setTitle("Draw an image from this document's assets");
        //noinspection unused event
        shapeMode.addValueChangeHandler(event -> onModeChanged());
        //noinspection unused event
        iconMode.addValueChangeHandler(event -> onModeChanged());
        //noinspection unused event
        imageMode.addValueChangeHandler(event -> onModeChanged());
        panel.add(shapeMode);
        panel.add(iconMode);
        panel.add(imageMode);
        return panel;
    }

    private Widget buildImagePanel() {
        final FlowPanel panel = new FlowPanel();
        panel.addStyleName("floormap-layer-style-image");
        panel.add(assetPickerPanel);
        panel.add(uploadButton);
        return panel;
    }

    private Widget buildPreviewPanel() {
        final FlowPanel panel = new FlowPanel();
        panel.addStyleName("floormap-layer-style-preview-row");
        panel.add(previewPanel);

        // Uploaded assets are only served once the document is saved, so an image
        // picked here can legitimately fail to render until then.
        final Label hint = new Label("Drawn at a fixed size on the map. "
                + "Newly uploaded images appear after the document is saved.");
        hint.addStyleName("floormap-layer-style-hint");
        panel.add(hint);
        return panel;
    }

    /// Radio groups fire a change on both the newly-selected and the deselected
    /// button, so enablement and the preview are refreshed once per real change.
    private void onModeChanged() {
        applyModeEnablement();
        fireChange();
    }

    private void applyModeEnablement() {
        final GraphicMode mode = getMode();
        shapeBox.setEnabled(mode == GraphicMode.SHAPE);
        iconPicker.setEnabled(mode == GraphicMode.ICON);
        uploadButton.setEnabled(mode == GraphicMode.IMAGE);
        setRowDimmed(ROW_SHAPE, mode != GraphicMode.SHAPE);
        setRowDimmed(ROW_ICON, mode != GraphicMode.ICON);
        setRowDimmed(ROW_IMAGE, mode != GraphicMode.IMAGE);
    }

    /// Dims the row that the current mode does not use.
    private void setRowDimmed(final int row, final boolean dimmed) {
        if (dimmed) {
            grid.getRowFormatter().addStyleName(row, DIMMED_ROW_STYLE);
        } else {
            grid.getRowFormatter().removeStyleName(row, DIMMED_ROW_STYLE);
        }
    }

    private void fireChange() {
        if (changeHandler != null) {
            changeHandler.run();
        }
    }

    @Override
    public Widget asWidget() {
        return grid;
    }

    @Override
    public void setShape(final String shape) {
        shapeBox.setValue(shape);
    }

    @Override
    public String getShape() {
        return shapeBox.getValue();
    }

    /// The presenter resolves the layer's effective colour before calling this, so
    /// the input is simply shown as given (a non-hex value normalises to black
    /// inside [ColourBox] — the native control has no empty state).
    @Override
    public void setColour(final String colour) {
        colourBox.setValue(colour);
        iconPicker.setColour(colourBox.getValue());
    }

    @Override
    public String getColour() {
        return colourBox.getValue();
    }

    @Override
    public void setMode(final GraphicMode mode) {
        shapeMode.setValue(mode == GraphicMode.SHAPE);
        iconMode.setValue(mode == GraphicMode.ICON);
        imageMode.setValue(mode == GraphicMode.IMAGE);
        applyModeEnablement();
    }

    @Override
    public GraphicMode getMode() {
        if (imageMode.getValue()) {
            return GraphicMode.IMAGE;
        }
        return iconMode.getValue()
                ? GraphicMode.ICON
                : GraphicMode.SHAPE;
    }

    @Override
    public void setIcon(final FloorMapIcon icon) {
        iconPicker.setValue(icon);
    }

    @Override
    public FloorMapIcon getIcon() {
        return iconPicker.getValue();
    }

    @Override
    public void setAssetPickerView(final Widget assetPickerView) {
        assetPickerPanel.setWidget(assetPickerView);
    }

    @Override
    public void setUploadHandler(final Runnable handler) {
        this.uploadHandler = handler;
    }

    @Override
    public void setChangeHandler(final Runnable handler) {
        this.changeHandler = handler;
    }

    @Override
    public void setPreview(final TypeStyle style) {
        previewPanel.setWidget(new HTML(FloorMapSwatchHtml.mapPreview(style, PREVIEW_SIZE_PX)));
    }
}
