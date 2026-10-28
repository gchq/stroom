/*
 * Copyright 2021 Crown Copyright
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

import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ShowMenuEvent
        extends GwtEvent<ShowMenuEvent.Handler> {

    private static Type<Handler> TYPE;

    private final List<Item> items;
    private final PopupPosition popupPosition;
    private final ShowPopupEvent.Handler showHandler;
    private final HidePopupEvent.Handler hideHandler;
    private final Element[] autoHidePartners;
    private final boolean allowCloseOnMoveLeft;
    private final Integer width;
    private final Item focusItem;
    private final Element opener;
    private final String name;

    private ShowMenuEvent(final List<Item> items,
                          final PopupPosition popupPosition,
                          final ShowPopupEvent.Handler showHandler,
                          final HidePopupEvent.Handler hideHandler,
                          final Element[] autoHidePartners,
                          final boolean allowCloseOnMoveLeft,
                          final Integer width,
                          final Item focusItem,
                          final Element opener,
                          final String name) {
        this.items = items;
        this.popupPosition = popupPosition;
        this.autoHidePartners = autoHidePartners;
        this.showHandler = showHandler;
        this.hideHandler = hideHandler;
        this.allowCloseOnMoveLeft = allowCloseOnMoveLeft;
        this.width = width;
        this.focusItem = focusItem;
        this.opener = opener;
        this.name = name;
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

    /// @return The item to focus when the menu is shown, or null for its first.
    public Item getFocusItem() {
        return focusItem;
    }

    public boolean isAllowCloseOnMoveLeft() {
        return allowCloseOnMoveLeft;
    }

    /// @return The button the menu was opened from, or null.
    public Element getOpener() {
        return opener;
    }

    /// @return The menu's accessible name, e.g. "Actions for Feed 'MyFeed'", or null for the menu to
    /// be named by its [#getOpener] if that was marked as opening a menu.
    public String getName() {
        return name;
    }

    /// @return The menu's width in pixels, or null for it to fit its items.
    public Integer getWidth() {
        return width;
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
        private Integer width = null;
        private Item focusItem = null;
        private Element opener;
        private String name;

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

        /// Sets the button that opens the menu. Pressing it while the menu is open doesn't count as a
        /// click outside the menu, and if it was marked as opening a menu (see [MenuOpener#mark]),
        /// its `aria-expanded` says whether the menu is open.
        ///
        /// @param opener The button's element, or null.
        /// @return This builder.
        public Builder opener(final Element opener) {
            this.opener = opener;
            if (opener != null) {
                addAutoHidePartner(opener);
            }
            return this;
        }

        /// Sets the menu's accessible name, which screen readers announce on entering it, e.g.
        /// "Actions for Feed 'MyFeed'". Don't include the word "menu"; the menu's role says that.
        /// The name is plain text, never HTML. It overrides naming the menu by its [#opener].
        ///
        /// @param name The menu's name, or null to name it by its opener.
        /// @return This builder.
        public Builder name(final String name) {
            this.name = name;
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

        /// Gives the menu a fixed width, e.g. that of the field it drops down from, rather than
        /// fitting it to its items. Items too long for it are cut short with an ellipsis.
        ///
        /// @param width The menu's width in pixels.
        /// @return This builder.
        public Builder width(final int width) {
            this.width = width;
            return this;
        }

        /// Focuses an item when the menu is shown, rather than its first, e.g. the current choice
        /// of a menu that chooses one of its items.
        ///
        /// @param focusItem The item to focus, one of the menu's items, or null for its first.
        /// @return This builder.
        public Builder focusItem(final Item focusItem) {
            this.focusItem = focusItem;
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
                    Objects.requireNonNullElse(allowCloseOnMoveLeft, false),
                    width,
                    focusItem,
                    opener,
                    name));
        }
    }
}

