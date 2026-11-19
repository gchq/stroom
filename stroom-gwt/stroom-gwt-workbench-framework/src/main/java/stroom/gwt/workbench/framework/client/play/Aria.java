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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/// The ARIA role rules that Testing Library's queries use, kept free of the DOM so they can be
/// tested on the JVM. [Dom] reads an element's tag and attributes and asks these methods.
///
/// There are two sets of rules, as in Testing Library:
///
/// * [#queryRole(String, String, Function)] is the role `getByRole` matches, from the
///   `aria-query` element/role table that `@testing-library/dom` uses, e.g. a `<section>` is
///   only a `region` if it has an accessible name, a `<header>` is always a `banner`;
/// * [#nameRole(String, String, int, Function)] is the role `dom-accessibility-api` uses while
///   computing an accessible name, which decides e.g. whether a name comes from the content.
final class Aria {

    // The implicit roles of elements that have one whatever their attributes, from aria-query
    private static final Map<String, String> QUERY_ROLES = new HashMap<>();
    // The implicit roles dom-accessibility-api gives elements by tag name
    private static final Map<String, String> NAME_ROLES = new HashMap<>();

    private static final List<String> NAME_FROM_CONTENT_ROLES = Arrays.asList(
            "button", "cell", "checkbox", "columnheader", "gridcell", "heading", "label", "legend", "link",
            "menuitem", "menuitemcheckbox", "menuitemradio", "option", "radio", "row", "rowheader", "switch",
            "tab", "tooltip", "treeitem");
    private static final List<String> RANGE_ROLES = Arrays.asList(
            "meter", "progressbar", "scrollbar", "slider", "spinbutton");
    private static final List<String> CONTROL_ROLES = Arrays.asList("button", "combobox", "listbox", "textbox");
    private static final List<String> PROHIBITS_NAMING_ROLES = Arrays.asList(
            "caption", "code", "deletion", "emphasis", "generic", "insertion", "paragraph", "presentation",
            "strong", "subscript", "superscript");
    // As the copy of dom-accessibility-api that Testing Library uses, which (unlike newer
    // versions) doesn't list aria-description
    private static final List<String> GLOBAL_ARIA_ATTRIBUTES = Arrays.asList(
            "aria-atomic", "aria-busy", "aria-controls", "aria-current", "aria-describedby", "aria-details",
            "aria-dropeffect", "aria-flowto", "aria-grabbed", "aria-hidden", "aria-keyshortcuts", "aria-label",
            "aria-labelledby", "aria-live", "aria-owns", "aria-relevant", "aria-roledescription");

    static {
        putAll(QUERY_ROLES, "generic", "b", "bdo", "body", "data", "div", "hgroup", "i", "pre", "q", "samp",
                "small", "span", "u");
        putAll(QUERY_ROLES, "group", "details", "fieldset", "optgroup", "address");
        putAll(QUERY_ROLES, "heading", "h1", "h2", "h3", "h4", "h5", "h6");
        putAll(QUERY_ROLES, "list", "menu", "ol", "ul");
        putAll(QUERY_ROLES, "rowgroup", "tbody", "tfoot", "thead");
        putAll(QUERY_ROLES, "term", "dfn", "dt");
        QUERY_ROLES.put("article", "article");
        QUERY_ROLES.put("aside", "complementary");
        QUERY_ROLES.put("blockquote", "blockquote");
        QUERY_ROLES.put("button", "button");
        QUERY_ROLES.put("caption", "caption");
        QUERY_ROLES.put("code", "code");
        QUERY_ROLES.put("datalist", "listbox");
        QUERY_ROLES.put("dd", "definition");
        QUERY_ROLES.put("del", "deletion");
        QUERY_ROLES.put("dialog", "dialog");
        QUERY_ROLES.put("em", "emphasis");
        QUERY_ROLES.put("figure", "figure");
        QUERY_ROLES.put("footer", "contentinfo");
        QUERY_ROLES.put("header", "banner");
        QUERY_ROLES.put("hr", "separator");
        QUERY_ROLES.put("html", "document");
        QUERY_ROLES.put("ins", "insertion");
        QUERY_ROLES.put("li", "listitem");
        QUERY_ROLES.put("main", "main");
        QUERY_ROLES.put("mark", "mark");
        QUERY_ROLES.put("math", "math");
        QUERY_ROLES.put("meter", "meter");
        QUERY_ROLES.put("nav", "navigation");
        QUERY_ROLES.put("option", "option");
        QUERY_ROLES.put("output", "status");
        QUERY_ROLES.put("p", "paragraph");
        QUERY_ROLES.put("progress", "progressbar");
        QUERY_ROLES.put("strong", "strong");
        QUERY_ROLES.put("sub", "subscript");
        QUERY_ROLES.put("sup", "superscript");
        QUERY_ROLES.put("table", "table");
        QUERY_ROLES.put("td", "cell");
        QUERY_ROLES.put("textarea", "textbox");
        QUERY_ROLES.put("time", "time");
        QUERY_ROLES.put("tr", "row");

        putAll(NAME_ROLES, "group", "details", "fieldset", "optgroup");
        putAll(NAME_ROLES, "heading", "h1", "h2", "h3", "h4", "h5", "h6");
        putAll(NAME_ROLES, "list", "menu", "ol", "ul");
        putAll(NAME_ROLES, "rowgroup", "tbody", "tfoot", "thead");
        putAll(NAME_ROLES, "button", "button", "summary");
        NAME_ROLES.put("article", "article");
        NAME_ROLES.put("aside", "complementary");
        NAME_ROLES.put("datalist", "listbox");
        NAME_ROLES.put("dd", "definition");
        NAME_ROLES.put("dialog", "dialog");
        NAME_ROLES.put("dt", "term");
        NAME_ROLES.put("figure", "figure");
        NAME_ROLES.put("form", "form");
        NAME_ROLES.put("footer", "contentinfo");
        NAME_ROLES.put("header", "banner");
        NAME_ROLES.put("hr", "separator");
        NAME_ROLES.put("html", "document");
        NAME_ROLES.put("legend", "legend");
        NAME_ROLES.put("li", "listitem");
        NAME_ROLES.put("math", "math");
        NAME_ROLES.put("main", "main");
        NAME_ROLES.put("nav", "navigation");
        NAME_ROLES.put("option", "option");
        NAME_ROLES.put("output", "status");
        NAME_ROLES.put("progress", "progressbar");
        NAME_ROLES.put("section", "region");
        NAME_ROLES.put("table", "table");
        NAME_ROLES.put("textarea", "textbox");
        NAME_ROLES.put("td", "cell");
        NAME_ROLES.put("th", "columnheader");
        NAME_ROLES.put("tr", "row");
    }

