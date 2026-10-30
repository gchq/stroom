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

package stroom.widget.datagrid.client;

import stroom.widget.datagrid.client.model.SelectionRules;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;

/// Small browser helpers used by [DataGrid] that GWT's DOM classes do not provide.
final class DataGridDom {

    private DataGridDom() {
        // Utility class.
    }

    /// Add an event listener.
    ///
    /// @param element  The element to listen on.
    /// @param type     The event type, e.g. `"scroll"`.
    /// @param capture  Whether to listen in the capture phase, e.g. for `focus` and `blur`, which
    ///                 do not bubble.
    /// @param listener The listener.
    /// @return A handle to pass to [#removeListener].
    static native JavaScriptObject addListener(Element element,
                                               String type,
                                               boolean capture,
                                               Listener listener) /*-{
        var fn = $entry(function (e) {
            listener.@stroom.widget.datagrid.client.DataGridDom.Listener::onEvent(*)(e);
        });
        element.addEventListener(type, fn, {capture: capture, passive: false});
        return {element: element, type: type, fn: fn, capture: capture};
    }-*/;

    /// Remove a listener added with [#addListener].
    ///
    /// @param handle The handle returned by [#addListener].
    static native void removeListener(JavaScriptObject handle) /*-{
        if (handle) {
            handle.element.removeEventListener(handle.type, handle.fn, {capture: handle.capture});
        }
    }-*/;

    /// Run a callback before the next repaint.
    ///
    /// @param callback The callback.
    static native void requestAnimationFrame(Runnable callback) /*-{
        $wnd.requestAnimationFrame($entry(function () {
            callback.@java.lang.Runnable::run()();
        }));
    }-*/;

    /// @param event A pointer event.
    /// @return The pointer id.
    static native int getPointerId(NativeEvent event) /*-{
        return event.pointerId || 0;
    }-*/;

    /// Route all further events for a pointer to an element until it is released.
    ///
    /// @param element   The element.
    /// @param pointerId The pointer id.
    static native void setPointerCapture(Element element, int pointerId) /*-{
        if (element.setPointerCapture) {
            try {
                element.setPointerCapture(pointerId);
            } catch (e) {
                // The pointer may already have been released.
            }
        }
    }-*/;

    /// Release a pointer captured with [#setPointerCapture].
    ///
    /// @param element   The element.
    /// @param pointerId The pointer id.
    static native void releasePointerCapture(Element element, int pointerId) /*-{
        if (element.releasePointerCapture) {
            try {
                element.releasePointerCapture(pointerId);
            } catch (e) {
                // The pointer may already have been released.
            }
        }
    }-*/;

    /// Focus an element without the browser scrolling it into view.
    ///
    /// @param element The element.
    static native void focusWithoutScroll(Element element) /*-{
        element.focus({preventScroll: true});
    }-*/;

    /// Focus an element without scrolling, saying whether it shows as keyboard focus.
    ///
    /// @param element      The element.
    /// @param focusVisible Whether it matches `:focus-visible`, as after a key, or not, as after a
    ///                     press.
    static native void focusWithoutScroll(Element element, boolean focusVisible) /*-{
        element.focus({preventScroll: true, focusVisible: focusVisible});
    }-*/;

    /// @param container An element.
    /// @param control   A control in it.
    /// @return The control's position among the container's controls (fields, buttons, links and
    /// other focusable elements), or -1 if it isn't one of them.
    static native int controlIndex(Element container, Element control) /*-{
        var controls = container.querySelectorAll(
            'input, textarea, select, button, a[href], [tabindex]:not([data-hcol])');
        return Array.prototype.indexOf.call(controls, control);
    }-*/;

    /// @param container An element.
    /// @param index     A position among the container's controls, as [#controlIndex] gives.
    /// @return The control at that position, or `null`.
    static native Element controlAt(Element container, int index) /*-{
        var controls = container.querySelectorAll(
            'input, textarea, select, button, a[href], [tabindex]:not([data-hcol])');
        return controls[index] || null;
    }-*/;

    /// @param element An element.
    /// @return The start of the text selected in it if it is a text field, otherwise -1.
    static native int getSelectionStart(Element element) /*-{
        try {
            return typeof element.selectionStart === 'number' ? element.selectionStart : -1;
        } catch (e) {
            return -1;
        }
    }-*/;

    /// @param element An element.
    /// @return The end of the text selected in it if it is a text field, otherwise -1.
    static native int getSelectionEnd(Element element) /*-{
        try {
            return typeof element.selectionEnd === 'number' ? element.selectionEnd : -1;
        } catch (e) {
            return -1;
        }
    }-*/;

