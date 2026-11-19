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

package stroom.widget.contextmenu.client.event;

import stroom.widget.contextmenu.client.TextEditTarget;
import stroom.widget.popup.client.PopupPosition;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class ContextMenuEvent extends GwtEvent<ContextMenuEvent.Handler> {

    private static Type<Handler> TYPE;
    private final PopupPosition popupPosition;
    private final TextEditTarget editTarget;

    protected ContextMenuEvent(final PopupPosition popupPosition) {
        this(popupPosition, null);
    }

    /// @param popupPosition Where to show the menu.
    /// @param editTarget    The text field or code editor the menu was opened on (see
    ///                      [#getEditTarget]), or null.
    protected ContextMenuEvent(final PopupPosition popupPosition, final TextEditTarget editTarget) {
        this.popupPosition = popupPosition;
        this.editTarget = editTarget;
    }

    public static <I> void fire(final HasContextMenuHandlers source,
                                final PopupPosition popupPosition) {
        fire(source, popupPosition, null);
    }

    /// Asks for a widget's menu, opened on a text field or code editor in it, e.g. an expression
    /// term's value.
    ///
    /// @param source        The widget.
    /// @param popupPosition Where to show the menu.
    /// @param editTarget    The text field or code editor the menu was opened on, whose edit items
    ///                      should start the menu, or null.
    public static void fire(final HasContextMenuHandlers source,
                            final PopupPosition popupPosition,
                            final TextEditTarget editTarget) {
        if (TYPE != null) {
            source.fireEvent(new ContextMenuEvent(popupPosition, editTarget));
        }
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onContextMenu(this);
    }

    public PopupPosition getPopupPosition() {
        return popupPosition;
    }

    /// @return The text field or code editor the menu was opened on, whose edit items should start
    /// the menu (see [stroom.widget.contextmenu.client.ContextMenuManager#withEditItems]), or null.
    public TextEditTarget getEditTarget() {
        return editTarget;
    }

    public interface Handler extends EventHandler {

        void onContextMenu(ContextMenuEvent event);
    }
}
