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

package stroom.floormap.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.UIObject;

/// ARIA attribute helpers for the floor map's views.
///
/// Exists because the floor map is unusually short of things a
/// `<label for>` can point at. A `for` attribute may only reference a
/// *labelable* element — an `input`, `select`, `textarea`
/// or `button` — and much of what this feature puts in a form row is not
/// one of those:
///
/// - `DateTimeBox` and `SelectionBox` are composites whose root is
///   a wrapper `div` with the real input nested inside, so
///   `FormGroup.setIdentity()` lands the id on the wrapper and the
///   association silently fails.
/// - Store pickers arrive as an injected view dropped into a
///   `SimplePanel`, so the row has no control of its own to name.
/// - Rows like *Position (X, Y)* hold two inputs under one visible
///   label, which cannot name both.
///
/// [#label(UIObject, String)] is the fix where the target really is an
/// input; [#group(UIObject, String)] is the fix for the composite and
/// multi-control cases, where the honest description of the row is "a named group
/// of controls" rather than "one named control".
///
/// Prefer `FormGroup.setIdentity()` to anything here when the row holds a
/// single plain input: a real `<label for>` also makes the visible label
/// click-to-focus, which `aria-label` does not.
public final class FloorMapAria {

    private static final String ARIA_LABEL = "aria-label";
    private static final String ARIA_LABELLEDBY = "aria-labelledby";
    private static final String ARIA_HIDDEN = "aria-hidden";
    private static final String ROLE = "role";

    /// Tag names that can take keyboard focus without an explicit tabindex. Used by
    /// [#firstFocusable(Element)] to find the real control inside a container, and so
    /// by both [#focusFirstFocusable(Element)] and
    /// [#labelInnerControl(UIObject, String)].
    private static final String[] FOCUSABLE_TAGS = {"input", "select", "textarea", "button", "a"};

    private FloorMapAria() {
        // Utility class.
    }

    /// Names a single control. Use only where the element really is the control —
    /// on a wrapper `div` an `aria-label` is ignored, because an
    /// element with no role has nothing to attach a name to. Use
    /// [#group(UIObject, String)] there instead.
    public static void label(final UIObject uiObject, final String label) {
        if (uiObject != null) {
            uiObject.getElement().setAttribute(ARIA_LABEL, label);
        }
    }

    /// As [#label(UIObject, String)], for a raw element.
    public static void label(final Element element, final String label) {
        if (element != null) {
            element.setAttribute(ARIA_LABEL, label);
        }
    }

    /// Names the real control nested inside a composite widget, rather than the
    /// widget's wrapper element.
    ///
    /// Use for a composite that wraps exactly one control — `CustomCheckBox`,
    /// whose root is a `div.SimpleTickBox` with the `<input>` inside it.
    /// [#label(UIObject, String)] on such a widget lands the name on the wrapper,
    /// where it is dropped; [#group(UIObject, String)] would work but describes a
    /// single checkbox as a group of controls, which is a worse reading than naming the
    /// checkbox itself.
    ///
    /// Prefer this to `group()` when there is one control, and `group()`
    /// when there are several or none.
    ///
    /// Skips `disabled` and `tabindex="-1"` candidates, matching
    /// [#focusFirstFocusable(Element)] — a composite that keeps a hidden or disabled
    /// input ahead of its real control would otherwise be named on the wrong element.
    ///
    /// @param label the name to apply; `null` is treated as no label and returns
    ///         `false` rather than writing "null" into the attribute
    /// @return `true` if an inner control was found and named. A `false` return
    ///         means the widget's shape was not what the caller assumed and the control is
    ///         still anonymous. Callers that cannot recover should at least log it: a silent
    ///         false is indistinguishable from success.
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean labelInnerControl(final UIObject uiObject, final String label) {
        if (uiObject == null || label == null) {
            return false;
        }
        final Element control = firstFocusable(uiObject.getElement());
        if (control == null) {
            return false;
        }
        control.setAttribute(ARIA_LABEL, label);
        return true;
    }

