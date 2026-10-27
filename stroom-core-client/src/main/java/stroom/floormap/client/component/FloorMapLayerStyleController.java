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

import stroom.alert.client.event.AlertEvent;
import stroom.docstore.shared.AbstractDoc;
import stroom.document.asset.client.component.DocumentAssetQuickUploadController;
import stroom.document.asset.client.component.DocumentAssetSelectionController;
import stroom.floormap.client.component.FloorMapLayerStyle.GraphicMode;
import stroom.floormap.shared.FloorMapIcon;
import stroom.floormap.shared.TypeStyle;
import stroom.floormap.shared.TypeStyle.Shape;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.function.Consumer;

/// Modal dialog for editing a single layer's appearance ([FloorMapLayerStyle]) — the graphic
/// drawn for facts of that type, held on its [TypeStyle]. The Layers panel's editor owns one and
/// shows it again for each layer.
///
/// A layer draws one of three things, chosen by the `Graphic` radios: a
/// **shape** ([TypeStyle#getShape()] filled with
/// [TypeStyle#getColour()]), one of the built-in **icons**
/// ([FloorMapIcon], filled with the same colour), or an
/// **image** ([TypeStyle#getGraphic()]) picked from the
/// document's asset store. The colour stays editable in image mode too, because it
/// is still used for areas of the type and for the glyph label.
///
/// Only the chosen mode's graphic is stored, so the renderer's precedence —
/// image, then icon, then shape — never has to arbitrate between a live choice and
/// an abandoned one.
///
/// Opened from the Editor Layers panel's per-row swatch. On OK it calls back
/// with a replacement `TypeStyle`, which the panel persists via the
/// type-styles bridge.
public class FloorMapLayerStyleController extends Controller {

    private final FloorMapLayerStyle view;
    private final DocumentAssetSelectionController assetSelection;
    private final DocumentAssetQuickUploadController quickUpload;

    /// The document owning the asset store the graphic is picked from.
    private AbstractDoc document;

    /// Bumped every time the dialog is shown, so an upload that completes after the
    /// user has moved on to a different layer is discarded rather than dropping its
    /// image into whichever layer is now being edited.
    private int showCount;

    /// Made by GIN, with the dialog's content.
    ///
    /// @param eventBus               The application's event bus, which the dialog is shown on.
    /// @param view                   The dialog's content.
    /// @param assetSelectionProvider Makes the image's asset picker.
    /// @param quickUploadProvider    Makes what uploads a new image.
    @Inject
    public FloorMapLayerStyleController(final EventBus eventBus,
                                        final FloorMapLayerStyle view,
                                        final Provider<DocumentAssetSelectionController> assetSelectionProvider,
                                        final Provider<DocumentAssetQuickUploadController> quickUploadProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        quickUpload = make(quickUploadProvider);

        assetSelection = make(assetSelectionProvider).attach(view.getAssetBox());
        view.setOnUpload(this::uploadGraphic);
        // Mode / shape / colour changes all alter what the preview should show.
        view.setOnChange(this::refreshPreview);
        //noinspection unused path
        assetSelection.setOnChange(path -> refreshPreview());
    }

    /// Sets the document whose asset store supplies layer graphics. Must be called
    /// before the dialog is shown, otherwise the picker has nothing to browse.
    ///
    /// @param document The document being edited.
    public void setDocument(final AbstractDoc document) {
        this.document = document;
        assetSelection.setDocument(document);
    }

    /// Shows the dialog for the given layer.
    ///
    /// @param style the layer's current style; its `type` names the layer in
    ///         the caption and is carried through to the replacement
    /// @param onOk  called with the replacement style when the user confirms
    public void show(final TypeStyle style,
                     final Consumer<TypeStyle> onOk) {
        showCount++;
        final String type = style.getType();
        view.setShape(style.getShape() == null
                ? FloorMapLayerStyle.DEFAULT_SHAPE_LABEL
                : style.getShape().name());
        // A layer with no stored colour is drawn in the built-in default for its
        // type, so that is what the picker must show — offering black instead both
        // misstated the current appearance and turned the layer black on OK.
        view.setColour(TypeStyle.colourForType(type, Collections.singletonList(style)));
        assetSelection.setSelectedAssetPath(style.getGraphic());
        view.setIcon(style.iconOrNull());
        view.setMode(style.hasGraphic()
                ? GraphicMode.IMAGE
                : style.hasIcon()
                        ? GraphicMode.ICON
                        : GraphicMode.SHAPE);
        refreshPreview();

        ControllerDialog.show(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(type != null
                        ? "Appearance — " + type
                        : "Appearance")
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final GraphicMode mode = view.getMode();
                        // Confirming a mode with nothing chosen would drop the
                        // layer's graphic and put nothing in its place, silently
                        // reducing it to the fallback glyph.
                        if (mode == GraphicMode.IMAGE && selectedGraphic() == null) {
                            AlertEvent.fireWarn(this,
                                    "Choose an image, or switch back to Shape.",
                                    e::reset);
                            return;
                        }
                        if (mode == GraphicMode.ICON && view.getIcon() == null) {
                            AlertEvent.fireWarn(this,
                                    "Choose an icon, or switch back to Shape.",
                                    e::reset);
                            return;
                        }
                        onOk.accept(styleFrom(type, mode));
                    }
                    e.hide();
                })
                .fire();
    }

    /// Uploads a new image into the document's asset store and selects it, so the
    /// user does not have to leave the dialog for the Assets tab.
    private void uploadGraphic() {
        if (document == null) {
            return;
        }
        // The upload finishes asynchronously, so remember which showing of the
        // dialog asked for it and ignore the result if that has moved on.
        final int requestedFor = showCount;
        quickUpload.upload(document, url -> {
            if (requestedFor != showCount || isDisposed()) {
                return;
            }
            assetSelection.setSelectedAssetPath(url);
            view.setMode(GraphicMode.IMAGE);
            refreshPreview();
        });
    }

    /// Pushes the current dialog state into the preview and keeps each chooser
    /// live only in the mode that uses it.
    private void refreshPreview() {
        final GraphicMode mode = view.getMode();
        assetSelection.setEnabled(mode == GraphicMode.IMAGE);
        view.setPreview(styleFrom(null, mode));
    }

    /// The style the dialog currently describes, for `type`.
    ///
    /// Only the chosen mode's graphic is carried, so switching mode and
    /// confirming genuinely replaces the graphic rather than leaving a stale value
    /// behind to win later — the renderer's precedence is image, then icon, then
    /// shape, so a leftover image would silently outrank a newly-picked icon.
    private TypeStyle styleFrom(final String type, final GraphicMode mode) {
        //noinspection EnhancedSwitchMigration
        switch (mode) {
            case IMAGE:
                return new TypeStyle(type, null, view.getColour(), selectedGraphic());
            case ICON:
                return TypeStyle.ofIcon(type, view.getIcon(), view.getColour());
            case SHAPE:
            default:
                return new TypeStyle(type, chosenShape(), view.getColour());
        }
    }

    /// The picked asset URL, or `null` when nothing is selected.
    private String selectedGraphic() {
        final String path = assetSelection.getSelectedAssetPath();
        return path == null || path.isEmpty()
                ? null
                : path;
    }

    /// The chosen shape, or `null` for the default glyph.
    private Shape chosenShape() {
        final String name = view.getShape();
        return FloorMapLayerStyle.DEFAULT_SHAPE_LABEL.equals(name)
                ? null
                : Shape.valueOf(name);
    }
}
