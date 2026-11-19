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

/// The JavaScript behind [Dom]'s user events, ported from user-event 14 (the version React
/// Storybook's `storybook/test` bundles): its event types and defaults, pointer moves, focus
/// moves, selection per mouse press, Tab destinations, radio groups, caret movement and click
/// behaviour. Each function names the user-event function it ports; see
/// `stroom-gwt-workbench/test-runner/selftest`, which checks them against user-event itself.
final class UserEventHelpers {

    private UserEventHelpers() {
        // Static utility
    }

    /// Adds the user event functions to [Dom]'s JavaScript helpers.
    ///
    /// @param helpers The helpers object, which the functions call each other through.
    static native void addTo(Object helpers) /*-{
        var arrayFrom = function (list) {
            return Array.prototype.slice.call(list);
        };
        // As user-event's symbols for the value and selection it tracks on a field
        var UI_VALUE = Symbol('Displayed value in UI');
        var UI_SELECTION = Symbol('Displayed selection in UI');
        var INITIAL_VALUE = Symbol('Initial value to compare on blur');
        var PREPARED = Symbol('Node prepared with document state workarounds');
        // As user-event's FOCUSABLE_SELECTOR
        var FOCUSABLE_SELECTOR = 'input:not([type=hidden]):not([disabled]), button:not([disabled]), '
            + 'select:not([disabled]), textarea:not([disabled]), [contenteditable=""], '
            + '[contenteditable="true"], a[href], [tabindex]:not([disabled])';
        // As user-event's clickableInputTypes
        var CLICKABLE_INPUT_TYPES = ['button', 'color', 'file', 'image', 'reset', 'submit', 'checkbox', 'radio'];
        // The events user-event fires: their constructor, and whether they bubble and can be
        // cancelled (from its eventMap)
        var USER_EVENTS = {
            auxclick: ['PointerEvent', true, true],
            click: ['PointerEvent', true, true],
            contextmenu: ['PointerEvent', true, true],
            dblclick: ['MouseEvent', true, true],
            mousedown: ['MouseEvent', true, true],
            mouseenter: ['MouseEvent', false, false],
            mouseleave: ['MouseEvent', false, false],
            mousemove: ['MouseEvent', true, true],
            mouseout: ['MouseEvent', true, true],
            mouseover: ['MouseEvent', true, true],
            mouseup: ['MouseEvent', true, true],
            pointerdown: ['PointerEvent', true, true],
            pointerenter: ['PointerEvent', false, false],
            pointerleave: ['PointerEvent', false, false],
            pointermove: ['PointerEvent', true, true],
            pointerout: ['PointerEvent', true, true],
            pointerover: ['PointerEvent', true, true],
            pointerup: ['PointerEvent', true, true],
            submit: ['Event', true, true]
        };
        var functions = {
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
            // Fires one of the events user-event fires, with its event type and defaults (its
            // eventMap), returning false if it was cancelled
            uiEvent: function (el, type, init) {
                var event = helpers.createEvent(el, type, init);
                return el.dispatchEvent(event);
            },
            createEvent: function (el, type, init) {
                var spec = USER_EVENTS[type] || ['Event', true, true];
                var win = helpers.win(el);
                var Ctor = win[spec[0]] || win.MouseEvent;
                var full = {bubbles: spec[1], cancelable: spec[2], composed: true, view: win};
                for (var key in init) {
                    full[key] = init[key];
                }
                if (spec[0] === 'PointerEvent') {
                    full.pointerId = 1;
                    full.pointerType = 'mouse';
                    full.isPrimary = true;
                }
                return new Ctor(type, full);
            },
            // Fires a click as user-event's dispatchUIEvent does, with its click behaviour: a
            // click on a label (other than on its control) is replaced by focusing the control
            // and clicking it, and a click on a file input blurs, fires 'fileDialog' and focuses
            // it, instead of the browser's default
            dispatchClick: function (el, event) {
                var behaviour = helpers.clickBehaviour(el, event);
                if (!behaviour) {
                    return el.dispatchEvent(event);
                }
                // As user-event: cancel the browser's default, but let listeners see whether they
                // cancelled the event
                event.preventDefault();
                var prevented = false;
                Object.defineProperty(event, 'defaultPrevented', {get: function () { return prevented; }});
                Object.defineProperty(event, 'preventDefault', {
                    value: function () { prevented = event.cancelable; }
                });
                el.dispatchEvent(event);
                if (!prevented) {
                    behaviour();
                }
                return !prevented;
            },
            clickBehaviour: function (el, event) {
                var context = el.closest('button,input,label,select,textarea');
                var control = context && context.localName === 'label' && context.control;
                if (control && control !== el) {
                    return function () {
                        if (helpers.isFocusable(control)) {
                            helpers.focusElement(control);
                            helpers.dispatchClick(control, new event.constructor(event.type, event));
                        }
                    };
                }
                if (el.localName === 'input' && el.type === 'file') {
                    return function () {
                        // blur fires when the file selector pops up
                        helpers.blurElement(el);
                        el.dispatchEvent(new (helpers.win(el)).Event('fileDialog'));
                        // focus fires after the file selector has been closed
                        helpers.focusElement(el);
                    };
                }
                return null;
            },
            // As user-event's getTreeDiff: the elements left (from a up) and entered (from b up)
            // when moving from a to b, and their common ancestors
            treeDiff: function (a, b) {
                var treeA = [];
                var treeB = [];
                var el;
                for (el = a; el; el = el.parentElement) {
                    treeA.push(el);
                }
                for (el = b; el; el = el.parentElement) {
                    treeB.push(el);
                }
                var i = 0;
                while (i < treeA.length && i < treeB.length
                    && treeA[treeA.length - 1 - i] === treeB[treeB.length - 1 - i]) {
                    i++;
                }
                return [treeA.slice(0, treeA.length - i), treeB.slice(0, treeB.length - i),
                    treeB.slice(treeB.length - i)];
            },
            hasPointerEvents: function (el) {
                var win = helpers.win(el);
                for (var node = el; node && node.ownerDocument; node = node.parentElement) {
                    var value = win.getComputedStyle(node).pointerEvents;
                    if (value && value !== 'inherit' && value !== 'unset') {
                        return value !== 'none';
                    }
                }
                return true;
            },
            // The middle of an element, where the pointer is put on it
            centre: function (el, mask) {
                var rect = el.getBoundingClientRect();
                var x = rect.left + rect.width / 2;
                var y = rect.top + rect.height / 2;
                return helpers.modifiers({clientX: x, clientY: y, screenX: x, screenY: y}, mask);
            },
            withInit: function (base, extra) {
                var init = {};
                var key;
                for (key in base) {
                    init[key] = base[key];
                }
                for (key in extra) {
                    init[key] = extra[key];
                }
                return init;
            },
            // The first half of moving the pointer from one element to another as user-event's
            // pointer move does (the pointer and mouse events interleaved): out of and leaving
            // the previous element's tree
            pointerLeave: function (prev, next, at) {
                var leave = helpers.treeDiff(prev, next)[0];
                var pointerInit = helpers.withInit(at, {button: -1, buttons: 0});
                var mouseInit = helpers.withInit(at, {button: 0, buttons: 0});
                if (prev !== next && helpers.hasPointerEvents(prev)) {
                    helpers.uiEvent(prev, 'pointerout', pointerInit);
                    leave.forEach(function (el) {
                        helpers.uiEvent(el, 'pointerleave', pointerInit);
                    });
                }
                if (prev !== next) {
                    helpers.uiEvent(prev, 'mouseout', mouseInit);
                    leave.forEach(function (el) {
                        helpers.uiEvent(el, 'mouseleave', mouseInit);
                    });
                }
            },
            // The second half: over and entering the next element's tree, then moving on it
            pointerEnter: function (prev, next, at) {
                var enter = helpers.treeDiff(prev, next)[1];
                var pointerInit = helpers.withInit(at, {button: -1, buttons: 0});
                var mouseInit = helpers.withInit(at, {button: 0, buttons: 0});
                if (prev !== next) {
                    helpers.uiEvent(next, 'pointerover', pointerInit);
                    enter.forEach(function (el) {
                        helpers.uiEvent(el, 'pointerenter', pointerInit);
                    });
                    helpers.uiEvent(next, 'mouseover', mouseInit);
                    enter.forEach(function (el) {
                        helpers.uiEvent(el, 'mouseenter', mouseInit);
                    });
                }
                helpers.uiEvent(next, 'pointermove', pointerInit);
                helpers.uiEvent(next, 'mousemove', mouseInit);
            },
            // As user-event's isFocusable
            isFocusable: function (el) {
                return el.nodeType === 1 && el.matches(FOCUSABLE_SELECTOR);
            },
            // As user-event's findClosest(el, isFocusable), which stops below the body
            focusable: function (el) {
                var node = el;
                do {
                    if (helpers.isFocusable(node)) {
                        return node;
                    }
                    node = node.parentElement;
                } while (node && node !== el.ownerDocument.body);
                return null;
            },
            // As user-event's isDisabled
            isDisabled: function (el) {
                return @stroom.gwt.workbench.framework.client.play.Dom::isUserEventDisabled(*)(el);
            },
            // As user-event's getActiveElementOrBody: the focused element, or the body if it is
            // disabled
            active: function (doc) {
                var el = doc.activeElement;
                if (!el || helpers.isDisabled(el)) {
                    return doc.body;
                }
                return el;
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
            // As user-event's focusElement: focuses the closest focusable element, or blurs the
            // focused element if there is none, then updates the document's selection
            focusElement: function (el) {
                var doc = el.ownerDocument;
                var target = helpers.focusable(el);
                var activeElement = helpers.active(doc);
                if ((target || doc.body) === activeElement) {
                    return;
                }
                if (target) {
                    helpers.focus(target);
                } else {
                    helpers.blur(doc);
                }
                helpers.updateSelectionOnFocus(target || doc.body);
            },
            // As user-event's blurElement
            blurElement: function (el) {
                if (!helpers.isFocusable(el) || helpers.active(el.ownerDocument) !== el) {
                    return;
                }
                helpers.blur(el.ownerDocument);
            },
            // As user-event's updateSelectionOnFocus
            updateSelectionOnFocus: function (el) {
                var selection = el.ownerDocument.getSelection();
                if (!selection || !selection.focusNode || !helpers.hasOwnSelection(el)) {
                    return;
                }
                var editable = helpers.contentEditableOf(selection.focusNode);
                if (editable) {
                    if (!selection.isCollapsed) {
                        var node = editable.firstChild && editable.firstChild.nodeType === 3
                            ? editable.firstChild : editable;
                        selection.setBaseAndExtent(node, 0, node, 0);
                    }
                } else {
                    selection.setBaseAndExtent(el, 0, el, 0);
                }
            },
            isTextField: function (el) {
                return el.nodeType === 1
                    && @stroom.gwt.workbench.framework.client.play.Dom::isTextField(*)(el);
            },
            // As user-event's hasOwnSelection: a text area or an input of an editable type
            hasOwnSelection: function (el) {
                return helpers.isTextField(el);
            },
            // As user-event's isClickableInput
            isClickableInput: function (el) {
                return el.nodeType === 1 && (el.localName === 'button' || (el.localName === 'input'
                    && CLICKABLE_INPUT_TYPES.indexOf(el.type) >= 0));
            },
            // As user-event's isContentEditable, which only checks the element's own attribute
            isContentEditable: function (el) {
                if (el.nodeType !== 1 || !el.hasAttribute('contenteditable')) {
                    return false;
                }
                var value = el.getAttribute('contenteditable');
                return value === 'true' || value === '';
            },
            // As user-event's getContentEditable: the content editable element a node is in
            contentEditableOf: function (node) {
                var el = node.nodeType === 1 ? node : node.parentElement;
                return el ? (el.closest('[contenteditable=""]') || el.closest('[contenteditable="true"]')) : null;
            },
            // As user-event's isEditable
            isEditable: function (el) {
                return (helpers.isTextField(el) && !el.readOnly) || helpers.isContentEditable(el);
            },
            // As user-event's getUIValue: the value being typed, which can differ from the value
            // property, e.g. of a number field while it is incomplete (see TextEdits)
            uiValue: function (el) {
                return el[UI_VALUE] === undefined ? el.value : String(el[UI_VALUE]);
            },
            // As user-event's setUIValue then setSelection: sets the value being typed (the
            // field's value property being set as the interceptor sanitises it) and the caret
            setUiValue: function (el, value, offset) {
                helpers.prepareElement(el);
                if (el[INITIAL_VALUE] === undefined) {
                    el[INITIAL_VALUE] = el.value;
                }
                el[UI_VALUE] = value;
                var marked = new String(value);
                marked[UI_VALUE] = true;
                el.value = marked;
                helpers.setSelection(el, offset, offset);
            },
            // As user-event's clearInitialValue: no change event when the field loses the focus
            clearInitialValue: function (el) {
                el[INITIAL_VALUE] = undefined;
            },
            // As user-event's getUISelection: the selection it set, or else the field's own
            selection: function (el) {
                var own = el[UI_SELECTION];
                var anchor;
                var focus;
                if (own) {
                    anchor = own.anchorOffset;
                    focus = own.focusOffset;
                } else {
                    anchor = helpers.nativeSelection(el, 'selectionStart');
                    focus = helpers.nativeSelection(el, 'selectionEnd');
                }
                return {start: Math.min(anchor, focus), end: Math.max(anchor, focus)};
            },
            nativeSelection: function (el, property) {
                try {
                    var value = el[property];
                    return value === null || value === undefined ? 0 : value;
                } catch (e) {
                    return 0;
                }
            },
            // As user-event's setUISelection: remembers the selection (as the browser may move the
            // field's own, e.g. when the document's selection is moved into it) and sets the
            // field's own
            setSelection: function (el, anchorOffset, focusOffset) {
                var length = helpers.uiValue(el).length;
                var sanitise = function (offset) {
                    return Math.max(0, Math.min(length, offset));
                };
                var anchor = sanitise(anchorOffset);
                var focus = sanitise(focusOffset);
                var start = Math.min(anchor, focus);
                var end = Math.max(anchor, focus);
                el[UI_SELECTION] = {anchorOffset: anchor, focusOffset: focus};
                if (helpers.nativeSelection(el, 'selectionStart') === start
                    && helpers.nativeSelection(el, 'selectionEnd') === end) {
                    return;
                }
                var marked = new Number(start);
                marked[UI_SELECTION] = true;
                try {
                    el.setSelectionRange(marked, end);
                } catch (e) {
                    // Some input types, e.g. number, have no selection
                }
            },
            // As user-event's prepareDocument, which each user event call does: fields get their
            // interceptors when they get the focus, and a field whose value was typed fires change
            // when it loses the focus
            prepareDocument: function (doc) {
                if (doc[PREPARED]) {
                    return;
                }
                doc.addEventListener('focus', function (event) {
                    helpers.prepareElement(event.target);
                }, {capture: true, passive: true});
                if (doc.activeElement) {
                    helpers.prepareElement(doc.activeElement);
                }
                doc.addEventListener('blur', function (event) {
                    var el = event.target;
                    var initial = el[INITIAL_VALUE];
                    if (initial !== undefined) {
                        if (el.value !== initial) {
                            el.dispatchEvent(new (helpers.win(el)).Event('change', {bubbles: true, cancelable: false}));
                        }
                        el[INITIAL_VALUE] = undefined;
                    }
                }, {capture: true, passive: true});
                doc[PREPARED] = true;
            },
            // As user-event's prepareElement: intercepts the program setting a field's value or
            // selection, so the value being typed and the selection follow it
            prepareElement: function (el) {
                if (!el || el.nodeType !== 1 || el[PREPARED]
                    || (el.localName !== 'input' && el.localName !== 'textarea')) {
                    return;
                }
                var intercept = function (name, implementation) {
                    var proto = Object.getOwnPropertyDescriptor(el.constructor.prototype, name);
                    var own = Object.getOwnPropertyDescriptor(el, name);
                    var target = proto && proto.set ? 'set' : 'value';
                    var descriptor = {};
                    var key;
                    for (key in (own || proto)) {
                        descriptor[key] = (own || proto)[key];
                    }
                    descriptor[target] = function () {
                        var result = implementation.apply(this, arguments);
                        var real = (!result.applyNative && own || proto)[target];
                        if (target === 'set') {
                            real.call(this, result.realArgs);
                        } else {
                            real.apply(this, result.realArgs);
                        }
                        if (result.then) {
                            result.then();
                        }
                    };
                    Object.defineProperty(el, name, descriptor);
                };
                intercept('value', function (value) {
                    var isUi = typeof value === 'object' && value !== null && value[UI_VALUE] === true;
                    var real = String(value);
                    // As user-event's sanitizeValue (a workaround for jsdom it also applies in
                    // browsers): a number field is given the number, e.g. 1 for "1."
                    if (el.localName === 'input' && el.type === 'number' && real !== '' && !isNaN(Number(value))) {
                        real = String(Number(value));
                    }
                    return {
                        applyNative: isUi, realArgs: real,
                        then: isUi ? null : function () {
                            el[UI_VALUE] = undefined;
                            helpers.setSelection(el, String(value).length, String(value).length);
                        }
                    };
                });
                intercept('setSelectionRange', function (start) {
                    var isUi = typeof start === 'object' && start !== null && start[UI_SELECTION] === true;
                    var args = Array.prototype.slice.call(arguments);
                    args[0] = Number(start);
                    return {
                        applyNative: isUi, realArgs: args,
                        then: function () {
                            if (!isUi) {
                                el[UI_SELECTION] = undefined;
                            }
                        }
                    };
                });
                ['selectionStart', 'selectionEnd'].forEach(function (name) {
                    intercept(name, function (value) {
                        return {
                            realArgs: value, then: function () {
                                el[UI_SELECTION] = undefined;
                            }
                        };
                    });
                });
                intercept('select', function () {
                    return {
                        realArgs: [], then: function () {
                            el[UI_SELECTION] = {anchorOffset: 0, focusOffset: helpers.uiValue(el).length};
                        }
                    };
                });
                intercept('setRangeText', function () {
                    return {
                        realArgs: Array.prototype.slice.call(arguments), then: function () {
                            el[UI_VALUE] = undefined;
                            el[UI_SELECTION] = undefined;
                        }
                    };
                });
                el[PREPARED] = true;
            },
            // Sets the document's selection, as user-event's setSelection does for elements
            // without their own selection
            setDocumentSelection: function (anchorNode, anchorOffset, focusNode, focusOffset) {
                var selection = anchorNode.ownerDocument.getSelection();
                if (selection) {
                    selection.setBaseAndExtent(anchorNode, anchorOffset, focusNode, focusOffset);
                }
            },
            // As user-event's getTextRange for a press without a caret position: one click puts
            // the caret at the end, two select the last word (or run of spaces or punctuation
            // character), three select the last line
            textRange: function (text, clickCount) {
                if (clickCount % 3 === 1 || text.length === 0) {
                    return [undefined, undefined];
                }
                if (clickCount % 3 === 2) {
                    return [text.length - text.match(/(\w+|\s+|\W)?$/)[0].length, undefined];
                }
                return [text.length - text.match(/[^\r\n]*$/)[0].length, undefined];
            },
            // As user-event's findNodeAtTextOffset
            nodeAtTextOffset: function (node, offset, isRoot) {
                var i = offset === undefined ? node.childNodes.length - 1 : 0;
                var step = offset === undefined ? -1 : 1;
                while (offset === undefined ? i >= (isRoot ? Math.max(node.childNodes.length - 1, 0) : 0)
                    : i <= node.childNodes.length) {
                    if (offset && i === node.childNodes.length) {
                        throw new Error('The given offset is out of bounds.');
                    }
                    var child = node.childNodes.item(i);
                    var text = String(child.textContent);
                    if (text.length) {
                        if (offset !== undefined && text.length < offset) {
                            offset -= text.length;
                        } else if (child.nodeType === 1) {
                            return helpers.nodeAtTextOffset(child, offset, false);
                        } else if (child.nodeType === 3) {
                            return {node: child, offset: offset !== undefined ? offset : child.nodeValue.length};
                        }
                    }
                    i += step;
                }
                return {node: node, offset: node.childNodes.length};
            },
            // As user-event's setSelectionPerMouseDown, for a press without a caret position
            selectPerMouseDown: function (target, clickCount) {
                if (helpers.isClickableInput(target)) {
                    return;
                }
                var own = helpers.hasOwnSelection(target);
                var text = String(own ? helpers.uiValue(target) : target.textContent);
                var range = helpers.textRange(text, clickCount);
                if (own) {
                    helpers.setSelection(target, range[0] !== undefined ? range[0] : text.length,
                        range[1] !== undefined ? range[1] : text.length);
                    return;
                }
                var start = helpers.nodeAtTextOffset(target, range[0], true);
                var end = helpers.nodeAtTextOffset(target, range[1], true);
                var domRange = target.ownerDocument.createRange();
                try {
                    domRange.setStart(start.node, start.offset);
                    domRange.setEnd(end.node, end.offset);
                } catch (e) {
                    throw new Error('The given offset is out of bounds.');
                }
                var selection = target.ownerDocument.getSelection();
                if (selection) {
                    selection.removeAllRanges();
                    selection.addRange(domRange.cloneRange());
                }
            },
            // As user-event's selectAll (Control+A)
            selectAll: function (target) {
                if (helpers.hasOwnSelection(target)) {
                    helpers.setSelection(target, 0, helpers.uiValue(target).length);
                    return;
                }
                var node = helpers.contentEditableOf(target) || target.ownerDocument.body;
                helpers.setDocumentSelection(node, 0, node, node.childNodes.length);
            },
            // As user-event's isAllSelected
            isAllSelected: function (target) {
                if (helpers.hasOwnSelection(target)) {
                    var own = helpers.selection(target);
                    return own.start === 0 && own.end === helpers.uiValue(target).length;
                }
                var node = helpers.contentEditableOf(target) || target.ownerDocument.body;
                var selection = target.ownerDocument.getSelection();
                return !!selection && selection.anchorNode === node && selection.focusNode === node
                    && selection.anchorOffset === 0 && selection.focusOffset === node.childNodes.length;
            },
            // As user-event's setSelectionRange (Home, End etc.), which only handles fields and
            // content editable elements with a single text node (it throws for others, which this
            // ignores)
            setSelectionRange: function (target, anchorOffset, focusOffset) {
                if (helpers.hasOwnSelection(target)) {
                    helpers.setSelection(target, anchorOffset, focusOffset);
                } else if (helpers.isContentEditable(target) && target.firstChild
                    && target.firstChild.nodeType === 3) {
                    helpers.setDocumentSelection(target.firstChild, anchorOffset, target.firstChild, focusOffset);
                }
            },
            // As user-event's moveSelection (ArrowLeft and ArrowRight)
            moveSelection: function (target, direction) {
                if (helpers.hasOwnSelection(target)) {
                    var own = helpers.selection(target);
                    var caret = own.start === own.end ? own.start + direction
                        : (direction < 0 ? own.start : own.end);
                    helpers.setSelection(target, caret, caret);
                    return;
                }
                var selection = target.ownerDocument.getSelection();
                if (!selection || !selection.focusNode) {
                    return;
                }
                if (selection.isCollapsed) {
                    var next = helpers.nextCursorPosition(selection.focusNode, selection.focusOffset, direction);
                    if (next) {
                        helpers.setDocumentSelection(next.node, next.offset, next.node, next.offset);
                    }
                } else if (direction < 0) {
                    selection.collapseToStart();
                } else {
                    selection.collapseToEnd();
                }
            },
            // As user-event's getNextCursorPosition
            nextCursorPosition: function (node, offset, direction, inputType) {
                if (node.nodeType === 3 && offset + direction >= 0 && offset + direction <= node.nodeValue.length) {
                    return {node: node, offset: offset + direction};
                }
                var nextNode = helpers.nextCharacterContentNode(node, offset, direction);
                if (!nextNode) {
                    return undefined;
                }
                if (nextNode.nodeType === 3) {
                    return {
                        node: nextNode,
                        offset: direction > 0 ? Math.min(1, nextNode.nodeValue.length)
                            : Math.max(nextNode.nodeValue.length - 1, 0)
                    };
                }
                if (nextNode.localName === 'br') {
                    var nextPlusOne = helpers.nextCharacterContentNode(nextNode, undefined, direction);
                    if (!nextPlusOne) {
                        if (direction < 0 && inputType === 'deleteContentBackward') {
                            return {node: nextNode.parentNode, offset: helpers.childOffset(nextNode)};
                        }
                        return undefined;
                    }
                    if (nextPlusOne.nodeType === 3) {
                        return {node: nextPlusOne, offset: direction > 0 ? 0 : nextPlusOne.nodeValue.length};
                    }
                    if (direction < 0 && nextPlusOne.localName === 'br') {
                        return {node: nextNode.parentNode, offset: helpers.childOffset(nextNode)};
                    }
                    return {
                        node: nextPlusOne.parentNode,
                        offset: helpers.childOffset(nextPlusOne) + (direction > 0 ? 0 : 1)
                    };
                }
                return {node: nextNode.parentNode, offset: helpers.childOffset(nextNode) + (direction > 0 ? 1 : 0)};
            },
            nextCharacterContentNode: function (node, offset, direction) {
                var nextOffset = Number(offset) + (direction < 0 ? -1 : 0);
                if (offset !== undefined && node.nodeType === 1 && nextOffset >= 0
                    && nextOffset < node.children.length) {
                    node = node.children[nextOffset];
                }
                var siblingName = direction === 1 ? 'nextSibling' : 'previousSibling';
                var childName = direction === 1 ? 'firstChild' : 'lastChild';
                for (;;) {
                    var sibling = node[siblingName];
                    if (sibling) {
                        node = sibling;
                        while (node.hasChildNodes()) {
                            node = node[childName];
                        }
                        if (helpers.isCharacterContent(node)) {
                            return node;
                        }
                    } else if (node.parentNode && (node.parentNode.nodeType !== 1
                        || (!helpers.isContentEditable(node.parentNode)
                            && node.parentNode !== node.ownerDocument.body))) {
                        node = node.parentNode;
                    } else {
                        return undefined;
                    }
                }
            },
            isCharacterContent: function (node) {
                if (node.nodeType === 3) {
                    return true;
                }
                if (node.nodeType === 1) {
                    if (node.localName === 'input' || node.localName === 'textarea') {
                        return node.type !== 'hidden';
                    }
                    return node.localName === 'br';
                }
                return false;
            },
            childOffset: function (node) {
                var i = 0;
                while (node.previousSibling) {
                    i++;
                    node = node.previousSibling;
                }
                return i;
            },
            // As user-event's walkRadio (arrow keys in a radio group)
            walkRadio: function (el, direction, mask) {
                var win = helpers.win(el);
                var group = arrayFrom(el.ownerDocument.querySelectorAll(el.name
                    ? 'input[type="radio"][name="' + win.CSS.escape(el.name) + '"]'
                    : 'input[type="radio"][name=""], input[type="radio"]:not([name])'));
                for (var i = group.indexOf(el) + direction; ; i += direction) {
                    if (!group[i]) {
                        i = direction > 0 ? 0 : group.length - 1;
                    }
                    if (group[i] === el) {
                        return;
                    }
                    if (helpers.isDisabled(group[i])) {
                        continue;
                    }
                    helpers.focusElement(group[i]);
                    var radio = group[i];
                    helpers.dispatchClick(radio, helpers.createEvent(radio, 'click', helpers.modifiers({}, mask)));
                    return;
                }
            },
            // As user-event's getTabDestination
            tabDestination: function (activeElement, shift) {
                var doc = activeElement.ownerDocument;
                var tabIndex = function (el) {
                    return Number(el.getAttribute('tabindex'));
                };
                var isRadio = function (el) {
                    return el.localName === 'input' && el.type === 'radio';
                };
                var enabled = arrayFrom(doc.querySelectorAll(FOCUSABLE_SELECTOR)).filter(function (el) {
                    return el === activeElement || !(tabIndex(el) < 0 || helpers.isDisabled(el));
                });
                // tabindex has no effect if the active element has a negative tabindex
                if (tabIndex(activeElement) >= 0) {
                    enabled.sort(function (a, b) {
                        var i = tabIndex(a);
                        var j = tabIndex(b);
                        if (i === j) {
                            return 0;
                        } else if (i === 0) {
                            return 1;
                        } else if (j === 0) {
                            return -1;
                        }
                        return i - j;
                    });
                }
                var checkedRadio = {};
                var pruned = [doc.body];
                var activeRadioGroup = isRadio(activeElement) ? activeElement.name : undefined;
                enabled.forEach(function (el) {
                    // For radio groups keep only the active radio, or else the checked one, or
                    // else all of them
                    if (isRadio(el) && el.name) {
                        if (el === activeElement) {
                            pruned.push(el);
                            return;
                        } else if (el.name === activeRadioGroup) {
                            return;
                        }
                        if (el.checked) {
                            pruned = pruned.filter(function (other) {
                                return !(isRadio(other) && other.name === el.name);
                            });
                            pruned.push(el);
                            checkedRadio[el.name] = el;
                            return;
                        }
                        if (checkedRadio[el.name] !== undefined) {
                            return;
                        }
                    }
                    pruned.push(el);
                });
                for (var index = pruned.indexOf(activeElement); ;) {
                    index += shift ? -1 : 1;
                    if (index === pruned.length) {
                        index = 0;
                    } else if (index === -1) {
                        index = pruned.length - 1;
                    }
                    if (pruned[index] === activeElement || pruned[index] === doc.body
                        || helpers.isVisibleForTab(pruned[index])) {
                        return pruned[index];
                    }
                }
            },
            // As user-event's isVisible
            isVisibleForTab: function (el) {
                var win = helpers.win(el);
                for (var node = el; node && node.ownerDocument; node = node.parentElement) {
                    var style = win.getComputedStyle(node);
                    if (style.display === 'none' || style.visibility === 'hidden') {
                        return false;
                    }
                }
                return true;
            },
            // As user-event's Tab keydown behaviour
            tab: function (doc, shift) {
                var destination = helpers.tabDestination(helpers.active(doc), shift);
                helpers.focusElement(destination);
                if (helpers.hasOwnSelection(destination)) {
                    helpers.setSelection(destination, 0, destination.value.length);
                }
            }
        };
        for (var key in functions) {
            helpers[key] = functions[key];
        }
    }-*/;
}
