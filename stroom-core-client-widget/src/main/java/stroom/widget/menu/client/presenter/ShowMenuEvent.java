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

package stroom.widget.menu.client.presenter;

import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;

import java.util.ArrayList;
import java.util.List;

public class ShowMenuEvent
        extends GwtEvent<ShowMenuEvent.Handler> {

    private static Type<Handler> TYPE;

    private final List<Item> items;
    private final PopupPosition popupPosition;
    private final ShowPopupEvent.Handler showHandler;
    private final HidePopupEvent.Handler hideHandler;
    private final Element[] autoHidePartners;
    private final boolean allowCloseOnMoveLeft;

    private ShowMenuEvent(final List<Item> items,
                          final PopupPosition popupPosition,
                          final ShowPopupEvent.Handler showHandler,
                          final HidePopupEvent.Handler hideHandler,
                          final Element[] autoHidePartners,
                          final boolean allowCloseOnMoveLeft) {
        this.items = items;
        this.popupPosition = popupPosition;
        this.autoHidePartners = autoHidePartners;
        this.showHandler = showHandler;
        this.hideHandler = hideHandler;
        this.allowCloseOnMoveLeft = allowCloseOnMoveLeft;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return TYPE;
    }

    public List<Item> getItems() {
        return items;
    }

    public PopupPosition getPopupPosition() {
        return popupPosition;
    }

    public ShowPopupEvent.Handler getShowHandler() {
        return showHandler;
    }

    public HidePopupEvent.Handler getHideHandler() {
        return hideHandler;
    }

    public Element[] getAutoHidePartners() {
        return autoHidePartners;
    }

    public boolean isAllowCloseOnMoveLeft() {
        return allowCloseOnMoveLeft;
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onShow(this);
    }

    public interface Handler extends EventHandler {

        void onShow(ShowMenuEvent event);
    }


    // --------------------------------------------------------------------------------


    public interface HasShowMenuHandlers extends HasHandlers {

        HandlerRegistration addShowMenuHandler(ShowMenuEvent.Handler handler);
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private List<Item> items;
        private PopupPosition popupPosition;
        private ShowPopupEvent.Handler showHandler;
        private HidePopupEvent.Handler hideHandler;
        private final List<Element> autoHidePartners = new ArrayList<>();
        private Boolean allowCloseOnMoveLeft = null;

        public Builder() {
        }

        public Builder items(final List<Item> items) {
            this.items = items;
            return this;
        }

        public Builder popupPosition(final PopupPosition popupPosition) {
            this.popupPosition = popupPosition;
            return this;
        }

        public Builder addAutoHidePartner(final Element... autoHidePartner) {
            if (autoHidePartner != null) {
                for (final Element element : autoHidePartner) {
                    this.autoHidePartners.add(element);
                }
            }
            return this;
        }

        public Builder onShow(final ShowPopupEvent.Handler handler) {
            this.showHandler = handler;
            return this;
        }

        public Builder onHide(final HidePopupEvent.Handler handler) {
            this.hideHandler = handler;
            return this;
        }

        /**
         * If allowCloseOnMoveLeft is true and the menu item has no parent (i.e. a root item)
         * then the menu will be closed. Useful when the menu is triggered by move right on
         * an explorer tree or similar. Default is false.
         */
        public Builder allowCloseOnMoveLeft(final boolean allowCloseOnMoveLeft) {
            this.allowCloseOnMoveLeft = allowCloseOnMoveLeft;
            return this;
        }

        /**
         * If allowCloseOnMoveLeft is true and the menu item has no parent (i.e. a root item)
         * then the menu will be closed. Useful when the menu is triggered by move right on
         * an explorer tree or similar. Default is false.
         */
        public Builder allowCloseOnMoveLeft() {
            this.allowCloseOnMoveLeft = true;
            return this;
        }

        public void fire(final HasHandlers hasHandlers) {
            Element[] elements = null;
            if (!autoHidePartners.isEmpty()) {
                elements = autoHidePartners.toArray(new Element[0]);
            }

            hasHandlers.fireEvent(new ShowMenuEvent(
                    items,
                    popupPosition,
                    showHandler,
                    hideHandler,
                    elements,
                    NullSafe.requireNonNullElse(allowCloseOnMoveLeft, false)));
        }
    }
}

