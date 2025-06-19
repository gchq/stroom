package stroom.widget.tooltip.client.event;

import stroom.event.client.StaticEventBus;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.tooltip.client.event.ShowHelpEvent.Handler;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class ShowHelpEvent extends GwtEvent<Handler> {

    private static Type<Handler> TYPE;

    private final PopupPosition popupPosition;
    private final Element element;
    private final SafeHtml content;

    private ShowHelpEvent(final Element element,
                          final PopupPosition popupPosition,
                          final SafeHtml content) {
        this.popupPosition = NullSafe.requireNonNullElseGet(popupPosition, () -> {
            final Rect relativeRect = new Rect(element);
            return new PopupPosition(relativeRect, PopupLocation.RIGHT);
        });
        this.element = element;
        this.content = NullSafe.requireNonNullElse(content, SafeHtmlUtils.EMPTY_SAFE_HTML);
    }

    private ShowHelpEvent(final Builder builder) {
        this(builder.element, builder.popupPosition, builder.content);
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    public static Builder builder(final Element element) {
        final Builder builder = new Builder();
        builder.element = element;
        return builder;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    public Element getElement() {
        return element;
    }

    public PopupPosition getPopupPosition() {
        return popupPosition;
    }

    public SafeHtml getContent() {
        return content;
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onShow(this);
    }

//    public PopupPosition getPopupPosition() {
//        return popupPosition;
//    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onShow(ShowHelpEvent event);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private PopupPosition popupPosition;
        private Element element;
        private SafeHtml content;
        private ShowHelpEvent.Handler showHandler;

        private Builder() {
        }

        public Builder withPopupPosition(final PopupPosition val) {
            popupPosition = val;
            return this;
        }

        public Builder withContent(final SafeHtml content) {
            this.content = content;
            return this;
        }

        public ShowHelpEvent.Builder onShow(final ShowHelpEvent.Handler handler) {
            this.showHandler = handler;
            return this;
        }

        public void fire() {
            final ShowHelpEvent showHelpEvent = new ShowHelpEvent(this);
            StaticEventBus.fire(showHelpEvent);
        }
    }
}
