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
