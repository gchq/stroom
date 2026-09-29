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

package stroom.floormap.client.view;

import stroom.floormap.client.FloorMapAria;
import stroom.floormap.client.presenter.FloorMapObjectEditPresenter.FloorMapObjectEditView;
import stroom.floormap.shared.FloorMapMeasurementUnits;
import stroom.floormap.shared.FloorMapMeasurementUnits.Unit;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.floormap.shared.TypeStyle;
import stroom.widget.colour.client.ColourBox;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimePopup;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.function.Consumer;

/// View implementation for the floor map object (fact) edit dialog.
///
/// Renders form fields for the object's name, type, effective time, position,
/// an image chooser, and the scale/rotation of its world→map transform. Every
/// fact — backgrounds included — is placed by that matrix.
///
/// **Position is shown resolved and in metres**, not as the raw
/// matrix translation: a fact's place on the map is `worldToMap · coords`,
/// and the two components mean nothing separately. Editing it solves the
/// translation back out ([FloorMapTransformationMatrix#placing]), leaving
/// the stored coordinates untouched — the same thing a canvas drag does.
public class FloorMapObjectEditViewImpl extends ViewImpl implements FloorMapObjectEditView {

    /// The unit the position and size boxes are typed in. Fixed rather than
    /// best-fitting: a field that changed between mm and km as you typed would be
    /// unusable.
    private static final Unit INPUT_UNIT = Unit.METRE;

    private final Widget widget;

    @UiField
    DateTimeBox effectiveTimeBox;
    @UiField
    TextBox nameBox;
    @UiField
    TextBox typeBox;
    @UiField
    SimplePanel chooseImgContainer;

    @UiField
    TextBox posX;
    @UiField
    TextBox posY;
    @UiField
    FormGroup sizeGroup;
    @UiField
    TextBox sizeW;
    @UiField
    TextBox sizeH;
    @UiField
    FormGroup scaleGroup;
    @UiField
    TextBox w2mSx;
    @UiField
    TextBox w2mSy;
    @UiField
    TextBox w2mRot;

    // Area-only fields (hidden for non-area objects).
    @UiField
    FormGroup fillGroup;
    @UiField
    CheckBox fillDefaultCheck;
    @UiField
    ColourBox fillBox;
    @UiField
    FormGroup opacityGroup;
    @UiField
    TextBox opacityBox;
    @UiField
    FormGroup verticesGroup;
    @UiField
    Label vertexCountLabel;
    // Wrapper rows and unit suffixes, held only so their ARIA can be set up in
    // the constructor — see nameControlsForScreenReaders().
    @UiField
    FlowPanel fillRow;
    @UiField
    FlowPanel positionRow;
    @UiField
    FlowPanel sizeRow;
    @UiField
    FlowPanel scaleRow;
    @UiField
    FlowPanel rotationRow;
    @UiField
    Label posUnit;
    @UiField
    Label sizeUnit;

    private boolean enabled = true;

    /// The fact's stored coordinates, exactly as loaded.
    ///
    /// Not shown: they are a *pre-transform* offset in the fact's own
    /// frame, so they are a real-world distance only while the placement matrix
    /// has unit scale. The dialog shows the resolved map position instead —
    /// `worldToMap · coords`, which is where the fact actually is — and
    /// carries these through an edit untouched, exactly as a canvas drag does.
    private final double[] loadedCoords = {0, 0};

    /// The placement matrix as loaded, used to resolve and re-derive the position.
    private double[] loadedMatrix = {1, 0, 0, 1, 0, 0};

    /// The text last written into the position boxes. If a box still holds it, the
    /// loaded translation is reused verbatim rather than being recomputed from a
    /// rounded display value — so opening a dialog and pressing OK cannot nudge an
    /// object.
    private String shownPosX = "";
    private String shownPosY = "";

    /// What one map unit means in the real world; never `null` in practice.
    private FloorMapMeasurementUnits measurementUnits;

