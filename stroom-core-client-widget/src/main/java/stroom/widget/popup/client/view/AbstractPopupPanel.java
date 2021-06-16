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
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.ui.PopupPanel;

public abstract class AbstractPopupPanel extends PopupPanel implements Popup {
    private final PopupType popupType;
    private final Glass dragGlass = new Glass(
            "popupPanel-dragGlass",
            "popupPanel-dragGlassVisible");

    public AbstractPopupPanel(final boolean autoHide, final boolean modal, final PopupType popupType) {
        super(autoHide, modal);
        this.popupType = popupType;
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

        if (event.getTypeInt() == Event.ONKEYDOWN) {
            final NativeEvent nativeEvent = event.getNativeEvent();

            switch (nativeEvent.getKeyCode()) {
                case KeyCodes.KEY_ESCAPE:
                    onEscapeKeyPressed();
                    break;
                case KeyCodes.KEY_ENTER:
                    // Only close the dialog if the Ctrl modifier is pressed
                    if (nativeEvent.getCtrlKey()) {
                        onEnterKeyPressed();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    protected void onEscapeKeyPressed() { }

    protected void onEnterKeyPressed() { }
}
