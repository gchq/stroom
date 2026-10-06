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

package stroom.floormap.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.document.asset.client.presenter.DocumentAssetDropDownPresenter;
import stroom.floormap.client.ValueAccessorFactory;
import stroom.floormap.client.presenter.FloorMapObjectEditPresenter.FloorMapObjectEditView;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapEditorModel;
import stroom.floormap.shared.FloorMapEntryParser;
import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.floormap.shared.FloorMapGeometry;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.floormap.shared.FloorMapMeasurementUnits;
import stroom.floormap.shared.FloorMapScreenGeometry;
import stroom.floormap.shared.ParsedValue;
import stroom.floormap.shared.TypeStyle;
import stroom.floormap.shared.ValueAccessor;
import stroom.util.shared.TemporalEntry;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;
import java.util.function.Function;
import javax.inject.Inject;

/**
 * Presenter for the properties form used to edit individual floor-map object
 * entries (temporal versions of a fact).
 *
 * <p>Used by the <strong>Editor tab</strong> ({@link FloorMapEditorPresenter}) only, always as a
 * modal OK/Cancel dialog via {@link #show(String, TemporalEntry, Consumer)}. The Map tab held an
 * inline copy of this form once; it no longer does.</p>
 *
 * <h3>Managed form fields</h3>
 * <ul>
 *   <li>Type, Name — free-text identification of the object.</li>
 *   <li>Image — selected from the document-asset dropdown.</li>
 *   <li>X / Y coordinates — the fact's stored pre-transform offset, round-tripped unchanged.
 *       The form does not show or edit them; placement is edited via the matrix below.</li>
 *   <li>Effective time — the timestamp of this temporal version.</li>
 *   <li>World-to-Map matrix — 6-element affine transform. Every fact, background included, is
 *       placed by this one; there is no separate Map-to-Screen field.</li>
 * </ul>
 *
 * <h3>Preconditions</h3>
 * <p>{@link #setMapName(String)} <strong>must</strong> be called before
 * {@link #buildEntry(long, String)} or {@link #show(String, TemporalEntry, Consumer)}
 * is invoked; otherwise an {@link IllegalStateException} is thrown.</p>
 */
public class FloorMapObjectEditPresenter extends MyPresenterWidget<FloorMapObjectEditView> {

    private final DocumentAssetDropDownPresenter documentAssetDropDownPresenter;
    private String objectId;
    private String mapName;
    private FloorMapDoc floorMapDoc;

    /** Supplies image aspect ratios; see {@link #setAspectRatioResolver}. */
    private Function<String, Double> aspectRatioResolver;

    /**
     * The entry the form was last populated from. {@link #buildValue()} starts
     * from this entry's parsed value and overlays the form fields on top, so
     * data the form does not manage — an area's geometry, any custom-mapped
     * fields — survives a property edit instead of being silently rebuilt away.
     */
    private TemporalEntry loadedEntry;

    /** Whether {@link #loadedEntry} carries area geometry. */
    private boolean loadedHasGeometry;

    /**
     * Resolves the JSON path for the given {@link Role} using the current
     * document's value schema.
     *
     * <p>Requires that {@link #setFloorMapDoc(FloorMapDoc)} has been called
     * with a non-null document before any invocation.</p>
     *
     * @param role the field role to resolve
     * @return the dot-separated JSON path for the role, or {@code null}
     *         if the role is not present in the schema
     * @throws NullPointerException if no document has been set
     */
    private String pathForRole(final Role role) {
        return FloorMapEntryParser.findPath(floorMapDoc.getValueSchema(), role);
    }

    /**
     * Stores the map name for later use by {@link #buildEntry(long, String)}.
     *
     * <p>This method <strong>must</strong> be called at least once before
     * any call to {@link #buildEntry(long, String)} or
     * {@link #show(String, TemporalEntry, Consumer)}.</p>
     *
     * @param mapName the temporal-store map name; must not be {@code null}
     * @throws IllegalArgumentException if {@code mapName} is {@code null}
     */
    public void setMapName(final String mapName) {
        if (mapName == null) {
            throw new IllegalArgumentException("mapName must not be null");
        }
        this.mapName = mapName;
    }

    private String requireMapName() {
        if (mapName == null) {
            throw new IllegalStateException(
                    "mapName has not been set — call setMapName() before using this presenter");
        }
        return mapName;
    }