    /// The fact's size in map units at scale 1, or `null` when it has no
    /// measurable extent — a shape marker is drawn at a fixed screen size, and an
    /// image whose proportions are not yet known has no knowable height. When
    /// null the size fields are hidden and the raw scale factors shown instead,
    /// so the stored value is still visible and editable.
    private double[] baseSize;

    /// The text last written into the size boxes; see [#shownPosX].
    private String shownSizeW = "";
    private String shownSizeH = "";

    /// Whether the user has touched the fill controls since the form was last
    /// populated. The presenter writes the fill only when this is set, so a
    /// stored fill value the picker cannot represent (e.g. `"red"` — SVG
    /// accepts it, an `<input type=color>` cannot) survives an edit of
    /// unrelated fields instead of being coerced to black.
    private boolean fillDirty;

    /// The colour the map actually paints an area of this type when it has no fill
    /// of its own, as resolved by the presenter from the document's type styles.
    ///
    /// The swatch shows this whenever *Default* is ticked, so the picker
    /// never advertises a colour that pressing OK would not produce. Leaving the
    /// last-picked colour showing (or black on a fresh dialog) would promise a fill
    /// that ticking Default does not deliver.
    private String defaultFill = TypeStyle.DEFAULT_COLOUR;

    @Inject
    public FloorMapObjectEditViewImpl(final Binder binder,
                                      final Provider<DateTimePopup> dateTimePopupProvider) {
        widget = binder.createAndBindUi(this);
        effectiveTimeBox.setPopupProvider(dateTimePopupProvider);
        nameControlsForScreenReaders();
        setAreaFieldsVisible(false);
        fillDefaultCheck.setValue(true);
        fillDefaultCheck.addValueChangeHandler(e -> {
            fillDirty = true;
            if (Boolean.TRUE.equals(e.getValue())) {
                // Back to Default — show the colour that will actually be used.
                fillBox.setValue(defaultFill);
            }
        });
        //noinspection unused e
        fillBox.addValueChangeHandler(e -> fillDirty = true);
        // The colour swatch stays clickable even while "Default" is ticked;
        // clicking it means "I want a custom colour", so untick Default (firing
        // its handler) before the native picker opens.
        //noinspection unused e
        fillBox.addDomHandler(e -> {
            if (Boolean.TRUE.equals(fillDefaultCheck.getValue())) {
                fillDefaultCheck.setValue(false, true);
            }
        }, MouseDownEvent.getType());
    }

