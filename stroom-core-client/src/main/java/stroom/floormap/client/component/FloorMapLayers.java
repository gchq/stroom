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
import stroom.floormap.shared.TypeStyle;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.LinkButton;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.DataTransfer.DropEffect;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.DragEndEvent;
import com.google.gwt.event.dom.client.DragLeaveEvent;
import com.google.gwt.event.dom.client.DragOverEvent;
import com.google.gwt.event.dom.client.DragStartEvent;
import com.google.gwt.event.dom.client.DropEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// The **Layers** panel — a dock tab listing the floor map's type
/// layers (front-to-back = z-order), each with a three-state visibility control.
///
/// Layers map to record types; the ordered list comes from the document's
/// [TypeStyle] entries (via [#setLayers(List)]). Per-layer visibility
/// is a *transient* client-side overlay (not persisted): each layer cycles
/// *full → 30% opacity → off*. Changes are pushed to the canvas through
/// [#setOnChange(Runnable)]; the owner reads
/// [#getHiddenTypes()] / [#getDimmedTypes()] and forwards them to
/// `FloorMapCanvasController.setLayerVisibility(...)`.
///
/// Editor-only controls are implemented here and shown when
/// [#setEditorMode(boolean)] is set: drag-and-keyboard reordering (which
/// rewrites the document's [TypeStyle] order, so it persists), a per-layer
/// lock that stops its items being moved on the canvas, and an appearance dialog
/// per layer. The lock is transient client-side state; the order and appearance
/// are document edits. While the document is read only ([#setReadOnly(boolean)]) the editing
/// controls are disabled: the grips, the swatches, a discovered type's Add and Discover. Visibility
/// and lock, which change only the view, still work.
///
/// It makes no REST calls and uses no bus, so it is a component its owners (the Map and Editor
/// tabs) make and give their callbacks.
public class FloorMapLayers extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    /// Visibility state per type: 0 = hidden, 1 = 30% opacity, 2 = full.
    private static final int OFF = 0;
    private static final int DIM = 1;
    private static final int FULL = 2;

    /// Size of each row's graphic preview in pixels.
    private static final int SWATCH_SIZE_PX = 16;

    private static final String ROW_DRAGGING = "floor-map-layers__row--dragging";
    private static final String ROW_DROP_ABOVE = "floor-map-layers__row--drop-above";
    private static final String ROW_DROP_BELOW = "floor-map-layers__row--drop-below";

    @UiField
    FlowPanel list;
    @UiField
    Label editorEmpty;
    @UiField
    Label mapEmpty;
    @UiField
    FlowPanel discoverFooter;
    @UiField
    ToolbarButton discover;
    @UiField
    Label discoverLabel;
    @UiField
    DivElement statusRegion;

    private final Map<String, Integer> visibilityByType = new LinkedHashMap<>();
    /// Transient per-type lock state (Editor only): locked items can't be moved.
    private final Set<String> lockedTypes = new HashSet<>();
    /// Types seen in the loaded data but not yet a saved layer (Editor only).
    private final Set<String> seenTypes = new HashSet<>();
    /// The rows [#rebuild()] adds to the list, before its empty messages and footer.
    private final List<Widget> rows = new ArrayList<>();

    private List<TypeStyle> layers = new ArrayList<>();
    private boolean editorMode;
    private Runnable onChange = () -> {
    };
    private Consumer<List<TypeStyle>> onTypeStylesEdit = typeStyles -> {
    };
    /// Opens the appearance dialog for a layer and calls back with the edited style.
    private BiConsumer<TypeStyle, Consumer<TypeStyle>> onEditStyle;
    /// Runs a full-store type discovery scan (Editor only).
    private Runnable onDiscover;
    /// Source row index during a drag-reorder, or -1 when not dragging.
    private int dragFromIndex = -1;

    /// The layer whose reorder grip should take focus after the next
    /// [#rebuild()], or `null`.
    ///
    /// Keyboard reordering rebuilds the whole list, which destroys the button
    /// the keystroke came from. Without handing focus back, a keyboard user gets
    /// exactly one move per visit to the panel.
    private String focusGripForType;

    /// Makes the panel, for the Map tab, with no layers.
    public FloorMapLayers() {
        initWidget(BINDER.createAndBindUi(this));
        discover.setOnClick(this::discover);
        //noinspection unused e
        discoverLabel.addClickHandler(e -> discover());
        rebuild();
    }

    /// @param editorMode `true` on the Editor tab (enables authoring
    ///         controls - reorder, lock, appearance, discovery); `false` on the Map tab
    public void setEditorMode(final boolean editorMode) {
        this.editorMode = editorMode;
        rebuild();
    }

    /// @param onChange run whenever a layer's visibility or lock changes, so the owner
    ///         can push [#getHiddenTypes()]/[#getDimmedTypes()]
    ///         to the canvas
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onTypeStylesEdit receives the new ordered type-styles list whenever the
    ///         panel edits the layers (Editor only), so the owner can apply
    ///         and persist the change. The list order is the z-order.
    public void setOnTypeStylesEdit(final Consumer<List<TypeStyle>> onTypeStylesEdit) {
        this.onTypeStylesEdit = onTypeStylesEdit;
    }

    /// @param onEditStyle opens the appearance dialog for a layer's [TypeStyle]
    ///         and invokes the callback with the edited style (Editor only)
    public void setOnEditStyle(final BiConsumer<TypeStyle, Consumer<TypeStyle>> onEditStyle) {
        this.onEditStyle = onEditStyle;
    }

    /// @param onDiscover runs a full facts-store type-discovery scan; when
    ///         set (Editor only) a Discover action is shown
    public void setOnDiscover(final Runnable onDiscover) {
        this.onDiscover = onDiscover;
        rebuild();
    }

    /// Merges the given discovered types into the saved layers (new types are
    /// appended alphabetically) and persists via [#setOnTypeStylesEdit(Consumer)].
    ///
    /// @param types the discovered type names
    public void mergeDiscovered(final Collection<String> types) {
        final int before = layers.size();
        final List<TypeStyle> newList = TypeStyle.merge(new ArrayList<>(layers), types);
        if (newList.size() == before) {
            return;
        }
        layers = newList;
        rebuild();
        onTypeStylesEdit.accept(new ArrayList<>(newList));
    }

    /// Sets the types observed in the currently-loaded data. Any that aren't yet
    /// a saved layer are shown (Editor only) as provisional rows the user can add.
    ///
    /// @param types the observed types; `null` treated as empty
    public void setSeenTypes(final Set<String> types) {
        final Set<String> next = types != null
                ? types
                : Collections.emptySet();
        if (!next.equals(seenTypes)) {
            seenTypes.clear();
            seenTypes.addAll(next);
            rebuild();
        }
    }

    /// Sets the ordered layers from the document's type styles. Existing per-type
    /// visibility is preserved; newly-appearing types default to fully visible,
    /// and state for types no longer present is dropped.
    ///
    /// @param typeStyles the ordered type styles; `null` treated as empty
    public void setLayers(final List<TypeStyle> typeStyles) {
        layers = typeStyles != null
                ? typeStyles
                : new ArrayList<>();

        final Map<String, Integer> next = new LinkedHashMap<>();
        final Set<String> present = new HashSet<>();
        for (final TypeStyle ts : layers) {
            final String type = ts.getType();
            if (type != null) {
                next.put(type, visibilityByType.getOrDefault(type, FULL));
                present.add(type);
            }
        }
        visibilityByType.clear();
        visibilityByType.putAll(next);
        lockedTypes.retainAll(present);
        rebuild();
    }

    /// @return types that are currently hidden (not drawn or hit-tested)
    public Set<String> getHiddenTypes() {
        return typesInState(OFF);
    }

    /// @return types that are currently dimmed to 30% opacity
    public Set<String> getDimmedTypes() {
        return typesInState(DIM);
    }

    /// @return types currently locked against movement (Editor only)
    public Set<String> getLockedTypes() {
        return new HashSet<>(lockedTypes);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        rebuild();
    }

    private void discover() {
        if (onDiscover != null && !isReadOnly()) {
            onDiscover.run();
        }
    }

    private Set<String> typesInState(final int state) {
        final Set<String> result = new HashSet<>();
        for (final Map.Entry<String, Integer> entry : visibilityByType.entrySet()) {
            if (entry.getValue() == state) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private void rebuild() {
        for (final Widget row : rows) {
            row.removeFromParent();
        }
        rows.clear();

        final Set<String> saved = new HashSet<>();
        for (int i = 0; i < layers.size(); i++) {
            final TypeStyle ts = layers.get(i);
            // Null-tolerant because this runs on every document read: a stored typeStyles
            // array carrying a literal null would otherwise take out the whole Layers
            // panel on open, rather than costing one row. Note the filtering here is
            // load-bearing beyond this loop — moveBy() relies on a null-typed layer having
            // no row, and therefore no grip to reorder from.
            if (ts != null && ts.getType() != null) {
                saved.add(ts.getType());
                addRow(buildRow(ts, i));
            }
        }

        // Editor-only: types seen in the data but not yet saved appear as
        // provisional rows the user can add as layers.
        if (editorMode) {
            for (final String type : new TreeSet<>(seenTypes)) {
                if (!saved.contains(type)) {
                    addRow(buildProvisionalRow(type));
                }
            }
        }

        // A Discover action that scans the whole facts store for types.
        final boolean showDiscover = editorMode && onDiscover != null;
        discoverFooter.setVisible(showDiscover);
        discover.setEnabled(!isReadOnly());

        // A floor map with no type styles saved is a perfectly ordinary state — entities and
        // facts render with defaults — but a blank panel reads as a failure. On the Editor tab the
        // message names the action that fills it; on the Map tab it says where that action lives,
        // since layers cannot be added from here.
        final boolean empty = rows.isEmpty() && !showDiscover;
        editorEmpty.setVisible(empty && editorMode);
        mapEmpty.setVisible(empty && !editorMode);
    }

    // Adds a row after the others, before the empty messages and footer
    private void addRow(final Widget row) {
        list.insert(row, rows.size());
        rows.add(row);
    }

    /// A discovered-but-unsaved type: name + an add button to make it a layer.
    private Widget buildProvisionalRow(final String type) {
        final FlowPanel row = new FlowPanel();
        row.addStyleName("floor-map-layers__row");
        row.addStyleName("floor-map-layers__row--provisional");

        final Label name = new Label(type);
        name.addStyleName("floor-map-layers__name");
        name.setTitle("Discovered type — not yet a saved layer");
        row.add(name);

        final Toolbar toolbar = new Toolbar(type + " layer actions");
        toolbar.addStyleName("floor-map-layers__toolbar");
        final ToolbarButton add = toolbar.button(
                SvgImage.ADD, "Add the discovered type " + type + " as a layer", () -> promote(type));
        add.addStyleName("floor-map-layers__add");
        add.setEnabled(!isReadOnly());
        row.add(toolbar);
        return row;
    }

    /// Promotes a discovered type to a saved layer and persists it.
    private void promote(final String type) {
        final List<TypeStyle> newList = TypeStyle.merge(
                new ArrayList<>(layers), Collections.singletonList(type));
        layers = newList;
        rebuild();
        onTypeStylesEdit.accept(new ArrayList<>(newList));
    }

    /// Replaces the style for `edited`'s type and persists it.
    private void applyStyle(final TypeStyle edited) {
        final List<TypeStyle> newList = new ArrayList<>(layers);
        for (int k = 0; k < newList.size(); k++) {
            if (Objects.equals(newList.get(k).getType(), edited.getType())) {
                newList.set(k, edited);
                break;
            }
        }
        layers = newList;
        rebuild();
        onTypeStylesEdit.accept(new ArrayList<>(newList));
    }

    /// Moves the layer from index `from` to the given insertion index
    /// (0..size, in current-list terms), then gives the owner the new order
    /// (Editor only). List order is z-order. `insertionIndex ==
    /// size` drops at the very bottom.
    private void reorderTo(final int from, final int insertionIndex) {
        if (from < 0 || from >= layers.size()) {
            return;
        }
        final List<TypeStyle> newList = new ArrayList<>(layers);
        final TypeStyle moved = newList.remove(from);
        // Removing the dragged item shifts everything after it down by one.
        int insert = from < insertionIndex
                ? insertionIndex - 1
                : insertionIndex;
        insert = Math.max(0, Math.min(insert, newList.size()));
        newList.add(insert, moved);
        commitOrder(newList);
    }

    /// Moves the layer at `from` by `delta` positions — the keyboard
    /// equivalent of a drag-reorder, bound to the arrow keys on a row's grip.
    ///
    /// Stated as a signed step rather than reusing [#reorderTo]'s
    /// insertion index because the two speak different languages: an insertion
    /// index has to account for the moved item's own removal (hence the
    /// `-1` adjustment there), and threading "move down one" through that
    /// conversion reads as an off-by-one waiting to happen. A move that would
    /// leave the list is a no-op, so holding the key at either end does
    /// nothing.
    private void moveBy(final int from, final int delta) {
        final int to = from + delta;
        if (from < 0 || from >= layers.size() || to < 0 || to >= layers.size()) {
            return;
        }
        final List<TypeStyle> newList = new ArrayList<>(layers);
        final TypeStyle moved = newList.remove(from);
        newList.add(to, moved);
        if (!commitOrder(newList)) {
            return;
        }
        // Announced only once the commit has been accepted, so the position quoted is
        // the one that stuck. 1-based, to match what the user sees rather than the index.
        //
        // getType() is used unguarded: rebuild() only builds a row — and therefore a grip —
        // for a layer with a non-null type, so a null-typed layer has no control to reorder
        // from and cannot reach here. An earlier "Layer" fallback implied otherwise, which
        // contradicted that filtering. The count is of layers, so in Editor mode it will be
        // smaller than the number of visible rows, which also lists provisional types.
        announce(moved.getType() + " layer moved to position "
                 + (to + 1) + " of " + newList.size());
    }

    /// Adopts `newList` as the layer order, redraws and persists — the one
    /// path out of both the drag and the keyboard reorder, so they cannot drift.
    /// A no-change reorder (dropped back where it started) returns without
    /// touching the document.
    ///
    /// @return `true` if the order changed and was committed. The keyboard path
    ///         uses this to decide whether to announce a move: announcing one that was
    ///         refused here would tell a screen-reader user something happened when
    ///         nothing did.
    private boolean commitOrder(final List<TypeStyle> newList) {
        if (newList.equals(layers)) {
            return false;
        }
        layers = newList;
        rebuild();
        onTypeStylesEdit.accept(new ArrayList<>(newList));
        return true;
    }

    /// Announces `message` through the panel's live region.
    ///
    /// A keyboard reorder produces no visible change a screen reader can pick up
    /// — the row simply appears elsewhere in a list it is not reading — so without
    /// this the move is silent and the user cannot tell whether the keystroke did
    /// anything, let alone where the layer ended up.
    private void announce(final String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        // Deliberately no "same as last time" guard, unlike the canvas view. Every
        // announcement here is one deliberate keystroke, and identical text can legitimately
        // repeat: move a layer down, drag it back — silent, because the pointer user can see
        // it — then move it down again, and the correct message is byte-identical to the last
        // one. Suppressing it would leave that keystroke unannounced, which is the exact
        // failure this region exists to prevent. The canvas can afford a guard because it
        // re-announces on state it recomputes, not on discrete commands.
        //
        // Setting the same text twice does still need to look like a change to assistive
        // technology, so clear the region first.
        if (message.equals(statusRegion.getInnerText())) {
            statusRegion.setInnerText("");
        }
        statusRegion.setInnerText(message);
    }

    /// True if the pointer is below the vertical midpoint of the given row.
    private static boolean isBelowMidpoint(final int clientY, final Element rowEl) {
        return clientY > rowEl.getAbsoluteTop() + (rowEl.getOffsetHeight() / 2);
    }

    private Widget buildRow(final TypeStyle ts, final int index) {
        final String type = ts.getType();
        final FlowPanel row = new FlowPanel();
        row.addStyleName("floor-map-layers__row");

        // Editor-only: drag a row to reorder the layers (z-order). List order is
        // the z-order — earlier = painted behind, later = painted in front. A read-only
        // document's rows show the grip, disabled, and can't be dragged.
        if (editorMode) {
            row.add(buildGrip(type, index));
        }
        if (editorMode && !isReadOnly()) {
            row.getElement().setDraggable(Element.DRAGGABLE_TRUE);
            row.addDomHandler(event -> {
                dragFromIndex = index;
                event.setData("text", String.valueOf(index));
                row.addStyleName(ROW_DRAGGING);
            }, DragStartEvent.getType());
            row.addDomHandler(event -> {
                event.preventDefault();
                event.getNativeEvent().getDataTransfer().setDropEffect(DropEffect.MOVE);
                // Indicate drop-above vs drop-below so the last position is reachable.
                final boolean below = isBelowMidpoint(
                        event.getNativeEvent().getClientY(), row.getElement());
                row.setStyleName(ROW_DROP_BELOW, below);
                row.setStyleName(ROW_DROP_ABOVE, !below);
            }, DragOverEvent.getType());
            //noinspection unused event
            row.addDomHandler(event -> clearDropIndicator(row), DragLeaveEvent.getType());
            row.addDomHandler(event -> {
                event.preventDefault();
                clearDropIndicator(row);
                final boolean below = isBelowMidpoint(
                        event.getNativeEvent().getClientY(), row.getElement());
                reorderTo(dragFromIndex, index + (below
                        ? 1
                        : 0));
            }, DropEvent.getType());
            //noinspection unused event
            row.addDomHandler(event -> {
                row.removeStyleName(ROW_DRAGGING);
                dragFromIndex = -1;
            }, DragEndEvent.getType());
        }

        final int state = visibilityByType.getOrDefault(type, FULL);

        final Label name = new Label(type);
        name.addStyleName("floor-map-layers__name");
        applyNameState(name, state);

        // The row's eye and lock buttons, as one tab stop
        final Toolbar toolbar = new Toolbar(type + " layer actions");
        toolbar.addStyleName("floor-map-layers__toolbar");

        // Its icon and name are set by applyEyeState
        final ToolbarButton eye = toolbar.button(SvgImage.EYE, type + " layer visibility", null);
        eye.addStyleName("floor-map-layers__eye");
        applyEyeState(eye, type, state);
        eye.setOnClick(() -> {
            // Cycle full → 30% → off → full.
            final int next = (visibilityByType.getOrDefault(type, FULL) + 2) % 3;
            visibilityByType.put(type, next);
            applyEyeState(eye, type, next);
            applyNameState(name, next);
            onChange.run();
        });

        // Editor-only: a lock toggle. Locked layers stay visible but their items
        // can't be moved on the canvas. Transient (per session).
        if (editorMode) {
            // Its icon and name are set by applyLockState
            final ToolbarButton lock = toolbar.button(SvgImage.UNLOCKED_DEFAULT_COLOUR, type + " layer lock", null);
            lock.addStyleName("floor-map-layers__lock");
            applyLockState(lock, type, lockedTypes.contains(type));
            lock.setOnClick(() -> {
                final boolean nowLocked = !lockedTypes.contains(type);
                if (nowLocked) {
                    lockedTypes.add(type);
                } else {
                    lockedTypes.remove(type);
                }
                applyLockState(lock, type, nowLocked);
                onChange.run();
            });
        }

        row.add(toolbar);
        row.add(name);

        // Editor-only: a swatch previewing the layer's graphic — its image if it
        // has one, otherwise its shape in its colour — which opens the appearance
        // dialog for this layer.
        if (editorMode) {
            row.add(buildSwatch(ts));
        }

        return row;
    }

    // Visible drag handle so the row reads as grabbable.
    //
    // A real <button> rather than a Label, because dragging cannot be the only way to reorder
    // (WCAG 2.1.1, and 2.5.7 for the dragging movement itself). Focus the grip and the arrow keys
    // move the layer; the mouse drag on the row is the same operation by another route.
    private Widget buildGrip(final String type, final int index) {
        final LinkButton grip = new LinkButton();
        // Only its own style (no link-button), so nothing fights the dot-grid background it paints
        grip.setStyleName("floor-map-layers__grip");
        grip.setTitle("Reorder the " + type
                      + " layer — drag, or use the up and down arrow keys");
        grip.setAriaDisabled(isReadOnly());
        grip.addKeyDownHandler(event -> {
            final int key = event.getNativeKeyCode();
            if (!isReadOnly() && (key == KeyCodes.KEY_UP || key == KeyCodes.KEY_DOWN)) {
                // Otherwise the arrow scrolls the Layers panel instead.
                event.preventDefault();
                event.stopPropagation();
                // Rebuilding the list destroys this button, so ask rebuild()
                // to restore focus to the moved layer's grip — without it,
                // one keypress moves the layer and focus falls back to the
                // document, making a second press impossible.
                focusGripForType = type;
                moveBy(index, key == KeyCodes.KEY_UP
                        ? -1
                        : 1);
            }
        });
        if (Objects.equals(type, focusGripForType)) {
            focusGripForType = null;
            // Deferred: the widget is not attached to the DOM yet, and
            // setFocus() on a detached element is a no-op.
            Scheduler.get().scheduleDeferred(() -> grip.setFocus(true));
        }
        return grip;
    }

    // A <button>, not a clickable div: it opens a dialog, so it has to be reachable and activatable
    // from the keyboard
    private Widget buildSwatch(final TypeStyle ts) {
        final LinkButton swatch = new LinkButton();
        // Only its own style (no link-button); the CSS resets the browser's button look so nothing
        // boxes in the preview graphic
        swatch.setStyleName("floor-map-layers__swatch");
        swatch.setHTML(FloorMapSwatchHtml.swatch(ts, SWATCH_SIZE_PX));
        // Names the layer: a panel of identically-titled "Edit appearance"
        // buttons tells a screen-reader user nothing about which one they are
        // on. The preview graphic itself is alt="" (decorative), so this
        // title is the button's whole accessible name.
        swatch.setTitle("Edit the appearance of the " + ts.getType() + " layer ("
                        + (ts.hasGraphic()
                ? "image & colour)"
                : "shape & colour)"));
        // It opens the layer's appearance dialog, unless the document is read only
        swatch.setOpensDialog(true);
        swatch.setAriaDisabled(isReadOnly());
        swatch.setOnClick(() -> {
            if (onEditStyle != null) {
                onEditStyle.accept(ts, this::applyStyle);
            }
        });
        return swatch;
    }

    private static void clearDropIndicator(final Widget row) {
        row.removeStyleName(ROW_DROP_ABOVE);
        row.removeStyleName(ROW_DROP_BELOW);
    }

    // Its icon's opacity follows from the state class (floor-map-layers.css): never under 0.7, the
    // lowest an enabled control's graphic may be drawn at here and still clear 3:1 against the
    // panel (WCAG 1.4.11), as these icons are the only sign of a layer's visibility and lock
    private static void applyLockState(final ToolbarButton lock,
                                       final String type,
                                       final boolean locked) {
        lock.setSvg(locked
                ? SvgImage.LOCKED_DEFAULT_COLOUR
                : SvgImage.UNLOCKED_DEFAULT_COLOUR);
        // Names the layer as well as the state, so the accessible name is
        // unambiguous in a panel of otherwise identical lock buttons.
        lock.setTitle(locked
                ? type + " layer is locked — items can’t be moved (click to unlock)"
                : type + " layer is unlocked — items can be moved (click to lock)");
        lock.setStyleName("floor-map-layers__lock--locked", locked);
    }

    private static void applyEyeState(final ToolbarButton eye,
                                      final String type,
                                      final int state) {
        eye.setStyleName("floor-map-layers__eye--off", state == OFF);
        eye.setStyleName("floor-map-layers__eye--dim", state == DIM);
        switch (state) {
            case OFF:
                eye.setSvg(SvgImage.EYE_OFF);
                eye.setTitle(type + " layer is hidden — click to show");
                break;
            case DIM:
                eye.setSvg(SvgImage.EYE);
                eye.setTitle(type + " layer is dimmed to 30% — click to hide");
                break;
            default:
                eye.setSvg(SvgImage.EYE);
                eye.setTitle(type + " layer is visible — click to dim to 30%");
                break;
        }
    }

    // Dims a layer's name in step with its visibility state (floor-map-layers.css)
    private static void applyNameState(final Label name, final int state) {
        name.setStyleName("floor-map-layers__name--off", state == OFF);
        name.setStyleName("floor-map-layers__name--dim", state == DIM);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapLayers> {

    }
}
