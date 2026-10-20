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

package stroom.gwt.workbench.framework.client;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;

import java.util.function.Consumer;

/// Browser APIs that GWT doesn't provide, used by the workbench manager and preview.
public final class BrowserUtil {

    /// The `type` of the message the preview posts to the manager when an action is logged.
    public static final String ACTION_MESSAGE = "wbm-action";
    /// The `type` of the message the manager posts to the preview to toggle the outline tool.
    public static final String OUTLINE_MESSAGE = "wbm-outline";
    /// The `type` of the message the manager posts to the preview to toggle the measure tool.
    public static final String MEASURE_MESSAGE = "wbm-measure";
    /// The `type` of the message the manager posts to the preview to set the vision filter.
    public static final String VISION_MESSAGE = "wbm-vision";
    /// The `type` of the message the manager posts to the preview when the user changes args.
    public static final String ARGS_MESSAGE = "wbm-args";
    /// The `type` of the message the manager posts to the preview to use the Interactions
    /// addon's debugger controls.
    public static final String PLAY_CONTROL_MESSAGE = "wbm-play-control";
    /// The `type` of the message the preview posts to the manager with the progress of a play
    /// function.
    public static final String INTERACTIONS_MESSAGE = "wbm-interactions";
    /// The `type` of the message the preview posts to the manager with the accessibility check
    /// results.
    public static final String A11Y_RESULT_MESSAGE = "wbm-a11y-result";
    /// The `type` of the message the manager posts to the preview to rerun the accessibility checks.
    public static final String A11Y_RUN_MESSAGE = "wbm-a11y-run";
    /// The `type` of the message the manager posts to the preview to outline elements.
    public static final String A11Y_HIGHLIGHT_MESSAGE = "wbm-a11y-highlight";
    /// The `type` of the message the manager posts to the preview to scroll to an element.
    public static final String A11Y_JUMP_MESSAGE = "wbm-a11y-jump";
    /// The `type` of the message the preview posts to the manager when a key is pressed, so
    /// that keyboard shortcuts work while the canvas has the focus.
    public static final String KEYDOWN_MESSAGE = "wbm-keydown";


    private BrowserUtil() {
        // Static utility
    }

    /// Adds a listener for a DOM event on an element that isn't part of a GWT widget.
    ///
    /// @param element   The element.
    /// @param eventType The event type, e.g. `click` or `input`.
    /// @param handler   Called with each event.
    public static void addListener(final Element element,
                                   final String eventType,
                                   final Consumer<Event> handler) {
        DOM.sinkBitlessEvent(element, eventType);
        final com.google.gwt.user.client.EventListener existing = DOM.getEventListener(element);
        DOM.setEventListener(element, event -> {
            if (existing != null) {
                existing.onBrowserEvent(event);
            }
            if (eventType.equals(event.getType())) {
                handler.accept(event);
            }
        });
    }

    /// @param element  The element to start from.
    /// @param selector A CSS selector.
    /// @return The closest ancestor (or self) matching the selector, or null if there isn't one.
    public static native Element closest(Element element, String selector) /*-{
        if (!element || !element.closest) {
            return null;
        }
        return element.closest(selector);
    }-*/;

    /// @param element  The element to search within.
    /// @param selector A CSS selector.
    /// @return The first element within the element matching the selector, or null.
    public static native Element querySelector(Element element, String selector) /*-{
        return element.querySelector(selector);
    }-*/;

    /// @param element  The element to search within.
    /// @param selector A CSS selector.
    /// @return All the elements within the element matching the selector, in document order.
    public static native NodeList<Element> querySelectorAll(Element element, String selector) /*-{
        return element.querySelectorAll(selector);
    }-*/;

    /// Scrolls an element into view if it isn't already visible.
    ///
    /// @param element The element.
    public static native void scrollIntoViewIfNeeded(Element element) /*-{
        if (element) {
            element.scrollIntoView({block: 'nearest'});
        }
    }-*/;

    /// Changes the URL without reloading the page.
    ///
    /// @param url     The new (relative) URL.
    /// @param replace True to replace the current history entry rather than add a new one.
    public static native void setUrl(String url, boolean replace) /*-{
        if (replace) {
            $wnd.history.replaceState(null, '', url);
        } else {
            $wnd.history.pushState(null, '', url);
        }
    }-*/;

    /// @param handler Called when the user navigates back or forward.
    public static native void addPopStateHandler(Runnable handler) /*-{
        $wnd.addEventListener('popstate', $entry(function () {
            handler.@java.lang.Runnable::run()();
        }));
    }-*/;

    /// @param name The name of a query parameter.
    /// @return The current value of the query parameter, or null if it isn't set. Unlike
    /// `Window.Location`, this reflects changes made with [#setUrl(String, boolean)].
    public static native String getQueryParameter(String name) /*-{
        var value = new $wnd.URLSearchParams($wnd.location.search).get(name);
        return value === null ? null : value;
    }-*/;