    /// Gives every control in this form an accessible name.
    ///
    /// The three rows holding a single plain input — Name, Type and Area Fill
    /// Opacity — are named by `identity` in the ui.xml, which produces a
    /// real `<label for>`. The rest cannot be: a `for` attribute only
    /// reaches a labelable element, and these rows hold either a composite widget
    /// (whose root is a wrapper `div` with the input nested inside), an
    /// injected picker view, or two inputs under one label. Each of those is named
    /// here instead — as a group for the row, plus an individual name per input so
    /// "X" and "Y" are distinguishable.
    ///
    /// The trailing "m" suffixes are hidden: every measurement input already
    /// says "in metres" in its own name, so announcing the unit again after each
    /// pair is noise.
    private void nameControlsForScreenReaders() {
        FloorMapAria.group(chooseImgContainer, "Image");

        FloorMapAria.group(fillRow, "Area Fill Colour");
        // The checkbox brings its own "Default" label; only the swatch is unnamed.
        FloorMapAria.label(fillBox, "Area fill colour");

        // DateTimeBox is a composite, so its root is a div and identity= would put
        // the id somewhere <label for> cannot follow.
        FloorMapAria.group(effectiveTimeBox, "Effective From Time");

        FloorMapAria.group(positionRow, "Position in metres");
        FloorMapAria.label(posX, "Position X in metres");
        FloorMapAria.label(posY, "Position Y in metres");

        FloorMapAria.group(sizeRow, "Size in metres");
        FloorMapAria.label(sizeW, "Width in metres");
        FloorMapAria.label(sizeH, "Height in metres");

        FloorMapAria.group(scaleRow, "Scale");
        FloorMapAria.label(w2mSx, "Scale X, as a multiple of natural size");
        FloorMapAria.label(w2mSy, "Scale Y, as a multiple of natural size");

        FloorMapAria.group(rotationRow, "Rotation");
        FloorMapAria.label(w2mRot, "Rotation in degrees, counter-clockwise");

        FloorMapAria.hide(posUnit);
        FloorMapAria.hide(sizeUnit);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public boolean isEffectiveTimeValid() {
        return effectiveTimeBox.getValue() != null;
    }

    @Override
    public long getEffectiveTime() {
        final Long value = effectiveTimeBox.getValue();
        if (value == null) {
            // Unreachable via the OK path, which checks isEffectiveTimeValid() first.
            // Fail loudly rather than unboxing null: this used to throw a bare NPE from
            // inside the dialog's OK handler, before any validation ran and with both
            // buttons already disabled, so the user was left with a dead dialog and no
            // message.
            throw new IllegalStateException(
                    "Effective time is empty or unparseable; check isEffectiveTimeValid() first");
        }
        return value;
    }

    @Override
    public void setEffectiveTime(final long timeMs) {
        effectiveTimeBox.setValue(timeMs);
    }

    @Override
    public double getX() {
        return loadedCoords[0];
    }

    @Override
    public void setX(final double x) {
        loadedCoords[0] = x;
        showPosition();
    }

    @Override
    public double getY() {
        return loadedCoords[1];
    }

    @Override
    public void setY(final double y) {
        loadedCoords[1] = y;
        showPosition();
    }

    @Override
    public void setMeasurementUnits(final FloorMapMeasurementUnits measurementUnits) {
        this.measurementUnits = measurementUnits;
        showPosition();
        showSize();
    }

    @Override
    public void setBaseSize(final double[] baseSize) {
        this.baseSize = baseSize != null && baseSize.length >= 2
                        && baseSize[0] > 0 && baseSize[1] > 0
                ? new double[]{baseSize[0], baseSize[1]}
                : null;
        // Size and scale are two statements of one thing, so only ever offer one.
        sizeGroup.setVisible(this.baseSize != null);
        scaleGroup.setVisible(this.baseSize == null);
        showSize();
    }

    @Override
    public String getName() {
        return nameBox.getText();
    }

    @Override
    public void setName(final String name) {
        nameBox.setText(name == null ? "" : name);
    }

    @Override
    public String getType() {
        return typeBox.getText();
    }

    @Override
    public void setType(final String type) {
        typeBox.setText(type == null ? "" : type);
    }

    @Override
    public void setTypeChangedHandler(final Consumer<String> handler) {
        //noinspection unused e
        typeBox.addKeyUpHandler(e -> handler.accept(typeBox.getText()));
        //noinspection unused e
        typeBox.addValueChangeHandler(e -> handler.accept(typeBox.getText()));
    }

    @Override
    public String getFill() {
        return Boolean.TRUE.equals(fillDefaultCheck.getValue())
                ? ""
                : fillBox.getValue();
    }

    @Override
    public void setDefaultFill(final String hexColour) {
        defaultFill = hexColour == null || hexColour.isEmpty()
                ? TypeStyle.DEFAULT_COLOUR
                : hexColour;
        // Retyping an object (or a fresh dialog) can change which default applies,
        // so a ticked Default must follow it.
        if (Boolean.TRUE.equals(fillDefaultCheck.getValue())) {
            fillBox.setValue(defaultFill);
        }
    }

    @Override
    public void setFill(final String hexColour) {
        final boolean isDefault = hexColour == null || hexColour.isEmpty();
        fillDefaultCheck.setValue(isDefault);
        // Either way the swatch shows the colour this area will be drawn in: its
        // own fill, or the type default it is inheriting.
        fillBox.setValue(isDefault ? defaultFill : hexColour);
        // The swatch stays clickable regardless of Default (clicking it unticks
        // Default); only the form's enabled state gates it.
        fillBox.setEnabled(enabled);
        fillDirty = false;
    }

    @Override
    public boolean isFillDirty() {
        return fillDirty;
    }

    @Override
    public Double getOpacity() {
        final String text = opacityBox.getText();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void setOpacity(final Double opacity) {
        opacityBox.setText(opacity != null ? String.valueOf(opacity) : "");
    }

    @Override
    public boolean isOpacityValid() {
        final String text = opacityBox.getText();
        if (text == null || text.trim().isEmpty()) {
            return true;
        }
        try {
            final double value = Double.parseDouble(text.trim());
            return value >= 0.0 && value <= 1.0;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void setVertexCount(final Integer count) {
        vertexCountLabel.setText(count != null ? String.valueOf(count) : "");
    }

    @Override
    public void setAreaFieldsVisible(final boolean visible) {
        fillGroup.setVisible(visible);
        opacityGroup.setVisible(visible);
        verticesGroup.setVisible(visible);
        // The position boxes stay visible for every fact type: an area has a
        // position too — the centre its outline is stored around.
    }

    @Override
    public void setChooseImgView(final Widget widget) {
        chooseImgContainer.setWidget(widget);
    }

    @Override
    public double[] getWorldToMapMatrix() {
        final String invalid = getGeometryFieldError();
        if (invalid != null) {
            // Unreachable via the OK path, which checks getGeometryFieldError() first.
            // Fail loudly rather than substituting a default: the silent fallbacks below
            // used to turn a typo in a position box into a move to 0 m, and a typo in the
            // rotation box into 0 degrees, with nothing shown to the user.
            throw new IllegalStateException(
                    "Geometry field '" + invalid + "' is not a valid number; "
                    + "check getGeometryFieldError() first");
        }
        final double[] scale = typedScale();
        final double sX = scale[0];
        final double sY = scale[1];
        final double rRad = Math.toRadians(parseDouble(w2mRot.getText(), 0.0));

        final double cos = Math.cos(rRad);
        final double sin = Math.sin(rRad);
        final double a = sX * cos;
        final double b = sX * sin;
        final double c = -sY * sin;
        final double d = sY * cos;

        // Solve the translation so the fact's stored coords land on the typed
        // position, keeping the scale and rotation just read from the form.
        final double[] target = typedPositionMapUnits();
        final FloorMapTransformationMatrix placed =
                new FloorMapTransformationMatrix(a, b, c, d, 0, 0)
                        .placing(loadedCoords[0], loadedCoords[1], target[0], target[1]);
        return new double[]{
                placed.getA(), placed.getB(), placed.getC(),
                placed.getD(), placed.getE(), placed.getF()
        };
    }

    @Override
    public void setWorldToMapMatrix(final double[] m) {
        if (m != null && m.length >= 6) {
            System.arraycopy(m, 0, loadedMatrix, 0, 6);
        } else {
            loadedMatrix = new double[]{1, 0, 0, 1, 0, 0};
        }
        populateMatrixFields(loadedMatrix, w2mSx, w2mSy, w2mRot);
        showPosition();
        showSize();
    }

    /// The position the user has typed, in map units — or, where a box is
    /// untouched, the exact position the fact was loaded at.
    ///
    /// The fallback matters: the boxes show metres rounded for legibility, so
    /// recomputing from the displayed text would quietly shift every object that
    /// passed through the dialog.
    private double[] typedPositionMapUnits() {
        final double[] loaded = resolvedPositionMapUnits();
        return new double[]{
                axisPositionMapUnits(posX, shownPosX, loaded[0]),
                axisPositionMapUnits(posY, shownPosY, loaded[1])
        };
    }

    private double axisPositionMapUnits(final TextBox box,
                                        final String shownText,
                                        final double loadedMapValue) {
        final String text = box.getText() == null
                ? ""
                : box.getText().trim();
        if (text.equals(shownText)) {
            return loadedMapValue;
        }
        return units().toMapUnits(parseDouble(text, 0.0), INPUT_UNIT);
    }

    /// The scale factors to build the matrix from: derived from the typed size
    /// where the fact has a measurable extent, otherwise read from the scale
    /// boxes directly.
    ///
    /// An untouched size box yields the loaded scale exactly, so passing
    /// through the dialog cannot nudge an object's size — the same guard the
    /// position boxes use.
    private double[] typedScale() {
        final double[] loaded = loadedScale();
        if (baseSize == null) {
            return new double[]{
                    parseDouble(w2mSx.getText(), 1.0),
                    parseDouble(w2mSy.getText(), 1.0)};
        }
        return new double[]{
                axisScale(sizeW, shownSizeW, baseSize[0], loaded[0]),
                axisScale(sizeH, shownSizeH, baseSize[1], loaded[1])};
    }

    private double axisScale(final TextBox box,
                             final String shownText,
                             final double baseMapUnits,
                             final double loadedScale) {
        final String text = box.getText() == null
                ? ""
                : box.getText().trim();
        if (text.equals(shownText)) {
            return loadedScale;
        }
        final double sizeMapUnits = units().toMapUnits(parseDouble(text, 0.0), INPUT_UNIT);
        if (sizeMapUnits <= 0 || baseMapUnits <= 0) {
            // A zero or negative size would collapse the matrix and make the
            // object unselectable, with no way back short of editing the store.
            return loadedScale;
        }
        // Keep the sign: a negative scale is how a flipped image is stored.
        return loadedScale < 0
                ? -sizeMapUnits / baseMapUnits
                : sizeMapUnits / baseMapUnits;
    }

    /// The scale factors of the matrix as loaded, sign preserved.
    private double[] loadedScale() {
        final double sX = Math.sqrt(loadedMatrix[0] * loadedMatrix[0]
                                    + loadedMatrix[1] * loadedMatrix[1]);
        final double det = loadedMatrix[0] * loadedMatrix[3] - loadedMatrix[1] * loadedMatrix[2];
        final double sY = (det >= 0 ? 1.0 : -1.0)
                          * Math.sqrt(loadedMatrix[2] * loadedMatrix[2]
                                      + loadedMatrix[3] * loadedMatrix[3]);
        return new double[]{sX, sY};
    }

    /// Writes the fact's size into the boxes, in metres.
    private void showSize() {
        if (baseSize == null) {
            shownSizeW = "";
            shownSizeH = "";
            return;
        }
        final double[] scale = loadedScale();
        shownSizeW = FloorMapMeasurementUnits.formatForInput(
                units().toUnit(baseSize[0] * Math.abs(scale[0]), INPUT_UNIT));
        shownSizeH = FloorMapMeasurementUnits.formatForInput(
                units().toUnit(baseSize[1] * Math.abs(scale[1]), INPUT_UNIT));
        sizeW.setText(shownSizeW);
        sizeH.setText(shownSizeH);
    }

    /// Where the fact actually is in map space: `worldToMap · coords`.
    private double[] resolvedPositionMapUnits() {
        return new FloorMapTransformationMatrix(
                loadedMatrix[0], loadedMatrix[1], loadedMatrix[2],
                loadedMatrix[3], loadedMatrix[4], loadedMatrix[5])
                .transformPoint(loadedCoords[0], loadedCoords[1]);
    }

    /// Writes the resolved position into the boxes, in metres.
    private void showPosition() {
        final double[] map = resolvedPositionMapUnits();
        shownPosX = FloorMapMeasurementUnits.formatForInput(
                units().toUnit(map[0], INPUT_UNIT));
        shownPosY = FloorMapMeasurementUnits.formatForInput(
                units().toUnit(map[1], INPUT_UNIT));
        posX.setText(shownPosX);
        posY.setText(shownPosY);
    }

    private FloorMapMeasurementUnits units() {
        return FloorMapMeasurementUnits.orDefault(measurementUnits);
    }

    @Override
    public String getGeometryFieldError() {
        // Mirrors exactly which boxes getWorldToMapMatrix() actually reads, so the user is
        // never blocked on a value that is about to be ignored:
        //  - a box still holding the text this dialog wrote is not parsed at all, it yields
        //    the loaded value, so it cannot be wrong however odd the formatting looks;
        //  - the size and scale boxes are alternatives, not both - typedScale() reads the
        //    scale boxes only for a fact with no measurable extent, and the size boxes only
        //    for one that has it.
        if (isEdited(posX, shownPosX) && !isFiniteNumber(posX)) {
            return "Position X";
        }
        if (isEdited(posY, shownPosY) && !isFiniteNumber(posY)) {
            return "Position Y";
        }
        if (baseSize == null) {
            if (!isFiniteNumber(w2mSx)) {
                return "Scale X";
            }
            if (!isFiniteNumber(w2mSy)) {
                return "Scale Y";
            }
        } else {
            if (isEdited(sizeW, shownSizeW) && !isFiniteNumber(sizeW)) {
                return "Width";
            }
            if (isEdited(sizeH, shownSizeH) && !isFiniteNumber(sizeH)) {
                return "Height";
            }
        }
        if (!isFiniteNumber(w2mRot)) {
            return "Rotation";
        }
        return null;
    }

    /// Whether the user has changed a box away from the text this dialog last wrote.
    private boolean isEdited(final TextBox box, final String shownText) {
        return !boxText(box).equals(shownText);
    }

    /// Whether the box holds a number the geometry can actually be built from. Blank fails:
    /// an empty position box is not "leave it alone" - the untouched case is handled by
    /// [#isEdited]. NaN and infinity fail too; either would poison the matrix and make
    /// the object unselectable and invisible.
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isFiniteNumber(final TextBox box) {
        try {
            final double value = Double.parseDouble(boxText(box));
            return !Double.isNaN(value) && !Double.isInfinite(value);
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    /// The trimmed text of a box, never `null`.
    private String boxText(final TextBox box) {
        return box.getText() == null
                ? ""
                : box.getText().trim();
    }

    /// Parses a double from the given string, returning `defaultVal` on failure.
    ///
    /// Reached only for values [#getGeometryFieldError()] has already accepted, so
    /// the default is a backstop rather than the silent correction it once was.
    private double parseDouble(final String val, final double defaultVal) {
        try {
            return Double.parseDouble(val.trim());
        } catch (final Exception ex) {
            return defaultVal;
        }
    }

    /// Decomposes a 6-element affine matrix `{a, b, c, d, tx, ty}` into its
    /// scale and rotation fields. Falls back to identity values if the matrix is
    /// null or too short. The translation is not shown as such — it is carried by
    /// the position boxes, resolved through the fact's coordinates.
    private void populateMatrixFields(final double[] m,
                                      final TextBox sx,
                                      final TextBox sy,
                                      final TextBox rot) {
        if (m != null && m.length >= 6) {
            final double sX = Math.sqrt(m[0] * m[0] + m[1] * m[1]);
            // The determinant of the 2×2 rotation-scale sub-matrix (a*d - b*c)
            // encodes the sign of scaleY relative to scaleX.  Using Math.sqrt
            // alone always produces a positive value, which silently loses a
            // negative scaleY (used for Y-axis flipping) on every round-trip.
            final double det = m[0] * m[3] - m[1] * m[2];
            final double sY = (det >= 0 ? 1.0 : -1.0) * Math.sqrt(m[2] * m[2] + m[3] * m[3]);
            final double rotationDeg = Math.round(Math.toDegrees(Math.atan2(m[1], m[0])) * 100.0) / 100.0;

            sx.setText(String.valueOf(Math.round(sX * 100.0) / 100.0));
            sy.setText(String.valueOf(Math.round(sY * 100.0) / 100.0));
            rot.setText(String.valueOf(rotationDeg));
        } else {
            sx.setText("1.0");
            sy.setText("1.0");
            rot.setText("0.0");
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        effectiveTimeBox.setEnabled(enabled);
        setTextBoxesEnabled(enabled,
                nameBox, typeBox,
                posX, posY, sizeW, sizeH, w2mSx, w2mSy, w2mRot,
                opacityBox);
        fillDefaultCheck.setEnabled(enabled);
        fillBox.setEnabled(enabled);
    }

    private static void setTextBoxesEnabled(final boolean enabled, final TextBox... boxes) {
        for (final TextBox box : boxes) {
            box.setEnabled(enabled);
        }
    }

    // --------------------------------------------------------------------------------

    public interface Binder extends UiBinder<Widget, FloorMapObjectEditViewImpl> {

    }
}
