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

import stroom.floormap.client.FloorMapSwatchHtml;
import stroom.floormap.client.presenter.FloorMapLayersPresenter.FloorMapLayersView;
import stroom.floormap.shared.TypeStyle;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.DataTransfer.DropEffect;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.DragEndEvent;
import com.google.gwt.event.dom.client.DragLeaveEvent;
import com.google.gwt.event.dom.client.DragOverEvent;
import com.google.gwt.event.dom.client.DragStartEvent;
import com.google.gwt.event.dom.client.DropEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

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
/// *full → 30% opacity → off*. Changes are pushed to the canvas through the
/// [change handler][#setChangeHandler(Runnable)]; the host presenter reads
/// [#getHiddenTypes()] / [#getDimmedTypes()] and forwards them to
/// `FloorMapCanvasPresenter.setLayerVisibility(...)`.
///
/// Editor-only controls are implemented here and shown when
/// [#setEditorMode(boolean)] is set: drag-and-keyboard reordering (which
/// rewrites the document's [TypeStyle] order, so it persists), a per-layer
/// lock that stops its items being moved on the canvas, and an appearance dialog
/// per layer. The lock is transient client-side state; the order and appearance
/// are document edits.
public class FloorMapLayersPresenter extends MyPresenterWidget<FloorMapLayersView> {

    /// Visibility state per type: 0 = hidden, 1 = 30% opacity, 2 = full.
    private static final int OFF = 0;
    private static final int DIM = 1;
    private static final int FULL = 2;

    private final Map<String, Integer> visibilityByType = new LinkedHashMap<>();
    /// Transient per-type lock state (Editor only): locked items can't be moved.
    private final Set<String> lockedTypes = new HashSet<>();
    /// Types seen in the loaded data but not yet a saved layer (Editor only).
    private final Set<String> seenTypes = new HashSet<>();
    private final FlowPanel list = new FlowPanel();

    private List<TypeStyle> layers = new ArrayList<>();
    private boolean editorMode;
    private Runnable changeHandler;
    private Consumer<List<TypeStyle>> typeStylesEditHandler;
    /// Opens the appearance dialog for a layer and calls back with the edited style.
    private BiConsumer<TypeStyle, Consumer<TypeStyle>> styleEditor;
    /// Runs a full-store type discovery scan (Editor only).
    private Runnable discoverHandler;

    /// Highlight colour for the drag-reorder drop indicator.
    private static final String DROP_INDICATOR = "#2196f3";
    /// Source row index during a drag-reorder, or -1 when not dragging.
    private int dragFromIndex = -1;

    /// The layer whose reorder grip should take focus after the next
    /// [#rebuild()], or `null`.
    ///
    /// Keyboard reordering rebuilds the whole list, which destroys the button
    /// the keystroke came from. Without handing focus back, a keyboard user gets
    /// exactly one move per visit to the panel.
    private String focusGripForType;

    @Inject
    public FloorMapLayersPresenter(final EventBus eventBus,
                                   final FloorMapLayersView view) {
        super(eventBus, view);
        list.addStyleName("floormap-layers-list");
        view.setList(list);
    }

    /// @param editorMode `true` on the Editor tab (enables authoring
    ///         controls - reorder, lock, appearance, discovery); `false` on the Map tab
    public void setEditorMode(final boolean editorMode) {
        this.editorMode = editorMode;
    }

    /// @param changeHandler run whenever a layer's visibility changes, so the host
    ///         can push [#getHiddenTypes()]/[#getDimmedTypes()]
    ///         to the canvas
    public void setChangeHandler(final Runnable changeHandler) {
        this.changeHandler = changeHandler;
    }

    /// @param handler receives the new ordered type-styles list whenever the
    ///         panel reorders layers (Editor only), so the host can apply
    ///         and persist the change. The list order is the z-order.
    public void setTypeStylesEditHandler(final Consumer<List<TypeStyle>> handler) {
        this.typeStylesEditHandler = handler;
    }

    /// @param styleEditor opens the appearance dialog for a layer's [TypeStyle]
    ///         and invokes the callback with the edited style (Editor only)
    public void setStyleEditor(final BiConsumer<TypeStyle, Consumer<TypeStyle>> styleEditor) {
        this.styleEditor = styleEditor;
    }

