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

package stroom.widget.layout.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuRequest;
import stroom.widget.menu.client.Separator;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.tab.client.GlobalResizeObserver;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.animation.client.AnimationScheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.ui.ComplexPanel;
import com.google.gwt.user.client.ui.ProvidesResize;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// Panes side by side (or one above another) with splitters between them that the user drags, or
/// moves with the keyboard, to share the space between the panes. It replaced GWT's
/// `SplitLayoutPanel` (Stroom's copies of it, `ThinSplitLayoutPanel` and `MySplitLayoutPanel`).
///
/// It is a CSS grid, so the panes are in the page in the order they are shown, and a screen reader
/// and the Tab key meet them in that order. Each splitter is a focusable `role="separator"` (the
/// WAI-ARIA window splitter pattern): Left and Right (Up and Down when the panes are stacked) move
/// it [#KEY_STEP_PX], or [#BIG_KEY_STEP_PX] with Shift; Page Up and Page Down grow and shrink its
/// pane by [#BIG_KEY_STEP_PX]; Home and End move it to its limits; and Enter collapses its pane to
/// its smallest and restores it. A splitter can be dragged with a mouse, a finger or a pen, and
/// follows a right-to-left page.
///
/// Each pane has a size: a share of the split pane (`50%`), which it keeps as the split pane is
/// resized, a fixed size (`250px`), or the rest of the space (`*`). One pane takes the rest; if
/// none is given `*`, the last does, and every other pane must be given a size (a split pane
/// missing one fails when it is shown). A splitter resizes the pane on its side away from the pane
/// that takes the rest, and that pane gives or takes the space.
///
/// ```
/// <l:SplitPane sizes="30%, *" names="Users, Groups">
///     <g:SimplePanel ui:field="users"/>
///     <g:SimplePanel ui:field="groups"/>
/// </l:SplitPane>
/// ```
///
/// When the split pane is resized (e.g. with the window), the pane taking the rest gives or takes
/// the space, until it is down to its smallest size ([#setMinSizes]); then the other panes give up
/// space, down to their smallest sizes. Each grows back to its own size as the split pane widens.
/// (GWT's split panels kept a fixed pane's size however small the panel became, so the pane beside
/// it could be squeezed to nothing.)
///
/// A pane collapsed to nothing (e.g. with Enter) is `inert`, so the focus can't go into it unseen.
/// Double clicking a splitter collapses and restores its pane, as Enter does. A pane is restored
/// to the size it had, or if it was dragged closed, to the size it was given.
///
/// A pane can be maximised (the others shrink to nothing, their splitters left to drag them back)
/// and restored: from a splitter's menu (a right click or long press on it, or the context menu
/// key when it has the focus), which offers to maximise the pane either side of it, so a pane can
/// be resized without dragging (WCAG 2.5.7); or with Alt+M ([KeyBinding.Action#MAXIMISE_PANE]),
/// which maximises the pane holding the focus, in the split pane nearest it, and restores it.
/// [#setOnResized] is told when the user has resized a pane.
///
/// Panes split both ways nest one split pane inside another. A pane's widget fills the pane
/// (`split-pane.css`), and is told whenever its pane changes size if it [RequiresResize], so it
/// needs no `onResize` from the split pane's parents.
public class SplitPane extends ComplexPanel implements RequiresResize, ProvidesResize {

    /// How far an arrow key moves a splitter, in pixels.
    public static final int KEY_STEP_PX = 10;

    /// How far Shift and an arrow key, or Page Up or Page Down, move a splitter, in pixels.
    public static final int BIG_KEY_STEP_PX = 50;

    private static final String BLOCK = "split-pane";
    private static final String PANE = BLOCK + "__pane";
    private static final String SPLITTER = BLOCK + "__splitter";
    // A pane or splitter that isn't shown
    private static final String PANE_HIDDEN = PANE + "--hidden";
    private static final String SPLITTER_HIDDEN = SPLITTER + "--hidden";
    private static final String VERTICAL = BLOCK + "--vertical";
    private static final String THIN = BLOCK + "--thin";
    // A state, but one ARIA has no attribute for (see split-pane.css)
    private static final String RESIZING = BLOCK + "--resizing";
    private static final String REST = "*";

    private final List<Pane> panes = new ArrayList<>();
    private final List<Element> splitters = new ArrayList<>();
    private boolean vertical;
    private List<String> sizes = List.of();
    private List<String> names = List.of();
    private List<String> minSizes = List.of();

