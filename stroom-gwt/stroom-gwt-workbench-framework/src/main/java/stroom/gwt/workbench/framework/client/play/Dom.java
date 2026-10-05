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
import com.google.gwt.dom.client.Element;

/// DOM helpers for interaction tests: finding elements the way Testing Library does (by role,
/// accessible name, text, label or test id) and simulating the user the way `userEvent` does.
final class Dom {

    private Dom() {
        // Static utility
    }

    // ---------- Queries ----------

    /// Finds elements by role, as Testing Library's `queryAllByRole` does, ignoring hidden ones.
    ///
    /// @param container The element to search within.
    /// @param role      The ARIA role, e.g. `button`.
    /// @param name      The accessible name, or null for any.
    /// @return The elements within the container with the role and (if not null) accessible name.
    static native JsArray<Element> queryAllByRole(Element container, String role, String name) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return helpers.all(container).filter(function (el) {
            return helpers.role(el) === role
                && helpers.accessible(el)
                && (name === null || helpers.name(el) === name);
        });
    }-*/;

    /// Finds elements by their text, as Testing Library's `queryAllByText` does. Like Testing
    /// Library, hidden elements are included; only `script` and `style` elements are ignored.
    ///
    /// @param container The element to search within.
    /// @param text      The text.
    /// @return The elements within the container whose own text is the text.
    static native JsArray<Element> queryAllByText(Element container, String text) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return helpers.all(container).filter(function (el) {
            return !helpers.ignored(el) && helpers.ownText(el) === text;
        });
    }-*/;

    /// Finds form controls by their label, as Testing Library's `queryAllByLabelText` does. Like
    /// [#queryAllByText(Element, String)], hidden elements are included.
    ///
    /// @param container The element to search within.
    /// @param text      The text of the label, `aria-label` or `aria-labelledby` element(s).
    /// @return The form controls within the container labelled with the text.
    static native JsArray<Element> queryAllByLabelText(Element container, String text) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return helpers.all(container).filter(function (el) {
            if (helpers.ignored(el)) {
                return false;
            }
            if (el.hasAttribute('aria-label') && helpers.normalise(el.getAttribute('aria-label')) === text) {
                return true;
            }
            if (el.hasAttribute('aria-labelledby') && helpers.labelledByText(el) === text) {
                return true;
            }
            if (el.labels && el.labels.length) {
                for (var i = 0; i < el.labels.length; i++) {
                    if (helpers.normalise(el.labels[i].textContent) === text) {
                        return true;
                    }
                }
            }
            return false;
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

    private static native Object helpers() /*-{
        if ($wnd.__sbmPlayHelpers) {
            return $wnd.__sbmPlayHelpers;
        }
        var implicitRoles = {
            BUTTON: 'button', A: 'link', TEXTAREA: 'textbox', SELECT: 'combobox', OPTION: 'option',
            H1: 'heading', H2: 'heading', H3: 'heading', H4: 'heading', H5: 'heading', H6: 'heading',
            UL: 'list', OL: 'list', LI: 'listitem', TABLE: 'table', TR: 'row', TD: 'cell',
            TH: 'columnheader', NAV: 'navigation', DIALOG: 'dialog', IMG: 'img', FORM: 'form',
            MAIN: 'main', ASIDE: 'complementary', HEADER: 'banner', FOOTER: 'contentinfo'
        };
        var inputRoles = {
            button: 'button', submit: 'button', reset: 'button', checkbox: 'checkbox',
            radio: 'radio', range: 'slider', number: 'spinbutton', search: 'searchbox'
        };
        var normalise = function (text) {
            return (text || '').replace(/\s+/g, ' ').trim();
        };
        var helpers = {
            normalise: normalise,
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
                if (el.tagName === 'INPUT' && (el.type === 'button' || el.type === 'submit')) {
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
            ownText: function (el) {
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
    /// @return True if the element is disabled, natively or with `aria-disabled`.
    static native boolean isDisabled(Element element) /*-{
        return !!element.disabled || element.getAttribute('aria-disabled') === 'true';
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

    /// Clicks an element, firing the pointer and mouse events a real click would.
    ///
    /// @param element     The element.
    /// @param doubleClick True to click twice and fire `dblclick`.
    static native void click(Element element, boolean doubleClick) /*-{
        var rect = element.getBoundingClientRect();
        var x = rect.left + rect.width / 2;
        var y = rect.top + rect.height / 2;
        var win = element.ownerDocument.defaultView;
        var mouse = function (type, detail) {
            element.dispatchEvent(new win.MouseEvent(type, {
                bubbles: true, cancelable: true, composed: true, view: win,
                clientX: x, clientY: y, button: 0, buttons: type === 'mouseup' || type === 'click' ? 0 : 1,
                detail: detail
            }));
        };
        var pointer = function (type) {
            if (win.PointerEvent) {
                element.dispatchEvent(new win.PointerEvent(type, {
                    bubbles: true, cancelable: true, composed: true, view: win,
                    clientX: x, clientY: y, button: 0, pointerType: 'mouse', isPrimary: true
                }));
            }
        };
        var once = function (detail) {
            pointer('pointerdown');
            mouse('mousedown', detail);
            if (element.focus) {
                element.focus();
            }
            pointer('pointerup');
            mouse('mouseup', detail);
            mouse('click', detail);
        };
        pointer('pointerover');
        mouse('mouseover', 0);
        pointer('pointermove');
        mouse('mousemove', 0);
        once(1);
        if (doubleClick) {
            once(2);
            mouse('dblclick', 2);
        }
    }-*/;

    /// Moves the mouse over an element, firing the pointer and mouse events a real mouse would.
    ///
    /// @param element The element.
    static native void hover(Element element) /*-{
        var win = element.ownerDocument.defaultView;
        ['pointerover', 'pointerenter', 'mouseover', 'mouseenter', 'pointermove', 'mousemove']
            .forEach(function (type) {
                var Ctor = type.indexOf('pointer') === 0 && win.PointerEvent ? win.PointerEvent : win.MouseEvent;
                element.dispatchEvent(new Ctor(type, {
                    bubbles: type.indexOf('enter') < 0, cancelable: true, view: win
                }));
            });
    }-*/;

    /// Presses a key on the focused element, e.g. `Enter` or `a`. Characters are only typed into
    /// text fields (text-like inputs, text areas and editable content). Enter clicks a focused
    /// button or link and Space clicks a focused button, check box or radio button.
    ///
    /// @param defaultTarget The element to press the key on if nothing has the focus.
    /// @param key           The key, e.g. `Enter`, `a` or ` ` for Space.
    static native void pressKey(Element defaultTarget, String key) /*-{
        var doc = defaultTarget.ownerDocument;
        var target = doc.activeElement || defaultTarget;
        var win = doc.defaultView;
        var codes = {
            Enter: 13, Escape: 27, Tab: 9, ' ': 32, ArrowUp: 38, ArrowDown: 40, ArrowLeft: 37,
            ArrowRight: 39, Home: 36, End: 35, Backspace: 8, Delete: 46, PageUp: 33, PageDown: 34
        };
        var keyCode = codes[key] || (key.length === 1 ? key.toUpperCase().charCodeAt(0) : 0);
        var event = function (type) {
            var e = new win.KeyboardEvent(type, {
                key: key, bubbles: true, cancelable: true, composed: true, view: win
            });
            // Old GWT widgets read keyCode/which, which the constructor doesn't set
            Object.defineProperty(e, 'keyCode', {get: function () { return keyCode; }});
            Object.defineProperty(e, 'which', {get: function () { return keyCode; }});
            Object.defineProperty(e, 'charCode', {
                get: function () { return type === 'keypress' ? keyCode : 0; }
            });
            return target.dispatchEvent(e);
        };
        var textInputTypes = ['', 'text', 'search', 'email', 'url', 'tel', 'password', 'number'];
        var isField = target.tagName === 'TEXTAREA'
            || (target.tagName === 'INPUT'
                && textInputTypes.indexOf((target.type || '').toLowerCase()) >= 0);
        var editable = !target.disabled && !target.readOnly;
        var isTextEntry = (isField && editable) || (!isField && !!target.isContentEditable);
        var inputType = target.tagName === 'INPUT' ? (target.type || '').toLowerCase() : '';
        var isSpaceClickable = target.tagName === 'BUTTON'
            || ['checkbox', 'radio', 'button', 'submit', 'reset'].indexOf(inputType) >= 0;
        var setText = function (update) {
            if (isField) {
                target.value = update(target.value);
            } else {
                target.textContent = update(target.textContent || '');
            }
            target.dispatchEvent(new win.Event('input', {bubbles: true}));
        };
        var notCancelled = event('keydown');
        if (notCancelled && (key.length === 1 || key === 'Enter')) {
            event('keypress');
        }
        if (notCancelled && isTextEntry && key.length === 1) {
            setText(function (text) { return text + key; });
        }
        if (notCancelled && isTextEntry && key === 'Backspace') {
            setText(function (text) { return text.slice(0, -1); });
        }
        if (notCancelled && key === 'Enter' && (target.tagName === 'BUTTON' || target.tagName === 'A')) {
            target.click();
        }
        event('keyup');
        if (notCancelled && key === ' ' && isSpaceClickable && !target.disabled) {
            target.click();
        }
    }-*/;

    /// Clears a form field and fires `input`, as `userEvent.clear` does.
    ///
    /// @param element The field.
    static native void clear(Element element) /*-{
        var win = element.ownerDocument.defaultView;
        element.focus();
        element.value = '';
        element.dispatchEvent(new win.Event('input', {bubbles: true}));
    }-*/;

    /// Selects the options of a `<select>` whose value or text matches, firing `input` and
    /// `change`.
    ///
    /// @param select The select.
    /// @param value  The value or text of the option.
    static native void selectOption(Element select, String value) /*-{
        var win = select.ownerDocument.defaultView;
        for (var i = 0; i < select.options.length; i++) {
            var option = select.options[i];
            if (option.value === value || option.textContent.trim() === value) {
                option.selected = true;
            }
        }
        select.dispatchEvent(new win.Event('input', {bubbles: true}));
        select.dispatchEvent(new win.Event('change', {bubbles: true}));
    }-*/;

    /// Moves the focus to the next focusable element, as the Tab key does.
    ///
    /// @param container Any element in the document.
    static native void tab(Element container) /*-{
        var doc = container.ownerDocument;
        var focusable = Array.prototype.slice.call(doc.querySelectorAll(
            'a[href], button, input, select, textarea, [tabindex]'))
            .filter(function (el) {
                return !el.disabled && el.tabIndex >= 0 && el.getClientRects().length > 0;
            });
        if (!focusable.length) {
            return;
        }
        var index = focusable.indexOf(doc.activeElement);
        var next = (index + 1) % focusable.length;
        focusable[next].focus();
    }-*/;
}
