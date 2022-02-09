package stroom.widget.menu.client.presenter;

import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
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

    private ShowMenuEvent(final List<Item> items,
                          final PopupPosition popupPosition,
                          final ShowPopupEvent.Handler showHandler,
                          final HidePopupEvent.Handler hideHandler,
                          final Element[] autoHidePartners) {
        this.items = items;
        this.popupPosition = popupPosition;
        this.autoHidePartners = autoHidePartners;
        this.showHandler = showHandler;
        this.hideHandler = hideHandler;
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

    @Override
    protected void dispatch(Handler handler) {
        handler.onShow(this);
    }

    public interface Handler extends EventHandler {

        void onShow(ShowMenuEvent event);
    }

    public interface HasShowMenuHandlers extends HasHandlers {

        HandlerRegistration addShowMenuHandler(ShowMenuEvent.Handler handler);
    }

    public static class Builder {

        private List<Item> items;
        private PopupPosition popupPosition;
        private ShowPopupEvent.Handler showHandler;
        private HidePopupEvent.Handler hideHandler;
        private final List<Element> autoHidePartners = new ArrayList<>();

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

        public void fire(HasHandlers hasHandlers) {
            Element[] elements = null;
            if (autoHidePartners.size() > 0) {
                elements = autoHidePartners.toArray(new Element[0]);
            }

            hasHandlers.fireEvent(new ShowMenuEvent(
                    items,
                    popupPosition,
                    showHandler,
                    hideHandler,
                    elements));
        }
    }
}