    private Runnable onResized;
    // The maximised pane, or -1 if none is
    private int maximised = -1;

    // The splitter being dragged (or -1), where the drag started and the size of its pane then, and
    // where the pointer has moved to since the panes were last laid out (applied once a frame)
    private int dragSplitter = -1;
    private double dragStartPosition;
    private double dragStartSize;
    private double dragPosition;
    private boolean dragFramePending;
    private boolean dragResized;

    /// Makes a split pane with its panes side by side.
    public SplitPane() {
        setElement(Document.get().createDivElement());
        setStyleName(BLOCK);
        listenForMaximise(getElement());
    }

    /// @param vertical Whether the panes are stacked one above another, rather than side by side.
    public void setVertical(final boolean vertical) {
        this.vertical = vertical;
        setStyleName(VERTICAL, vertical);
        for (final Element splitter : splitters) {
            setOrientation(splitter);
        }
        layout();
    }

    /// @param thin Whether the splitters are thin lines, rather than gaps between the panes.
    public void setThin(final boolean thin) {
        setStyleName(THIN, thin);
    }

    /// Sets the panes' sizes, in order, e.g. `"30%, *"` or `"*, 400px"`.
    ///
    /// @param sizes For each pane, a share of the split pane (e.g. `30%`), a fixed size (e.g.
    ///              `400px`) or `*` for the rest of the space. The last pane's may be left out, if
    ///              it takes the rest.
    public void setSizes(final String sizes) {
        this.sizes = split(sizes);
        for (final Pane pane : panes) {
            pane.size = null;
        }
        layout();
    }

    /// Sets the panes' names, in order, e.g. `"Users, Groups"`. A splitter is announced by the name
    /// of the pane it resizes, so every pane a splitter resizes (each but the one taking the rest)
    /// must have one; a split pane missing one fails when it is shown, as it does for a size.
    ///
    /// @param names For each pane, its name.
    public void setNames(final String names) {
        this.names = split(names);
        updateAria();
    }

    /// Sets the panes' smallest sizes, in order, e.g. `"100px, 200px"`. Dragging a splitter stops
    /// before a pane is made smaller than this.
    ///
    /// @param minSizes For each pane, its smallest size, in pixels (the `px` is optional).
    public void setMinSizes(final String minSizes) {
        this.minSizes = split(minSizes);
        layout();
    }

    /// Sets what is told when the user has resized a pane: when they let go of a splitter they
    /// dragged, or move one with the keyboard or a double click. It isn't told of the split pane
    /// itself being resized, or of sizes set in code.
    ///
    /// @param onResized What is told, e.g. to save the panes' sizes with [#getPaneSize].
    public void setOnResized(final Runnable onResized) {
        this.onResized = onResized;
    }

    /// Adds a pane holding a widget, after the others.
    ///
    /// @param widget The pane's widget, which fills it.
    @Override
    public void add(final Widget widget) {
        if (!panes.isEmpty()) {
            final Element splitter = createSplitter();
            splitters.add(splitter);
            getElement().appendChild(splitter);
        }
        final Element element = Document.get().createDivElement();
        element.setClassName(PANE);
        element.setId(Document.get().createUniqueId());
        getElement().appendChild(element);
        final Pane pane = new Pane(widget, element);
        panes.add(pane);
        add(widget, element);
        if (isAttached()) {
            observe(pane);
        }
        layout();
    }

    /// Removes a pane, with the splitter before it (or after it, if it is the first).
    ///
    /// @param widget The pane's widget.
    /// @return Whether it was a pane's widget.
    @Override
    public boolean remove(final Widget widget) {
        final int index = indexOf(widget);
        if (index < 0 || !super.remove(widget)) {
            return false;
        }
        final Pane pane = panes.remove(index);
        GlobalResizeObserver.removeListener(pane.element);
        pane.element.removeFromParent();
        if (!splitters.isEmpty()) {
            splitters.remove(Math.max(0, index - 1)).removeFromParent();
        }
        layout();
        return true;
    }

    /// Hides or shows a pane, with its splitter.
    ///
    /// @param widget The pane's widget.
    /// @param hidden Whether the pane is hidden.
    public void setPaneHidden(final Widget widget, final boolean hidden) {
        panes.get(requireIndexOf(widget)).hidden = hidden;
        layout();
    }

