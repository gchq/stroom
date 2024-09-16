package stroom.widget.util.client;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;

public interface GlobalKeyHandler {

    void onKeyDown(KeyDownEvent event);

    void onKeyUp(KeyUpEvent event);
}
