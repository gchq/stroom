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

import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.List;

/// Measures whether a target smaller than 24px (e.g. a splitter, 7px wide) has the room around it
/// that WCAG 2.5.8 (Target Size, Minimum) asks of an undersized target: a 24px circle centred on
/// it must not touch another target, e.g.
/// `play.expect(TargetSpacing.crowding(splitter)).toEqual(List.of())`.
///
/// A target is anything a user can click or focus: a control, a link, an element with a
/// `tabindex` or a `role` of `button`, as shown (not hidden or inside an `inert` element).
public final class TargetSpacing {

    /// The radius of the circle that must be clear, in pixels.
    public static final double RADIUS_PX = 12;

    private TargetSpacing() {
        // Static utility
    }

    /// The targets too near a target.
    ///
    /// @param target The target.
    /// @return The names of the other targets within [#RADIUS_PX] of its centre (as
    /// [TabStops#nameOf] names them), or an empty list if it has the room it needs.
    public static Value<List<String>> crowding(final Query target) {
        final Value<Element> element = target.element();
        return Value.of(target.describe() + " targets within " + RADIUS_PX + "px", () -> {
            final JsArray<Element> near = near(element.get(), RADIUS_PX, null);
            final List<String> names = new ArrayList<>();
            for (int i = 0; i < near.length(); i++) {
                names.add(TabStops.nameOf(near.get(i)));
            }
            return names;
        });
    }

    /// The targets too near a target, leaving out those in one element (e.g. a grid's rows beside a
    /// splitter, which are accepted as full-width targets; see `ISSUES.md`).
    ///
    /// @param target   The target.
    /// @param ignoring The element whose targets (itself included) are left out.
    /// @return The names of the other targets within [#RADIUS_PX] of its centre, not in `ignoring`.
    public static Value<List<String>> crowding(final Query target, final Query ignoring) {
        final Value<Element> element = target.element();
        final Value<Element> ignored = ignoring.element();
        final String description = target.describe() + " targets within " + RADIUS_PX + "px, not in "
                                   + ignoring.describe();
        return Value.of(description, () -> {
            final JsArray<Element> near = near(element.get(), RADIUS_PX, ignored.get());
            final List<String> names = new ArrayList<>();
            for (int i = 0; i < near.length(); i++) {
                names.add(TabStops.nameOf(near.get(i)));
            }
            return names;
        });
    }

    private static native JsArray<Element> near(Element target, double radius, Element ignoring) /*-{
        var box = target.getBoundingClientRect();
        var x = box.left + box.width / 2;
        var y = box.top + box.height / 2;
        var selector = 'a[href], button, input:not([type=hidden]), select, textarea, [tabindex], '
            + '[role=button]';
        var near = [];
        var candidates = target.ownerDocument.querySelectorAll(selector);
        for (var i = 0; i < candidates.length; i++) {
            var other = candidates[i];
            if (other === target || target.contains(other) || other.contains(target)
                || (ignoring && ignoring.contains(other))
                || other.closest('[inert]') || other.getClientRects().length === 0) {
                continue;
            }
            var r = other.getBoundingClientRect();
            if (r.width === 0 || r.height === 0) {
                continue;
            }
            var dx = Math.max(r.left - x, 0, x - r.right);
            var dy = Math.max(r.top - y, 0, y - r.bottom);
            if (Math.sqrt(dx * dx + dy * dy) < radius) {
                near.push(other);
            }
        }
        return near;
    }-*/;
}
