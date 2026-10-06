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

import stroom.gwt.workbench.framework.client.play.TextEdits.Edit;

import com.google.gwt.core.client.JavaScriptObject;
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
///
/// The role and text editing rules are in [Aria] and [TextEdits], which are tested on the JVM;
/// the accessible name is worked out as `dom-accessibility-api` (which Testing Library uses) does.
final class Dom {

    private Dom() {
        // Static utility
    }

    // ---------- Queries ----------

    /// Finds elements by role, as Testing Library's `queryAllByRole` does: elements within the
    /// container (not the container itself) with the role, excluding inaccessible ones (hidden
    /// with `display: none`, `visibility: hidden`, the `hidden` attribute or `aria-hidden`).
    ///
    /// @param container The element to search within.
    /// @param role      The ARIA role, e.g. `button`.
    /// @param name      Matches the accessible name, or null for any.
    /// @return The elements within the container with the role and (if not null) accessible name.
    static native JsArray<Element> queryAllByRole(Element container, String role, TextMatch name) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return helpers.all(container).filter(function (el) {
            return helpers.queryRole(el) === role
                && !helpers.inaccessible(el)
                && (name === null || helpers.matchesExactly(name, helpers.accessibleName(el)));
        });
    }-*/;

    /// Finds elements by their text, as Testing Library's `queryAllByText` does, including the
    /// container itself. Like Testing Library, hidden elements are included; only `script` and
    /// `style` elements are ignored.
    ///
    /// @param container The element to search within.
    /// @param text      Matches the text.
    /// @param selector  Only elements matching this CSS selector are included, or null for all.
    /// @return The elements whose own text matches.
    static native JsArray<Element> queryAllByText(Element container, TextMatch text, String selector) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var css = selector === null ? '*' : selector;
        var candidates = Array.prototype.slice.call(container.querySelectorAll(css));
        if (container.matches && container.matches(css)) {
            candidates.unshift(container);
        }
        return candidates.filter(function (el) {
            return !el.matches('script, style') && helpers.matches(text, helpers.nodeText(el));
        });
    }-*/;

    /// Finds form controls by their label, as Testing Library's `queryAllByLabelText` does: by
    /// `aria-label`, by the text of the elements its `aria-labelledby` refers to (together or each
    /// on its own) or by the text of its `<label>`s. Like
    /// [#queryAllByText(Element, TextMatch, String)], hidden elements are included.
    ///
    /// @param container The element to search within.
    /// @param text      Matches the text of the label, `aria-label` or `aria-labelledby`
    ///                  element(s).
    /// @return The form controls within the container labelled with matching text.
    static native JsArray<Element> queryAllByLabelText(Element container, TextMatch text) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return helpers.all(container).filter(function (el) {
            var ariaLabel = el.getAttribute('aria-label');
            if (ariaLabel !== null && helpers.matches(text, ariaLabel)) {
                return true;
            }
            var labels = [];
            if (el.hasAttribute('aria-labelledby')) {
                labels = el.getAttribute('aria-labelledby').split(/\s+/).map(function (id) {
                    return id ? el.ownerDocument.getElementById(id) : null;
                }).filter(function (label) {
                    return label !== null;
                });
            } else if (el.labels) {
                labels = Array.prototype.slice.call(el.labels);
            }
            var contents = labels.map(helpers.labelContent).filter(function (content) {
                return !!content;
            });
            if (contents.length && helpers.matches(text, contents.join(' '))) {
                return true;
            }
            return contents.length > 1 && contents.some(function (content) {
                return helpers.matches(text, content);
            });
        });
    }-*/;

    /// Finds elements by their `title` attribute (or the `<title>` of an `<svg>`), as Testing
    /// Library's `queryAllByTitle` does.
    ///
    /// @param container The element to search within.
    /// @param text      Matches the title.
    /// @return The matching elements.
    static native JsArray<Element> queryAllByTitle(Element container, TextMatch text) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return Array.prototype.slice.call(container.querySelectorAll('[title], svg > title')).filter(function (el) {
            if (el.hasAttribute('title') && helpers.matches(text, el.getAttribute('title'))) {
                return true;
            }
            return el.tagName.toLowerCase() === 'title' && el.parentElement
                && el.parentElement.tagName.toLowerCase() === 'svg'
                && helpers.matches(text, helpers.nodeText(el));
        });
    }-*/;

    /// Finds elements by an attribute's value (with whitespace collapsed, as Testing Library
    /// matches it), e.g. for `queryAllByPlaceholderText` and `queryAllByTestId`.
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
    /// does: an input's or text area's `value` (of any input type, e.g. `on` for a check box), or
    /// the own text of a select's selected option.
    ///
    /// @param container The element to search within.
    /// @param text      Matches the value.
    /// @return The matching fields.
    static native JsArray<Element> queryAllByDisplayValue(Element container, TextMatch text) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        return Array.prototype.slice.call(container.querySelectorAll('input,textarea,select'))
            .filter(function (el) {
                if (el.tagName === 'SELECT') {
                    return Array.prototype.slice.call(el.options).some(function (option) {
                        return option.selected && helpers.matches(text, helpers.nodeText(option));
                    });
                }
                return helpers.matches(text, el.value);
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

    // ---------- Roles, called by the JavaScript helpers ----------

    /// @param element An element.
    /// @return The role Testing Library's `*ByRole` queries give it, or null.
    static String queryRoleOf(final Element element) {
        return Aria.queryRole(getLocalName(element), getInputType(element), name -> getAttribute(element, name));
    }

    /// @param element An element.
    /// @return The role `dom-accessibility-api` gives it while computing an accessible name, or
    /// null.
    static String nameRoleOf(final Element element) {
        return Aria.nameRole(getLocalName(element), getInputType(element), getSelectSize(element),
                name -> getAttribute(element, name));
    }

    private static native String getLocalName(Element element) /*-{
        return element.localName || element.tagName.toLowerCase();
    }-*/;

    // The type property of an input, which is 'text' for a missing or invalid type
    private static native String getInputType(Element element) /*-{
        return element.localName === 'input' ? String(element.type || 'text').toLowerCase() : null;
    }-*/;

    private static native int getSelectSize(Element element) /*-{
        return element.localName === 'select' ? (element.size || 0) : 0;
    }-*/;

    /// @return The JavaScript helpers behind the queries and user events, created once per page.
    static native Object helpers() /*-{
        if ($wnd.__sbmPlayHelpers2) {
            return $wnd.__sbmPlayHelpers2;
        }
        var Dom = {
            queryRole: function (el) {
                return @stroom.gwt.workbench.framework.client.play.Dom::queryRoleOf(*)(el);
            },
            nameRole: function (el) {
                return @stroom.gwt.workbench.framework.client.play.Dom::nameRoleOf(*)(el);
            },
            allowsNameFromContent: function (role) {
                return @stroom.gwt.workbench.framework.client.play.Aria::allowsNameFromContent(*)(role);
            },
            isControl: function (role) {
                return @stroom.gwt.workbench.framework.client.play.Aria::isControl(*)(role);
            },
            isRange: function (role) {
                return @stroom.gwt.workbench.framework.client.play.Aria::isRange(*)(role);
            },
            prohibitsNaming: function (role) {
                return @stroom.gwt.workbench.framework.client.play.Aria::prohibitsNaming(*)(role);
            },
            isPresentational: function (role) {
                return @stroom.gwt.workbench.framework.client.play.Aria::isPresentational(*)(role);
            }
        };
        var normalise = function (text) {
            return (text || '').replace(/\s+/g, ' ').trim();
        };
        var isElement = function (node) {
            return node !== null && node !== undefined && node.nodeType === 1;
        };
        var arrayFrom = function (list) {
            return Array.prototype.slice.call(list);
        };
        var labelledNodeNames = ['button', 'meter', 'output', 'progress', 'select', 'textarea', 'input'];
        var helpers = {
            normalise: normalise,
            // As Testing Library's default matcher, which collapses whitespace first
            matches: function (match, text) {
                if (text === null || text === undefined) {
                    return false;
                }
                return match.@stroom.gwt.workbench.framework.client.play.TextMatch::matches(*)(normalise(text));
            },
            // As Testing Library matches an accessible name, which is already flattened
            matchesExactly: function (match, text) {
                return match.@stroom.gwt.workbench.framework.client.play.TextMatch::matches(*)(text);
            },
            all: function (container) {
                return arrayFrom(container.querySelectorAll('*'));
            },
            queryRole: Dom.queryRole,
            // As Testing Library's isInaccessible
            inaccessible: function (el) {
                var win = el.ownerDocument.defaultView;
                if (win.getComputedStyle(el).visibility === 'hidden') {
                    return true;
                }
                for (var node = el; node; node = node.parentElement) {
                    if (node.hidden === true || node.getAttribute('aria-hidden') === 'true'
                        || win.getComputedStyle(node).display === 'none') {
                        return true;
                    }
                }
                return false;
            },
            // The text Testing Library's text queries match: the element's own text nodes, or a
            // button-like input's value
            nodeText: function (el) {
                if (el.matches('input[type=submit], input[type=button], input[type=reset]')) {
                    return el.value;
                }
                var text = '';
                for (var i = 0; i < el.childNodes.length; i++) {
                    if (el.childNodes[i].nodeType === 3) {
                        text += el.childNodes[i].textContent;
                    }
                }
                return text;
            },
            // The text of a label, as Testing Library's getLabelContent: its text without that of
            // the form controls in it
            labelContent: function (label) {
                if (label.tagName.toLowerCase() !== 'label') {
                    return label.value || label.textContent;
                }
                var textOf = function (node) {
                    if (labelledNodeNames.indexOf(node.nodeName.toLowerCase()) >= 0) {
                        return '';
                    }
                    if (node.nodeType === 3) {
                        return node.textContent;
                    }
                    return arrayFrom(node.childNodes).map(textOf).join('');
                };
                return textOf(label);
            },
            // The accessible name, as dom-accessibility-api's computeAccessibleName (without
            // pseudo elements, as Testing Library's default configuration)
            accessibleName: function (root) {
                if (Dom.prohibitsNaming(Dom.nameRole(root))) {
                    return '';
                }
                var win = root.ownerDocument.defaultView;
                var consulted = new Set();
                var hasRole = function (node, roles) {
                    return isElement(node) && roles.indexOf(Dom.nameRole(node)) !== -1;
                };
                var isHidden = function (node) {
                    if (!isElement(node)) {
                        return false;
                    }
                    if (node.hasAttribute('hidden') || node.getAttribute('aria-hidden') === 'true') {
                        return true;
                    }
                    var style = win.getComputedStyle(node);
                    return style.getPropertyValue('display') === 'none'
                        || style.getPropertyValue('visibility') === 'hidden';
                };
                var idRefs = function (node, attribute) {
                    if (!isElement(node) || !node.hasAttribute(attribute)) {
                        return [];
                    }
                    var scope = node.getRootNode ? node.getRootNode() : node.ownerDocument;
                    if (!scope.getElementById) {
                        scope = node.ownerDocument;
                    }
                    return node.getAttribute(attribute).split(' ').map(function (id) {
                        return scope.getElementById(id);
                    }).filter(function (el) {
                        return el !== null;
                    });
                };
                var useAttribute = function (el, name) {
                    var attribute = el.getAttributeNode(name);
                    if (attribute !== null && !consulted.has(attribute) && attribute.value.trim() !== '') {
                        consulted.add(attribute);
                        return attribute.value;
                    }
                    return null;
                };
                var isLabelable = function (el) {
                    var name = el.localName;
                    return name === 'button' || (name === 'input' && el.getAttribute('type') !== 'hidden')
                        || name === 'meter' || name === 'output' || name === 'progress' || name === 'select'
                        || name === 'textarea';
                };
                var getLabels = function (el) {
                    if (el.labels === null) {
                        return null;
                    }
                    if (el.labels !== undefined) {
                        return arrayFrom(el.labels);
                    }
                    if (!isLabelable(el)) {
                        return null;
                    }
                    return arrayFrom(el.ownerDocument.querySelectorAll('label')).filter(function (label) {
                        return label.control === el;
                    });
                };
                var selectedOptions = function (listbox) {
                    if (listbox.localName === 'select') {
                        return arrayFrom(listbox.selectedOptions || listbox.querySelectorAll('[selected]'));
                    }
                    return arrayFrom(listbox.querySelectorAll('[aria-selected="true"]'));
                };
                var compute;
                // The text of an element's content, with a space around block (non-inline) children
                var fromContent = function (node, context) {
                    var text = '';
                    var children = arrayFrom(node.childNodes).concat(idRefs(node, 'aria-owns'));
                    children.forEach(function (child) {
                        var result = compute(child, {embedded: context.embedded, referenced: false, recursion: true});
                        var display = isElement(child)
                            ? win.getComputedStyle(child).getPropertyValue('display') : 'inline';
                        var separator = display !== 'inline' ? ' ' : '';
                        text += separator + result + separator;
                    });
                    return text.trim();
                };
                // The name given by the host language, e.g. a label, alt text or legend
                var fromHostLanguage = function (node) {
                    if (!isElement(node)) {
                        return null;
                    }
                    var name = node.localName;
                    var children = arrayFrom(node.childNodes);
                    var i;
                    if (name === 'fieldset') {
                        consulted.add(node);
                        for (i = 0; i < children.length; i++) {
                            if (isElement(children[i]) && children[i].localName === 'legend') {
                                return compute(children[i], {embedded: false, referenced: false, recursion: false});
                            }
                        }
                    } else if (name === 'table') {
                        consulted.add(node);
                        for (i = 0; i < children.length; i++) {
                            if (isElement(children[i]) && children[i].localName === 'caption') {
                                return compute(children[i], {embedded: false, referenced: false, recursion: false});
                            }
                        }
                    } else if (name === 'svg') {
                        consulted.add(node);
                        for (i = 0; i < children.length; i++) {
                            if (isElement(children[i]) && children[i].localName === 'title'
                                && children[i].ownerSVGElement !== undefined) {
                                return children[i].textContent;
                            }
                        }
                        return null;
                    } else if (name === 'img' || name === 'area') {
                        var alt = useAttribute(node, 'alt');
                        if (alt !== null) {
                            return alt;
                        }
                    } else if (name === 'optgroup') {
                        var label = useAttribute(node, 'label');
                        if (label !== null) {
                            return label;
                        }
                    }
                    if (name === 'input'
                        && (node.type === 'button' || node.type === 'submit' || node.type === 'reset')) {
                        var value = useAttribute(node, 'value');
                        if (value !== null) {
                            return value;
                        }
                        if (node.type === 'submit') {
                            return 'Submit';
                        }
                        if (node.type === 'reset') {
                            return 'Reset';
                        }
                    }
                    var labels = getLabels(node);
                    if (labels !== null && labels.length !== 0) {
                        consulted.add(node);
                        return labels.map(function (labelElement) {
                            return compute(labelElement, {embedded: true, referenced: false, recursion: true});
                        }).filter(function (text) {
                            return text.length > 0;
                        }).join(' ');
                    }
                    if (name === 'input' && node.type === 'image') {
                        var imageAlt = useAttribute(node, 'alt');
                        if (imageAlt !== null) {
                            return imageAlt;
                        }
                        var imageTitle = useAttribute(node, 'title');
                        return imageTitle !== null ? imageTitle : 'Submit Query';
                    }
                    if (hasRole(node, ['button'])) {
                        var content = fromContent(node, {embedded: false, referenced: false});
                        if (content !== '') {
                            return content;
                        }
                    }
                    return null;
                };
                compute = function (current, context) {
                    if (consulted.has(current)) {
                        return '';
                    }
                    if (isHidden(current) && !context.referenced) {
                        consulted.add(current);
                        return '';
                    }
                    var labelAttribute = isElement(current) ? current.getAttributeNode('aria-labelledby') : null;
                    var labelElements = labelAttribute !== null && !consulted.has(labelAttribute)
                        ? idRefs(current, 'aria-labelledby') : [];
                    if (!context.referenced && labelElements.length > 0) {
                        consulted.add(labelAttribute);
                        return labelElements.map(function (element) {
                            return compute(element, {embedded: context.embedded, referenced: true, recursion: false});
                        }).join(' ');
                    }
                    var role = isElement(current) ? Dom.nameRole(current) : null;
                    var skipToContent = context.recursion && Dom.isControl(role);
                    if (!skipToContent) {
                        var ariaLabel = ((isElement(current) && current.getAttribute('aria-label')) || '').trim();
                        if (ariaLabel !== '') {
                            consulted.add(current);
                            return ariaLabel;
                        }
                        if (!Dom.isPresentational(role)) {
                            var hostName = fromHostLanguage(current);
                            if (hostName !== null) {
                                consulted.add(current);
                                return hostName;
                            }
                        }
                    }
                    if (role === 'menu') {
                        consulted.add(current);
                        return '';
                    }
                    if (skipToContent || context.embedded || context.referenced) {
                        if (role === 'combobox' || role === 'listbox') {
                            consulted.add(current);
                            var selected = selectedOptions(current);
                            if (selected.length === 0) {
                                return current.localName === 'input' ? current.value : '';
                            }
                            return selected.map(function (option) {
                                return compute(option,
                                    {embedded: context.embedded, referenced: false, recursion: true});
                            }).join(' ');
                        }
                        if (Dom.isRange(role)) {
                            consulted.add(current);
                            if (current.hasAttribute('aria-valuetext')) {
                                return current.getAttribute('aria-valuetext');
                            }
                            if (current.hasAttribute('aria-valuenow')) {
                                return current.getAttribute('aria-valuenow');
                            }
                            return current.getAttribute('value') || '';
                        }
                        if (role === 'textbox') {
                            consulted.add(current);
                            return current.localName === 'input' || current.localName === 'textarea'
                                ? current.value : (current.textContent || '');
                        }
                    }
                    if (Dom.allowsNameFromContent(role) || (isElement(current) && context.referenced)
                        || (isElement(current) && current.localName === 'caption')) {
                        var fromChildren = fromContent(current, {embedded: context.embedded, referenced: false});
                        if (fromChildren !== '') {
                            consulted.add(current);
                            return fromChildren;
                        }
                    }
                    if (current.nodeType === 3) {
                        consulted.add(current);
                        return current.textContent || '';
                    }
                    if (context.recursion) {
                        consulted.add(current);
                        return fromContent(current, {embedded: context.embedded, referenced: false});
                    }
                    var title = isElement(current) ? useAttribute(current, 'title') : null;
                    consulted.add(current);
                    return title !== null ? title : '';
                };
                var name = compute(root, {embedded: false, referenced: false, recursion: false});
                return name.trim().replace(/\s\s+/g, ' ');
            }
        };
        @stroom.gwt.workbench.framework.client.play.UserEventHelpers::addTo(*)(helpers);
        $wnd.__sbmPlayHelpers2 = helpers;
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
    /// @return The element's classes, i.e. its `class` attribute (which, unlike `className`, is a
    /// string for SVG elements too), or an empty string.
    static native String getClassName(Element element) /*-{
        return element.getAttribute('class') || '';
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

    /// Whether an element has a style, as jest-dom's `toHaveStyle` checks in a real browser: the
    /// expected value is normalised by setting it on a style declaration (so e.g. `#f00` becomes
    /// `rgb(255, 0, 0)`, but a named colour such as `red` stays `red`), then compared with the
    /// element's computed value, which must be the same text. A value the browser doesn't accept
    /// normalises to an empty string, which never matches a computed value.
    ///
    /// @param element  The element.
    /// @param property The CSS property, e.g. `font-weight`.
    /// @param value    The expected value, e.g. `bold`.
    /// @return True if the element's computed style for the property matches.
    static native boolean hasStyle(Element element, String property, String value) /*-{
        var doc = element.ownerDocument;
        var computed = doc.defaultView.getComputedStyle(element);
        var declared;
        if (property.indexOf('--') === 0) {
            // As jest-dom, which sets style['--x'], which isn't a CSS property, so the value is
            // kept as it is
            declared = value;
        } else {
            var copy = doc.createElement('div');
            copy.style.setProperty(property, value);
            declared = copy.style.getPropertyValue(property);
        }
        return computed[property] === declared || computed.getPropertyValue(property) === declared;
    }-*/;

    /// @param element The element.
    /// @param side    A property of `DOMRect`, e.g. `left` or `width`.
    /// @return The property of the element's bounding client rectangle.
    static native double getRect(Element element, String side) /*-{
        return element.getBoundingClientRect()[side];
    }-*/;

    /// Whether an element is visible, as jest-dom's `toBeVisible` decides: it is in the document,
    /// and neither it nor an ancestor has `display: none`, `visibility: hidden` or `collapse`,
    /// `opacity: 0` or the `hidden` attribute, and it isn't in (or itself) a closed `<details>`,
    /// except for the details' `<summary>`.
    ///
    /// @param element The element.
    /// @return True if the element is visible.
    static native boolean isVisible(Element element) /*-{
        var win = element.ownerDocument.defaultView;
        if (element.ownerDocument !== element.getRootNode({composed: true})) {
            return false;
        }
        var previous = null;
        for (var node = element; node; node = node.parentElement) {
            var style = win.getComputedStyle(node);
            if (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse'
                || style.opacity === '0' || style.opacity === 0) {
                return false;
            }
            var detailsVisible = node.nodeName === 'DETAILS'
                && !(previous && previous.nodeName === 'SUMMARY') ? node.hasAttribute('open') : true;
            if (node.hasAttribute('hidden') || !detailsVisible) {
                return false;
            }
            previous = node;
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

    /// The value jest-dom's `toHaveValue` compares, with its JavaScript type: a number field's
    /// value as a number (null if empty), a select's selected option's value (null for a multiple
    /// select, whose value is an array in jest-dom, which never equals a single value), another
    /// field's value
    /// as a string, or for an element with the role `meter`, `progressbar`, `slider` or
    /// `spinbutton`, its `aria-valuenow` as a number.
    ///
    /// @param element The element.
    /// @return The value as a String or Double, or null if it has none.
    static native Object getTypedValue(Element element) /*-{
        var tag = element.tagName.toLowerCase();
        if (tag === 'input') {
            if (element.type === 'number') {
                return element.value === '' ? null : @java.lang.Double::valueOf(D)(Number(element.value));
            }
            return element.value;
        }
        if (tag === 'select') {
            var selected = Array.prototype.slice.call(element.options).filter(function (option) {
                return option.selected;
            });
            if (element.multiple) {
                // An array in jest-dom, which never equals a single value
                return null;
            }
            return selected.length ? selected[0].value : null;
        }
        if (element.value !== undefined && element.value !== null) {
            return typeof element.value === 'number'
                ? @java.lang.Double::valueOf(D)(element.value) : String(element.value);
        }
        var role = element.getAttribute('role');
        if (['meter', 'progressbar', 'slider', 'spinbutton'].indexOf(role) >= 0) {
            return @java.lang.Double::valueOf(D)(Number(element.getAttribute('aria-valuenow')));
        }
        return null;
    }-*/;

    /// @param element An element.
    /// @return True if it is a check box or radio button input, which jest-dom's `toHaveValue`
    /// refuses.
    static native boolean isCheckableInput(Element element) /*-{
        return element.tagName.toLowerCase() === 'input' && (element.type === 'checkbox' || element.type === 'radio');
    }-*/;

    /// Whether an element is checked, as jest-dom's `toBeChecked` decides.
    ///
    /// @param element An element.
    /// @return 1 if it is checked, 0 if it isn't, or -1 if it can't be checked: it isn't a check
    /// box or radio button input, nor an element with a role that supports `aria-checked`
    /// (`checkbox`, `menuitemcheckbox`, `menuitemradio`, `option`, `radio`, `switch` or
    /// `treeitem`) and `aria-checked` of `true` or `false`.
    static native int getCheckedState(Element element) /*-{
        if (element.tagName.toLowerCase() === 'input' && (element.type === 'checkbox' || element.type === 'radio')) {
            return element.checked ? 1 : 0;
        }
        var roles = ['checkbox', 'menuitemcheckbox', 'menuitemradio', 'option', 'radio', 'switch', 'treeitem'];
        var ariaChecked = element.getAttribute('aria-checked');
        if (roles.indexOf(element.getAttribute('role')) >= 0 && (ariaChecked === 'true' || ariaChecked === 'false')) {
            return ariaChecked === 'true' ? 1 : 0;
        }
        return -1;
    }-*/;

    /// Whether an element is disabled, as jest-dom's `toBeDisabled` decides: a form control (or
    /// custom element) with the `disabled` attribute, or in a disabled ancestor (except the
    /// first legend of a disabled field set). `aria-disabled` doesn't count.
    ///
    /// @param element The element.
    /// @return True if the element is disabled.
    static native boolean isDisabled(Element element) /*-{
        var formTags = ['fieldset', 'input', 'select', 'optgroup', 'option', 'button', 'textarea'];
        var tagOf = function (el) {
            return el.tagName.toLowerCase();
        };
        var canBeDisabled = function (el) {
            var tag = tagOf(el);
            return formTags.indexOf(tag) >= 0 || tag.indexOf('-') >= 0;
        };
        var isElementDisabled = function (el) {
            return canBeDisabled(el) && el.hasAttribute('disabled');
        };
        var isFirstLegend = function (el, parent) {
            if (tagOf(el) !== 'legend' || tagOf(parent) !== 'fieldset') {
                return false;
            }
            var children = Array.prototype.slice.call(parent.children);
            for (var i = 0; i < children.length; i++) {
                if (tagOf(children[i]) === 'legend') {
                    return children[i] === el;
                }
            }
            return false;
        };
        var isAncestorDisabled = function (el) {
            var parent = el.parentElement;
            return !!parent && ((isElementDisabled(parent) && !isFirstLegend(el, parent))
                || isAncestorDisabled(parent));
        };
        return canBeDisabled(element) && (isElementDisabled(element) || isAncestorDisabled(element));
    }-*/;

    /// Whether user-event treats an element as disabled for pointer and editing actions: it, or
    /// an ancestor, is a form control with the `disabled` attribute, or it is in a disabled field
    /// set (outside the field set's first legend).
    ///
    /// @param element The element.
    /// @return True if user-event treats the element as disabled.
    static native boolean isUserEventDisabled(Element element) /*-{
        var controls = ['button', 'input', 'select', 'textarea', 'optgroup', 'option'];
        for (var el = element; el; el = el.parentElement) {
            var tag = el.tagName.toLowerCase();
            if (controls.indexOf(tag) >= 0) {
                if (el.hasAttribute('disabled')) {
                    return true;
                }
            } else if (tag === 'fieldset') {
                var legend = el.querySelector(':scope > legend');
                if (el.hasAttribute('disabled') && !(legend && legend.contains(element))) {
                    return true;
                }
            } else if (tag.indexOf('-') >= 0) {
                if (el.constructor.formAssociated && el.hasAttribute('disabled')) {
                    return true;
                }
            }
        }
        return false;
    }-*/;

    /// @param element The element.
    /// @return True if the element's `disabled` property is set, in which case `userEvent.type`
    /// does nothing.
    static native boolean hasDisabledProperty(Element element) /*-{
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

    /// @param object Any JavaScript object, e.g. an element or event passed to a spy.
    /// @return A short description of it: an element as [#describe(Element)] does, otherwise as
    /// JavaScript converts it to a string.
    static native String describeObject(JavaScriptObject object) /*-{
        if (object && object.nodeType === 1) {
            return @stroom.gwt.workbench.framework.client.play.Dom::describe(*)(object);
        }
        if (object && object.nodeType === 3) {
            return '#text ' + JSON.stringify(object.textContent);
        }
        try {
            return String(object);
        } catch (e) {
            return Object.prototype.toString.call(object);
        }
    }-*/;

    // ---------- User events ----------

    /// Clicks an element as `userEvent.click`, `dblClick` and `pointer({ keys: "[MouseRight]",
    /// target })` do (user-event 14's direct API, as React Storybook plays call it, which starts
    /// each call with a new pointer that isn't over any element):
    ///
    /// * if `move`, moves the pointer from the body onto the element (see
    ///   [#hover(Element, boolean)]);
    /// * for each click, fires `pointerdown`, `mousedown` (setting the caret or selection as a
    ///   press does, see below, and moving the focus to the closest focusable element, or away,
    ///   unless cancelled), `contextmenu` for the secondary button, `pointerup`, `mouseup` and
    ///   `click` (or `auxclick` for another button), and `dblclick` after the second click.
    ///
    /// As in user-event, a press sets the selection as if at the end of the element's text: one
    /// click puts the caret at the end of a field's value (or of the element's text), a double
    /// click selects the last word and a triple click the last line. A disabled element (see
    /// [#isUserEventDisabled(Element)]) only gets the pointer events, and the focus still moves.
    /// A click on a label focuses and clicks its control (as user-event does instead of the
    /// browser's default) and a click on a file input fires `fileDialog` rather than opening one.
    ///
    /// @param element    The element.
    /// @param clickCount The number of clicks, e.g. 2 for a double click.
    /// @param button     0 for the main button, 1 for the middle button, 2 for the secondary
    ///                   button.
    /// @param modifiers  The modifier keys held, as a mask of [Keys#SHIFT] etc.
    /// @param move       True to move the pointer onto the element first, as `click` and
    ///                   `dblClick` do; false for `pointer({ keys, target })`, which doesn't.
    /// @throws PlayException If the element has (or inherits) `pointer-events: none`, as
    ///                       user-event throws.
    static void click(final Element element,
                      final int clickCount,
                      final int button,
                      final int modifiers,
                      final boolean move) {
        prepareDocument(element);
        if (move) {
            // As user-event: leave the body, check the element has pointer events, then enter it
            movePointer(element, true, false);
            assertPointerEvents(element);
            movePointer(element, false, true);
        } else {
            assertPointerEvents(element);
        }
        nativeClick(element, clickCount, button, modifiers, isUserEventDisabled(element));
    }

    // Moves the pointer from the body onto the element, firing the events of leaving the body
    // and/or of entering the element
    private static native void movePointer(Element element, boolean leave, boolean enter) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var body = element.ownerDocument.body;
        var at = helpers.centre(element, 0);
        if (leave) {
            helpers.pointerLeave(body, element, at);
        }
        if (enter) {
            helpers.pointerEnter(body, element, at);
        }
    }-*/;

    private static native void nativeClick(Element element,
                                           int clickCount,
                                           int button,
                                           int modifiers,
                                           boolean disabled) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var at = helpers.centre(element, modifiers);
        // The buttons mask: 1 main, 2 secondary, 4 middle
        var pressed = button === 2 ? 2 : (button === 1 ? 4 : 1);
        for (var i = 1; i <= clickCount; i++) {
            var prevented = !helpers.uiEvent(element, 'pointerdown',
                helpers.withInit(at, {button: button, buttons: pressed}));
            if (!prevented && (disabled || helpers.uiEvent(element, 'mousedown',
                helpers.withInit(at, {button: button, buttons: pressed, detail: i})))) {
                helpers.selectPerMouseDown(element, i);
                helpers.focusElement(element);
            }
            if (!disabled && button === 2) {
                helpers.uiEvent(element, 'contextmenu', helpers.withInit(at, {button: 2, buttons: pressed}));
            }
            helpers.uiEvent(element, 'pointerup', helpers.withInit(at, {button: button, buttons: 0}));
            if (!disabled) {
                if (!prevented) {
                    helpers.uiEvent(element, 'mouseup', helpers.withInit(at, {button: button, buttons: 0, detail: i}));
                }
                var clickInit = helpers.withInit(at, {button: button, buttons: 0, detail: i});
                if (button === 0) {
                    helpers.dispatchClick(element, helpers.createEvent(element, 'click', clickInit));
                } else {
                    helpers.uiEvent(element, 'auxclick', clickInit);
                }
                if (button === 0 && i === 2) {
                    helpers.uiEvent(element, 'dblclick', helpers.withInit(at, {button: 0, buttons: 0, detail: 2}));
                }
            }
        }
    }-*/;

    /// Moves the pointer as `userEvent.hover` and `userEvent.unhover` do (user-event 14's direct
    /// API, as React Storybook plays call it):
    ///
    /// * `hover` starts with a new pointer, over the body, and moves it onto the element:
    ///   `pointerout` and `mouseout` on the body, `pointerover` and `mouseover` on the element,
    ///   `pointerenter` and `mouseenter` on the element and each ancestor below the body (the
    ///   element first), then `pointermove` and `mousemove` on the element;
    /// * `unhover` starts with the pointer on the element and moves it to the body: `pointerout`
    ///   and `mouseout` on the element, `pointerleave` and `mouseleave` on the element and each
    ///   ancestor below the body, `pointerover` and `mouseover` on the body, then `pointermove`
    ///   and `mousemove` on the body.
    ///
    /// @param element The element.
    /// @param over    True to move onto the element, false to move off it.
    /// @throws PlayException If the element has (or inherits) `pointer-events: none`, as
    ///                       user-event throws.
    static void hover(final Element element, final boolean over) {
        prepareDocument(element);
        if (over) {
            movePointer(element, true, false);
            assertPointerEvents(element);
            movePointer(element, false, true);
        } else {
            assertPointerEvents(element);
            moveOff(element);
        }
    }

    private static native void moveOff(Element element) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var body = element.ownerDocument.body;
        var at = {clientX: 0, clientY: 0, screenX: 0, screenY: 0};
        helpers.pointerLeave(element, body, at);
        helpers.pointerEnter(element, body, at);
    }-*/;

    /// @throws PlayException If the element has (or inherits) `pointer-events: none`.
    private static void assertPointerEvents(final Element element) {
        final String declaredBy = pointerEventsNone(element);
        if (declaredBy != null) {
            throw new PlayException("Unable to perform pointer interaction as the element "
                                    + (declaredBy.isEmpty()
                    ? "has `pointer-events: none`: " + describe(element)
                    : "inherits `pointer-events: none` from " + declaredBy + ": " + describe(element)));
        }
    }

    /// @return Null if the element has pointer events, an empty string if it declares
    /// `pointer-events: none`, or a description of the ancestor it inherits it from.
    private static native String pointerEventsNone(Element element) /*-{
        var win = element.ownerDocument.defaultView;
        if (win.getComputedStyle(element).pointerEvents !== 'none') {
            return null;
        }
        var declaring = element;
        while (declaring.parentElement && win.getComputedStyle(declaring.parentElement).pointerEvents === 'none') {
            declaring = declaring.parentElement;
        }
        return declaring === element
            ? ''
            : @stroom.gwt.workbench.framework.client.play.Dom::describe(*)(declaring);
    }-*/;

    /// Fires a keyboard event on the focused element (or the body if nothing, or a disabled
    /// element, has the focus, as user-event does), with the legacy `keyCode`, `which` and
    /// `charCode` properties GWT reads.
    ///
    /// @param defaultTarget Any element in the document.
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
        var target = helpers.active(doc);
        var win = doc.defaultView;
        var event = new win.KeyboardEvent(type, helpers.modifiers({
            key: key, code: code, bubbles: true, cancelable: true, composed: true, view: win
        }, modifiers));
        Object.defineProperty(event, 'keyCode', {get: function () { return keyCode; }});
        Object.defineProperty(event, 'which', {get: function () { return keyCode; }});
        Object.defineProperty(event, 'charCode', {get: function () { return charCode; }});
        return target.dispatchEvent(event);
    }-*/;

    /// Does what user-event 14 does by default for a key on the focused element (see
    /// [Keys#keyDownAction(String, String, int)] and [Keys#keyPressAction(String)]).
    ///
    /// @param defaultTarget Any element in the document.
    /// @param action        The name of a [Keys.DefaultAction].
    /// @param key           The key value, e.g. `a`.
    /// @param modifiers     The modifier keys held, as a mask of [Keys#SHIFT] etc.
    static native void keyDefault(Element defaultTarget, String action, String key, int modifiers) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        // The input types user-event clicks on Enter, and those it submits a single-input form for
        var CLICK_INPUT_ON_ENTER = ['button', 'color', 'file', 'image', 'reset', 'submit'];
        var SUBMIT_SINGLE_INPUT_ON_ENTER = ['email', 'month', 'password', 'search', 'tel', 'text', 'url', 'week'];
        var doc = defaultTarget.ownerDocument;
        var target = helpers.active(doc);
        var edit = function (data, inputType) {
            @stroom.gwt.workbench.framework.client.play.Dom::edit(*)(target, data, inputType);
        };
        var isRadio = target.localName === 'input' && target.type === 'radio';
        var isField = target.localName === 'input' || target.localName === 'textarea';
        var click = function (el) {
            helpers.dispatchClick(el, helpers.createEvent(el, 'click', helpers.modifiers({}, modifiers)));
        };
        if (action === 'TYPE') {
            if (helpers.isEditable(target)) {
                edit(key, 'insertText');
            }
        } else if (action === 'DELETE_BACKWARD' || action === 'DELETE_FORWARD') {
            if (helpers.isEditable(target)) {
                edit('', action === 'DELETE_BACKWARD' ? 'deleteContentBackward' : 'deleteContentForward');
            }
        } else if (action === 'SELECT_ALL') {
            helpers.selectAll(target);
        } else if (action === 'MOVE_TO_START' || action === 'MOVE_TO_END') {
            if (isField || helpers.isContentEditable(target)) {
                var length = helpers.isContentEditable(target)
                    ? (target.textContent || '').length : helpers.uiValue(target).length;
                var position = action === 'MOVE_TO_START' ? 0 : length;
                helpers.setSelectionRange(target, position, position);
            }
        } else if (action === 'PAGE_UP' || action === 'PAGE_DOWN') {
            if (target.localName === 'input') {
                var pagePosition = action === 'PAGE_UP' ? 0 : helpers.uiValue(target).length;
                helpers.setSelectionRange(target, pagePosition, pagePosition);
            }
        } else if (action === 'MOVE_LEFT' || action === 'MOVE_RIGHT') {
            var direction = action === 'MOVE_LEFT' ? -1 : 1;
            if (isRadio) {
                helpers.walkRadio(target, direction, modifiers);
            } else {
                helpers.moveSelection(target, direction);
            }
        } else if (action === 'ARROW_UP' || action === 'ARROW_DOWN') {
            if (isRadio) {
                helpers.walkRadio(target, action === 'ARROW_UP' ? -1 : 1, modifiers);
            }
        } else if (action === 'FOCUS_NEXT' || action === 'FOCUS_PREVIOUS') {
            helpers.tab(doc, action === 'FOCUS_PREVIOUS');
        } else if (action === 'ENTER') {
            // As user-event's keypress behaviour for Enter
            if (target.localName === 'button'
                || (target.localName === 'input' && CLICK_INPUT_ON_ENTER.indexOf(target.type) >= 0)
                || (target.localName === 'a' && !!target.href)) {
                click(target);
                return;
            }
            if (target.localName === 'input') {
                // Implicit submission: click the form's default button, or submit a form whose
                // only input this is
                var form = target.form;
                var submit = form ? form.querySelector('input[type="submit"], button:not([type]), '
                    + 'button[type="submit"]') : null;
                if (submit) {
                    click(submit);
                } else if (form && SUBMIT_SINGLE_INPUT_ON_ENTER.indexOf(target.type) >= 0
                    && form.querySelectorAll('input').length === 1) {
                    helpers.uiEvent(form, 'submit', {});
                }
                return;
            }
            if (helpers.isEditable(target)) {
                edit('\n', helpers.isContentEditable(target) && (modifiers & 1) === 0
                    ? 'insertParagraph' : 'insertLineBreak');
            }
        }
    }-*/;

    /// Clicks the focused element if releasing Space activates it, as user-event does on the
    /// `keyup` of Space (if neither the `keydown` nor the `keyup` was cancelled): a button, or an
    /// input of a clickable type (button, colour, file, image, reset, submit, check box or radio
    /// button).
    ///
    /// @param defaultTarget Any element in the document.
    /// @param modifiers     The modifier keys held, as a mask of [Keys#SHIFT] etc.
    static native void spaceActivate(Element defaultTarget, int modifiers) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var target = helpers.active(defaultTarget.ownerDocument);
        if (helpers.isClickableInput(target)) {
            helpers.dispatchClick(target, helpers.createEvent(target, 'click', helpers.modifiers({}, modifiers)));
        }
    }-*/;

    // ---------- Editing ----------

    /// @param element An element.
    /// @return True if user-event can type into it as a text field (whether or not it is read
    /// only), i.e. a text area or an input of an editable type.
    static boolean isTextField(final Element element) {
        return TextEdits.isTextField(getLocalName(element), getInputType(element));
    }

    /// @param element An element.
    /// @return True if typing changes it, as user-event's `isEditable`: a text field that isn't
    /// read only, or an element whose own `contenteditable` attribute is empty or `true`.
    static boolean isEditable(final Element element) {
        return (isTextField(element) && !isReadOnly(element)) || isContentEditable(element);
    }

    private static native boolean isReadOnly(Element element) /*-{
        return !!element.readOnly;
    }-*/;

    private static native boolean isContentEditable(Element element) /*-{
        return @stroom.gwt.workbench.framework.client.play.Dom::helpers()().isContentEditable(element);
    }-*/;

    /// Types or deletes text as user-event's `input` does: fires `beforeinput` and, unless that
    /// is cancelled, changes the field's value (see [TextEdits]) and fires `input` (for a date
    /// or time field, only once its value is complete, then `change` too). In a content editable
    /// element, edits its content at the document's selection, keeping its markup.
    ///
    /// @param element   The field, or a content editable element.
    /// @param data      The text typed, or an empty string to delete.
    /// @param inputType The `inputType`, e.g. `insertText` or `deleteContentBackward`.
    static void edit(final Element element, final String data, final String inputType) {
        if (element == null || !isEditable(element)) {
            return;
        }
        if (!isTextField(element)) {
            editContent(element, data, inputType);
            return;
        }
        final String type = getInputType(element);
        final boolean dateOrTime = "date".equals(type) || "time".equals(type);
        // There is no beforeinput on date and time fields
        if (!dateOrTime && !dispatchInputEvent(element, "beforeinput", inputType, data)) {
            return;
        }
        final Edit edit = TextEdits.edit(getUiValue(element), getSelectionStart(element), getSelectionEnd(element),
                data, inputType, type, TextEdits.maxLength(getLocalName(element), type,
                        getAttribute(element, "maxlength")), value -> isValidDateOrTimeValue(element, value));
        if (edit == null) {
            return;
        }
        setUiValue(element, edit.getValue(), edit.getOffset());
        if (dateOrTime) {
            // The browser only takes a complete date or time. As in user-event, whose input event
            // init has no inputType, the input event's inputType is "undefined".
            if (isValidDateOrTimeValue(element, edit.getValue())) {
                dispatchInputEvent(element, "input", "undefined", null);
                dispatchInputEvent(element, "change", null, null);
                clearInitialValue(element);
            }
        } else {
            dispatchInputEvent(element, "input", inputType, data);
        }
    }

    // As user-event's isValidDateOrTimeValue: whether the browser takes the value
    private static native boolean isValidDateOrTimeValue(Element element, String value) /*-{
        var clone = element.cloneNode();
        clone.value = value;
        return clone.value === value;
    }-*/;

    private static native String getUiValue(Element element) /*-{
        return @stroom.gwt.workbench.framework.client.play.Dom::helpers()().uiValue(element);
    }-*/;

    private static native void setUiValue(Element element, String value, int offset) /*-{
        @stroom.gwt.workbench.framework.client.play.Dom::helpers()().setUiValue(element, value, offset);
    }-*/;

    private static native int getSelectionStart(Element element) /*-{
        return @stroom.gwt.workbench.framework.client.play.Dom::helpers()().selection(element).start;
    }-*/;

    private static native int getSelectionEnd(Element element) /*-{
        return @stroom.gwt.workbench.framework.client.play.Dom::helpers()().selection(element).end;
    }-*/;

    private static native void clearInitialValue(Element element) /*-{
        @stroom.gwt.workbench.framework.client.play.Dom::helpers()().clearInitialValue(element);
    }-*/;

    /// Prepares the document as each user-event call does (see `UserEventHelpers`): fields
    /// track the value being typed and their selection, and fire `change` when they lose the
    /// focus if typing changed their value.
    ///
    /// @param element Any element in the document.
    static native void prepareDocument(Element element) /*-{
        @stroom.gwt.workbench.framework.client.play.Dom::helpers()().prepareDocument(element.ownerDocument);
    }-*/;

    /// @return False if the event was cancelled.
    private static native boolean dispatchInputEvent(Element element,
                                                     String type,
                                                     String inputType,
                                                     String data) /*-{
        var win = element.ownerDocument.defaultView;
        if (type === 'change') {
            return element.dispatchEvent(new win.Event('change', {bubbles: true}));
        }
        var event = new win.InputEvent(type, {bubbles: true, cancelable: type === 'beforeinput', composed: true});
        // As user-event, which sets the properties directly, so e.g. an inputType the browser
        // doesn't know (user-event's "undefined" for a date or time field) is kept
        Object.defineProperty(event, 'inputType', {get: function () { return inputType === null ? '' : inputType; }});
        Object.defineProperty(event, 'data', {get: function () { return data; }});
        return element.dispatchEvent(event);
    }-*/;

    // As user-event's input into a content editable element: edits at the document's selection
    // (which a click puts at the end of the text), if the selection is in a content editable
    // element, keeping the element's markup
    private static native void editContent(Element element, String data, String inputType) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        var win = element.ownerDocument.defaultView;
        var selection = element.ownerDocument.getSelection();
        if (!selection || !selection.anchorNode || !helpers.contentEditableOf(element)
            || !helpers.contentEditableOf(selection.anchorNode) || selection.rangeCount === 0) {
            return;
        }
        var range = selection.getRangeAt(0);
        if (!element.dispatchEvent(new win.InputEvent('beforeinput', {
            bubbles: true, cancelable: true, composed: true, inputType: inputType, data: data
        }))) {
            return;
        }
        var deleted = false;
        if (!range.collapsed) {
            deleted = true;
            range.deleteContents();
        } else if (inputType === 'deleteContentBackward' || inputType === 'deleteContentForward') {
            var next = helpers.nextCursorPosition(range.startContainer, range.startOffset,
                inputType === 'deleteContentBackward' ? -1 : 1, inputType);
            if (next) {
                deleted = true;
                var deleteRange = range.cloneRange();
                if (deleteRange.comparePoint(next.node, next.offset) < 0) {
                    deleteRange.setStart(next.node, next.offset);
                } else {
                    deleteRange.setEnd(next.node, next.offset);
                }
                deleteRange.deleteContents();
            }
        }
        if (data) {
            if (range.endContainer.nodeType === 3) {
                var offset = range.endOffset;
                range.endContainer.insertData(offset, data);
                range.setStart(range.endContainer, offset + data.length);
                range.setEnd(range.endContainer, offset + data.length);
            } else {
                var text = element.ownerDocument.createTextNode(data);
                range.insertNode(text);
                range.setStart(text, data.length);
                range.setEnd(text, data.length);
            }
        }
        if (deleted || data) {
            element.dispatchEvent(new win.InputEvent('input', {bubbles: true, composed: true, inputType: inputType}));
        }
    }-*/;

    /// Clears a form field (or content editable element) as `userEvent.clear` does: focuses it,
    /// selects all its content and deletes it, firing `beforeinput` and `input` (and `change`
    /// once it loses the focus).
    ///
    /// @param element The field.
    /// @throws PlayException If the field is disabled, read only or not editable, can't be
    ///                       focused or its content can't be selected, as user-event throws.
    static void clear(final Element element) {
        prepareDocument(element);
        if (!isEditable(element) || isUserEventDisabled(element)) {
            throw new PlayException("clear() is only supported on editable elements: " + describe(element));
        }
        focusElement(element);
        if (!hasFocus(element)) {
            throw new PlayException("The element to be cleared could not be focused: " + describe(element));
        }
        if (!selectAll(element)) {
            throw new PlayException("The element content to be cleared could not be selected: "
                                    + describe(element));
        }
        edit(element, "", "deleteContentBackward");
    }

    private static native void focusElement(Element element) /*-{
        @stroom.gwt.workbench.framework.client.play.Dom::helpers()().focusElement(element);
    }-*/;

    // Selects all the element's content, returning true if it is all selected
    private static native boolean selectAll(Element element) /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        helpers.selectAll(element);
        return helpers.isAllSelected(element);
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
                @stroom.gwt.workbench.framework.client.play.Dom::click(*)(option, 1, 0, 0, true);
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
