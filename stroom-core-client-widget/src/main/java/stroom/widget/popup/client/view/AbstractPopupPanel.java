/*
 * Copyright 2016-2025 Crown Copyright
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
