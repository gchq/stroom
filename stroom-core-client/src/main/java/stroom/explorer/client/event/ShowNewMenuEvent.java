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

package stroom.explorer.client.event;

import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ShowNewMenuEvent extends GwtEvent<ShowNewMenuEvent.Handler> {

    private static Type<Handler> TYPE;
    private final Element element;
    private final PopupPosition popupPosition;

    private ShowNewMenuEvent(final Element element,
                             final PopupPosition popupPosition) {
        this.element = element;
        this.popupPosition = popupPosition;
    }

    public static void fire(final HasHandlers handlers,
                            final Element element,
                            final PopupPosition popupPosition) {
        handlers.fireEvent(new ShowNewMenuEvent(element, popupPosition));
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
        handler.onShow(this);
    }

    public Element getElement() {
        return element;
    }

    public PopupPosition getPopupPosition() {
        return popupPosition;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onShow(ShowNewMenuEvent event);
    }
}