    /// Sets a pane's size, as [#setSizes] does for all of them.
    ///
    /// @param widget The pane's widget.
    /// @param size   A share of the split pane (e.g. `30%`), a fixed size (e.g. `400px`) or `*`.
    public void setPaneSize(final Widget widget, final String size) {
        panes.get(requireIndexOf(widget)).size = size.trim();
        layout();
    }

    /// @param widget The pane's widget.
    /// @return The pane's size as it was set or dragged to, e.g. `30%`, `400px` or `*`, or null if
    /// it has none.
    public String getPaneSize(final Widget widget) {
        return sizeOf(requireIndexOf(widget));
    }

    /// Maximises a pane: the others shrink to nothing (their splitters stay, so they can be dragged
    /// back), until [#restore] is called or a splitter is moved.
    ///
    /// @param widget The pane's widget.
    public void maximise(final Widget widget) {
        maximised = requireIndexOf(widget);
        layout();
    }

    /// Restores the panes to their sizes before one was maximised.
    public void restore() {
        maximised = -1;
        layout();
    }

    /// Does nothing for the panes' widgets, which are told when their own panes are resized
    /// (whether by a splitter or by the split pane's parents), but brings the splitters' values up
    /// to date.
    @Override
    public void onResize() {
        updateAria();
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        for (final Pane pane : panes) {
            observe(pane);
        }
        layout();
    }

    @Override
    protected void onUnload() {
        for (final Pane pane : panes) {
            GlobalResizeObserver.removeListener(pane.element);
        }
        super.onUnload();
    }

    // --------------------------------------------------------------------------------
    // Layout

    // Sets the grid's tracks, each shown pane and splitter in order, once the split pane is shown
    // (a template sets its sizes and adds its panes in no set order)
    private void layout() {
        if (!isAttached()) {
            return;
        }
        final int rest = restIndex();
        final StringBuilder tracks = new StringBuilder();
        for (int i = 0; i < panes.size(); i++) {
            final Pane pane = panes.get(i);
            if (i > 0) {
                final Element splitter = splitters.get(i - 1);
                final boolean shown = isSplitterShown(i - 1);
                setShown(splitter, shown, SPLITTER_HIDDEN);
                if (shown) {
                    tracks.append("var(--split-pane__splitter-size) ");
                }
            }
            setShown(pane.element, !pane.hidden, PANE_HIDDEN);
            if (!pane.hidden) {
                tracks.append(track(i, rest)).append(' ');
            }
        }
        // The columns, or the rows when stacked (split-pane.css)
        CssProperties.set(getElement(), "--split-pane__tracks", tracks.toString().trim());
        updateAria();
    }

    private String track(final int index, final int rest) {
        if (maximised >= 0) {
            // The maximised pane takes all the space; the others, none (whatever their minimums)
            return index == maximised
                    ? "minmax(0px, 1fr)"
                    : "0px";
        }
        final int min = minSizeOf(index);
        if (index == rest) {
            return "minmax(" + min + "px, 1fr)";
        }
        final String size = sizeOf(index);
        if (size == null) {
            throw new IllegalStateException("The split pane's " + describe(index)
                                            + " has no size: give it one with sizes, e.g. \"30%\" or \"400px\"");
        }
        // Its own size when there is room; down to its smallest once the rest has given all it can
        return "minmax(" + min + "px, " + size + ")";
    }

    // A splitter is shown if the panes either side of it are
    private boolean isSplitterShown(final int splitterIndex) {
        return !panes.get(splitterIndex).hidden && !panes.get(splitterIndex + 1).hidden;
    }

    // The pane that takes the rest of the space: the one given *, else the last
    private int restIndex() {
        int rest = -1;
        for (int i = 0; i < panes.size(); i++) {
            if (REST.equals(sizeOf(i))) {
                if (rest >= 0) {
                    throw new IllegalStateException("The split pane's " + describe(rest) + " and "
                                                    + describe(i) + " both take the rest (*): only one can");
                }
                rest = i;
            }
        }
        return rest >= 0
                ? rest
                : panes.size() - 1;
    }

    // The pane's size as set or dragged to, else as given by sizes, or null if it has none
    private String sizeOf(final int index) {
        final Pane pane = panes.get(index);
        if (pane.size != null) {
            return pane.size;
        }
        if (index < sizes.size() && !sizes.get(index).isEmpty()) {
            return sizes.get(index);
        }
        return null;
    }