    /// @param discoverHandler runs a full facts-store type-discovery scan; when
    ///         set (Editor only) a Discover action is shown
    public void setDiscoverHandler(final Runnable discoverHandler) {
        this.discoverHandler = discoverHandler;
        rebuild();
    }

    /// Merges the given discovered types into the saved layers (new types are
    /// appended alphabetically) and persists via the edit handler.
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
        if (typeStylesEditHandler != null) {
            typeStylesEditHandler.accept(new ArrayList<>(newList));
        }
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
        list.clear();
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
                list.add(buildRow(ts, i));
            }
        }

        // Editor-only: types seen in the data but not yet saved appear as
        // provisional rows the user can add as layers.
        if (editorMode) {
            for (final String type : new TreeSet<>(seenTypes)) {
                if (!saved.contains(type)) {
                    list.add(buildProvisionalRow(type));
                }
            }

            // A Discover action that scans the whole facts store for types.
            if (discoverHandler != null) {
                list.add(buildDiscoverFooter());
            }
        }

        // An empty panel is otherwise indistinguishable from a broken one. This says which it is,
        // and the wording differs by tab because so does the remedy: the Editor can add layers,
        // and the read-only Map tab cannot.
        if (list.getWidgetCount() == 0) {
            list.add(buildEmptyPlaceholder());
        }
    }

    /// A line explaining an empty panel.
    ///
    /// A floor map with no type styles saved is a perfectly ordinary state — entities and facts
    /// render with defaults — but a blank panel reads as a failure, and this feature has enough
    /// silent-empty states already. On the Editor tab the message names the action that fills it; on
    /// the Map tab it says where that action lives, since layers cannot be added from here.
    private Widget buildEmptyPlaceholder() {
        final Label label = new Label(editorMode
                ? "No layers yet. Types found in the data appear here, or use Discover types."
                : "No layers configured. They are added on the Editor tab.");
        label.addStyleName("floormap-layer-empty");
        return label;
    }

    private Widget buildDiscoverFooter() {
        final FlowPanel footer = new FlowPanel();
        footer.addStyleName("floormap-layers-footer");

        final InlineSvgButton discover = new InlineSvgButton();
        discover.setSvg(SvgImage.REFRESH);
        discover.setTitle("Discover types from the facts store");
        //noinspection unused event
        discover.addClickHandler(event -> discoverHandler.run());
        footer.add(discover);

        final Label label = new Label("Discover types");
        label.addStyleName("floormap-layers-footer-label");
        // Kept clickable as a larger pointer target for the button beside it. That
        // is allowed — the action itself is fully keyboard-operable via that
        // button — but the label must not be announced, or a screen reader reads
        // "Discover types" twice: once as the button's name, once as loose text
        // that looks interactive and is not focusable.
        label.getElement().setAttribute("aria-hidden", "true");
        //noinspection unused event
        label.addClickHandler(event -> discoverHandler.run());
        footer.add(label);
        return footer;
    }

    /// A discovered-but-unsaved type: name + an add button to make it a layer.
    private Widget buildProvisionalRow(final String type) {
        final FlowPanel row = new FlowPanel();
        row.addStyleName("floormap-layer-row");
        row.addStyleName("floormap-layer-row--provisional");

        final Label name = new Label(type);
        name.addStyleName("floormap-layer-name");
        name.setTitle("Discovered type — not yet a saved layer");
        row.add(name);

        final InlineSvgButton add = new InlineSvgButton();
        add.addStyleName("floormap-layer-add");
        add.setSvg(SvgImage.ADD);
        add.setTitle("Add the discovered type " + type + " as a layer");
        //noinspection unused event
        add.addClickHandler(event -> promote(type));
        row.add(add);
        return row;
    }

    /// Promotes a discovered type to a saved layer and persists via the handler.
    private void promote(final String type) {
        final List<TypeStyle> newList = TypeStyle.merge(
                new ArrayList<>(layers), Collections.singletonList(type));
        layers = newList;
        rebuild();
        if (typeStylesEditHandler != null) {
            typeStylesEditHandler.accept(new ArrayList<>(newList));
        }
    }

    /// Replaces the style for `edited`'s type and persists via the handler.
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
        if (typeStylesEditHandler != null) {
            typeStylesEditHandler.accept(new ArrayList<>(newList));
        }
    }

    /// Moves the layer from index `from` to the given insertion index
    /// (0..size, in current-list terms), then notifies the edit handler with the
    /// new order (Editor only). List order is z-order. `insertionIndex ==
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
        getView().announce(moved.getType() + " layer moved to position "
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
        if (typeStylesEditHandler != null) {
            typeStylesEditHandler.accept(new ArrayList<>(newList));
        }
        return true;
    }

    /// True if the pointer is below the vertical midpoint of the given row.
    private static boolean isBelowMidpoint(final int clientY, final Element rowEl) {
        return clientY > rowEl.getAbsoluteTop() + (rowEl.getOffsetHeight() / 2);
    }

    private Widget buildRow(final TypeStyle ts, final int index) {
        final String type = ts.getType();
        final FlowPanel row = new FlowPanel();
        row.addStyleName("floormap-layer-row");

        // Editor-only: drag a row to reorder the layers (z-order). List order is
        // the z-order — earlier = painted behind, later = painted in front.
        if (editorMode) {
            // Visible drag handle so the row reads as grabbable.
            //
            // A real <button> rather than a Label, because dragging cannot be the
            // only way to reorder (WCAG 2.1.1, and 2.5.7 for the dragging
            // movement itself). Focus the grip and the arrow keys move the layer;
            // the mouse drag below is the same operation by another route.
            //
            // setStyleName (not addStyleName) drops GWT's "gwt-Button" primary
            // style, so the app's button chrome cannot fight the dot-grid
            // background this class paints.
            final Button grip = new Button();
            grip.setStyleName("floormap-layer-grip");
            grip.setTitle("Reorder the " + type
                    + " layer — drag, or use the up and down arrow keys");
            grip.addKeyDownHandler(event -> {
                final int key = event.getNativeKeyCode();
                if (key == KeyCodes.KEY_UP || key == KeyCodes.KEY_DOWN) {
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
            row.add(grip);

            row.getElement().setDraggable(Element.DRAGGABLE_TRUE);
            row.addDomHandler(event -> {
                dragFromIndex = index;
                event.setData("text", String.valueOf(index));
                row.getElement().getStyle().setOpacity(0.4);
            }, DragStartEvent.getType());
            row.addDomHandler(event -> {
                event.preventDefault();
                event.getNativeEvent().getDataTransfer().setDropEffect(DropEffect.MOVE);
                // Indicate drop-above vs drop-below so the last position is reachable.
                final boolean below = isBelowMidpoint(
                        event.getNativeEvent().getClientY(), row.getElement());
                row.getElement().getStyle().setProperty("boxShadow", below
                        ? "inset 0 -2px 0 0 " + DROP_INDICATOR
                        : "inset 0 2px 0 0 " + DROP_INDICATOR);
            }, DragOverEvent.getType());
            //noinspection unused event
            row.addDomHandler(event ->
                    row.getElement().getStyle().clearProperty("boxShadow"), DragLeaveEvent.getType());
            row.addDomHandler(event -> {
                event.preventDefault();
                row.getElement().getStyle().clearProperty("boxShadow");
                final boolean below = isBelowMidpoint(
                        event.getNativeEvent().getClientY(), row.getElement());
                reorderTo(dragFromIndex, index + (below ? 1 : 0));
            }, DropEvent.getType());
            //noinspection unused event
            row.addDomHandler(event -> {
                row.getElement().getStyle().clearOpacity();
                dragFromIndex = -1;
            }, DragEndEvent.getType());
        }

        final int state = visibilityByType.getOrDefault(type, FULL);

        final Label name = new Label(type);
        name.addStyleName("floormap-layer-name");
        applyNameState(name, state);

        final InlineSvgButton eye = new InlineSvgButton();
        eye.addStyleName("floormap-layer-eye");
        applyEyeState(eye, type, state);
        //noinspection unused event
        eye.addClickHandler(event -> {
            // Cycle full → 30% → off → full.
            final int next = (visibilityByType.getOrDefault(type, FULL) + 2) % 3;
            visibilityByType.put(type, next);
            applyEyeState(eye, type, next);
            applyNameState(name, next);
            if (changeHandler != null) {
                changeHandler.run();
            }
        });

        row.add(eye);

        // Editor-only: a lock toggle. Locked layers stay visible but their items
        // can't be moved on the canvas. Transient (per session).
        if (editorMode) {
            final InlineSvgButton lock = new InlineSvgButton();
            lock.addStyleName("floormap-layer-lock");
            applyLockState(lock, type, lockedTypes.contains(type));
            //noinspection unused event
            lock.addClickHandler(event -> {
                final boolean nowLocked = !lockedTypes.contains(type);
                if (nowLocked) {
                    lockedTypes.add(type);
                } else {
                    lockedTypes.remove(type);
                }
                applyLockState(lock, type, nowLocked);
                if (changeHandler != null) {
                    changeHandler.run();
                }
            });
            row.add(lock);
        }

        row.add(name);

        // Editor-only: a swatch previewing the layer's graphic — its image if it
        // has one, otherwise its shape in its colour — which opens the appearance
        // dialog for this layer.
        if (editorMode) {
            // A <button>, not a clickable div: it opens a dialog, so it has to be
            // reachable and activatable from the keyboard. setStyleName drops
            // GWT's "gwt-Button" primary style so the app's button chrome does not
            // box in the preview graphic; the CSS resets what remains.
            final Button swatch = new Button();
            swatch.setStyleName("floormap-layer-swatch");
            swatch.setHTML(FloorMapSwatchHtml.swatch(ts, SWATCH_SIZE_PX));
            // Names the layer: a panel of identically-titled "Edit appearance"
            // buttons tells a screen-reader user nothing about which one they are
            // on. The preview graphic itself is alt="" (decorative), so this
            // title is the button's whole accessible name.
            swatch.setTitle("Edit the appearance of the " + type + " layer ("
                    + (ts.hasGraphic()
                            ? "image & colour)"
                            : "shape & colour)"));
            //noinspection unused event
            swatch.addDomHandler(event -> {
                if (styleEditor != null) {
                    styleEditor.accept(ts, this::applyStyle);
                }
            }, ClickEvent.getType());
            row.add(swatch);
        }

        return row;
    }

    /// Size of each row's graphic preview in pixels.
    private static final int SWATCH_SIZE_PX = 16;

    /// The lowest opacity an *enabled* control's graphic may be drawn at
    /// here and still clear 3:1 against the panel background (WCAG 1.4.11).
    ///
    /// Body text at 0.45 over the light theme's white composites to about
    /// #969696 — 2.96:1, just under. These icons are the only indication of a
    /// layer's visibility and lock state, so they are exactly the "meaningful
    /// non-text content" the rule is about, and cannot be treated as decoration.
    /// Disabled controls are exempt, but none of these are disabled.
    private static final double MIN_ENABLED_ICON_OPACITY = 0.7;

    private void applyLockState(final InlineSvgButton lock,
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
        lock.getElement().getStyle().setOpacity(locked
                ? 0.95
                : MIN_ENABLED_ICON_OPACITY);
    }

    private void applyEyeState(final InlineSvgButton eye,
                               final String type,
                               final int state) {
        switch (state) {
            case OFF:
                eye.setSvg(SvgImage.EYE_OFF);
                eye.setTitle(type + " layer is hidden — click to show");
                eye.getElement().getStyle().setOpacity(MIN_ENABLED_ICON_OPACITY);
                break;
            case DIM:
                eye.setSvg(SvgImage.EYE);
                eye.setTitle(type + " layer is dimmed to 30% — click to hide");
                eye.getElement().getStyle().setOpacity(MIN_ENABLED_ICON_OPACITY);
                break;
            default:
                eye.setSvg(SvgImage.EYE);
                eye.setTitle(type + " layer is visible — click to dim to 30%");
                eye.getElement().getStyle().setOpacity(1.0);
                break;
        }
    }

    /// Dims a layer's name in step with its visibility state.
    ///
    /// The hidden state's floor is 0.65 rather than the icons' 0.7 because this
    /// is text, which needs 4.5:1 rather than 3:1: 0.55 over white composites to
    /// about #7e7e7e — 4.06:1, short of the mark.
    private void applyNameState(final Label name, final int state) {
        final double opacity = state == OFF
                ? 0.65
                : state == DIM
                        ? 0.75
                        : 1.0;
        name.getElement().getStyle().setOpacity(opacity);
    }

    /// View contract: a toolbar area (header/actions) above a scrolling list.
    public interface FloorMapLayersView extends View {

        void setList(Widget listWidget);

        /// Announces `message` through the panel's live region.
        ///
        /// A keyboard reorder produces no visible change a screen reader can pick up
        /// — the row simply appears elsewhere in a list it is not reading — so without
        /// this the move is silent and the user cannot tell whether the keystroke did
        /// anything, let alone where the layer ended up.
        void announce(String message);
    }
}
