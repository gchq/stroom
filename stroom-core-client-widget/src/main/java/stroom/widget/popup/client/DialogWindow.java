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

package stroom.widget.popup.client;

import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuRequest;
import stroom.widget.menu.client.presenter.Separator;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// Moves and resizes a dialog without dragging it (WCAG 2.1.1 and 2.5.7), from its title bar's window
/// options button: Move and Resize modes, in which the arrow keys move or resize it (10px, or 1px with
/// Shift), Enter keeps it and Escape puts it back; Maximise and Restore (also a double click on the
/// title bar); and Restore Size, which puts back the size and position it opened with. The limits are
/// the mouse's: a dialog can be moved partly off the window (keeping its title bar in it), but not
/// resized off it.
final class DialogWindow {

    private static final int STEP = 10;
    private static final int FINE_STEP = 1;
    // The room left between a maximised dialog and each edge of the window, as when it opens
    private static final int WINDOW_MARGIN = 10;
    // How much of a dialog stays in the window when it is moved off it
    private static final int KEPT_IN_WINDOW = 22;
    // The smallest a dialog with no minimum of its own is resized to
    private static final int SMALLEST = 100;
    private static final String MOVING = "dialog--moving";
    private static final String RESIZING = "dialog--resizing";
    // The keys, the hint's second line
    private static final String KEYS_HINT = "Arrow keys, Enter to finish, Escape to cancel";

    private final AbstractPopupPanel dialog;
    private final PopupSize popupSize;
    private final ToolbarButton button;
    private final Element hint;

    private Mode mode = Mode.NONE;
    // Where the dialog was when the mode started, to put it back on Escape
    private Bounds modeStart;
    // Where the dialog was when it opened, once it has been moved or resized, for Restore Size
    private Bounds original;
    // Where the dialog was before it was maximised, while it is
    private Bounds beforeMaximise;
    // Where the dialog was when the mouse was last pressed on it to move or resize it
    private Bounds beforeMouse;
    private JavaScriptObject listeners;

    /// @param dialog    The dialog.
    /// @param popupSize Its size and how it can be resized, or null if it can't be.
    /// @param button    Its title bar's window options button.
    /// @param hint      Its hint, centred over it, which says what to do in a mode (a live region).
    DialogWindow(final AbstractPopupPanel dialog,
                 final PopupSize popupSize,
                 final ToolbarButton button,
                 final Element hint) {
        this.dialog = dialog;
        this.popupSize = popupSize;
        this.button = button;
        this.hint = hint;
        button.setOnClick(this::showMenu);
        dialog.addCloseHandler(event -> leaveMode());
    }

    /// Notes that the mouse may be about to move or resize the dialog (a press on its title bar or a
    /// resize handle), and leaves any mode.
    void beforeMouseChange() {
        leaveMode();
        beforeMouse = current();
    }

    /// Notes that the mouse has moved or resized the dialog, so that Restore Size can put back where
    /// it was.
    void afterMouseChange() {
        if (original == null && beforeMouse != null) {
            original = beforeMouse;
        }
    }

    /// Maximises the dialog, or restores it if it is maximised (a double click on its title bar).
    void toggleMaximise() {
        if (!canResizeWidth() && !canResizeHeight()) {
            return;
        }
        leaveMode();
        if (beforeMaximise != null) {
            apply(beforeMaximise);
            beforeMaximise = null;
        } else {
            rememberOriginal();
            final Bounds now = current();
            beforeMaximise = now;
            final int windowLeft = Window.getScrollLeft();
            final int windowTop = Window.getScrollTop();
            final int left = canResizeWidth()
                    ? windowLeft + WINDOW_MARGIN
                    : now.left;
            final int top = canResizeHeight()
                    ? windowTop + WINDOW_MARGIN
                    : now.top;
            final int width = canResizeWidth()
                    ? limit(Window.getClientWidth() - 2 * WINDOW_MARGIN, popupSize.getWidth())
                    : now.width;
            final int height = canResizeHeight()
                    ? limit(Window.getClientHeight() - 2 * WINDOW_MARGIN, popupSize.getHeight())
                    : now.height;
            apply(new Bounds(left, top, width, height));
        }
    }