    private Aria() {
        // Static utility
    }

    private static void putAll(final Map<String, String> map, final String role, final String... tags) {
        for (final String tag : tags) {
            map.put(tag, role);
        }
    }

    /// The role Testing Library's `*ByRole` queries give an element: the first word of its `role`
    /// attribute, or its implicit role from `aria-query`'s element/role table.
    ///
    /// @param localName    The element's local name, e.g. `button`.
    /// @param typeProperty For an `<input>`, its `type` property (which is `text` if the attribute
    ///                     is missing or invalid); otherwise ignored.
    /// @param attributes   Gives the value of an attribute of the element, or null if it doesn't
    ///                     have it.
    /// @return The role, or null if it has none.
    static String queryRole(final String localName,
                            final String typeProperty,
                            final Function<String, String> attributes) {
        final String explicit = attributes.apply("role");
        if (explicit != null) {
            return firstWord(explicit);
        }
        switch (localName) {
            case "input":
                return inputQueryRole(typeProperty, attributes);
            case "select":
                return attributes.apply("multiple") != null || attributes.apply("size") != null
                        ? "listbox"
                        : "combobox";
            case "a":
            case "area":
                return isSet(attributes.apply("href"))
                        ? "link"
                        : "generic";
            case "img":
                return "".equals(attributes.apply("alt"))
                        ? "presentation"
                        : "img";
            case "section":
                return isSet(attributes.apply("aria-label")) || isSet(attributes.apply("aria-labelledby"))
                        ? "region"
                        : "generic";
            case "form":
                return isSet(attributes.apply("aria-label")) || isSet(attributes.apply("aria-labelledby"))
                       || isSet(attributes.apply("name"))
                        ? "form"
                        : null;
            case "th":
                // Testing Library matches the scope with a CSS attribute selector, which ignores
                // the case of an HTML attribute's value, e.g. scope="Row"
                final String scope = attributes.apply("scope");
                return "row".equalsIgnoreCase(scope) || "rowgroup".equalsIgnoreCase(scope)
                        ? "rowheader"
                        : "columnheader";
            default:
                return QUERY_ROLES.get(localName);
        }
    }

    private static String inputQueryRole(final String typeProperty, final Function<String, String> attributes) {
        final String type = typeProperty != null
                ? typeProperty.toLowerCase()
                : "text";
        final String list = attributes.apply("list");
        final boolean hasList = isSet(list);
        // In aria-query, a text-like input is a combobox if it has a non-empty list attribute and
        // a text box (or search box) only if it has none, so an empty list="" gives no role
        final boolean emptyList = list != null && list.isEmpty();
        switch (type) {
            case "button":
            case "image":
            case "reset":
            case "submit":
                return "button";
            case "checkbox":
            case "radio":
                return type;
            case "range":
                return "slider";
            case "number":
                return "spinbutton";
            case "search":
                if (emptyList) {
                    return null;
                }
                return hasList
                        ? "combobox"
                        : "searchbox";
            case "email":
            case "tel":
            case "text":
            case "url":
                if (emptyList) {
                    return null;
                }
                return hasList
                        ? "combobox"
                        : "textbox";
            default:
                // e.g. password, which has no role
                return null;
        }
    }

