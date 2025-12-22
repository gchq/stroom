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

package stroom.widget.popup.client.event;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.ArrayList;
import java.util.List;

public class ShowPopupEvent extends GwtEvent<ShowPopupEvent.Handler> {

    private static Type<Handler> TYPE;

    private final PresenterWidget<?> presenterWidget;
    private final PopupType popupType;
    private final PopupPosition popupPosition;
    private final PopupSize popupSize;
    private final SvgImage icon;
    private final String caption;
    private final ShowPopupEvent.Handler showHandler;
    private final HidePopupRequestEvent.Handler hideRequestHandler;
    private final HidePopupEvent.Handler hideHandler;
    private final Boolean modal;
    private final Element[] autoHidePartners;

    private ShowPopupEvent(final PresenterWidget<?> presenterWidget,
                           final PopupType popupType,
                           final PopupPosition popupPosition,
                           final PopupSize popupSize,
                           final SvgImage icon,
                           final String caption,
                           final ShowPopupEvent.Handler showHandler,
                           final HidePopupRequestEvent.Handler hideRequestHandler,
                           final HidePopupEvent.Handler hideHandler,
                           final Boolean modal,
                           final Element... autoHidePartners) {
        this.presenterWidget = presenterWidget;
        this.popupType = popupType;
        this.popupPosition = popupPosition;
        this.popupSize = popupSize;
        this.icon = icon;
        this.caption = caption;
        this.showHandler = showHandler;
        this.hideRequestHandler = hideRequestHandler;
        this.hideHandler = hideHandler;
        this.modal = modal;
        this.autoHidePartners = autoHidePartners;
    }

    public static Builder builder(final PresenterWidget<?> presenterWidget) {
        return new Builder(presenterWidget);
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

    public SvgImage getIcon() {
        return icon;
    }

    public String getCaption() {
        return caption;
    }

    public Handler getShowHandler() {
        return showHandler;
    }

    public HidePopupRequestEvent.Handler getHideRequestHandler() {
        return hideRequestHandler;
    }

    public HidePopupEvent.Handler getHideHandler() {
        return hideHandler;
    }

    public Boolean getModal() {
        return modal;
    }

    public Element[] getAutoHidePartners() {
        return autoHidePartners;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onShow(ShowPopupEvent event);
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private final PresenterWidget<?> presenterWidget;
        private PopupType popupType;
        private PopupPosition popupPosition;
        private PopupSize popupSize;
        private SvgImage icon;
        private String caption;
        private ShowPopupEvent.Handler showHandler;
        private HidePopupRequestEvent.Handler hideRequestHandler;
        private HidePopupEvent.Handler hideHandler;
        private Boolean modal;
        private final List<Element> autoHidePartners = new ArrayList<>();

        public Builder(final PresenterWidget<?> presenterWidget) {
            this.presenterWidget = presenterWidget;
        }

        public Builder popupType(final PopupType popupType) {
            this.popupType = popupType;
            return this;
        }

        public Builder popupPosition(final PopupPosition popupPosition) {
            this.popupPosition = popupPosition;
            return this;
        }

        public Builder popupSize(final PopupSize popupSize) {
            this.popupSize = popupSize;
            return this;
        }

        public Builder icon(final SvgImage icon) {
            this.icon = icon;
            return this;
        }

        public Builder caption(final String caption) {
            this.caption = caption;
            return this;
        }

        public Builder modal(final Boolean modal) {
            this.modal = modal;
            return this;
        }

        /**
         * Same as passing {@code true} to {@link Builder#modal(Boolean)}
         */
        public Builder modal() {
            this.modal = true;
            return this;
        }

        /**
         * Same as passing {@code false} to {@link Builder#modal(Boolean)}
         */
        public Builder modeless() {
            this.modal = false;
            return this;
        }

        public Builder addAutoHidePartner(final Element... autoHidePartner) {
            this.autoHidePartners.addAll(NullSafe.asList(autoHidePartner));
            return this;
        }

        public Builder onShow(final ShowPopupEvent.Handler handler) {
            this.showHandler = handler;
            return this;
        }

        public Builder onHideRequest(final HidePopupRequestEvent.Handler handler) {
            this.hideRequestHandler = handler;
            return this;
        }

        public Builder onHide(final HidePopupEvent.Handler handler) {
            this.hideHandler = handler;
            return this;
        }

        public void fire() {
            // By default, we will automatically hide popups unless they have a handler that alters the behaviour.
            if (hideRequestHandler == null) {
                hideRequestHandler = e -> {
//                    GWT.log("isOk: " + e.isOk());
                    HidePopupEvent.builder(presenterWidget)
                            .autoClose(e.isAutoClose())
                            .ok(e.isOk())
                            .fire();
                };
            }

            Element[] elements = null;
            if (NullSafe.hasItems(autoHidePartners)) {
                elements = autoHidePartners.toArray(new Element[0]);
            }

//            if (!presenterWidget.getView().asWidget().isVisible()) {
            presenterWidget.fireEvent(new ShowPopupEvent(
                    presenterWidget,
                    popupType,
                    popupPosition,
                    popupSize,
                    icon,
                    caption,
                    showHandler,
                    hideRequestHandler,
                    hideHandler,
                    modal,
                    elements));
//            }
        }
    }
}
