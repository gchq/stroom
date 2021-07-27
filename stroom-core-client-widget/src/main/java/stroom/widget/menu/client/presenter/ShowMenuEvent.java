package stroom.widget.menu.client.presenter;

import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;

import java.util.List;

public class ShowMenuEvent
        extends GwtEvent<ShowMenuEvent.Handler> {

    private static Type<Handler> TYPE;

    private final List<Item> items;
    private final PopupPosition popupPosition;
    private final Element[] autoHidePartner;

    public static <T> void fire(final HasHandlers source,
                                final List<Item> items,
                                final PopupPosition popupPosition,
                                final Element... autoHidePartner) {
        if (TYPE != null) {
            ShowMenuEvent event = new ShowMenuEvent(items, popupPosition, autoHidePartner);
            source.fireEvent(event);
        }
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<Handler>();
        }
        return TYPE;
    }

    private ShowMenuEvent(final List<Item> items,
                          final PopupPosition popupPosition,
                          final Element[] autoHidePartner) {
        this.items = items;
        this.popupPosition = popupPosition;
        this.autoHidePartner = autoHidePartner;
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

    public Element[] getAutoHidePartner() {
        return autoHidePartner;
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
}