    /**
     * Supplies image aspect ratios, so an image fact's real-world size can be
     * stated and edited. Wired by the hosting tab to its canvas, which has
     * already measured any image it has drawn.
     *
     * @param aspectRatioResolver returns width/height for an image URL, or
     *                            {@code null} when it is not known
     */
    public void setAspectRatioResolver(final Function<String, Double> aspectRatioResolver) {
        this.aspectRatioResolver = aspectRatioResolver;
    }

    /**
     * The fact's size in map units at scale 1 — what the form multiplies by the
     * placement matrix's scale to state a real-world size, and divides a typed
     * size by to set that scale.
     *
     * <p>{@code null} when the fact has no measurable extent, which hides the
     * size fields: a shape glyph is drawn at a fixed screen size and does not
     * scale at all, and an image whose proportions the canvas has not measured
     * yet has a width but no knowable height.</p>
     *
     * @param image    the fact's image path, if any
     * @param vertices the fact's outline in its own frame, if any
     * @return {@code {width, height}} in map units, or {@code null}
     */
    private double[] baseSize(final String image, final double[][] vertices) {
        if (image != null && !image.isEmpty()) {
            final Double aspect = aspectRatioResolver != null
                    ? aspectRatioResolver.apply(image)
                    : null;
            if (aspect == null || aspect <= 0 || Double.isNaN(aspect) || Double.isInfinite(aspect)) {
                return null;
            }
            final double width = FloorMapScreenGeometry.DEFAULT_IMAGE_DISPLAY_WIDTH;
            return new double[]{width, width / aspect};
        }
        // An area is measured across its outline; the vertices are stored in the
        // fact's own frame, so this is its size before the matrix scales it.
        final double[] bounds = FloorMapGeometry.aabb(vertices);
        if (bounds == null) {
            return null;
        }
        final double width = bounds[2] - bounds[0];
        final double height = bounds[3] - bounds[1];
        return width > 0 && height > 0
                ? new double[]{width, height}
                : null;
    }

    /**
     * Sets the floor-map document and configures the asset dropdown to list
     * assets belonging to that document.
     *
     * <p>A {@code null} document is <strong>not</strong> a supported state for
     * editing. There is no fallback to a default value schema: the next call to
     * {@link #pathForRole(Role)} throws {@link NullPointerException}, as its own
     * {@code @throws} records, and {@code buildValue} and {@code resetInputs} do
     * the same. Passing {@code null} is only safe when clearing a dialog that will
     * not be shown again before a real document is set.</p>
     *
     * @param floorMapDoc the floor-map document, or {@code null} to clear
     */
    public void setFloorMapDoc(final FloorMapDoc floorMapDoc) {
        this.floorMapDoc = floorMapDoc;
        documentAssetDropDownPresenter.setDocument(floorMapDoc);
        // Always called before the form is populated, so the position boxes know
        // their scale by the time they are filled in.
        getView().setMeasurementUnits(floorMapDoc != null
                ? floorMapDoc.getMeasurementUnits()
                : null);
    }

    @Inject
    public FloorMapObjectEditPresenter(final EventBus eventBus,
                                       final FloorMapObjectEditView view,
                                       final DocumentAssetDropDownPresenter documentAssetDropDownPresenter) {
        super(eventBus, view);
        this.documentAssetDropDownPresenter = documentAssetDropDownPresenter;
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setChooseImgView(documentAssetDropDownPresenter.getView().asWidget());
        // Typing "area" into a fresh Add-Object dialog reveals the area fields.
        getView().setTypeChangedHandler(this::onTypeChanged);
    }

    /**
     * Re-applies everything the object's type decides: whether the area-only
     * fields are shown, and which default fill the swatch advertises.
     */
    private void onTypeChanged(final String type) {
        updateAreaFieldsVisibility(type);
        getView().setDefaultFill(defaultFillForType(type));
    }

    /**
     * Shows or hides the area-only fields (fill colour, opacity, vertex count):
     * visible when the object is typed {@code "area"} or already carries
     * geometry.
     */
    private void updateAreaFieldsVisibility(final String type) {
        final boolean isArea = isArea(type);
        getView().setAreaFieldsVisible(isArea);
    }