    private int minSizeOf(final int index) {
        if (index < minSizes.size()) {
            final String minSize = minSizes.get(index).replace("px", "").trim();
            if (!minSize.isEmpty()) {
                return (int) Double.parseDouble(minSize);
            }
        }
        return 0;
    }

    private String describe(final int index) {
        final String name = nameOf(index);
        return name.isEmpty()
                ? "pane " + (index + 1)
                : "'" + name + "' pane";
    }

    private String nameOf(final int index) {
        return index < names.size()
                ? names.get(index)
                : "";
    }

    // --------------------------------------------------------------------------------
    // Resizing

    // The pane a splitter resizes: the one on its side away from the pane that takes the rest
    private int controlledPane(final int splitterIndex) {
        return splitterIndex < restIndex()
                ? splitterIndex
                : splitterIndex + 1;
    }

    // Whether moving the splitter on, through the panes' order, grows its pane
    private boolean growsForward(final int splitterIndex) {
        return controlledPane(splitterIndex) == splitterIndex;
    }

    // Whether the panes' order runs against the screen's (side by side panes on a right-to-left
    // page run from right to left)
    private boolean isReversed() {
        return !vertical && "rtl".equals(computedDirection(getElement()));
    }

    // How much a move on screen (right or down is positive) grows a splitter's pane
    private double growth(final int splitterIndex, final double moved) {
        final double forward = isReversed()
                ? -moved
                : moved;
        return growsForward(splitterIndex)
                ? forward
                : -forward;
    }

    private double paneSize(final int index) {
        final Element element = panes.get(index).element;
        return vertical
                ? element.getOffsetHeight()
                : element.getOffsetWidth();
    }

    private double containerSize() {
        return vertical
                ? getElement().getClientHeight()
                : getElement().getClientWidth();
    }

    private double maxSize(final int splitterIndex) {
        final int rest = restIndex();
        return paneSize(controlledPane(splitterIndex)) + paneSize(rest) - minSizeOf(rest);
    }

    // Resizes a splitter's pane, within its limits, keeping its size a share or a fixed size
    private void resize(final int splitterIndex, final double size) {
        leaveMaximised();
        final int controlled = controlledPane(splitterIndex);
        final double clamped = Math.max(minSizeOf(controlled), Math.min(size, maxSize(splitterIndex)));
        final Pane pane = panes.get(controlled);
        final String current = sizeOf(controlled);
        if (current != null && current.endsWith("%")) {
            final double container = containerSize();
            if (container <= 0) {
                return;
            }
            pane.size = round(clamped * 100 / container) + "%";
        } else {
            pane.size = Math.round(clamped) + "px";
        }
        layout();
    }

    // Once a pane is maximised, moving a splitter carries on from the panes as they are shown: each
    // takes the size it is shown at as its own (a share stays a share)
    private void leaveMaximised() {
        if (maximised < 0) {
            return;
        }
        final double container = containerSize();
        final int rest = restIndex();
        for (int i = 0; i < panes.size(); i++) {
            final Pane pane = panes.get(i);
            if (i != rest && !pane.hidden) {
                final String size = sizeOf(i);
                pane.size = size != null && size.endsWith("%") && container > 0
                        ? round(paneSize(i) * 100 / container) + "%"
                        : Math.round(paneSize(i)) + "px";
            }
        }
        maximised = -1;
    }

    private void toggleMaximised(final int index) {
        maximised = maximised == index
                ? -1
                : index;
        layout();
        resized();
    }

    private static String round(final double value) {
        final double rounded = Math.round(value * 100) / 100.0;
        return rounded == Math.floor(rounded)
                ? String.valueOf((long) rounded)
                : String.valueOf(rounded);
    }

    // Called from the splitters' listeners

    private void onPointerDown(final Element splitter, final double position) {
        dragSplitter = splitters.indexOf(splitter);
        dragStartPosition = position;
        dragPosition = position;
        dragStartSize = paneSize(controlledPane(dragSplitter));
        dragResized = false;
        addStyleName(RESIZING);
    }

