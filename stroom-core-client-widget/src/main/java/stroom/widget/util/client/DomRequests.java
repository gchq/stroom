/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.util.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.HandlerRegistration;

import java.util.function.Consumer;

/// Requests that a widget with no event bus makes of the app (e.g. to show a menu or open a link),
/// sent up the page as DOM events, which one relay for the page passes on to the event bus. The
/// request classes (`MenuRequest`, `OpenHyperlinkRequest`, `AskStroomAiTableRequest`) each name
/// their event and say what it carries; this sends and receives them.
public final class DomRequests {

    private DomRequests() {
        // Static utility
    }

    /// Sends a request up the page from an element, which must be in the page for it to arrive.
    ///
    /// @param source  The element the request is from.
    /// @param type    The request's event type, e.g. `stroom-menu-request`.
    /// @param request What the request carries.
    public static native void send(Element source, String type, Object request) /*-{
        source.dispatchEvent(new CustomEvent(type, {bubbles: true, detail: request}));
    }-*/;

    /// Receives every request of a type sent in the page, from any element.
    ///
    /// @param type    The request's event type.
    /// @param handler What receives each request's carried object.
    /// @param <T>     The type of what the request carries.
    /// @return The registration that stops receiving them.
    public static <T> HandlerRegistration receive(final String type, final Consumer<T> handler) {
        final Object listener = listen(type, handler);
        return () -> unlisten(type, listener);
    }

    // Called from the listener
    @SuppressWarnings("unchecked")
    private static <T> void deliver(final Consumer<T> handler, final Object request) {
        handler.accept((T) request);
    }

    private static native Object listen(String type, Consumer<?> handler) /*-{
        var listener = $entry(function (event) {
            @stroom.widget.util.client.DomRequests::deliver(*)(handler, event.detail);
        });
        $doc.addEventListener(type, listener);
        return listener;
    }-*/;

    private static native void unlisten(String type, Object listener) /*-{
        $doc.removeEventListener(type, listener);
    }-*/;
}
