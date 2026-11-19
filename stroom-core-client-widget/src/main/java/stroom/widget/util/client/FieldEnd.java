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

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;

/// Keeps a text field's text clear of what is drawn over its end (e.g. a password box's show/hide
/// button and badge, or a quick filter's buttons), whose width changes as buttons come and go or a
/// badge's text changes.
///
/// While watching, it sets the field's `--field__end-space` to how far the overlay reaches into the
/// field from its end, plus a [#GAP], which the field's stylesheet uses as its end padding, e.g.
/// `padding-right: var(--field__end-space, 22px)` (the fallback being for before the field is
/// shown). Fields whose overlay never changes width (an icon box's icon) set the padding in CSS.
public final class FieldEnd {

    /// The space, in pixels, between the end of a field's text and what is drawn over its end.
    public static final int GAP = 4;
    /// The CSS custom property set on the field.
    public static final String END_SPACE = "--field__end-space";

    private final Element field;
    private final Element end;
    private JavaScriptObject observer;

    /// @param field The text field (an input).
    /// @param end   What is drawn over the field's end.
    public FieldEnd(final Element field, final Element end) {
        this.field = field;
        this.end = end;
    }

    /// Starts keeping the text clear, e.g. when the widget is attached. Starting again does nothing.
    public void start() {
        if (observer == null) {
            observer = observe(field, end, END_SPACE, GAP);
        }
    }

    /// Stops keeping the text clear, e.g. when the widget is detached, leaving the end space as it is.
    public void stop() {
        if (observer != null) {
            disconnect(observer);
            observer = null;
        }
    }

    // Measures how far the end reaches into the field (from the field's inner edge) whenever the
    // end or the field changes size. The padding is set in the next frame, and only when it
    // changes, as changing it while the browser delivers sizes would make it report a loop
    private static native JavaScriptObject observe(Element field,
                                                   Element end,
                                                   String property,
                                                   int gap) /*-{
        var measure = function () {
            var f = field.getBoundingClientRect();
            var e = end.getBoundingClientRect();
            if (f.width === 0 || e.width === 0) {
                // Not shown yet (or nothing over the end): the stylesheet's fallback applies
                return '';
            }
            var border = parseFloat($wnd.getComputedStyle(field).borderRightWidth) || 0;
            var space = Math.ceil(f.right - border - e.left) + gap;
            return Math.max(space, gap) + 'px';
        };
        var apply = function () {
            var value = measure();
            if (field.style.getPropertyValue(property) !== value) {
                if (value) {
                    field.style.setProperty(property, value);
                } else {
                    field.style.removeProperty(property);
                }
            }
        };
        var update = function () {
            $wnd.requestAnimationFrame(apply);
        };
        var observer = new $wnd.ResizeObserver(update);
        observer.observe(end);
        observer.observe(field, {box: 'border-box'});
        update();
        return observer;
    }-*/;

    private static native void disconnect(JavaScriptObject observer) /*-{
        observer.disconnect();
    }-*/;
}