    // Pointer moves come faster than the screen is drawn, and laying the panes out (e.g. a large
    // table in one) can be slow, so the latest is applied once before each frame
    private void onPointerMove(final double position) {
        if (dragSplitter < 0) {
            return;
        }
        dragPosition = position;
        if (!dragFramePending) {
            dragFramePending = true;
            AnimationScheduler.get().requestAnimationFrame(timestamp -> {
                dragFramePending = false;
                applyDrag();
            });
        }
    }

    private void applyDrag() {
        if (dragSplitter >= 0 && dragPosition != dragStartPosition) {
            resize(dragSplitter, dragStartSize + growth(dragSplitter, dragPosition - dragStartPosition));
            dragResized = true;
        }
    }

    private void onPointerUp() {
        if (dragSplitter < 0) {
            return;
        }
        // Where the pointer was let go, if its last move is still waiting for a frame
        applyDrag();
        dragSplitter = -1;
        removeStyleName(RESIZING);
        if (dragResized) {
            resized();
        }
    }

    private void onDoubleClick(final Element splitter) {
        final int splitterIndex = splitters.indexOf(splitter);
        final int controlled = controlledPane(splitterIndex);
        toggleCollapsed(splitterIndex, controlled, paneSize(controlled));
        resized();
    }

    // Alt+M anywhere in a pane (or on a splitter, for the pane it resizes); returns whether used
    private boolean onPaneKeyDown(final NativeEvent event, final Element target) {
        if (!KeyBinding.isShortcut(event, Action.MAXIMISE_PANE)) {
            return false;
        }
        final int index = paneHolding(target);
        if (index < 0) {
            return false;
        }
        toggleMaximised(index);
        return true;
    }

    // The pane an element is in, or the pane a splitter resizes, or -1 if it is in neither
    private int paneHolding(final Element element) {
        final int splitterIndex = splitters.indexOf(element);
        if (splitterIndex >= 0) {
            return controlledPane(splitterIndex);
        }
        for (int i = 0; i < panes.size(); i++) {
            if (panes.get(i).element.isOrHasChild(element)) {
                return i;
            }
        }
        return -1;
    }

    // A splitter's menu: maximise the pane either side of it, or restore them. Alt+M is hinted on
    // the item it would do (from the splitter, it maximises the pane the splitter resizes)
    private void onContextMenu(final Element splitter, final int x, final int y) {
        final int splitterIndex = splitters.indexOf(splitter);
        final int controlled = controlledPane(splitterIndex);
        final List<Item> items = new ArrayList<>();
        int priority = 0;
        for (final int index : new int[]{splitterIndex, splitterIndex + 1}) {
            items.add(new IconMenuItem.Builder()
                    .priority(++priority)
                    .icon(SvgImage.MAXIMISE)
                    .text("Maximise " + paneLabel(index, splitterIndex))
                    .enabled(maximised != index)
                    .action(maximised < 0 && index == controlled
                            ? Action.MAXIMISE_PANE
                            : null)
                    .command(() -> toggleMaximised(index))
                    .build());
        }
        items.add(new Separator(++priority));
        items.add(new IconMenuItem.Builder()
                .priority(++priority)
                .icon(SvgImage.MINIMISE)
                .text("Restore Pane Sizes")
                .enabled(maximised >= 0)
                .action(maximised >= 0
                        ? Action.MAXIMISE_PANE
                        : null)
                .command(() -> toggleMaximised(maximised))
                .build());
        MenuRequest.fire(splitter, "Pane Layout", items, new PopupPosition(x, y));
    }

    // A pane's name, else where it is beside the splitter, e.g. "Left Pane"
    private String paneLabel(final int index, final int splitterIndex) {
        final String name = nameOf(index);
        if (!name.isEmpty()) {
            return name;
        }
        final boolean before = index <= splitterIndex;
        final String side;
        if (vertical) {
            side = before
                    ? "Top"
                    : "Bottom";
        } else {
            side = before != isReversed()
                    ? "Left"
                    : "Right";
        }
        return side + " Pane";
    }

    private void resized() {
        if (onResized != null) {
            onResized.run();
        }
    }

