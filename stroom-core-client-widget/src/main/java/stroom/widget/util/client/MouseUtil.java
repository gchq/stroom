package stroom.widget.util.client;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.MouseEvent;

public class MouseUtil {

    public static boolean isPrimary(final MouseEvent<?> event) {
//        GWT.log("isPrimary: " + event.getNativeEvent().getType() + " " + event.getNativeButton());
        return (event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0;
    }

    public static boolean isSecondary(final MouseEvent<?> event) {
//        GWT.log("isSecondary: " + event.getNativeEvent().getType() + " " + event.getNativeButton());
        return (event.getNativeButton() & NativeEvent.BUTTON_RIGHT) != 0;
    }

    public static boolean isPrimary(final NativeEvent event) {
//        GWT.log("isPrimary: " + event.getType() + " " + event.getButton());
        return (event.getButton() & NativeEvent.BUTTON_LEFT) != 0;
    }

    public static boolean isSecondary(final NativeEvent event) {
//        GWT.log("isSecondary: " + event.getType() + " " + event.getButton());
        return (event.getButton() & NativeEvent.BUTTON_RIGHT) != 0;
    }
}
