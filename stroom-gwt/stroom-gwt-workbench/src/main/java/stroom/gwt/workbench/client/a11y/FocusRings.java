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

import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Value;

import com.google.gwt.dom.client.Element;

/// What draws a control's focus ring, so a play can check that one is drawn, e.g.
/// `play.expect(FocusRings.drawn(button)).not().toBe(FocusRings.NONE)`.
///
/// A focus ring is the control's box shadow (`--focus-shadow`). In forced colours (e.g. Windows high
/// contrast) no box shadow is drawn and a focus ring is an outline (`base.css`), so there it is the
/// outline, if one is drawn. The runner's real input checks measure it the same way
/// (`test-runner/lib/real-input/focus-ring.mjs`).
public final class FocusRings {

    /// What [#drawn(Query)] gives when no ring is drawn.
    public static final String NONE = "none";

    private FocusRings() {
        // Static utility
    }

    /// What draws a control's focus ring.
    ///
    /// @param control The control.
    /// @return Its box shadow; in forced colours, if it has none, its outline as
    /// `outline <width> <colour>`; or [#NONE] if neither is drawn.
    public static Value<String> drawn(final Query control) {
        final Value<Element> element = control.element();
        return Value.of(control.describe() + " focus ring", () -> drawn(element.get()));
    }

    private static native String drawn(Element control) /*-{
        var style = $wnd.getComputedStyle(control);
        if (style.boxShadow !== 'none') {
            return style.boxShadow;
        }
        if ($wnd.matchMedia('(forced-colors: active)').matches
                && style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) > 0) {
            return 'outline ' + style.outlineWidth + ' ' + style.outlineColor;
        }
        return 'none';
    }-*/;
}