    /// Select text in a text field, e.g. to put its caret back where it was. Does nothing if the
    /// element isn't a text field or either position is negative.
    ///
    /// @param element The element.
    /// @param start   The start of the selection.
    /// @param end     The end of the selection.
    static native void setSelectionRange(Element element, int start, int end) /*-{
        if (start < 0 || end < 0 || typeof element.setSelectionRange !== 'function') {
            return;
        }
        try {
            element.setSelectionRange(start, end);
        } catch (e) {
            // Not a text field, e.g. a number input
        }
    }-*/;

    /// Scroll a scroller sideways so that an element in it is in view, moving as little as
    /// possible: to its left edge if it's wider than the view.
    ///
    /// @param scroller The scroller.
    /// @param element  An element in it.
    static native void scrollHorizontallyWithin(Element scroller, Element element) /*-{
        var view = scroller.getBoundingClientRect();
        var box = element.getBoundingClientRect();
        var left = view.left;
        var right = view.left + scroller.clientWidth;
        if (box.left < left) {
            scroller.scrollLeft -= left - box.left;
        } else if (box.right > right) {
            scroller.scrollLeft += Math.min(box.right - right, box.left - left);
        }
    }-*/;

    /// @return The element that has the focus, or `null`.
    static native Element getActiveElement() /*-{
        return $doc.activeElement;
    }-*/;

    /// @param element An element.
    /// @return The element's rendered height in pixels, including fractions.
    static native double getHeight(Element element) /*-{
        return element.getBoundingClientRect().height;
    }-*/;

    /// @param element An element.
    /// @return The element's rendered width in pixels, including fractions.
    static native double getWidth(Element element) /*-{
        return element.getBoundingClientRect().width;
    }-*/;

    /// Remove any text selection from the page, e.g. one started by the press that began a drag.
    static native void clearTextSelection() /*-{
        var selection = $wnd.getSelection ? $wnd.getSelection() : null;
        if (selection) {
            selection.removeAllRanges();
        }
    }-*/;

    /// Get the text a piece of HTML shows, without rendering it. Block elements and line breaks
    /// separate the text either side of them with a space, as they would when shown.
    ///
    /// @param html The HTML.
    /// @return The text, with whitespace not yet normalised.
    static native String textOfHtml(String html) /*-{
        var div = $doc.createElement("div");
        div.innerHTML = html;
        var blocks = div.querySelectorAll("div,p,br,li,tr,td,th,h1,h2,h3,h4,h5,h6,table,ul,ol,pre");
        for (var i = 0; i < blocks.length; i++) {
            blocks[i].appendChild($doc.createTextNode(" "));
        }
        return div.textContent || "";
    }-*/;

    /// Offer some text to the user as a file download.
    ///
    /// @param fileName The file name.
    /// @param mimeType The file's MIME type, e.g. `"text/csv"`.
    /// @param text     The file's content.
    static native void download(String fileName, String mimeType, String text) /*-{
        var blob = new Blob([text], {type: mimeType});
        var link = $doc.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = fileName;
        link.hidden = true;
        $doc.body.appendChild(link);
        link.click();
        $doc.body.removeChild(link);
        // The download has started by now, but give the browser a moment before freeing it.
        $wnd.setTimeout(function () {
            URL.revokeObjectURL(link.href);
        }, 1000);
    }-*/;

    /// @param element An element.
    /// @return The distance in pixels from the element's top edge to its offset parent's.
    static native double getOffsetTop(Element element) /*-{
        return element.offsetTop;
    }-*/;

    /// @return Whether the browser is on macOS (or another Apple system), where Cmd does what Ctrl
    /// does elsewhere.
    static native boolean isMac() /*-{
        var nav = $wnd.navigator;
        var platform = (nav.userAgentData && nav.userAgentData.platform) || nav.platform || "";
        return /mac|iphone|ipad/i.test(platform);
    }-*/;

    /// @param event A key or mouse event.
    /// @return Whether it counts as one with Ctrl held: Ctrl, or Cmd on macOS (design deviation D3).
    static boolean isCtrl(final NativeEvent event) {
        return SelectionRules.isCtrl(event.getCtrlKey(), event.getMetaKey(), isMac());
    }

    /// @param event A key event.
    /// @return Whether the meta key is held for itself, not standing in for Ctrl (as Cmd does on
    /// macOS): such keys are left to the browser and the system.
    static boolean isOtherMeta(final NativeEvent event) {
        return event.getMetaKey() && !isMac();
    }


    // --------------------------------------------------------------------------------


    /// A browser event listener.
    interface Listener {

        /// Called when the event fires.
        ///
        /// @param event The event.
        void onEvent(NativeEvent event);
    }
}
