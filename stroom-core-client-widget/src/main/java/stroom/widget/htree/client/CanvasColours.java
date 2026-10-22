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

package stroom.widget.htree.client;

import com.google.gwt.dom.client.Element;

/// The colours a tree's connectors are drawn with on a canvas, which forced colours (e.g. Windows
/// High Contrast) don't change, as they do the page's: there they are the user's text colour.
public final class CanvasColours {

    private CanvasColours() {
        // Static utility
    }

    /// @return Whether forced colours are on (e.g. Windows High Contrast).
    public static native boolean isForcedColours() /*-{
        return !!($wnd.matchMedia && $wnd.matchMedia("(forced-colors: active)").matches);
    }-*/;

    /// @param element An element, e.g. the canvas.
    /// @return Its text colour, which in forced colours is the user's; if it has none yet (e.g. it
    /// isn't in the document yet, when a tree is first drawn), the user's text colour by its system
    /// colour's name, `CanvasText`.
    public static native String getTextColour(Element element) /*-{
        var colour = $wnd.getComputedStyle(element).color;
        return colour ? colour : "CanvasText";
    }-*/;

    /// @param element  An element, e.g. the canvas.
    /// @param property A custom property, e.g. "--pipeline-connector__raw".
    /// @return Its value there, trimmed, or an empty string if it has none.
    public static native String getProperty(Element element, String property) /*-{
        return $wnd.getComputedStyle(element).getPropertyValue(property).trim();
    }-*/;
}
