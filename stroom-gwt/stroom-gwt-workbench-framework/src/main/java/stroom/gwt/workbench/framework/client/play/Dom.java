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

package stroom.gwt.workbench.framework.client.play;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;

/// DOM helpers for interaction tests: finding elements the way Testing Library does (by role,
/// accessible name, text, label, title, placeholder, display value or test id) and simulating
/// the user the way `userEvent` and `fireEvent` do.
///
/// Events are real DOM events dispatched on the elements, with the legacy properties GWT reads,
/// e.g. `keyCode`, `which` and `charCode` on keyboard events and `button` on mouse events, so
/// GWT widgets (which listen to the native events sunk on their elements) react as they do to a
/// real user.
final class Dom {

    private Dom() {
        // Static utility
    }

    // ---------- Queries ----------

    /// Finds elements by role, as Testing Library's `queryAllByRole` does, ignoring hidden ones.
    ///
    /// @param container The element to search within.
    /// @param role      The ARIA role, e.g. `button`.
    /// @param name      Matches the accessible name, or null for any.
    /// @return The elements within the container with the role and (if not null) accessible name.
    static native JsArray<Element> queryAllByRole(Element container, String role, TextMatch name) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return helpers.all(container).filter(function (el) {
            return helpers.role(el) === role
                && helpers.accessible(el)
                && (name === null || helpers.matches(name, helpers.name(el)));
        });
    }-*/;

    /// Finds elements by their text, as Testing Library's `queryAllByText` does. Like Testing
    /// Library, hidden elements are included; only `script` and `style` elements are ignored.
    ///
    /// @param container The element to search within.
    /// @param text      Matches the text.
    /// @param selector  Only elements matching this CSS selector are included, or null for all.
    /// @return The elements within the container whose own text matches.
    static native JsArray<Element> queryAllByText(Element container, TextMatch text, String selector) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return helpers.all(container).filter(function (el) {
            return !helpers.ignored(el)
                && (selector === null || el.matches(selector))
                && helpers.matches(text, helpers.nodeText(el));
        });
    }-*/;

    /// Finds form controls by their label, as Testing Library's `queryAllByLabelText` does. Like
    /// [#queryAllByText(Element, TextMatch, String)], hidden elements are included.
    ///
    /// @param container The element to search within.
    /// @param text      Matches the text of the label, `aria-label` or `aria-labelledby`
    ///                  element(s).
    /// @return The form controls within the container labelled with matching text.
    static native JsArray<Element> queryAllByLabelText(Element container, TextMatch text) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return helpers.all(container).filter(function (el) {
            if (helpers.ignored(el)) {
                return false;
            }
            var ariaLabel = el.getAttribute('aria-label');
            if (ariaLabel !== null && helpers.matches(text, helpers.normalise(ariaLabel))) {
                return true;
            }
            if (el.hasAttribute('aria-labelledby') && helpers.matches(text, helpers.labelledByText(el))) {
                return true;
            }
            if (el.labels && el.labels.length) {
                for (var i = 0; i < el.labels.length; i++) {
                    if (helpers.matches(text, helpers.normalise(el.labels[i].textContent))) {
                        return true;
                    }
                }
            }
            return false;
        });
    }-*/;

    /// Finds elements by their `title` attribute (or SVG `<title>` element), as Testing Library's
    /// `queryAllByTitle` does.
    ///
    /// @param container The element to search within.
    /// @param text      Matches the title.
    /// @return The matching elements.
    static native JsArray<Element> queryAllByTitle(Element container, TextMatch text) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return helpers.all(container).filter(function (el) {
            if (el.hasAttribute('title') && helpers.matches(text, helpers.normalise(el.getAttribute('title')))) {
                return true;
            }
            return el.tagName.toLowerCase() === 'title' && el.parentElement
                && el.parentElement.namespaceURI === 'http://www.w3.org/2000/svg'
                && helpers.matches(text, helpers.normalise(el.textContent));
        });
    }-*/;

    /// Finds elements by an attribute's value, e.g. for `queryAllByPlaceholderText` and
    /// `queryAllByTestId`.
    ///
    /// @param container The element to search within.
    /// @param attribute The attribute, e.g. `placeholder`.
    /// @param text      Matches the attribute's value.
    /// @return The matching elements.
    static native JsArray<Element> queryAllByAttribute(Element container, String attribute, TextMatch text) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return helpers.all(container).filter(function (el) {
            return el.hasAttribute(attribute) && helpers.matches(text, el.getAttribute(attribute));
        });
    }-*/;

    /// Finds form fields by their current value, as Testing Library's `queryAllByDisplayValue`
    /// does: an input's or text area's value, or the text of a select's selected option.
    ///
    /// @param container The element to search within.
    /// @param text      Matches the value.
    /// @return The matching fields.
    static native JsArray<Element> queryAllByDisplayValue(Element container, TextMatch text) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return Array.prototype.slice.call(container.querySelectorAll('input, select, textarea'))
            .filter(function (el) {
                if (el.tagName === 'SELECT') {
                    return Array.prototype.slice.call(el.options).some(function (option) {
                        return option.selected && helpers.matches(text, helpers.normalise(option.textContent));
                    });
                }
                var type = (el.type || '').toLowerCase();
                if (type === 'checkbox' || type === 'radio' || type === 'file') {
                    return false;
                }
                return helpers.matches(text, helpers.normalise(el.value));
            });
    }-*/;

    /// Finds elements with a CSS selector.
    ///
    /// @param container The element to search within.
    /// @param selector  The CSS selector.
    /// @return The elements within the container matching the CSS selector.
    /// @throws PlayException If the selector isn't valid.
    static JsArray<Element> querySelectorAll(final Element container, final String selector) {
        final JsArray<Element> matches = nativeQuerySelectorAll(container, selector);
        if (matches == null) {
            throw new PlayException("Invalid CSS selector: " + selector);
        }
        return matches;
    }

    /// @return The matching elements, or null if the selector isn't valid.
    private static native JsArray<Element> nativeQuerySelectorAll(Element container, String selector) /*-{
        try {
            return Array.prototype.slice.call(container.querySelectorAll(selector));
        } catch (e) {
            return null;
        }
    }-*/;

    /// @param element An element.
    /// @param selector A CSS selector.
    /// @return The closest ancestor of the element (or the element) matching the selector, or none.
    /// @throws PlayException If the selector isn't valid.
    static JsArray<Element> closest(final Element element, final String selector) {
        final JsArray<Element> matches = nativeClosest(element, selector);
        if (matches == null) {
            throw new PlayException("Invalid CSS selector: " + selector);
        }
        return matches;
    }

    private static native JsArray<Element> nativeClosest(Element element, String selector) /*-{
        try {
            var match = element.closest(selector);
            return match ? [match] : [];
        } catch (e) {
            return null;
        }
    }-*/;

    /// @param element Any element.
    /// @return The document's body.
    static native JsArray<Element> body(Element element) /*-{
        var doc = element ? element.ownerDocument : $doc;
        return [doc.body];
    }-*/;

    private static native Object helpers() /*-{
        if ($wnd.__sbmPlayHelpers) {
            return $wnd.__sbmPlayHelpers;
        }
        var implicitRoles = {
            BUTTON: 'button', A: 'link', TEXTAREA: 'textbox', SELECT: 'combobox', OPTION: 'option',
            H1: 'heading', H2: 'heading', H3: 'heading', H4: 'heading', H5: 'heading', H6: 'heading',
            UL: 'list', OL: 'list', LI: 'listitem', TABLE: 'table', TR: 'row', TD: 'cell',
            TH: 'columnheader', NAV: 'navigation', DIALOG: 'dialog', IMG: 'img', FORM: 'form',
            MAIN: 'main', ASIDE: 'complementary', HEADER: 'banner', FOOTER: 'contentinfo',
            FIELDSET: 'group', HR: 'separator', PROGRESS: 'progressbar', TBODY: 'rowgroup',
            THEAD: 'rowgroup', TFOOT: 'rowgroup', ARTICLE: 'article', SECTION: 'region'
        };
        var inputRoles = {
            button: 'button', submit: 'button', reset: 'button', image: 'button', checkbox: 'checkbox',
            radio: 'radio', range: 'slider', number: 'spinbutton', search: 'searchbox'
        };
        var normalise = function (text) {
            return (text || '').replace(/\s+/g, ' ').trim();
        };
        var helpers = {
            normalise: normalise,
            matches: function (match, text) {
                return match.@stroom.gwt.workbench.framework.client.play.TextMatch::matches(Ljava/lang/String;)(text);
            },
            all: function (container) {
                return Array.prototype.slice.call(container.querySelectorAll('*'));
            },
            role: function (el) {
                var explicit = el.getAttribute('role');
                if (explicit) {
                    return explicit.split(' ')[0];
                }
                if (el.tagName === 'INPUT') {
                    return inputRoles[(el.type || 'text').toLowerCase()] || 'textbox';
                }
                if (el.tagName === 'A' && !el.hasAttribute('href')) {
                    return null;
                }
                if (el.tagName === 'SELECT' && (el.multiple || el.size > 1)) {
                    return 'listbox';
                }
                return implicitRoles[el.tagName] || null;
            },
            name: function (el) {
                var label = el.getAttribute('aria-label');
                if (label) {
                    return normalise(label);
                }
                if (el.getAttribute('aria-labelledby')) {
                    return helpers.labelledByText(el);
                }
                if (el.labels && el.labels.length) {
                    return normalise(el.labels[0].textContent);
                }
                if (el.tagName === 'IMG') {
                    return normalise(el.getAttribute('alt'));
                }
                if (el.tagName === 'INPUT' && (el.type === 'button' || el.type === 'submit' || el.type === 'reset')) {
                    return normalise(el.value);
                }
                return normalise(el.textContent || el.getAttribute('title'));
            },
            labelledByText: function (el) {
                var text = el.getAttribute('aria-labelledby').split(/\s+/).map(function (id) {
                    var target = id ? el.ownerDocument.getElementById(id) : null;
                    return target ? target.textContent : '';
                }).join(' ');
                return normalise(text);
            },
            ignored: function (el) {
                return el.tagName === 'SCRIPT' || el.tagName === 'STYLE';
            },
            // The text Testing Library's text queries match: the element's own text nodes, or a
            // button-like input's value
            nodeText: function (el) {
                if (el.tagName === 'INPUT' && /^(submit|button|reset)$/i.test(el.type || '')) {
                    return normalise(el.value);
                }
                var text = '';
                for (var i = 0; i < el.childNodes.length; i++) {
                    if (el.childNodes[i].nodeType === 3) {
                        text += el.childNodes[i].textContent;
                    }
                }
                return normalise(text);
            },
            accessible: function (el) {
                if (helpers.ignored(el)) {
                    return false;
                }
                for (var node = el; node && node.nodeType === 1; node = node.parentElement) {
                    if (node.getAttribute('aria-hidden') === 'true') {
                        return false;
                    }
                    var style = $wnd.getComputedStyle(node);
                    if (style.display === 'none' || style.visibility === 'hidden') {
                        return false;
                    }
                }
                return true;
            },
            win: function (el) {
                return el.ownerDocument.defaultView;
            },
            // The modifier keys of an event init, from a mask of Keys.SHIFT etc.
            modifiers: function (init, mask) {
                init.shiftKey = (mask & 1) !== 0;
                init.ctrlKey = (mask & 2) !== 0;
                init.altKey = (mask & 4) !== 0;
                init.metaKey = (mask & 8) !== 0;
                return init;
            },
            mouse: function (el, type, init) {
                var win = helpers.win(el);
                var isPointer = type.indexOf('pointer') === 0;
                var Ctor = isPointer ? (win.PointerEvent || win.MouseEvent) : win.MouseEvent;
                var full = {bubbles: true, cancelable: true, composed: true, view: win};
                for (var key in init) {
                    full[key] = init[key];
                }
                if (isPointer) {
                    full.pointerId = 1;
                    full.pointerType = 'mouse';
                    full.isPrimary = true;
                }
                return el.dispatchEvent(new Ctor(type, full));
            },
            // The closest element to el (or el) that a mouse press would focus
            focusable: function (el) {
                var selector = 'a[href], area[href], button, input, select, textarea, iframe, summary, '
                    + '[tabindex], [contenteditable=""], [contenteditable="true"]';
                for (var node = el; node && node.nodeType === 1; node = node.parentElement) {
                    if (node.matches(selector) && !node.disabled) {
                        return node;
                    }
                }
                return null;
            },
            focusEvents: function (el, type, related) {
                var win = helpers.win(el);
                el.dispatchEvent(new win.FocusEvent(type, {relatedTarget: related, view: win}));
                el.dispatchEvent(new win.FocusEvent(type === 'focus' ? 'focusin' : 'focusout',
                    {bubbles: true, relatedTarget: related, view: win}));
            },
            // Focuses el, firing the focus (and blur) events by hand if the browser doesn't, as it
            // doesn't while the page isn't focused
            focus: function (el) {
                var doc = el.ownerDocument;
                var previous = doc.activeElement;
                if (previous === el) {
                    return;
                }
                var focused = false;
                var blurred = false;
                var onFocus = function () { focused = true; };
                var onBlur = function () { blurred = true; };
                el.addEventListener('focus', onFocus);
                if (previous) {
                    previous.addEventListener('blur', onBlur);
                }
                el.focus();
                el.removeEventListener('focus', onFocus);
                if (previous) {
                    previous.removeEventListener('blur', onBlur);
                }
                if (doc.activeElement !== el) {
                    return;
                }
                if (previous && previous !== doc.body && !blurred) {
                    helpers.focusEvents(previous, 'blur', el);
                }
                if (!focused) {
                    helpers.focusEvents(el, 'focus', previous !== doc.body ? previous : null);
                }
            },
            blur: function (doc) {
                var previous = doc.activeElement;
                if (!previous || previous === doc.body) {
                    return;
                }
                var blurred = false;
                var onBlur = function () { blurred = true; };
                previous.addEventListener('blur', onBlur);
                previous.blur();
                previous.removeEventListener('blur', onBlur);
                if (!blurred) {
                    helpers.focusEvents(previous, 'blur', null);
                }
            },
            // Moves the focus as a mouse press on the element would
            focusFromPointer: function (el) {
                var target = helpers.focusable(el);
                if (target) {
                    helpers.focus(target);
                } else {
                    helpers.blur(el.ownerDocument);
                }
            },
            isTextField: function (el) {
                if (el.tagName === 'TEXTAREA') {
                    return true;
                }
                var types = ['', 'text', 'search', 'email', 'url', 'tel', 'password', 'number'];
                return el.tagName === 'INPUT' && types.indexOf((el.type || '').toLowerCase()) >= 0;
            },
            isEditable: function (el) {
                return helpers.isTextField(el) ? (!el.disabled && !el.readOnly) : !!el.isContentEditable;
            },
            // Remembers a field's value before it's edited so that, as user-event does, a change
            // event is fired when it loses the focus if its value has changed
            trackEdit: function (el) {
                if (!helpers.isTextField(el) || el.__playEdit) {
                    return;
                }
                var win = helpers.win(el);
                el.__playEdit = {initial: el.value};
                var onBlur = function () {
                    el.removeEventListener('blur', onBlur);
                    var initial = el.__playEdit ? el.__playEdit.initial : el.value;
                    delete el.__playEdit;
                    if (el.value !== initial) {
                        el.dispatchEvent(new win.Event('change', {bubbles: true}));
                    }
                };
                el.addEventListener('blur', onBlur);
            },
            selection: function (el) {
                var start = null;
                var end = null;
                try {
                    start = el.selectionStart;
                    end = el.selectionEnd;
                } catch (e) {
                    // Some input types, e.g. number, have no selection
                }
                if (start === null || start === undefined) {
                    start = end = el.value.length;
                }
                return {start: start, end: end};
            },
            setSelection: function (el, start, end) {
                try {
                    el.setSelectionRange(start, end);
                } catch (e) {
                    // Some input types, e.g. number, have no selection
                }
            },
            // Replaces the field's selection with text, or deletes a character if the selection is
            // empty and text is empty, firing input as typing does
            edit: function (el, text, inputType, forward) {
                var win = helpers.win(el);
                helpers.trackEdit(el);
                if (helpers.isTextField(el)) {
                    var value = el.value;
                    var sel = helpers.selection(el);
                    var start = sel.start;
                    var end = sel.end;
                    if (start === end && text === '') {
                        if (forward) {
                            end = Math.min(value.length, end + 1);
                        } else {
                            start = Math.max(0, start - 1);
                        }
                    }
                    if (start === end && text === '') {
                        return;
                    }
                    el.value = value.substring(0, start) + text + value.substring(end);
                    helpers.setSelection(el, start + text.length, start + text.length);
                } else if (text === '') {
                    el.textContent = (el.textContent || '').slice(0, -1);
                } else {
                    el.textContent = (el.textContent || '') + text;
                }
                el.dispatchEvent(new win.InputEvent('input', {
                    bubbles: true, inputType: inputType, data: text || null
                }));
            },
            tabbable: function (doc) {
                return Array.prototype.slice.call(doc.querySelectorAll(
                    'a[href], button, input, select, textarea, [tabindex], [contenteditable="true"]'))
                    .filter(function (el) {
                        return !el.disabled && el.tabIndex >= 0 && el.getClientRects().length > 0
                            && !(el.tagName === 'INPUT' && el.type === 'hidden');
                    });
            }
        };
        $wnd.__sbmPlayHelpers = helpers;
        return helpers;
    }-*/;

    // ---------- State ----------

    /// @param element The element.
    /// @return The element's text content with whitespace collapsed, as `toHaveTextContent` uses.
    static native String getTextContent(Element element) /*-{
        return (element.textContent || '').replace(/\s+/g, ' ').trim();
    }-*/;

    /// @param element The element.
    /// @return The element's raw `textContent`.
    static native String getRawTextContent(Element element) /*-{
        return element.textContent;
    }-*/;

    /// @param element The element.
    /// @param name    The name of the attribute.
    /// @return The attribute's value, or null if the element doesn't have it.
    static native String getAttribute(Element element, String name) /*-{
        return element.getAttribute(name);
    }-*/;

    /// @param element The element.
    /// @param name    The name of the attribute.
    /// @return True if the element has the attribute.
    static native boolean hasAttribute(Element element, String name) /*-{
        return element.hasAttribute(name);
    }-*/;

    /// @param element The element.
    /// @param name    The name of a property, e.g. `scrollTop`.
    /// @return The property as a String, Double or Boolean, or null.
    static native Object getProperty(Element element, String name) /*-{
        var value = element[name];
        if (value === undefined || value === null) {
            return null;
        }
        if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'string') {
            return value;
        }
        return String(value);
    }-*/;

    /// @param element The element.
    /// @param name    The name of a CSS property, e.g. `font-weight`.
    /// @return The element's computed value for the property.
    static native String getComputedStyle(Element element, String name) /*-{
        return element.ownerDocument.defaultView.getComputedStyle(element).getPropertyValue(name);
    }-*/;

    /// @param element The element.
    /// @param side    A property of `DOMRect`, e.g. `left` or `width`.
    /// @return The property of the element's bounding client rectangle.
    static native double getRect(Element element, String side) /*-{
        return element.getBoundingClientRect()[side];
    }-*/;

    /// @param element The element.
    /// @return True if the element is in the document and neither it nor an ancestor is hidden.
    static native boolean isVisible(Element element) /*-{
        if (!element.isConnected) {
            return false;
        }
        for (var node = element; node && node.nodeType === 1; node = node.parentElement) {
            var style = $wnd.getComputedStyle(node);
            if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
                return false;
            }
        }
        return true;
    }-*/;

    /// @param element The element.
    /// @return True if the element is in the document.
    static native boolean isConnected(Element element) /*-{
        return !!element.isConnected;
    }-*/;

    /// @param element The element.
    /// @return True if the element has the keyboard focus.
    static native boolean hasFocus(Element element) /*-{
        return element.ownerDocument.activeElement === element;
    }-*/;

    /// @param element The element, usually a form field.
    /// @return The element's `value` property, or null if it doesn't have one.
    static native String getValue(Element element) /*-{
        return element.value === undefined ? null : String(element.value);
    }-*/;

    /// @param element A check box, radio button or element with `aria-checked`.
    /// @return True if the element is checked.
    static native boolean isChecked(Element element) /*-{
        if (element.checked !== undefined) {
            return !!element.checked;
        }
        return element.getAttribute('aria-checked') === 'true';
    }-*/;

    /// @param element The element.
    /// @return True if the element is disabled, natively (including by a disabled fieldset) or
    /// with `aria-disabled`.
    static native boolean isDisabled(Element element) /*-{
        if (element.getAttribute('aria-disabled') === 'true') {
            return true;
        }
        try {
            if (element.matches(':disabled')) {
                return true;
            }
        } catch (e) {
            // Not a form control
        }
        return !!element.disabled;
    }-*/;

    /// @param element The element.
    /// @return A short description of an element as Storybook shows it, e.g. `<div.stroom-menu>`.
    static native String describe(Element element) /*-{
        var text = '<' + element.tagName.toLowerCase();
        if (element.id) {
            text += '#' + element.id;
        } else if (element.classList && element.classList.length) {
            text += '.' + Array.prototype.slice.call(element.classList).join('.');
        }
        return text + '>';
    }-*/;

    // ---------- User events ----------

    /// Clicks an element as a mouse does, firing the pointer and mouse events and moving the focus
    /// (to the closest focusable ancestor, or away) on mouse down unless that's cancelled.
    ///
    /// @param element    The element.
    /// @param clickCount The number of clicks, e.g. 2 for a double click, which also fires
    ///                   `dblclick`.
    /// @param button     0 for the main button, 2 for the secondary button, which fires
    ///                   `contextmenu` rather than `click`.
    /// @param modifiers  The modifier keys held, as a mask of [Keys#SHIFT] etc.
    static native void click(Element element, int clickCount, int button, int modifiers) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var rect = element.getBoundingClientRect();
        var at = function (init) {
            init.clientX = rect.left + rect.width / 2;
            init.clientY = rect.top + rect.height / 2;
            init.screenX = init.clientX;
            init.screenY = init.clientY;
            return helpers.modifiers(init, modifiers);
        };
        var pressed = button === 2 ? 2 : 1;
        helpers.mouse(element, 'pointerover', at({button: 0, buttons: 0}));
        helpers.mouse(element, 'mouseover', at({button: 0, buttons: 0}));
        helpers.mouse(element, 'pointermove', at({button: -1, buttons: 0}));
        helpers.mouse(element, 'mousemove', at({button: 0, buttons: 0}));
        for (var i = 1; i <= clickCount; i++) {
            helpers.mouse(element, 'pointerdown', at({button: button, buttons: pressed}));
            if (helpers.mouse(element, 'mousedown', at({button: button, buttons: pressed, detail: i}))) {
                helpers.focusFromPointer(element);
            }
            helpers.mouse(element, 'pointerup', at({button: button, buttons: 0}));
            helpers.mouse(element, 'mouseup', at({button: button, buttons: 0, detail: i}));
            if (button === 2) {
                helpers.mouse(element, 'contextmenu', at({button: 2, buttons: 0, detail: 0}));
            } else {
                helpers.mouse(element, 'click', at({button: button, buttons: 0, detail: i}));
            }
        }
        if (clickCount === 2 && button === 0) {
            helpers.mouse(element, 'dblclick', at({button: 0, buttons: 0, detail: 2}));
        }
    }-*/;

    /// Moves the mouse onto (or off) an element, firing the pointer and mouse events a real mouse
    /// would.
    ///
    /// @param element The element.
    /// @param over    True to move onto the element, false to move off it.
    static native void hover(Element element, boolean over) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var types = over
            ? ['pointerover', 'pointerenter', 'mouseover', 'mouseenter', 'pointermove', 'mousemove']
            : ['pointermove', 'mousemove', 'pointerout', 'pointerleave', 'mouseout', 'mouseleave'];
        var rect = element.getBoundingClientRect();
        types.forEach(function (type) {
            var leaveOrEnter = type.indexOf('enter') > 0 || type.indexOf('leave') > 0;
            helpers.mouse(element, type, {
                bubbles: !leaveOrEnter, cancelable: !leaveOrEnter,
                clientX: over ? rect.left + rect.width / 2 : rect.right + 1,
                clientY: over ? rect.top + rect.height / 2 : rect.bottom + 1
            });
        });
    }-*/;

    /// Fires a keyboard event on the focused element (or the default target if nothing has the
    /// focus), with the legacy `keyCode`, `which` and `charCode` properties GWT reads.
    ///
    /// @param defaultTarget The element to use if nothing has the focus.
    /// @param type          `keydown`, `keypress` or `keyup`.
    /// @param key           The key value, e.g. `a` or `Enter`.
    /// @param code          The physical key, e.g. `KeyA`.
    /// @param keyCode       The legacy `keyCode` (and `which`).
    /// @param charCode      The legacy `charCode`.
    /// @param modifiers     The modifier keys held, as a mask of [Keys#SHIFT] etc.
    /// @return False if the event was cancelled.
    static native boolean keyEvent(Element defaultTarget,
                                   String type,
                                   String key,
                                   String code,
                                   int keyCode,
                                   int charCode,
                                   int modifiers) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var doc = defaultTarget.ownerDocument;
        var target = doc.activeElement || doc.body || defaultTarget;
        var win = doc.defaultView;
        var event = new win.KeyboardEvent(type, helpers.modifiers({
            key: key, code: code, bubbles: true, cancelable: true, composed: true, view: win
        }, modifiers));
        Object.defineProperty(event, 'keyCode', {get: function () { return keyCode; }});
        Object.defineProperty(event, 'which', {get: function () { return keyCode; }});
        Object.defineProperty(event, 'charCode', {get: function () { return charCode; }});
        return target.dispatchEvent(event);
    }-*/;

    /// Does what the browser does by default for a key press on the focused element.
    ///
    /// @param defaultTarget Any element in the document.
    /// @param action        The name of a [Keys.DefaultAction].
    /// @param key           The key value, e.g. `a`.
    static native void keyDefault(Element defaultTarget, String action, String key) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var doc = defaultTarget.ownerDocument;
        var target = doc.activeElement || doc.body;
        var editable = target && helpers.isEditable(target);
        if (action === 'TYPE' && editable) {
            helpers.edit(target, key, 'insertText', false);
        } else if (action === 'DELETE_BACKWARD' && editable) {
            helpers.edit(target, '', 'deleteContentBackward', false);
        } else if (action === 'DELETE_FORWARD' && editable) {
            helpers.edit(target, '', 'deleteContentForward', true);
        } else if (action === 'SELECT_ALL' && helpers.isTextField(target)) {
            helpers.setSelection(target, 0, target.value.length);
        } else if ((action === 'MOVE_TO_START' || action === 'MOVE_TO_END') && helpers.isTextField(target)) {
            var position = action === 'MOVE_TO_START' ? 0 : target.value.length;
            helpers.setSelection(target, position, position);
        } else if (action === 'FOCUS_NEXT' || action === 'FOCUS_PREVIOUS') {
            var tabbable = helpers.tabbable(doc);
            if (tabbable.length) {
                var index = tabbable.indexOf(doc.activeElement);
                var next = action === 'FOCUS_NEXT'
                    ? (index + 1) % tabbable.length
                    : (index <= 0 ? tabbable.length - 1 : index - 1);
                helpers.focus(tabbable[next]);
            }
        } else if (action === 'ENTER') {
            if (target.tagName === 'TEXTAREA' && editable) {
                helpers.edit(target, '\n', 'insertLineBreak', false);
            } else if (target.tagName === 'BUTTON' || (target.tagName === 'A' && target.hasAttribute('href'))
                || (target.tagName === 'INPUT' && /^(button|submit|reset|image)$/i.test(target.type))) {
                target.click();
            }
        }
    }-*/;

    /// Clicks the focused element if Space activates it, i.e. it's a button, check box or radio
    /// button, as a browser does when Space is released.
    ///
    /// @param defaultTarget Any element in the document.
    static native void spaceActivate(Element defaultTarget) /*-{
        var target = defaultTarget.ownerDocument.activeElement;
        if (!target || target.disabled) {
            return;
        }
        var type = target.tagName === 'INPUT' ? (target.type || '').toLowerCase() : '';
        if (target.tagName === 'BUTTON'
            || ['checkbox', 'radio', 'button', 'submit', 'reset', 'image'].indexOf(type) >= 0) {
            target.click();
        }
    }-*/;

    /// Moves the caret of a text field to the end of its text, as `userEvent.type` does before
    /// typing.
    ///
    /// @param element The field.
    static native void moveCaretToEnd(Element element) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var target = element.ownerDocument.activeElement;
        if (target && helpers.isTextField(target)) {
            helpers.setSelection(target, target.value.length, target.value.length);
        }
    }-*/;

    /// Clears a form field as `userEvent.clear` does: focuses it, selects all its text and
    /// deletes it, firing `input` (and `change` once it loses the focus).
    ///
    /// @param element The field.
    static native void clear(Element element) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        helpers.focus(element);
        if (helpers.isTextField(element)) {
            if (element.value === '') {
                return;
            }
            helpers.setSelection(element, 0, element.value.length);
            if (element.selectionStart === null || element.selectionStart === undefined) {
                // Inputs without a selection, e.g. number
                helpers.trackEdit(element);
                element.value = '';
                element.dispatchEvent(new (helpers.win(element).InputEvent)('input', {
                    bubbles: true, inputType: 'deleteContentBackward'
                }));
                return;
            }
            helpers.edit(element, '', 'deleteContentBackward', false);
        } else if (element.isContentEditable) {
            element.textContent = '';
            element.dispatchEvent(new (helpers.win(element).InputEvent)('input', {
                bubbles: true, inputType: 'deleteContentBackward'
            }));
        }
    }-*/;

    /// Selects the options of a `<select>` (or the `option`s of an ARIA listbox) whose value or
    /// text matches, firing `input` and `change` (or clicking the ARIA options).
    ///
    /// @param select The select or listbox.
    /// @param values The values or texts of the options.
    /// @return The values that matched no option.
    static native JsArrayString selectOptions(Element select, JsArrayString values) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var win = helpers.win(select);
        var missing = [];
        var list = Array.prototype.slice.call(values);
        if (select.tagName === 'SELECT') {
            helpers.focus(select);
            list.forEach(function (value) {
                var found = false;
                for (var i = 0; i < select.options.length; i++) {
                    var option = select.options[i];
                    if (option.value === value || helpers.normalise(option.textContent) === value) {
                        option.selected = true;
                        found = true;
                        if (!select.multiple) {
                            break;
                        }
                    }
                }
                if (!found) {
                    missing.push(value);
                }
            });
            select.dispatchEvent(new win.Event('input', {bubbles: true}));
            select.dispatchEvent(new win.Event('change', {bubbles: true}));
            return missing;
        }
        var options = Array.prototype.slice.call(select.querySelectorAll('[role="option"]'));
        list.forEach(function (value) {
            var option = options.filter(function (el) {
                return helpers.normalise(el.textContent) === value || el.getAttribute('data-value') === value;
            })[0];
            if (option) {
                @stroom.gwt.workbench.framework.client.play.Dom::click(*)(option, 1, 0, 0);
            } else {
                missing.push(value);
            }
        });
        return missing;
    }-*/;

    /// Chooses a file in a file input, as `userEvent.upload` does, firing `input` and `change`.
    ///
    /// @param input    The `<input type="file">` (or an element whose label it is).
    /// @param name     The file name, e.g. `data.txt`.
    /// @param content  The file's content.
    /// @param mimeType The file's type, e.g. `text/plain`.
    static native void upload(Element input, String name, String content, String mimeType) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var win = helpers.win(input);
        var target = input;
        if (target.tagName === 'LABEL' && target.control) {
            target = target.control;
        }
        if (target.tagName !== 'INPUT' || target.type !== 'file') {
            throw new Error('userEvent.upload needs an <input type="file">, not ' + target.tagName.toLowerCase());
        }
        var transfer = new win.DataTransfer();
        transfer.items.add(new win.File([content], name, {type: mimeType || ''}));
        target.files = transfer.files;
        target.dispatchEvent(new win.Event('input', {bubbles: true}));
        target.dispatchEvent(new win.Event('change', {bubbles: true}));
    }-*/;

    /// Sets a form field's value without firing any events, as `fireEvent.change(el, { target:
    /// { value } })` does before firing its event.
    ///
    /// @param element The field.
    /// @param value   The value.
    static native void setValue(Element element, String value) /*-{
        if (element.isContentEditable && element.value === undefined) {
            element.textContent = value;
        } else {
            element.value = value;
        }
    }-*/;

    /// Fires an event on an element, as `fireEvent` does.
    ///
    /// @param target     The element.
    /// @param type       The event type, e.g. `mousedown`.
    /// @param eventClass The event's constructor, e.g. `MouseEvent`; `Event` if the browser
    ///                   doesn't have it.
    /// @param bubbles    True if the event bubbles.
    /// @param cancelable True if the event can be cancelled.
    /// @param clientX    The x coordinate, for mouse and pointer events.
    /// @param clientY    The y coordinate, for mouse and pointer events.
    /// @param button     The mouse button.
    /// @param buttons    The mouse buttons held.
    /// @param detail     The click count.
    /// @param modifiers  The modifier keys held, as a mask of [Keys#SHIFT] etc.
    /// @param key        The key value, for keyboard events, may be null.
    /// @param code       The physical key, for keyboard events, may be null.
    /// @param keyCode    The legacy `keyCode` and `which`, for keyboard events.
    /// @param charCode   The legacy `charCode`, for keyboard events.
    /// @return False if the event was cancelled.
    static native boolean fireEvent(Element target,
                                    String type,
                                    String eventClass,
                                    boolean bubbles,
                                    boolean cancelable,
                                    double clientX,
                                    double clientY,
                                    int button,
                                    int buttons,
                                    int detail,
                                    int modifiers,
                                    String key,
                                    String code,
                                    int keyCode,
                                    int charCode) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var win = helpers.win(target);
        var Ctor = win[eventClass] || win.Event;
        var init = helpers.modifiers({
            bubbles: bubbles, cancelable: cancelable, composed: true, view: win,
            clientX: clientX, clientY: clientY, screenX: clientX, screenY: clientY,
            button: button, buttons: buttons, detail: detail
        }, modifiers);
        if (eventClass === 'PointerEvent') {
            init.pointerId = 1;
            init.pointerType = 'mouse';
            init.isPrimary = true;
        }
        if (key !== null) {
            init.key = key;
        }
        if (code !== null) {
            init.code = code;
        }
        if (eventClass === 'Event') {
            init = {bubbles: bubbles, cancelable: cancelable, composed: true};
        }
        var event = new Ctor(type, init);
        if (eventClass === 'KeyboardEvent') {
            Object.defineProperty(event, 'keyCode', {get: function () { return keyCode; }});
            Object.defineProperty(event, 'which', {get: function () { return keyCode; }});
            Object.defineProperty(event, 'charCode', {get: function () { return charCode; }});
        }
        return target.dispatchEvent(event);
    }-*/;
}