    /// Shows a URL in an iframe without adding an entry to the browser's history, so that the
    /// back button moves between stories rather than between iframe pages.
    ///
    /// @param iframe The iframe.
    /// @param url    The (relative) URL to show.
    public static native void replaceLocation(IFrameElement iframe, String url) /*-{
        if (iframe.contentWindow && iframe.getAttribute('src')) {
            iframe.contentWindow.location.replace(url);
        } else {
            iframe.setAttribute('src', url);
        }
    }-*/;

    /// @param event A keyboard event.
    /// @return The `code` of the key, e.g. `KeyA`, which doesn't depend on the keyboard layout or
    /// modifiers.
    public static native String getKeyCode(NativeEvent event) /*-{
        return event.code || null;
    }-*/;

    /// Reads a value from the browser's local storage.
    ///
    /// @param key The key.
    /// @return The value, or null if there isn't one or local storage isn't available.
    public static native String getLocalStorage(String key) /*-{
        try {
            var value = $wnd.localStorage.getItem(key);
            return value === null ? null : value;
        } catch (e) {
            return null;
        }
    }-*/;

    /// Writes a value to the browser's local storage, ignoring any failure.
    ///
    /// @param key   The key.
    /// @param value The value, or null to remove the key.
    public static native void setLocalStorage(String key, String value) /*-{
        try {
            if (value === null) {
                $wnd.localStorage.removeItem(key);
            } else {
                $wnd.localStorage.setItem(key, value);
            }
        } catch (e) {
            // Local storage isn't available, e.g. in a private window
        }
    }-*/;

    /// Copies text to the clipboard.
    ///
    /// @param text The text to copy.
    public static native void copyToClipboard(String text) /*-{
        if ($wnd.navigator.clipboard) {
            $wnd.navigator.clipboard.writeText(text);
        }
    }-*/;

    /// Moves the keyboard focus into the page in an iframe.
    ///
    /// @param iframe The iframe.
    public static native void focusIframe(IFrameElement iframe) /*-{
        iframe.focus();
        if (iframe.contentWindow) {
            iframe.contentWindow.focus();
        }
    }-*/;

    /// Sets a CSS custom property on an element, which GWT's `Style` can't do.
    ///
    /// @param element The element.
    /// @param name    The name of the property, e.g. `--wbm-sidebar-width`.
    /// @param value   The value.
    public static native void setCssVariable(Element element, String name, String value) /*-{
        element.style.setProperty(name, value);
    }-*/;

    /// Focuses an element, first focusing this window in case the focus is in an iframe (e.g.
    /// when a shortcut was pressed in the preview).
    ///
    /// @param element The element to focus.
    public static native void focus(Element element) /*-{
        $wnd.focus();
        element.focus();
    }-*/;

    /// Sends a request to the workbench server's API.
    ///
    /// @param method   `GET` or `POST`.
    /// @param url      The (relative) URL.
    /// @param callback Called with the HTTP status and the response body. The status is 0 if the
    ///                 request failed, e.g. the server isn't running.
    public static native void request(String method, String url, ResponseCallback callback) /*-{
        $wnd.fetch(url, {method: method}).then(function (response) {
            return response.text().then($entry(function (text) {
                callback.@stroom.gwt.workbench.framework.client.BrowserUtil.ResponseCallback::onResponse(*)(
                    response.status, text);
            }));
        })['catch']($entry(function () {
            callback.@stroom.gwt.workbench.framework.client.BrowserUtil.ResponseCallback::onResponse(*)(0, null);
        }));
    }-*/;

    /// @param value A value for a URL query parameter.
    /// @return The value encoded for use in a URL.
    public static native String encodeQueryParam(String value) /*-{
        return encodeURIComponent(value);
    }-*/;

    /// @return The absolute URL of the current page.
    public static native String getHref() /*-{
        return $wnd.location.href;
    }-*/;

    /// Reloads the page in an iframe.
    ///
    /// @param iframe The iframe.
    public static native void reload(IFrameElement iframe) /*-{
        if (iframe.contentWindow) {
            iframe.contentWindow.location.reload();
        }
    }-*/;

    /// Posts a message from the preview to the manager page that contains it.
    ///
    /// @param type   The type of the message.
    /// @param name   A name, e.g. the name of an action.
    /// @param detail Detail about the message.
    public static native void postToManager(String type, String name, String detail) /*-{
        if ($wnd.parent && $wnd.parent !== $wnd) {
            $wnd.parent.postMessage({type: type, name: name, detail: detail}, $wnd.location.origin);
        }
    }-*/;

