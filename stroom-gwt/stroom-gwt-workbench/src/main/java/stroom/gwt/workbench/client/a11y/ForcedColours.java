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

package stroom.gwt.workbench.client.a11y;

import com.google.gwt.dom.client.Element;

/// Whether the story is shown in forced colours (e.g. Windows high contrast, which the runner
/// emulates with `--forced-colours`), and the system colours used there, for plays that check
/// colours: there the browser replaces the theme's colours with a few system ones, so a play that
/// checks a theme colour checks the system colour the stylesheet uses there instead.
public final class ForcedColours {

    private ForcedColours() {
        // Static utility
    }

    /// Whether forced colours are active (`forced-colors: active`).
    ///
    /// @return True in forced colours.
    public static native boolean active() /*-{
        return $wnd.matchMedia('(forced-colors: active)').matches;
    }-*/;

    /// A system colour as a computed colour, to compare with an element's, e.g. `Highlight` as
    /// `rgb(0, 0, 255)`.
    ///
    /// @param systemColour A CSS system colour keyword, e.g. `Highlight` or `CanvasText`.
    /// @return Its computed colour in the current palette.
    public static native String colour(String systemColour) /*-{
        var probe = $doc.createElement('div');
        probe.style.forcedColorAdjust = 'none';
        probe.style.backgroundColor = systemColour;
        $doc.body.appendChild(probe);
        var colour = $wnd.getComputedStyle(probe).backgroundColor;
        probe.remove();
        return colour;
    }-*/;

    /// Whether an outline is drawn on an element, as the keyboard focus is in forced colours.
    ///
    /// @param element The element.
    /// @return True if it has an outline.
    public static native boolean hasOutline(Element element) /*-{
        var style = $wnd.getComputedStyle(element);
        return style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) > 0;
    }-*/;
}
