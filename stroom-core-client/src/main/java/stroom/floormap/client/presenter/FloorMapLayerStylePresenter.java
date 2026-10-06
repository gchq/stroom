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

package stroom.floormap.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.docstore.shared.AbstractDoc;
import stroom.document.asset.client.presenter.DocumentAssetDropDownPresenter;
import stroom.document.asset.client.presenter.DocumentAssetQuickUploadPresenter;
import stroom.floormap.client.presenter.FloorMapLayerStylePresenter.FloorMapLayerStyleView;
import stroom.floormap.client.presenter.FloorMapLayerStylePresenter.FloorMapLayerStyleView.GraphicMode;
import stroom.floormap.shared.FloorMapIcon;
import stroom.floormap.shared.TypeStyle;
import stroom.floormap.shared.TypeStyle.Shape;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Collections;
import java.util.function.Consumer;

/// Modal dialog for editing a single layer's appearance — the graphic drawn for
/// facts of that type, held on its [TypeStyle].
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
public class FloorMapLayerStylePresenter extends MyPresenterWidget<FloorMapLayerStyleView> {

    private final DocumentAssetDropDownPresenter assetDropDownPresenter;
    private final Provider<DocumentAssetQuickUploadPresenter> quickUploadProvider;

    /// The document owning the asset store the graphic is picked from.
    private AbstractDoc document;

    /// Bumped every time the dialog is shown, so an upload that completes after the
    /// user has moved on to a different layer is discarded rather than dropping its
    /// image into whichever layer is now being edited.
    private int showCount;

    @Inject
    public FloorMapLayerStylePresenter(final EventBus eventBus,
                                       final FloorMapLayerStyleView view,
                                       final DocumentAssetDropDownPresenter assetDropDownPresenter,
                                       final Provider<DocumentAssetQuickUploadPresenter> quickUploadProvider) {
        super(eventBus, view);
        this.assetDropDownPresenter = assetDropDownPresenter;
        this.quickUploadProvider = quickUploadProvider;

        view.setAssetPickerView(assetDropDownPresenter.getView().asWidget());
        view.setUploadHandler(this::uploadGraphic);
        // Mode / shape / colour changes all alter what the preview should show.
        view.setChangeHandler(this::refreshPreview);
        //noinspection unused event
        assetDropDownPresenter.addDataSelectionHandler(event -> refreshPreview());
    }

    /// Sets the document whose asset store supplies layer graphics. Must be called
    /// before the dialog is shown, otherwise the picker has nothing to browse.
    public void setDocument(final AbstractDoc document) {
        this.document = document;
        assetDropDownPresenter.setDocument(document);
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
        quickUploadProvider.get().upload(document, this, url -> {
            if (requestedFor != showCount) {
                return;
            }
            assetDropDownPresenter.setSelectedAssetPath(url);
            getView().setMode(GraphicMode.IMAGE);
            refreshPreview();
        });
    }

    /// Pushes the current dialog state into the preview and keeps each chooser
    /// live only in the mode that uses it.
    private void refreshPreview() {
        final GraphicMode mode = getView().getMode();
        assetDropDownPresenter.setEnabled(mode == GraphicMode.IMAGE);
        getView().setPreview(styleFrom(null, mode));
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
                return new TypeStyle(type, null, getView().getColour(), selectedGraphic());
            case ICON:
                return TypeStyle.ofIcon(type, getView().getIcon(), getView().getColour());
            case SHAPE:
            default:
                return new TypeStyle(type, chosenShape(), getView().getColour());
        }
    }

    /// The picked asset URL, or `null` when nothing is selected.
    private String selectedGraphic() {
        final String path = assetDropDownPresenter.getSelectedAssetPath();
        return path == null || path.isEmpty()
                ? null
                : path;
    }

    /// The chosen shape, or `null` for the default glyph.
    private Shape chosenShape() {
        final String name = getView().getShape();
        return FloorMapLayerStyleView.DEFAULT_SHAPE_LABEL.equals(name)
                ? null
                : Shape.valueOf(name);
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
        getView().setShape(style.getShape() == null
                ? FloorMapLayerStyleView.DEFAULT_SHAPE_LABEL
                : style.getShape().name());
        // A layer with no stored colour is drawn in the built-in default for its
        // type, so that is what the picker must show — offering black instead both
        // misstated the current appearance and turned the layer black on OK.
        getView().setColour(TypeStyle.colourForType(type, Collections.singletonList(style)));
        assetDropDownPresenter.setSelectedAssetPath(style.getGraphic());
        getView().setIcon(style.iconOrNull());
        getView().setMode(style.hasGraphic()
                ? GraphicMode.IMAGE
                : style.hasIcon()
                        ? GraphicMode.ICON
                        : GraphicMode.SHAPE);
        refreshPreview();

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(type != null
                        ? "Appearance — " + type
                        : "Appearance")
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final GraphicMode mode = getView().getMode();
                        // Confirming a mode with nothing chosen would drop the
                        // layer's graphic and put nothing in its place, silently
                        // reducing it to the fallback glyph.
                        if (mode == GraphicMode.IMAGE && selectedGraphic() == null) {
                            AlertEvent.fireWarn(FloorMapLayerStylePresenter.this,
                                    "Choose an image, or switch back to Shape.",
                                    e::reset);
                            return;
                        }
                        if (mode == GraphicMode.ICON && getView().getIcon() == null) {
                            AlertEvent.fireWarn(FloorMapLayerStylePresenter.this,
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

    /// View contract: a shape / icon / image mode toggle, a chooser for each, a
    /// colour chooser and a preview.
    public interface FloorMapLayerStyleView extends View {

        /// Dropdown label for "no configured shape" (the default rectangle glyph).
        String DEFAULT_SHAPE_LABEL = "(default)";

        /// What a layer draws for facts of its type.
        enum GraphicMode {
            /// A coloured [Shape].
            SHAPE,
            /// A built-in [FloorMapIcon], filled with the layer's colour.
            ICON,
            /// An image uploaded to the document's asset store.
            IMAGE
        }

        void setShape(String shape);

        String getShape();

        void setColour(String colour);

        String getColour();

        /// Which of the three graphics the layer draws.
        void setMode(GraphicMode mode);

        GraphicMode getMode();

        /// Selects a built-in icon, or `null` for none.
        void setIcon(FloorMapIcon icon);

        FloorMapIcon getIcon();

        /// Installs the asset picker widget the presenter owns.
        void setAssetPickerView(Widget assetPickerView);

        /// Registers the handler run when the user clicks the upload button.
        void setUploadHandler(Runnable handler);

        /// Registers the handler run whenever the mode, shape or colour changes, so
        /// the presenter can refresh the preview.
        void setChangeHandler(Runnable handler);

        /// Renders a preview of the given style.
        void setPreview(TypeStyle style);
    }
}