    private void showMenu() {
        final List<Item> items = new ArrayList<>();
        int priority = 0;
        items.add(new IconMenuItem.Builder()
                .priority(priority++)
                .icon(SvgImage.MOVE)
                .text("Move")
                .command(() -> enterMode(Mode.MOVE))
                .build());
        if (canResizeWidth() || canResizeHeight()) {
            items.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(SvgImage.RESIZE)
                    .text("Resize")
                    .command(() -> enterMode(Mode.RESIZE))
                    .build());
            items.add(new Separator(priority++));
            final boolean maximised = beforeMaximise != null;
            items.add(new IconMenuItem.Builder()
                    .priority(priority++)
                    .icon(maximised
                            ? SvgImage.MINIMISE
                            : SvgImage.MAXIMISE)
                    .text(maximised
                            ? "Restore"
                            : "Maximise")
                    .command(this::toggleMaximise)
                    .build());
        }
        if (original != null) {
            items.add(new IconMenuItem.Builder()
                    .priority(priority)
                    .icon(SvgImage.UNDO)
                    .text("Restore Size")
                    .tooltip("Put the dialog back as it opened")
                    .command(this::restoreSize)
                    .build());
        }
        final Rect below = new Rect(button.getElement());
        MenuRequest.fireFromOpener(button.getElement(),
                "Window options",
                items,
                new PopupPosition(below, PopupLocation.BELOW));
    }

    private void restoreSize() {
        leaveMode();
        if (original != null) {
            apply(original);
            original = null;
            beforeMaximise = null;
        }
    }

    private void enterMode(final Mode newMode) {
        leaveMode();
        rememberOriginal();
        mode = newMode;
        modeStart = current();
        dialog.addStyleName(newMode == Mode.MOVE
                ? MOVING
                : RESIZING);
        showHint();
        listeners = addListeners(dialog.getElement());
        // The keys go to the dialog while the window options button has the focus (the closing menu
        // gives it back)
        Scheduler.get().scheduleDeferred(() -> button.setFocus(true));
    }

    private void leaveMode() {
        if (mode != Mode.NONE) {
            removeListeners(listeners);
            listeners = null;
            dialog.removeStyleName(MOVING);
            dialog.removeStyleName(RESIZING);
            hint.setInnerText("");
            mode = Mode.NONE;
            modeStart = null;
        }
    }

    // A key pressed in the dialog while in a mode. Returns whether the key was used
    private boolean onKey(final NativeEvent event) {
        final int keyCode = event.getKeyCode();
        if (keyCode == KeyCodes.KEY_ENTER) {
            leaveMode();
            return true;
        } else if (keyCode == KeyCodes.KEY_ESCAPE) {
            final Bounds start = modeStart;
            leaveMode();
            apply(start);
            return true;
        } else if (keyCode == KeyCodes.KEY_TAB) {
            // Keeps the change, and goes on to whatever Tab goes to
            leaveMode();
            return false;
        }
        final int step = event.getShiftKey()
                ? FINE_STEP
                : STEP;
        final int dx;
        final int dy;
        switch (keyCode) {
            case KeyCodes.KEY_LEFT:
                dx = -step;
                dy = 0;
                break;
            case KeyCodes.KEY_RIGHT:
                dx = step;
                dy = 0;
                break;
            case KeyCodes.KEY_UP:
                dx = 0;
                dy = -step;
                break;
            case KeyCodes.KEY_DOWN:
                dx = 0;
                dy = step;
                break;
            default:
                return false;
        }
        if (mode == Mode.MOVE) {
            move(dx, dy);
        } else {
            resize(dx, dy);
        }
        return true;
    }

    // Moves the dialog, keeping its title bar in the window, as the mouse does
    private void move(final int dx, final int dy) {
        final Bounds now = current();
        final int windowLeft = Window.getScrollLeft();
        final int windowTop = Window.getScrollTop();
        final int left = Math.max(windowLeft,
                Math.min(windowLeft + Window.getClientWidth() - KEPT_IN_WINDOW, now.left + dx));
        final int top = Math.max(windowTop,
                Math.min(windowTop + Window.getClientHeight() - KEPT_IN_WINDOW, now.top + dy));
        apply(new Bounds(left, top, now.width, now.height));
    }

    // Resizes the dialog from its right and bottom edges, within its limits, and not past the
    // window's edges (a dialog already past them isn't made smaller by that)
    private void resize(final int dx, final int dy) {
        final Bounds now = current();
        int width = now.width;
        int height = now.height;
        if (dx != 0 && canResizeWidth()) {
            final int room = Window.getScrollLeft() + Window.getClientWidth() - now.left;
            width = Math.min(limit(now.width + dx, popupSize.getWidth()), Math.max(now.width, room));
        }
        if (dy != 0 && canResizeHeight()) {
            final int room = Window.getScrollTop() + Window.getClientHeight() - now.top;
            height = Math.min(limit(now.height + dy, popupSize.getHeight()), Math.max(now.height, room));
        }
        apply(new Bounds(now.left, now.top, width, height));
        showHint();
    }

    private void showHint() {
        if (mode == Mode.MOVE) {
            hint.setInnerText("Moving\n" + KEYS_HINT);
        } else if (mode == Mode.RESIZE) {
            final Bounds now = current();
            hint.setInnerText("Resizing " + now.width + " by " + now.height + "\n" + KEYS_HINT);
        }
    }

    private void rememberOriginal() {
        if (original == null) {
            original = current();
        }
    }

    private boolean canResizeWidth() {
        return popupSize != null && popupSize.getWidth() != null && popupSize.getWidth().isResizable();
    }

    private boolean canResizeHeight() {
        return popupSize != null && popupSize.getHeight() != null && popupSize.getHeight().isResizable();
    }

    // A size within a dimension's minimum and maximum
    private static int limit(final int value, final Size size) {
        final int min = size.getMin() != null
                ? size.getMin()
                : SMALLEST;
        int limited = Math.max(min, value);
        if (size.getMax() != null) {
            limited = Math.min(size.getMax(), limited);
        }
        return limited;
    }

    private Bounds current() {
        final Element sized = dialog.getWidget().getElement();
        return new Bounds(dialog.getPopupLeft(), dialog.getPopupTop(), sized.getOffsetWidth(),
                sized.getOffsetHeight());
    }

    private void apply(final Bounds bounds) {
        if (bounds == null) {
            return;
        }
        final Widget widget = dialog.getWidget();
        if (canResizeWidth()) {
            setWidth(widget.getElement(), bounds.width);
        }
        if (canResizeHeight()) {
            setHeight(widget.getElement(), bounds.height);
        }
        dialog.setPopupPosition(bounds.left, bounds.top);
        if (widget instanceof final RequiresResize requiresResize) {
            requiresResize.onResize();
        }
    }

    // Listens for keys in the dialog (before its widgets have them) and for any press of a pointer,
    // which keeps the change and leaves the mode
    private native JavaScriptObject addListeners(Element element) /*-{
        var self = this;
        var key = $entry(function (event) {
            if (self.@stroom.widget.popup.client.DialogWindow::onKey(*)(event)) {
                event.preventDefault();
                event.stopPropagation();
            }
        });
        var pointer = $entry(function () {
            self.@stroom.widget.popup.client.DialogWindow::leaveMode()();
        });
        element.addEventListener('keydown', key, true);
        $doc.addEventListener('pointerdown', pointer, true);
        return {element: element, key: key, pointer: pointer};
    }-*/;

    private static native void removeListeners(JavaScriptObject listeners) /*-{
        if (listeners) {
            listeners.element.removeEventListener('keydown', listeners.key, true);
            $doc.removeEventListener('pointerdown', listeners.pointer, true);
        }
    }-*/;


    // --------------------------------------------------------------------------------


    private enum Mode {
        NONE,
        MOVE,
        RESIZE
    }


    // --------------------------------------------------------------------------------


    // Where a dialog is and how big
    private record Bounds(int left, int top, int width, int height) {

    }

    /// Sets a dialog's width (`--dialog__width` on its container, which `dialog.css` uses).
    ///
    /// @param container The dialog's container, the root of its template.
    /// @param width     The width in pixels.
    static void setWidth(final Element container, final int width) {
        CssProperties.set(container, "--dialog__width", width + "px");
    }

    /// Sets a dialog's height (`--dialog__height` on its container, which `dialog.css` uses).
    ///
    /// @param container The dialog's container, the root of its template.
    /// @param height    The height in pixels.
    static void setHeight(final Element container, final int height) {
        CssProperties.set(container, "--dialog__height", height + "px");
    }
}