    /**
     * The colour the canvas fills an area of this type with when it carries no
     * fill of its own — resolved against the document's type styles by the same
     * shared rule the renderer uses, so the picker and the map agree.
     */
    private String defaultFillForType(final String type) {
        return TypeStyle.colourForType(type == null ? null : type.trim(),
                floorMapDoc != null ? floorMapDoc.getTypeStyles() : null);
    }

    private boolean isArea(final String type) {
        return (type != null && FloorMapJsonKeys.AREA.equals(type.trim()))
                || loadedHasGeometry;
    }

    /**
     * Shows the properties form as a modal OK/Cancel dialog for a
     * <em>new</em> (not yet persisted) entry.
     *
     * <p>The form is pre-populated from {@code entry}. When the user clicks OK
     * the current form state is built into a {@link TemporalEntry} and passed
     * to {@code onSave}. Clicking Cancel discards the changes. Editing the
     * effective time simply sets the new entry's time — no move/clone
     * question is asked, since there is no persisted version to move.</p>
     *
     * @param caption the dialog title — e.g. "Add Time Properties" or "Add Object"
     * @param entry   the entry to pre-populate the form with; may be {@code null} for a blank form
     * @param onSave  called with the built entry when the user clicks OK
     */
    public void show(final String caption,
                     final TemporalEntry entry,
                     final Consumer<TemporalEntry> onSave) {
        doShow(caption, entry, false, (built, _) -> {
            onSave.accept(built);
            return true;
        });
    }

    /**
     * Shows the properties form as a modal OK/Cancel dialog for an
     * <em>existing</em> entry.
     *
     * <p>Behaves like {@link #show(String, TemporalEntry, Consumer)}, except
     * that when the user changes the effective time they are asked whether to
     * <em>move</em> the version to the new time or <em>clone</em> it (keeping
     * the original version at the old time). The choice is reported via the
     * second argument of {@code onSave}.</p>
     *
     * @param caption the dialog title — e.g. "Edit Time Properties"
     * @param entry   the entry to pre-populate the form with; must not be {@code null}
     * @param onSave  called with the built entry when the user clicks OK; the
     *                boolean is {@code true} when the user chose to clone
     *                rather than move (only possible when the time changed).
     *                Return {@code false} to reject the save and leave the dialog
     *                open with the user's input intact
     */
    public void showForEdit(final String caption,
                            final TemporalEntry entry,
                            final SaveHandler onSave) {
        doShow(caption, entry, true, onSave);
    }

    /**
     * Save handler that can <em>reject</em> what the user entered.
     *
     * <p>Returning {@code false} keeps the dialog open, so a caller that rejects
     * a save (for example because the chosen effective time is already occupied)
     * does not also throw away everything the user typed.</p>
     */
    @FunctionalInterface
    public interface SaveHandler {

        /**
         * @param entry the entry built from the current form state
         * @param clone {@code true} when the user chose to clone rather than move
         * @return {@code true} to accept and close, {@code false} to stay open
         */
        boolean onSave(TemporalEntry entry, boolean clone);
    }

