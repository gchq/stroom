/*
 * Copyright 2016 Crown Copyright
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

import stroom.widget.datagrid.client.Glass;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.ui.PopupPanel;

import java.util.function.Consumer;

public abstract class AbstractPopupPanel extends PopupPanel implements Popup {

    private final Glass dragGlass = new Glass(
            "popup-drag-glass",
            "popup-drag-glass--visible");
    // While an auto-hide popup shows, the window's blur listener that closes it when the focus goes
    // into a frame
    private JavaScriptObject frameFocusListener;

    private Consumer<DialogAction> onAction;

    /// Makes a popup that is neither modal nor closed by a click outside it; a subclass sets either
    /// with `setModal` or `setAutoHideEnabled`.
    public AbstractPopupPanel() {
        // Escape from inside the popup closes it here, once the widget with the focus has had it
        addDomHandler(this::onKeyDown, KeyDownEvent.getType());
    }

    /// @param onAction Told what the user asks of the popup: to close it (Escape, a click outside an
    ///                 auto-hide popup) or OK (Ctrl+Enter), or null for nothing.
    public void setOnAction(final Consumer<DialogAction> onAction) {
        this.onAction = onAction;
    }

    /// Tells the popup's owner what the user asks of it, as [#setOnAction(Consumer)] says.
    ///
    /// @param action What the user asks.
    protected void fireAction(final DialogAction action) {
        if (onAction != null) {
            onAction.accept(action);
        }
    }

    public Glass getDragGlass() {
        return dragGlass;
    }

    // The drag glass is shown in beginDragging and hidden in endDragging, i.e. on a mouse-up the
    // popup actually receives. Any drag whose release is never delivered - the button let go
    // outside the browser window, focus lost mid-drag, or the dialog closed while the button is
    // still down - would otherwise leave a full-viewport glass attached to the body. It swallows
    // every click, so the whole UI is dead: no CPU, nothing in the console, the server healthy,
    // and DevTools the only way back. abandonDrag below exists to recover from that.

    /**
     * Abandons any drag in progress and removes the drag glass.
     *
     * <p>Subclasses override to clear their own drag state as well, then call {@code super}.
     * Idempotent, because it is reached from several directions and may be reached twice.</p>
     */
    protected void abandonDrag() {
        dragGlass.hide();
    }

    /**
     * Whether a mouse button is still held.
     *
     * <p>{@code buttons} is the held-button bitmask, which is what a {@code mousemove} carries;
     * {@code button} identifies the button of a press or release and is not meaningful here. Used
     * to notice a release that happened where no listener could see it — the browser delivers no
     * event at all for a mouse-up outside its own window, so the first evidence is a subsequent
     * move with nothing held.</p>
     */
    protected static native boolean isButtonHeld(NativeEvent event) /*-{
        return (event.buttons === undefined) ? true : (event.buttons !== 0);
    }-*/;

    /**
     * {@inheritDoc}
     *
     * <p>The deterministic half of the leak: a popup closed mid-drag never sees its mouse-up, so
     * the glass has to come off on teardown.</p>
     */
    @Override
    protected void onUnload() {
        super.onUnload();
        abandonDrag();
        if (frameFocusListener != null) {
            removeFrameFocusListener(frameFocusListener);
            frameFocusListener = null;
        }
    }

    /// An auto-hide popup closes on a click outside it, but a click in a frame (e.g. a markdown
    /// preview or a visualisation) is the frame's own event, which the page never sees. The page's
    /// window loses the focus to the frame instead, so the popup closes on that (#3550).
    @Override
    protected void onLoad() {
        super.onLoad();
        if (isAutoHideEnabled() && frameFocusListener == null) {
            frameFocusListener = addFrameFocusListener();
        }
    }

    // The focus went into a frame: close, as a click outside would, unless the frame is in the popup
    private void onFocusInFrame(final Element frame) {
        if (isShowing() && isAutoHideEnabled() && !getElement().isOrHasChild(frame)) {
            hide(true);
        }
    }

    private native JavaScriptObject addFrameFocusListener() /*-{
        var self = this;
        var listener = function () {
            // The window also loses the focus to another window or tab, which leaves the popup open
            var active = $doc.activeElement;
            if (active && active.tagName === 'IFRAME') {
                self.@stroom.widget.popup.client.AbstractPopupPanel::onFocusInFrame(*)(active);
            }
        };
        $wnd.addEventListener('blur', listener);
        return listener;
    }-*/;

    private static native void removeFrameFocusListener(JavaScriptObject listener) /*-{
        $wnd.removeEventListener('blur', listener);
    }-*/;

    /// Notifies the popup when Escape or Ctrl+Enter is pressed: Escape closes it (through its
    /// Close or Cancel button, where it has one) and Ctrl+Enter is OK.
    ///
    /// Escape pressed inside the popup isn't handled here: it goes to the widget with the focus
    /// first, which may use it (a quick filter clears, a field's list closes, a move is put back)
    /// and call `preventDefault()`, and the popup closes in `onKeyDown` only if none did. Escape
    /// pressed anywhere else (the focus on the page, or on nothing) closes the popup at once.
    @Override
    protected void onPreviewNativeEvent(final NativePreviewEvent event) {
        super.onPreviewNativeEvent(event);

        final NativeEvent nativeEvent = event.getNativeEvent();
        final Action action = KeyBinding.test(nativeEvent);
        if (event.getTypeInt() == Event.ONKEYDOWN) {
            final Element target = getTargetElement(nativeEvent);
            // We should not be handling key down events if the target is the ACE editor
            // as this messes with things like code completion, vim bindings, etc.
            // If the user is focused on something other than the editor then it's all fine.
            if (!isAceEditor(target) && action != null) {
                switch (action) {
                    case CLOSE:
                        if (target == null || !getElement().isOrHasChild(target)) {
                            // Cancel the event so ancestors don't also handle it
                            event.cancel();
                            onCloseAction();
                        }
                        // Otherwise it goes on to the widget with the focus (PopupPanel consumes
                        // it, so popups below this one don't see it either)
                        break;
                    case OK:
                        // Cancel the event so ancestors don't also handle it
                        event.cancel();
                        onOkAction();
                        break;
                }
            }
        }
    }

    // Escape from inside the popup that no widget used closes it, and goes no further (so the page
    // behind, e.g. a dashboard that stops its query on Escape, doesn't see it)
    private void onKeyDown(final KeyDownEvent event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (nativeEvent.getKeyCode() == KeyCodes.KEY_ESCAPE
            && !nativeEvent.getAltKey()
            && !nativeEvent.getCtrlKey()
            && !nativeEvent.getMetaKey()
            && !nativeEvent.getShiftKey()
            && !isDefaultPrevented(nativeEvent)
            && !isAceEditor(getTargetElement(nativeEvent))) {
            nativeEvent.preventDefault();
            nativeEvent.stopPropagation();
            onCloseAction();
        }
    }

    private static Element getTargetElement(final NativeEvent nativeEvent) {
        final EventTarget eventTarget = nativeEvent.getEventTarget();
        return eventTarget != null && Element.is(eventTarget)
                ? Element.as(eventTarget)
                : null;
    }

    private static boolean isAceEditor(final Element element) {
        return element != null && element.hasClassName("ace_text-input");
    }

    private static native boolean isDefaultPrevented(NativeEvent event) /*-{
        return !!event.defaultPrevented;
    }-*/;

    private void onCloseAction() {
        fireAction(DialogAction.CLOSE);
    }

    private void onOkAction() {
        fireAction(DialogAction.OK);
    }
}