    /// Posts a message from the manager to the preview in an iframe.
    ///
    /// @param iframe The iframe containing the preview.
    /// @param type   The type of the message.
    /// @param value  A value for the message.
    public static native void postToPreview(IFrameElement iframe, String type, boolean value) /*-{
        if (iframe.contentWindow) {
            iframe.contentWindow.postMessage({type: type, value: value}, $wnd.location.origin);
        }
    }-*/;

    /// Posts a message from the manager to the preview in an iframe.
    ///
    /// @param iframe The iframe containing the preview.
    /// @param type   The type of the message.
    /// @param name   A value for the message, may be null.
    public static native void postToPreview(IFrameElement iframe, String type, String name) /*-{
        if (iframe.contentWindow) {
            iframe.contentWindow.postMessage({type: type, name: name}, $wnd.location.origin);
        }
    }-*/;

    /// Posts a message from the manager to the preview in an iframe.
    ///
    /// @param iframe The iframe containing the preview.
    /// @param type   The type of the message.
    /// @param name   A value for the message, may be null.
    /// @param detail Another value for the message, may be null.
    public static native void postToPreview(IFrameElement iframe, String type, String name, String detail) /*-{
        if (iframe.contentWindow) {
            iframe.contentWindow.postMessage({type: type, name: name, detail: detail}, $wnd.location.origin);
        }
    }-*/;

    /// Listens for messages posted to this window by its parent window, i.e. messages from the
    /// manager to the preview. Messages from any other window or origin are ignored.
    ///
    /// @param handler Called with the type, name and detail of each message.
    public static native void addMessageHandler(MessageHandler handler) /*-{
        $wnd.addEventListener('message', $entry(function (event) {
            if (!@stroom.gwt.workbench.framework.client.BrowserUtil::isFromWindow(*)(event, $wnd.parent)) {
                return;
            }
            @stroom.gwt.workbench.framework.client.BrowserUtil::dispatchMessage(*)(event, handler);
        }));
    }-*/;

    /// Listens for messages posted to this window by the page in an iframe, i.e. messages from
    /// the preview to the manager. Messages from any other window or origin are ignored.
    ///
    /// @param iframe  The iframe whose page may post messages.
    /// @param handler Called with the type, name and detail of each message.
    public static native void addMessageHandler(IFrameElement iframe, MessageHandler handler) /*-{
        $wnd.addEventListener('message', $entry(function (event) {
            if (!@stroom.gwt.workbench.framework.client.BrowserUtil::isFromWindow(*)(event, iframe.contentWindow)) {
                return;
            }
            @stroom.gwt.workbench.framework.client.BrowserUtil::dispatchMessage(*)(event, handler);
        }));
    }-*/;

    /// GWT runs its code in a hidden iframe, so a message posted by GWT code in a page comes from
    /// that iframe's window rather than the page's own window.
    ///
    /// @return True if the message was posted by the window or by a frame directly inside it.
    private static native boolean isFromWindow(JavaScriptObject event, JavaScriptObject window) /*-{
        var source = event.source;
        return !!source && !!window && (source === window || source.parent === window);
    }-*/;

    private static native void dispatchMessage(JavaScriptObject event, MessageHandler handler) /*-{
        if (event.origin !== $wnd.location.origin || !event.data || !event.data.type) {
            return;
        }
        var data = event.data;
        handler.@stroom.gwt.workbench.framework.client.BrowserUtil.MessageHandler::onMessage(*)(
            String(data.type),
            data.name == null ? null : String(data.name),
            data.detail == null ? null : String(data.detail),
            !!data.value);
    }-*/;

    /// @return The element that has the keyboard focus in this page, or null if none.
    public static native Element getActiveElement() /*-{
        return $doc.activeElement || null;
    }-*/;

    /// @param event A mouse event.
    /// @return The `buttons` of the event, i.e. a bit for each mouse button held down.
    public static native int getButtons(NativeEvent event) /*-{
        return event.buttons || 0;
    }-*/;

    /// Listens for this window losing the focus, e.g. when the user switches to another window.
    ///
    /// @param handler Called each time the window loses the focus.
    public static native void addWindowBlurHandler(Runnable handler) /*-{
        $wnd.addEventListener('blur', $entry(function () {
            handler.@java.lang.Runnable::run()();
        }));
    }-*/;


    // --------------------------------------------------------------------------------


    /// Receives messages posted between the manager and the preview.
    public interface MessageHandler {

        /// @param type   The type of the message.
        /// @param name   The name in the message, may be null.
        /// @param detail The detail in the message, may be null.
        /// @param value  The boolean value in the message.
        void onMessage(String type, String name, String detail, boolean value);
    }


    // --------------------------------------------------------------------------------


    /// Receives the response to [#request(String, String, ResponseCallback)].
    public interface ResponseCallback {

        /// @param status The HTTP status, or 0 if the request failed.
        /// @param body   The response body, or null if the request failed.
        void onResponse(int status, String body);
    }
}
