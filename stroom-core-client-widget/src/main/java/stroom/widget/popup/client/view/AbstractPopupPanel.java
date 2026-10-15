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

package stroom.widget.popup.client.view;

import stroom.data.grid.client.Glass;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.ui.PopupPanel;

public abstract class AbstractPopupPanel extends PopupPanel implements Popup {

    final DialogActionUiHandlers dialogActionHandler;
    private final Glass dragGlass = new Glass(
            "popupPanel-dragGlass",
            "popupPanel-dragGlassVisible");

    public AbstractPopupPanel(final DialogActionUiHandlers dialogActionHandler,
                              final boolean autoHide,
                              final boolean modal) {
        super(autoHide, modal);
        this.dialogActionHandler = dialogActionHandler;
    }

    public Glass getDragGlass() {
        return dragGlass;
    }

    // STROOMWORKS-LOCAL: KEEP LOCAL ON MERGE FROM master.
    // Upstream shows the drag glass in beginDragging and hides it ONLY in endDragging, i.e. only
    // on a mouse-up the popup actually receives. Any drag whose release is never delivered - the
    // button let go outside the browser window, focus lost mid-drag, or the dialog closed while
    // the button is still down - leaves a full-viewport glass attached to the body. It swallows
    // every click, so the whole UI is dead: no CPU, nothing in the console, the server healthy,
    // and DevTools the only way back. Diagnosed 2026-09-04; see
    // docs/task-popup-drag-glass-orphaned.md. Dropping these hunks reinstates that.

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
    }

    /**
     * Notify the dialog when either the Enter or Escape key is pressed.
     * For dialogs with a close button, the Escape will cause them to close.
     * The combination Ctrl+Enter key will close the dialog, with a `true` result.
     */
    @Override
    protected void onPreviewNativeEvent(final NativePreviewEvent event) {
        super.onPreviewNativeEvent(event);

        final NativeEvent nativeEvent = event.getNativeEvent();
        final Action action = KeyBinding.test(nativeEvent);
        if (event.getTypeInt() == Event.ONKEYDOWN) {
            final EventTarget eventTarget = nativeEvent.getEventTarget();
            final boolean isEditor;
            if (eventTarget != null) {
                final Element element = Element.as(eventTarget);
//                GWT.log("element: " + element.getTagName() + "." + element.getClassName());
                // We should not be handling key down events if the target is the ACE editor
                // as this messes with things like code completion, vim bindings, etc.
                // If the user is focused on something other than the editor then it's all fine.
                isEditor = element != null && element.hasClassName("ace_text-input");
            } else {
                isEditor = false;
            }

            if (!isEditor) {
                if (action != null) {
                    switch (action) {
                        case CLOSE:
                            // Cancel the event so ancestors don't also handle it
                            event.cancel();
                            onCloseAction();
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
    }

    private void onCloseAction() {
        if (dialogActionHandler != null) {
            dialogActionHandler.onDialogAction(DialogAction.CLOSE);
        }
    }

    private void onOkAction() {
        if (dialogActionHandler != null) {
            dialogActionHandler.onDialogAction(DialogAction.OK);
        }
    }
}