    // Returns whether the key was used
    private boolean onKeyDown(final Element splitter, final String key, final boolean shift) {
        final int splitterIndex = splitters.indexOf(splitter);
        final int controlled = controlledPane(splitterIndex);
        final double size = paneSize(controlled);
        final int step = shift
                ? BIG_KEY_STEP_PX
                : KEY_STEP_PX;
        final String back = vertical
                ? "ArrowUp"
                : "ArrowLeft";
        final String forward = vertical
                ? "ArrowDown"
                : "ArrowRight";
        if (back.equals(key)) {
            resize(splitterIndex, size + growth(splitterIndex, -step));
        } else if (forward.equals(key)) {
            resize(splitterIndex, size + growth(splitterIndex, step));
        } else if (shift) {
            // Shift only changes an arrow key's step
            return false;
        } else if ("PageUp".equals(key)) {
            resize(splitterIndex, size + BIG_KEY_STEP_PX);
        } else if ("PageDown".equals(key)) {
            resize(splitterIndex, size - BIG_KEY_STEP_PX);
        } else if ("Home".equals(key)) {
            resize(splitterIndex, minSizeOf(controlled));
        } else if ("End".equals(key)) {
            resize(splitterIndex, maxSize(splitterIndex));
        } else if ("Enter".equals(key)) {
            toggleCollapsed(splitterIndex, controlled, size);
        } else {
            return false;
        }
        resized();
        return true;
    }

    // Collapses the pane to its smallest, or restores it to where it was
    private void toggleCollapsed(final int splitterIndex, final int controlled, final double size) {
        leaveMaximised();
        final Pane pane = panes.get(controlled);
        if (size > minSizeOf(controlled)) {
            pane.sizeBeforeCollapse = sizeOf(controlled);
            resize(splitterIndex, minSizeOf(controlled));
        } else {
            // To where it was, or if it was dragged closed, to the size it was given
            final String restored = pane.sizeBeforeCollapse != null
                    ? pane.sizeBeforeCollapse
                    : givenSizeOf(controlled);
            if (restored != null) {
                pane.size = restored;
                pane.sizeBeforeCollapse = null;
                layout();
            }
        }
    }

    private String givenSizeOf(final int index) {
        return index < sizes.size() && !sizes.get(index).isEmpty()
                ? sizes.get(index)
                : null;
    }

    // --------------------------------------------------------------------------------
    // Splitters

    private Element createSplitter() {
        final Element splitter = Document.get().createDivElement();
        splitter.setClassName(SPLITTER);
        splitter.setAttribute("role", "separator");
        splitter.setAttribute("tabindex", "0");
        setOrientation(splitter);
        listen(splitter);
        return splitter;
    }

    // A separator's orientation is its line's: side by side panes are split by a vertical line
    private void setOrientation(final Element splitter) {
        splitter.setAttribute("aria-orientation", vertical
                ? "horizontal"
                : "vertical");
    }

    // Each splitter's value is the size of the pane it resizes, as a percentage of the split pane;
    // a pane collapsed to nothing is inert
    private void updateAria() {
        if (!isAttached()) {
            return;
        }
        for (int i = 0; i < panes.size(); i++) {
            final Pane pane = panes.get(i);
            setInert(pane.element, !pane.hidden && paneSize(i) < 1);
        }
        final double container = containerSize();
        for (int i = 0; i < splitters.size(); i++) {
            final Element splitter = splitters.get(i);
            final int controlled = controlledPane(i);
            splitter.setAttribute("aria-controls", panes.get(controlled).element.getId());
            final String name = nameOf(controlled);
            if (name.isEmpty()) {
                throw new IllegalStateException("The split pane's " + describe(controlled)
                                                + " has no name, which its splitter is announced by: "
                                                + "give it one with names, e.g. \"Users, Groups\"");
            }
            splitter.setAttribute("aria-label", name);
            if (container > 0 && isSplitterShown(i)) {
                final String now = percent(paneSize(controlled), container);
                splitter.setAttribute("aria-valuemin", percent(minSizeOf(controlled), container));
                splitter.setAttribute("aria-valuemax", percent(maxSize(i), container));
                splitter.setAttribute("aria-valuenow", now);
                // Said instead of the bare number, e.g. "30% of the width"
                splitter.setAttribute("aria-valuetext", now + "% of the " + (vertical
                        ? "height"
                        : "width"));
            }
        }
    }

    private static String percent(final double size, final double container) {
        return String.valueOf(Math.round(size * 100 / container));
    }

