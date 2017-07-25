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

package stroom.widget.popup.client.event;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.gwtplatform.mvp.client.PresenterWidget;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class ShowPopupEvent extends GwtEvent<ShowPopupEvent.Handler> {
    private static Type<Handler> TYPE;
    private final PresenterWidget<?> presenterWidget;
    private final PopupType popupType;
    private final PopupPosition popupPosition;
    private final PopupSize popupSize;
    private final String caption;
    private final PopupUiHandlers popupUiHandlers;
    private final Boolean modal;
    private final Element[] autoHidePartners;
    private ShowPopupEvent(final PresenterWidget<?> presenterWidget, final PopupType popupType,
                           final PopupPosition popupPosition, final PopupSize popupSize, final String caption,
                           final PopupUiHandlers popupUiHandlers, final Boolean modal, final Element... autoHidePartners) {
        this.presenterWidget = presenterWidget;
        this.popupType = popupType;
        this.popupPosition = popupPosition;
        this.popupSize = popupSize;
        this.caption = caption;
        this.popupUiHandlers = popupUiHandlers;
        this.modal = modal;
        this.autoHidePartners = autoHidePartners;
    }

    /**
     * Show a popup center of the screen and sized to it's content without a
     * handler.
     */
    public static void fire(final HasHandlers handlers, final PresenterWidget<?> presenterWidget,
                            final PopupType popupType, final String caption, final Element... autoHidePartner) {
        fire(handlers, presenterWidget, popupType, null, null, caption, null, null, autoHidePartner);
    }

    /**
     * Show a popup center of the screen and sized by the popup size.
     */
    public static void fire(final HasHandlers handlers, final PresenterWidget<?> presenterWidget,
                            final PopupType popupType, final PopupSize popupSize, final String caption,
                            final PopupUiHandlers popupUiHandlers, final Element... autoHidePartner) {
        fire(handlers, presenterWidget, popupType, null, popupSize, caption, popupUiHandlers, null, autoHidePartner);
    }

    /**
     * Show a popup center of the screen and sized to it's content with optional
     * modality.
     */
    public static void fire(final HasHandlers handlers, final PresenterWidget<?> presenterWidget,
                            final PopupType popupType, final String caption, final PopupUiHandlers popupUiHandlers, final Boolean modal,
                            final Element... autoHidePartner) {
        fire(handlers, presenterWidget, popupType, null, null, caption, popupUiHandlers, modal, autoHidePartner);
    }

    /**
     * Show a popup center of the screen and sized to it's content.
     */
    public static void fire(final HasHandlers handlers, final PresenterWidget<?> presenterWidget,
                            final PopupType popupType, final String caption, final PopupUiHandlers popupUiHandlers,
                            final Element... autoHidePartner) {
        fire(handlers, presenterWidget, popupType, null, null, caption, popupUiHandlers, null, autoHidePartner);
    }

    /**
     * Show a popup in the specified position and sized to it's content.
     */
    public static void fire(final HasHandlers handlers, final PresenterWidget<?> presenterWidget,
                            final PopupType popupType, final PopupPosition popupPosition, final PopupUiHandlers popupUiHandlers,
                            final Element... autoHidePartner) {
        fire(handlers, presenterWidget, popupType, popupPosition, null, null, popupUiHandlers, null, autoHidePartner);
    }

    /**
     * Show a popup in the specified position and the specified size.
     */
    public static void fire(final HasHandlers handlers, final PresenterWidget<?> presenterWidget,
                            final PopupType popupType, final PopupPosition popupPosition, final PopupSize popupSize,
                            final String caption, final PopupUiHandlers popupUiHandlers, final Boolean modal,
                            final Element... autoHidePartners) {
        handlers.fireEvent(new ShowPopupEvent(presenterWidget, popupType, popupPosition, popupSize, caption,
                popupUiHandlers, modal, autoHidePartners));
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

    public PresenterWidget<?> getPresenterWidget() {
        return presenterWidget;
    }

    public PopupType getPopupType() {
        return popupType;
    }

    public PopupPosition getPopupPosition() {
        return popupPosition;
    }

    public PopupSize getPopupSize() {
        return popupSize;
    }

    public String getCaption() {
        return caption;
    }

    public PopupUiHandlers getPopupUiHandlers() {
        return popupUiHandlers;
    }

    public Boolean getModal() {
        return modal;
    }

    public Element[] getAutoHidePartners() {
        return autoHidePartners;
    }

    public interface Handler extends EventHandler {
        void onShow(ShowPopupEvent event);
    }
}
