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

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.List;

/// Measures whether a control's focus ring is drawn in full or cut off by something around it that
/// clips (an element whose `overflow` isn't `visible`), so that keyboard users can see where the
/// focus is (WCAG 2.4.7, Focus Visible; 2.4.11, Focus Not Obscured), e.g.
/// `play.expect(FocusRingClipping.cuts(button)).toEqual(List.of())`.
///
/// The ring is what the control's own `box-shadow` (other than an inset one) and `outline` draw
/// outside its border box, as they are drawn when it is measured: so measure it while it shows its
/// keyboard focus.
public final class FocusRingClipping {

    private FocusRingClipping() {
        // Static utility
    }

    /// Where a control's focus ring is cut off.
    ///
    /// @param control The control, showing its focus ring.
    /// @return For each side of the ring that is cut off, the side, how much of the ring is cut off
    /// and the classes of the innermost element that cuts it, e.g. `top 4px by .split-pane__pane`;
    /// an empty list if the ring is drawn in full. A control that draws no ring outside itself gives
    /// an empty list too, so check that the ring is drawn as well.
    public static Value<List<String>> cuts(final Query control) {
        final Value<Element> element = control.element();
        return Value.of(control.describe() + " focus ring cut off", () -> {
            final JsArrayString cuts = cuts(element.get(), reach(element.get()));
            final List<String> list = new ArrayList<>();
            for (int i = 0; i < cuts.length(); i++) {
                list.add(cuts.get(i));
            }
            return list;
        });
    }

    // How far the control's box-shadow (other than an inset one) or outline reaches past its border
    // box, in pixels, or 0 if it draws nothing outside itself
    private static native double reach(Element control) /*-{
        var style = $wnd.getComputedStyle(control);
        var reach = 0;
        // Chrome gives each shadow as '<colour> <x> <y> <blur> <spread>[ inset]', with the colour
        // (which may itself hold commas, e.g. rgba(...)) first
        var shadows = style.boxShadow === 'none' ? [] : style.boxShadow.split(/,(?![^(]*\))/);
        for (var i = 0; i < shadows.length; i++) {
            var shadow = shadows[i];
            if (/\binset\b/.test(shadow)) {
                continue;
            }
            var lengths = shadow.replace(/rgba?\([^)]*\)/, '').match(/-?[\d.]+px/g) || [];
            var x = parseFloat(lengths[0] || 0);
            var y = parseFloat(lengths[1] || 0);
            var blur = parseFloat(lengths[2] || 0);
            var spread = parseFloat(lengths[3] || 0);
            reach = Math.max(reach, spread + blur + Math.max(Math.abs(x), Math.abs(y)));
        }
        if (style.outlineStyle !== 'none') {
            reach = Math.max(reach, parseFloat(style.outlineWidth) + parseFloat(style.outlineOffset));
        }
        return reach;
    }-*/;

    private static native JsArrayString cuts(Element control, double reach) /*-{
        var cuts = [];
        if (reach <= 0) {
            return cuts;
        }
        var box = control.getBoundingClientRect();
        var ring = {
            left: box.left - reach,
            top: box.top - reach,
            right: box.right + reach,
            bottom: box.bottom + reach
        };
        var sides = ['top', 'right', 'bottom', 'left'];
        var found = {};
        for (var a = control.parentElement; a; a = a.parentElement) {
            var style = $wnd.getComputedStyle(a);
            if (style.overflowX === 'visible' && style.overflowY === 'visible') {
                continue;
            }
            // An element clips to its padding box: inside its border, and its scroll bars
            var r = a.getBoundingClientRect();
            var clip = {
                left: r.left + a.clientLeft,
                top: r.top + a.clientTop,
                right: r.left + a.clientLeft + (a === $doc.documentElement ? $wnd.innerWidth : a.clientWidth),
                bottom: r.top + a.clientTop + (a === $doc.documentElement ? $wnd.innerHeight : a.clientHeight)
            };
            var cut = {
                top: clip.top - ring.top,
                right: ring.right - clip.right,
                bottom: ring.bottom - clip.bottom,
                left: clip.left - ring.left
            };
            for (var i = 0; i < sides.length; i++) {
                var side = sides[i];
                // Rounded, as boxes can be a fraction of a pixel out
                var px = Math.round(cut[side] * 10) / 10;
                if (px >= 0.5 && !found[side]) {
                    var name = typeof a.className === 'string' && a.className.trim()
                        ? '.' + a.className.trim().split(/\s+/).join('.')
                        : a.localName;
                    found[side] = side + ' ' + px + 'px by ' + name;
                }
            }
        }
        for (var j = 0; j < sides.length; j++) {
            if (found[sides[j]]) {
                cuts.push(found[sides[j]]);
            }
        }
        return cuts;
    }-*/;
}