    /**
     * Shared implementation behind {@link #show} and {@link #showForEdit}.
     *
     * <p>The key the dialog will write under is resolved <strong>once, here</strong>
     * and captured for the OK handler, so it cannot drift if the surrounding
     * selection changes while the dialog is open, and cannot be left stale by a
     * caller that forgot {@link #setObject}. For an existing entry it is that
     * entry's own key; only a blank new-object form falls back to
     * {@link #objectId}.</p>
     *
     * @param caption        the dialog title
     * @param entry          the entry to pre-populate from, or {@code null} for a blank form
     * @param askMoveOrClone whether to ask move-or-clone when the effective time changes
     * @param onSave         called with the built entry and the clone flag on OK; the
     *                       dialog stays open if it returns {@code false}
     */
    private void doShow(final String caption,
                        final TemporalEntry entry,
                        final boolean askMoveOrClone,
                        final SaveHandler onSave) {
        final String key = FloorMapEditorModel.resolveEntryKey(entry, objectId);
        loadEntry(entry);
        //noinspection unused e
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(caption)
                .onShow(e -> getView().setEnabled(true))
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        // Every path that declines to close MUST call e.reset(). Clicking OK
                        // puts the dialog into its in-progress state - both buttons disabled
                        // and OK showing a spinner (OkCancelContent.onDialogAction) - and only
                        // reset() or hide() releases it. Returning without either leaves the
                        // dialog open but dead: Escape is the only way out, and it discards
                        // everything the user typed. The three sibling dialogs
                        // (FloorMapSetScalePresenter, FloorMapGroupEditPresenter,
                        // FloorMapLayerStylePresenter) all pass e::reset for this reason.
                        final String type = getView().getType();
                        if (type == null || type.trim().isEmpty()) {
                            AlertEvent.fireError(this,
                                    "Object type must not be empty. "
                                    + "Please enter a type (e.g. 'gate', 'camera', 'person').",
                                    e::reset);
                            return;
                        }
                        if (isArea(type) && !getView().isOpacityValid()) {
                            AlertEvent.fireError(this,
                                    "Area fill opacity must be a number between 0 and 1, "
                                    + "or empty for the default.",
                                    e::reset);
                            return;
                        }
                        final String badGeometryField = getView().getGeometryFieldError();
                        if (badGeometryField != null) {
                            // Checked before reading the matrix, for the same reason as the
                            // effective time below: the geometry boxes are free text, and a
                            // parse failure used to be silently replaced by a default and
                            // saved as a deliberate edit.
                            AlertEvent.fireError(this,
                                    badGeometryField + " must be a number.",
                                    e::reset);
                            return;
                        }
                        if (!getView().isEffectiveTimeValid()) {
                            // Checked before reading the value: DateTimeBox yields null for a
                            // cleared or unparseable field, and unboxing that threw an NPE from
                            // inside this handler - before any validation ran, and with the
                            // buttons already disabled.
                            AlertEvent.fireError(this,
                                    "Effective From Time must be a valid date and time.",
                                    e::reset);
                            return;
                        }
                        // Kept primitive: TemporalEntry.getEffectiveTimeMs() is a Long, so
                        // comparing it against a Long below would ask whether the two are
                        // the same object rather than the same instant.
                        final long time = getView().getEffectiveTime();
                        if (askMoveOrClone && entry != null && entry.getEffectiveTimeMs() != time) {
                            // Effective time changed — ask whether to move or clone.
                            ConfirmEvent.fire(this,
                                    "You have changed the effective time. "
                                    + "Do you want to move the version to the new time? "
                                    + "(OK to move, Cancel to create a new cloned version at the new time)",
                                    move -> {
                                        // Keep the dialog open when the handler rejects, so the
                                        // user's input survives - but reset it, or "open" means
                                        // "open and unusable".
                                        if (onSave.onSave(buildEntry(time, key), !move)) {
                                            e.hide();
                                        } else {
                                            e.reset();
                                        }
                                    });
                        } else {
                            if (onSave.onSave(buildEntry(time, key), false)) {
                                e.hide();
                            } else {
                                e.reset();
                            }
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    /**
     * Stores the object ID. Called by the Editor tab.
     * Does not trigger a server fetch — form population is driven by
     * {@link #loadEntry(TemporalEntry)}.
     *
     * @param objectId the fact key for the object being edited
     */
    public void setObject(final String objectId) {
        this.objectId = objectId;
    }

    /**
     * Loads a temporal entry into the form.
     *
     * <p>Called internally when the dialog opens, and by
     * {@link stroom.floormap.client.presenter.FloorMapEditorPresenter} with {@code null} to clear
     * the form after a delete. It is not driven by the Time List selection. Populates all form
     * fields and enables or disables the form based on whether {@code entry} is non-null.</p>
     *
     * @param entry the entry to display, or {@code null} to clear and disable the form
     */
    public void loadEntry(final TemporalEntry entry) {
        getView().setEnabled(entry != null);
        documentAssetDropDownPresenter.setEnabled(entry != null);
        resetInputs(entry);
    }

    /**
     * Builds a serialised value string from the current view state using
     * the format-independent {@link ValueAccessor} abstraction.
     *
     * @return the serialised value (JSON or XML); never {@code null}
     */
    private String buildValue() {
        final ValueAccessor accessor = ValueAccessorFactory.forFormat(floorMapDoc.getValueFormat());

        // Start from the loaded entry's value and overlay the form fields, so
        // data the form does not manage (area geometry, custom-mapped fields)
        // round-trips a property edit untouched.
        ParsedValue newValue = loadedEntry != null
                ? accessor.parse(loadedEntry.getValue())
                : null;
        if (newValue == null) {
            newValue = accessor.createEmpty("entry");
        }

        accessor.setString(newValue, pathForRole(Role.TYPE), getView().getType());
        accessor.setString(newValue, pathForRole(Role.LABEL), getView().getName());
        accessor.setString(newValue, pathForRole(Role.IMAGE),
                documentAssetDropDownPresenter.getSelectedAssetPath() == null
                        ? ""
                        : documentAssetDropDownPresenter.getSelectedAssetPath());

        accessor.setArray(newValue, pathForRole(Role.POSITION),
                new double[]{getView().getX(), getView().getY()});

        // Every fact — background included — is placed by its WORLD_TO_MAP matrix.
        accessor.setArray(newValue, pathForRole(Role.WORLD_TO_MAP),
                getView().getWorldToMapMatrix());

        // Fill/opacity are written only for areas, so non-area objects never
        // accumulate empty area fields even when the schema maps the roles.
        // An empty fill / blank opacity means "use the default" and removes
        // the field. The fill is written only when the user actually touched
        // the fill controls — a stored value the picker cannot represent
        // (e.g. "red") must survive an edit of unrelated fields. GEOMETRY is
        // never written from the form — it is preserved purely by the overlay
        // above.
        if (isArea(getView().getType())) {
            final String fillPath = pathForRole(Role.FILL);
            if (fillPath != null && getView().isFillDirty()) {
                final String fill = getView().getFill();
                accessor.setString(newValue, fillPath,
                        fill != null && !fill.isEmpty() ? fill : null);
            }
            final String opacityPath = pathForRole(Role.OPACITY);
            if (opacityPath != null) {
                accessor.setNumber(newValue, opacityPath, getView().getOpacity());
            }
        }

        return accessor.serialise(newValue);
    }

    /**
     * Builds a {@link TemporalEntry} from the current view state without
     * making a REST call.
     *
     * <p>The key is passed in rather than read from {@link #objectId} so that an
     * edit is always written under the key of the entry actually being edited —
     * see {@link FloorMapEditorModel#resolveEntryKey}.</p>
     *
     * @param effectiveTimeMs the effective time for the new entry
     * @param key             the fact key to write under
     * @return the constructed entry; never {@code null}
     */
    private TemporalEntry buildEntry(final long effectiveTimeMs, final String key) {
        return new TemporalEntry(requireMapName(), key, effectiveTimeMs, buildValue());
    }

    /**
     * Populates the form fields from the given temporal entry, or resets all
     * fields to sensible defaults if {@code selected} is {@code null}.
     *
     * <p>When an entry is provided its JSON value is parsed to extract type,
     * name, image path, coordinates, and the affine transformation matrices.
     * Any parse errors are silently ignored and the affected fields retain
     * their zero/identity defaults.</p>
     *
     * <p>When {@code selected} is {@code null} and the current
     * {@link #objectId} is the background sentinel, the name and type fields
     * are pre-filled with the background defaults; otherwise they are left
     * blank.</p>
     *
     * @param selected the temporal entry to populate from, or {@code null}
     *                 to reset
     */
    private void resetInputs(final TemporalEntry selected) {
        loadedEntry = selected;
        loadedHasGeometry = false;
        if (selected != null) {
            getView().setEffectiveTime(selected.getEffectiveTimeMs());
            double x = 0.0;
            double y = 0.0;
            String name = "";
            String type = "";
            String img = "";
            String fill = "";
            Double opacity = null;
            Integer vertexCount = null;
            double[][] vertices = null;
            final double[] w2m = new double[]{1.0, 0.0, 0.0, 1.0, 0.0, 0.0};

            try {
                final ValueAccessor accessor = ValueAccessorFactory.forFormat(floorMapDoc.getValueFormat());
                final ParsedValue parsed = accessor.parse(selected.getValue());
                if (parsed != null) {
                    final String parsedName = accessor.getString(parsed, pathForRole(Role.LABEL));
                    if (parsedName != null) {
                        name = parsedName;
                    }
                    final String parsedType = accessor.getString(parsed, pathForRole(Role.TYPE));
                    if (parsedType != null) {
                        type = parsedType;
                    }
                    final String parsedImg = accessor.getString(parsed, pathForRole(Role.IMAGE));
                    if (parsedImg != null) {
                        img = parsedImg;
                    }

                    final double[] coords = accessor.getArray(parsed, pathForRole(Role.POSITION));
                    if (coords != null && coords.length >= 2) {
                        x = coords[0];
                        y = coords[1];
                    }

                    final double[] parsedW2m = accessor.getArray(parsed, pathForRole(Role.WORLD_TO_MAP));
                    if (parsedW2m != null && parsedW2m.length >= 6) {
                        System.arraycopy(parsedW2m, 0, w2m, 0, 6);
                    }

                    final String geometryPath = pathForRole(Role.GEOMETRY);
                    if (geometryPath != null) {
                        final double[] geometry = accessor.getArray(parsed, geometryPath);
                        if (geometry != null && geometry.length >= 6) {
                            loadedHasGeometry = true;
                            vertexCount = geometry.length / 2;
                            vertices = FloorMapEntryParser.parseVertices(geometry);
                        }
                    }
                    final String fillPath = pathForRole(Role.FILL);
                    if (fillPath != null) {
                        final String parsedFill = accessor.getString(parsed, fillPath);
                        if (parsedFill != null) {
                            fill = parsedFill;
                        }
                    }
                    final String opacityPath = pathForRole(Role.OPACITY);
                    if (opacityPath != null) {
                        opacity = accessor.getNumber(parsed, opacityPath);
                    }
                }
            } catch (final Exception ex) {
                // Ignore
            }
            getView().setX(x);
            getView().setY(y);
            getView().setName(name);
            getView().setType(type);
            documentAssetDropDownPresenter.setSelectedAssetPath(img);
            getView().setWorldToMapMatrix(w2m);
            // Before setFill: the swatch shows the default while Default is ticked.
            getView().setDefaultFill(defaultFillForType(type));
            getView().setFill(fill);
            getView().setOpacity(opacity);
            getView().setVertexCount(vertexCount);
            getView().setBaseSize(baseSize(img, vertices));
            updateAreaFieldsVisibility(type);
        } else {
            getView().setEffectiveTime(0L);
            getView().setX(0.0);
            getView().setY(0.0);
            final boolean background = FloorMapJsonKeys.BACKGROUND.equals(objectId);
            getView().setName(background
                    ? FloorMapJsonKeys.BACKGROUND_DISPLAY_NAME
                    : "");
            getView().setType(background
                    ? FloorMapJsonKeys.BACKGROUND
                    : "");
            documentAssetDropDownPresenter.setSelectedAssetPath("");
            getView().setWorldToMapMatrix(new double[]{1.0, 0.0, 0.0, 1.0, 0.0, 0.0});
            getView().setDefaultFill(defaultFillForType(background
                    ? FloorMapJsonKeys.BACKGROUND
                    : null));
            getView().setFill("");
            getView().setOpacity(null);
            getView().setVertexCount(null);
            getView().setBaseSize(null);
            updateAreaFieldsVisibility(null);
        }
    }

    // --------------------------------------------------------------------------------

    /**
     * View contract for the object-edit properties form.
     *
     * <p>Implementations provide the UI widgets for all editable fields:
     * effective time, coordinates, name, type, image chooser, and the two
     * affine-transform matrices.</p>
     */
    public interface FloorMapObjectEditView extends View {

        /**
         * Whether the effective-time field currently holds a parseable date-time.
         *
         * <p>{@code DateTimeBox} yields {@code null} for text it cannot parse and for a
         * cleared box, so this must be checked before {@link #getEffectiveTime()}.</p>
         */
        boolean isEffectiveTimeValid();

        /**
         * Returns the effective-time value entered by the user, in epoch milliseconds.
         *
         * @throws IllegalStateException if the field is empty or unparseable — call
         *                               {@link #isEffectiveTimeValid()} first
         */
        long getEffectiveTime();

        /** Sets the effective-time field to the given epoch-millisecond value. */
        void setEffectiveTime(long timeMS);

        /**
         * Returns the fact's stored coordinates, unchanged.
         *
         * <p>These are a pre-transform offset in the fact's own frame, not a
         * position on the map, so the form neither shows nor edits them: the
         * position boxes carry the resolved map position and write it into the
         * placement matrix. The value round-trips so an edit cannot disturb
         * it.</p>
         */
        double getX();

        /** Supplies the fact's stored X coordinate. */
        void setX(double x);

        /** Returns the fact's stored Y coordinate, unchanged. */
        double getY();

        /** Supplies the fact's stored Y coordinate. */
        void setY(double y);

        /**
         * Sets what one map unit means in the real world, so the position boxes
         * can be shown in metres.
         *
         * @param measurementUnits the document's units; {@code null} uses the
         *                         default scale
         */
        void setMeasurementUnits(FloorMapMeasurementUnits measurementUnits);

        /**
         * Sets the fact's size in map units at scale 1, so the form can state
         * and edit its real-world size.
         *
         * @param baseSize {@code {width, height}}, or {@code null} when the fact
         *                 has no measurable extent — the size fields are then
         *                 hidden and the raw scale factors shown instead
         */
        void setBaseSize(double[] baseSize);

        /** Returns the object display name entered by the user. */
        String getName();

        /** Sets the object display name field. */
        void setName(String name);

        /** Returns the object type entered by the user. */
        String getType();

        /** Sets the object type field. */
        void setType(String type);

        /**
         * Registers a callback invoked (with the current type text) as the
         * user edits the type field, so type-dependent fields can be shown or
         * hidden live.
         */
        void setTypeChangedHandler(Consumer<String> handler);

        /**
         * Returns the explicit area fill colour (7-char hex), or {@code ""}
         * when the type default should be used.
         */
        String getFill();

        /** Sets the area fill colour; {@code null}/empty selects the type default. */
        void setFill(String hexColour);

        /**
         * Tells the view which colour the map draws an area of this type in when
         * it has no fill of its own, so the swatch can show it while
         * <em>Default</em> is ticked instead of a colour that would not be used.
         *
         * @param hexColour the resolved default (7-char hex); {@code null}/empty
         *                  falls back to {@link TypeStyle#DEFAULT_COLOUR}
         */
        void setDefaultFill(String hexColour);

        /**
         * {@code true} once the user has touched the fill controls since the
         * last {@link #setFill}; the fill is only written back when set, so a
         * stored value the picker cannot represent survives unrelated edits.
         */
        boolean isFillDirty();


        /** Returns the area fill opacity, or {@code null} when unset (default). */
        Double getOpacity();

        /** Sets the area fill opacity field; {@code null} clears it. */
        void setOpacity(Double opacity);

        /**
         * {@code true} when the opacity field is blank or holds a number in
         * {@code [0, 1]}; used to block saving a value that would otherwise
         * be silently discarded or persisted out of range.
         */
        boolean isOpacityValid();

        /** Shows the (read-only) polygon vertex count; {@code null} clears it. */
        void setVertexCount(Integer count);

        /**
         * Shows or hides the area-only fields (fill, opacity, vertex count).
         * The point-position X/Y fields are inversely visible — an area is
         * placed purely by its world-to-map matrix.
         */
        void setAreaFieldsVisible(boolean visible);

        /**
         * Installs the image-chooser (asset dropdown) widget into the form.
         *
         * @param widget the dropdown widget; must not be {@code null}
         */
        void setChooseImgView(Widget widget);

        /**
         * The display name of the first geometry field holding something that is not a
         * usable number, or {@code null} when they are all fine.
         *
         * <p>Must be checked before {@link #getWorldToMapMatrix()}: the position, size,
         * scale and rotation boxes are free text, and each one silently became a default
         * on a parse failure - a typo in a position box moved the object to 0 m, a typo in
         * the rotation box straightened it - so a mistake was committed as a real edit
         * with nothing shown to the user.</p>
         *
         * @return the offending field's label, e.g. {@code "Position X"}, or {@code null}
         */
        String getGeometryFieldError();

        /**
         * Returns the 6-element world-to-map affine transformation matrix.
         *
         * @throws IllegalStateException if a geometry field is not a usable number — call
         *                               {@link #getGeometryFieldError()} first
         */
        double[] getWorldToMapMatrix();

        /** Sets the 6-element world-to-map affine transformation matrix. */
        void setWorldToMapMatrix(double[] matrix);

        /**
         * Enables or disables all form fields.
         *
         * @param enabled {@code true} to enable editing, {@code false} to
         *                disable (grey-out) all inputs
         */
        void setEnabled(final boolean enabled);
    }
}