    /// The role `dom-accessibility-api` gives an element while it computes an accessible name.
    /// It differs a little from [#queryRole(String, String, Function)], e.g. a `<section>` is
    /// always a `region` and a link without an `href` has no role.
    ///
    /// @param localName    The element's local name, e.g. `button`.
    /// @param typeProperty For an `<input>`, its `type` property; otherwise ignored.
    /// @param sizeProperty For a `<select>`, its `size` property; otherwise ignored.
    /// @param attributes   Gives the value of an attribute of the element, or null if it doesn't
    ///                     have it.
    /// @return The role, or null if it has none.
    static String nameRole(final String localName,
                           final String typeProperty,
                           final int sizeProperty,
                           final Function<String, String> attributes) {
        final String roleAttribute = attributes.apply("role");
        final String explicit = roleAttribute != null && !roleAttribute.trim().isEmpty()
                ? firstWord(roleAttribute.trim())
                : null;
        if (explicit == null || "presentation".equals(explicit)) {
            final String implicit = implicitNameRole(localName, typeProperty, sizeProperty, attributes);
            if (!"presentation".equals(explicit) || hasGlobalAriaAttributes(attributes, implicit)) {
                return implicit;
            }
        }
        return explicit;
    }

    private static String implicitNameRole(final String localName,
                                           final String typeProperty,
                                           final int sizeProperty,
                                           final Function<String, String> attributes) {
        final String mapped = NAME_ROLES.get(localName);
        if (mapped != null) {
            return mapped;
        }
        switch (localName) {
            case "a":
            case "area":
            case "link":
                return attributes.apply("href") != null
                        ? "link"
                        : null;
            case "img":
                return "".equals(attributes.apply("alt")) && !hasGlobalAriaAttributes(attributes, "img")
                        ? "presentation"
                        : "img";
            case "input":
                return inputNameRole(typeProperty, attributes.apply("list") != null);
            case "select":
                return attributes.apply("multiple") != null || sizeProperty > 1
                        ? "listbox"
                        : "combobox";
            default:
                return null;
        }
    }

    private static String inputNameRole(final String typeProperty, final boolean hasList) {
        final String type = typeProperty != null
                ? typeProperty
                : "text";
        switch (type) {
            case "button":
            case "image":
            case "reset":
            case "submit":
                return "button";
            case "checkbox":
            case "radio":
                return type;
            case "range":
                return "slider";
            case "email":
            case "tel":
            case "text":
            case "url":
                return hasList
                        ? "combobox"
                        : "textbox";
            case "search":
                return hasList
                        ? "combobox"
                        : "searchbox";
            case "number":
                return "spinbutton";
            default:
                return null;
        }
    }

    /// @param attributes Gives the value of an attribute, or null if the element doesn't have it.
    /// @param role       The element's implicit role, whose prohibited attributes don't count.
    /// @return True if the element has a global ARIA attribute, which makes browsers ignore
    /// `role="presentation"`.
    static boolean hasGlobalAriaAttributes(final Function<String, String> attributes, final String role) {
        for (final String attribute : GLOBAL_ARIA_ATTRIBUTES) {
            if (attributes.apply(attribute) != null && !isProhibited(role, attribute)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProhibited(final String role, final String attribute) {
        if (role == null) {
            return false;
        }
        if ("generic".equals(role)) {
            return "aria-label".equals(attribute) || "aria-labelledby".equals(attribute)
                   || "aria-roledescription".equals(attribute);
        }
        return PROHIBITS_NAMING_ROLES.contains(role)
               && ("aria-label".equals(attribute) || "aria-labelledby".equals(attribute));
    }

    /// @param role A role, may be null.
    /// @return True if an element with the role takes its accessible name from its content, e.g.
    /// a button or link but not a dialog, group or list box.
    static boolean allowsNameFromContent(final String role) {
        return role != null && NAME_FROM_CONTENT_ROLES.contains(role);
    }

    /// @param role A role, may be null.
    /// @return True if the role is a range, e.g. a slider, whose name in another element's name
    /// is its value.
    static boolean isRange(final String role) {
        return role != null && RANGE_ROLES.contains(role);
    }

    /// @param role A role, may be null.
    /// @return True if the role is a control, e.g. a text box, whose name in another element's
    /// name is its value.
    static boolean isControl(final String role) {
        return role != null && (CONTROL_ROLES.contains(role) || RANGE_ROLES.contains(role));
    }

    /// @param role A role, may be null.
    /// @return True if an element with the role has no accessible name, e.g. `generic`.
    static boolean prohibitsNaming(final String role) {
        return role != null && PROHIBITS_NAMING_ROLES.contains(role);
    }

    /// @param role A role, may be null.
    /// @return True if the role is `none` or `presentation`.
    static boolean isPresentational(final String role) {
        return "none".equals(role) || "presentation".equals(role);
    }

    private static String firstWord(final String text) {
        final int space = text.indexOf(' ');
        return space >= 0
                ? text.substring(0, space)
                : text;
    }

    private static boolean isSet(final String value) {
        return value != null && !value.isEmpty();
    }
}