    /// The first natively-focusable descendant of `container` that is a legitimate
    /// target — neither `disabled` nor removed from the tab order — or `null`.
    ///
    /// "First" means first in `FOCUSABLE_TAGS` order, not document order: every
    /// `input` is considered before any `select`, and so on. So in
    /// `<button/><input/>` the input wins, not the button that precedes it.
    ///
    /// Shared by [#labelInnerControl(UIObject, String)] and
    /// [#focusFirstFocusable(Element)] so the two cannot disagree about which element
    /// counts as "the control inside this widget".
    private static Element firstFocusable(final Element container) {
        if (container == null) {
            return null;
        }
        for (final String tag : FOCUSABLE_TAGS) {
            final NodeList<Element> candidates = container.getElementsByTagName(tag);
            for (int i = 0; i < candidates.getLength(); i++) {
                final Element candidate = candidates.getItem(i);
                if (!candidate.hasAttribute("disabled")
                        && !"-1".equals(candidate.getAttribute("tabindex"))) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /// Marks `uiObject` as a named group of controls.
    ///
    /// The explicit `role="group"` is the load-bearing half: without it
    /// the name has nothing to attach to and is dropped. With it, a screen reader
    /// announces the group on entry and then each control inside, which is the
    /// best available reading of a row like *Position (X, Y)* — or of a
    /// composite widget whose inner input a `<label for>` cannot reach.
    public static void group(final UIObject uiObject, final String label) {
        if (uiObject != null) {
            uiObject.getElement().setAttribute(ROLE, "group");
            uiObject.getElement().setAttribute(ARIA_LABEL, label);
        }
    }

    /// Points `uiObject`'s accessible name at the element with `id`.
    public static void labelledBy(final UIObject uiObject, final String id) {
        if (uiObject != null) {
            uiObject.getElement().setAttribute(ARIA_LABELLEDBY, id);
        }
    }

    /// Names `target` after the text already sitting in a [Grid] cell.
    ///
    /// Several of the floor map's dialogs lay out label/control pairs as a
    /// two-column `Grid`, which puts the label text in a `<td>`. A
    /// `<td>` is not a `<label>`, so that text names nothing —
    /// visually it reads as a form, but to a screen reader the controls are
    /// anonymous.
    ///
    /// This wires the two together via `aria-labelledby` rather than by
    /// swapping the cell for a real `<label for>`, because `aria-labelledby`
    /// leaves the existing markup and CSS (`td:first-child` alignment and
    /// colour rules) untouched. The trade-off is that clicking the text does not
    /// focus the control the way a real `<label>` would.
    ///
    /// @param grid   the grid holding the label text
    /// @param row    the label cell's row
    /// @param column the label cell's column
    /// @param target the control to name
    public static void labelledByCell(final Grid grid,
                                      final int row,
                                      final int column,
                                      final UIObject target) {
        if (grid == null || target == null) {
            return;
        }
        final Element cell = grid.getCellFormatter().getElement(row, column);
        // Reuse an id if the cell already has one — labelling two controls from
        // the same cell (a value box and its unit list, say) must not renumber it.
        String id = cell.getId();
        if (id == null || id.isEmpty()) {
            id = uniqueId("floormap-label");
            cell.setId(id);
        }
        labelledBy(target, id);
    }

    /// Hides `uiObject` from assistive technology while leaving it visible.
    ///
    /// Only correct when the content is genuinely redundant — decoration, or
    /// text already carried by an adjacent control's accessible name. Hiding
    /// anything else simply deletes it for screen-reader users.
    public static void hide(final UIObject uiObject) {
        if (uiObject != null) {
            uiObject.getElement().setAttribute(ARIA_HIDDEN, "true");
        }
    }

    /// Configures `uiObject` as a polite, atomic live region.
    ///
    /// `role="status"` carries an implicit `aria-live="polite"`; both are set
    /// because some screen readers honour only one. `aria-atomic` makes the region read
    /// as a whole sentence rather than diffed word by word.
    ///
    /// The caller still owns visibility: a live region must remain rendered, since a hidden
    /// one is dropped from the accessibility tree and never announces. The floor map's regions
    /// use the `stroom-floormap-visually-hidden` class for that.
    ///
    /// Exists so the canvas and Layers panels cannot drift apart on the attribute triple —
    /// they had it copied verbatim.
    public static void liveRegion(final UIObject uiObject) {
        if (uiObject != null) {
            final Element element = uiObject.getElement();
            element.setAttribute(ROLE, "status");
            element.setAttribute("aria-live", "polite");
            element.setAttribute("aria-atomic", "true");
        }
    }

    /// A document-unique id, for wiring up `for` / `aria-labelledby`.
    public static String uniqueId(final String prefix) {
        return prefix + "-" + DOM.createUniqueId();
    }

    /// Focuses the first natively-focusable descendant of `container`, and
    /// reports whether it found one. "First" is by tag priority rather than document order — see
    /// [#firstFocusable(Element)].
    ///
    /// Calling `focus()` on a plain `div` is a silent no-op — the
    /// element is not focusable, so the browser simply leaves focus where it was.
    /// A dialog that "focuses" a wrapper panel therefore opens with focus still
    /// back on whatever the user was last on, which is the failure this
    /// exists to avoid.
    ///
    /// @return `true` if something was focused
    public static boolean focusFirstFocusable(final Element container) {
        // A disabled or explicitly-removed control is not a focus target; focusing it would
        // be another silent no-op. firstFocusable applies that filter.
        final Element candidate = firstFocusable(container);
        if (candidate == null) {
            return false;
        }
        candidate.focus();
        return true;
    }
}
