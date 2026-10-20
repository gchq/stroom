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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestAria {

    @Test
    void testQueryRole_explicit() {
        assertThat(queryRole("div", null, "role", "button")).isEqualTo("button");
        // Only the first role counts, as in Testing Library
        assertThat(queryRole("div", null, "role", "switch checkbox")).isEqualTo("switch");
        assertThat(queryRole("button", null, "role", "tab")).isEqualTo("tab");
    }

    @Test
    void testQueryRole_implicit() {
        assertThat(queryRole("button", null)).isEqualTo("button");
        assertThat(queryRole("a", null, "href", "#x")).isEqualTo("link");
        assertThat(queryRole("a", null)).isEqualTo("generic");
        assertThat(queryRole("h3", null)).isEqualTo("heading");
        assertThat(queryRole("td", null)).isEqualTo("cell");
        assertThat(queryRole("th", null)).isEqualTo("columnheader");
        assertThat(queryRole("th", null, "scope", "row")).isEqualTo("rowheader");
        // Regression: the scope's case mattered, unlike in Testing Library's attribute selector
        assertThat(queryRole("th", null, "scope", "Row")).isEqualTo("rowheader");
        assertThat(queryRole("th", null, "scope", "ROWGROUP")).isEqualTo("rowheader");
        assertThat(queryRole("th", null, "scope", "Col")).isEqualTo("columnheader");
        assertThat(queryRole("li", null)).isEqualTo("listitem");
        assertThat(queryRole("div", null)).isEqualTo("generic");
        assertThat(queryRole("img", null)).isEqualTo("img");
        assertThat(queryRole("img", null, "alt", "")).isEqualTo("presentation");
        assertThat(queryRole("textarea", null)).isEqualTo("textbox");
        assertThat(queryRole("dialog", null)).isEqualTo("dialog");
        assertThat(queryRole("svg", null)).isNull();
    }

    @Test
    void testQueryRole_contextual() {
        // Regression: these roles were given whatever the element's attributes
        assertThat(queryRole("section", null)).isEqualTo("generic");
        assertThat(queryRole("section", null, "aria-label", "Results")).isEqualTo("region");
        assertThat(queryRole("form", null)).isNull();
        assertThat(queryRole("form", null, "name", "login")).isEqualTo("form");
        assertThat(queryRole("header", null)).isEqualTo("banner");
        assertThat(queryRole("footer", null)).isEqualTo("contentinfo");
        // Regression: meter and output had no role
        assertThat(queryRole("meter", null)).isEqualTo("meter");
        assertThat(queryRole("output", null)).isEqualTo("status");
    }

    @Test
    void testQueryRole_inputs() {
        assertThat(queryRole("input", "text")).isEqualTo("textbox");
        assertThat(queryRole("input", "email")).isEqualTo("textbox");
        assertThat(queryRole("input", "text", "list", "options")).isEqualTo("combobox");
        // Regression: an empty list attribute gave a text box, but in aria-query (and so Testing
        // Library) such an input has no role
        assertThat(queryRole("input", "text", "list", "")).isNull();
        assertThat(queryRole("input", "email", "list", "")).isNull();
        assertThat(queryRole("input", "search", "list", "")).isNull();
        assertThat(queryRole("input", "number", "list", "")).isEqualTo("spinbutton");
        assertThat(queryRole("input", "search")).isEqualTo("searchbox");
        assertThat(queryRole("input", "number")).isEqualTo("spinbutton");
        assertThat(queryRole("input", "range")).isEqualTo("slider");
        assertThat(queryRole("input", "checkbox")).isEqualTo("checkbox");
        assertThat(queryRole("input", "radio")).isEqualTo("radio");
        assertThat(queryRole("input", "submit")).isEqualTo("button");
        // Regression: a password field was a textbox; it has no role
        assertThat(queryRole("input", "password")).isNull();
        assertThat(queryRole("input", "hidden")).isNull();
        assertThat(queryRole("select", null)).isEqualTo("combobox");
        assertThat(queryRole("select", null, "multiple", "")).isEqualTo("listbox");
        assertThat(queryRole("select", null, "size", "4")).isEqualTo("listbox");
    }

    @Test
    void testNameRole() {
        assertThat(nameRole("section", null, 0)).isEqualTo("region");
        assertThat(nameRole("a", null, 0)).isNull();
        assertThat(nameRole("a", null, 0, "href", "#")).isEqualTo("link");
        assertThat(nameRole("summary", null, 0)).isEqualTo("button");
        assertThat(nameRole("legend", null, 0)).isEqualTo("legend");
        assertThat(nameRole("select", null, 4)).isEqualTo("listbox");
        assertThat(nameRole("select", null, 1)).isEqualTo("combobox");
        assertThat(nameRole("div", null, 0)).isNull();
        assertThat(nameRole("div", null, 0, "role", "dialog")).isEqualTo("dialog");
        // role="presentation" is ignored if the element has a global ARIA attribute
        assertThat(nameRole("button", null, 0, "role", "presentation")).isEqualTo("presentation");
        assertThat(nameRole("button", null, 0, "role", "presentation", "aria-label", "x")).isEqualTo("button");
    }

    @Test
    void testNameFromContent() {
        // Regression: every role took its name from its content, e.g. a dialog's name was all
        // its text
        assertThat(Aria.allowsNameFromContent("button")).isTrue();
        assertThat(Aria.allowsNameFromContent("link")).isTrue();
        assertThat(Aria.allowsNameFromContent("tab")).isTrue();
        assertThat(Aria.allowsNameFromContent("option")).isTrue();
        assertThat(Aria.allowsNameFromContent("menuitem")).isTrue();
        assertThat(Aria.allowsNameFromContent("heading")).isTrue();
        assertThat(Aria.allowsNameFromContent("cell")).isTrue();
        assertThat(Aria.allowsNameFromContent("dialog")).isFalse();
        assertThat(Aria.allowsNameFromContent("group")).isFalse();
        assertThat(Aria.allowsNameFromContent("listbox")).isFalse();
        assertThat(Aria.allowsNameFromContent("textbox")).isFalse();
        assertThat(Aria.allowsNameFromContent("tablist")).isFalse();
        assertThat(Aria.allowsNameFromContent("meter")).isFalse();
        assertThat(Aria.allowsNameFromContent("combobox")).isFalse();
        assertThat(Aria.allowsNameFromContent("region")).isFalse();
        assertThat(Aria.allowsNameFromContent(null)).isFalse();
    }

    @Test
    void testRoleKinds() {
        assertThat(Aria.isControl("textbox")).isTrue();
        assertThat(Aria.isControl("slider")).isTrue();
        assertThat(Aria.isControl("link")).isFalse();
        assertThat(Aria.isRange("spinbutton")).isTrue();
        assertThat(Aria.isRange("button")).isFalse();
        assertThat(Aria.prohibitsNaming("generic")).isTrue();
        assertThat(Aria.prohibitsNaming("button")).isFalse();
        assertThat(Aria.isPresentational("none")).isTrue();
        assertThat(Aria.isPresentational("img")).isFalse();
    }

    @Test
    void testAriaDescriptionIsntAGlobalAttribute() {
        // Regression: aria-description made role="presentation" be ignored, but it isn't in the
        // global attributes of the dom-accessibility-api that Testing Library uses
        assertThat(nameRole("button", null, 0, "role", "presentation", "aria-description", "d"))
                .isEqualTo("presentation");
        assertThat(nameRole("button", null, 0, "role", "presentation", "aria-describedby", "d"))
                .isEqualTo("button");
    }

    private static String queryRole(final String localName, final String type, final String... attributes) {
        return Aria.queryRole(localName, type, attributes(attributes)::get);
    }

    private static String nameRole(final String localName,
                                   final String type,
                                   final int size,
                                   final String... attributes) {
        return Aria.nameRole(localName, type, size, attributes(attributes)::get);
    }

    private static Map<String, String> attributes(final String... namesAndValues) {
        final Map<String, String> map = new HashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            map.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return map;
    }
}
