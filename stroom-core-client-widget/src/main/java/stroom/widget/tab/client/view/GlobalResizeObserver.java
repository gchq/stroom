package stroom.widget.tab.client.view;

import com.google.gwt.dom.client.Element;

import java.util.HashMap;
import java.util.Map;

public class GlobalResizeObserver {

    private static final Map<Element, ResizeListener> listeners = new HashMap<>();

    static {
        init();
    }

    private GlobalResizeObserver() {
    }

    public static void addListener(final Element element, final ResizeListener listener) {
        listeners.put(element, listener);
        observe(element);
    }

    public static void removeListener(final Element element) {
        unobserve(element);
        listeners.remove(element);
    }

    private static void elementResize(final Element element) {
//        GWT.log("GlobalResizeObserver " + element.toString());
        final ResizeListener listener = listeners.get(element);
        if (listener != null) {
            listener.onResize(element);
        }
    }

    private static native void init()/*-{
        $wnd.globalResizeHandler = @stroom.widget.tab.client.view.GlobalResizeObserver::elementResize(*);
    }-*/;

    private static native void disconnect() /*-{
        $wnd.globalResizeObserver.disconnect();
    }-*/;

    private static native void observe(final Element element) /*-{
        $wnd.globalResizeObserver.observe(element);
    }-*/;

    private static native void unobserve(final Element element) /*-{
        $wnd.globalResizeObserver.unobserve(element);
    }-*/;
}