    private native void listen(Element splitter) /*-{
        var self = this;
        var position = function (event) {
            return self.@stroom.widget.layout.client.SplitPane::vertical ? event.clientY : event.clientX;
        };
        splitter.addEventListener('pointerdown', function (event) {
            if (event.button !== 0) {
                return;
            }
            // Keep the pointer's events while it drags, even over another pane or a frame (an event
            // made by a script has no pointer to capture)
            try {
                splitter.setPointerCapture(event.pointerId);
            } catch (e) {
                return;
            }
            // The press itself gives the splitter the focus, as the pointer's (so not shown as the
            // keyboard's, as focusing it from script would be); text isn't selected while
            // dragging, by split-pane.css
            self.@stroom.widget.layout.client.SplitPane::onPointerDown(*)(splitter, position(event));
        });
        splitter.addEventListener('pointermove', function (event) {
            if (splitter.hasPointerCapture(event.pointerId)) {
                self.@stroom.widget.layout.client.SplitPane::onPointerMove(*)(position(event));
            }
        });
        var end = function () {
            self.@stroom.widget.layout.client.SplitPane::onPointerUp()();
        };
        splitter.addEventListener('pointerup', end);
        splitter.addEventListener('pointercancel', end);
        splitter.addEventListener('lostpointercapture', end);
        splitter.addEventListener('dblclick', function (event) {
            event.preventDefault();
            self.@stroom.widget.layout.client.SplitPane::onDoubleClick(*)(splitter);
        });
        // A right click, a long press, or the context menu key when it has the focus (placed by the
        // splitter when the browser gives no pointer position)
        splitter.addEventListener('contextmenu', function (event) {
            event.preventDefault();
            event.stopPropagation();
            var box = splitter.getBoundingClientRect();
            var atPointer = event.clientX !== 0 || event.clientY !== 0;
            var x = atPointer ? event.clientX : box.left + box.width / 2;
            var y = atPointer ? event.clientY : box.top + Math.min(box.height / 2, 40);
            self.@stroom.widget.layout.client.SplitPane::onContextMenu(*)(splitter, Math.round(x), Math.round(y));
        });
        splitter.addEventListener('keydown', function (event) {
            if (event.altKey || event.ctrlKey || event.metaKey) {
                return;
            }
            if (self.@stroom.widget.layout.client.SplitPane::onKeyDown(*)(splitter, event.key, event.shiftKey)) {
                event.preventDefault();
                event.stopPropagation();
            }
        });
    }-*/;

    // Keys pressed anywhere in the split pane; the nearest split pane to the focus takes Alt+M
    private native void listenForMaximise(Element element) /*-{
        var self = this;
        element.addEventListener('keydown', function (event) {
            if (self.@stroom.widget.layout.client.SplitPane::onPaneKeyDown(*)(event, event.target)) {
                event.preventDefault();
                event.stopPropagation();
            }
        });
    }-*/;

    private static native void setInert(Element element, boolean inert) /*-{
        if (element.inert !== inert) {
            element.inert = inert;
        }
    }-*/;

    private static native String computedDirection(Element element) /*-{
        return element.ownerDocument.defaultView.getComputedStyle(element).direction;
    }-*/;

    // --------------------------------------------------------------------------------
    // Helpers

    private void observe(final Pane pane) {
        GlobalResizeObserver.addListener(pane.element, element -> {
            if (pane.widget instanceof final RequiresResize requiresResize) {
                requiresResize.onResize();
            }
            updateAria();
        });
    }

    private int indexOf(final Widget widget) {
        for (int i = 0; i < panes.size(); i++) {
            if (panes.get(i).widget == widget) {
                return i;
            }
        }
        return -1;
    }

    private int requireIndexOf(final Widget widget) {
        final int index = indexOf(widget);
        if (index < 0) {
            throw new IllegalArgumentException("Not a pane's widget");
        }
        return index;
    }

    private static void setShown(final Element element, final boolean shown, final String hidden) {
        if (shown) {
            element.removeClassName(hidden);
        } else {
            element.addClassName(hidden);
        }
    }

    private static List<String> split(final String list) {
        final List<String> items = new ArrayList<>();
        if (list != null && !list.isBlank()) {
            for (final String item : list.split(",")) {
                items.add(item.trim());
            }
        }
        return items;
    }

    // --------------------------------------------------------------------------------

    // A pane: its widget, its element, and its size if set or dragged (else from the sizes given)
    private static final class Pane {

        private final Widget widget;
        private final Element element;
        private String size;
        private String sizeBeforeCollapse;
        private boolean hidden;

        private Pane(final Widget widget, final Element element) {
            this.widget = widget;
            this.element = element;
        }
    }
}
