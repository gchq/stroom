package stroom.widget.tab.client.event;

import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;

public interface HasShowTabMenuHandlers extends HasHandlers {

    HandlerRegistration addShowTabMenuHandler(ShowTabMenuEvent.Handler handler);
}
