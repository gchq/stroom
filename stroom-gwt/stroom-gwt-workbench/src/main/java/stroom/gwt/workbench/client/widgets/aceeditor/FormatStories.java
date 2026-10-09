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

package stroom.gwt.workbench.client.widgets.aceeditor;

import stroom.editor.client.model.XmlFormatter;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.PreElement;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [XmlFormatter], what the Ace editor's Format action runs on XML (GWT
/// `EditorViewImpl.formatAsIfXml` calls `new XmlFormatter().format(text)`).
///
/// Each story shows its cases (the name, the input, faded, and the formatted output), and its
/// play checks the formatter's results.
public final class FormatStories {

    private static final String NESTED = "<root><a><b>text</b></a></root>";
    private static final String NOT_XML = "a < b && c > d";
    private static final String IDEMPOTENT_SOURCE = "<root><a><b>text</b></a><!-- c --><d e=\"1\"/></root>";

    private FormatStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/AceEditor/format", FormatStories.class)
                .layout(StoryLayout.CENTERED)
                // XmlFormatter.format: indent by two spaces per nesting level, one element per line
                .story("FormatsNestedElements", context -> cases(
                        new Case("nested", NESTED, format(NESTED))))
                .withPlay(play -> play.expect("format(nested)", () -> format(NESTED)).toBe(String.join("\n",
                        "<root>",
                        "  <a>",
                        "    <b>text</b>",
                        "  </a>",
                        "</root>")))
                // Only data that looksLikeXml is formatted; anything else comes back unchanged
                .story("LeavesNonXmlAlone", context -> cases(
                        new Case("not xml", NOT_XML, format(NOT_XML))))
                .withPlay(play -> {
                    // Angle brackets, but no element pair or self-closing tag, so not XML
                    play.expect("looksLikeXml('a < b && c > d')", () -> XmlFormatter.looksLikeXml(NOT_XML))
                            .toBe(false);
                    play.expect("format('a < b && c > d')", () -> format(NOT_XML)).toBe(NOT_XML);
                    // A lone unclosed tag is not a pair either
                    play.expect("looksLikeXml('<notclosed')", () -> XmlFormatter.looksLikeXml("<notclosed"))
                            .toBe(false);
                    // But a self-closing element, an element pair or a declaration all are
                    play.expect("looksLikeXml('<a/>')", () -> XmlFormatter.looksLikeXml("<a/>")).toBe(true);
                    play.expect("looksLikeXml('<a>x</a>')", () -> XmlFormatter.looksLikeXml("<a>x</a>"))
                            .toBe(true);
                    play.expect("looksLikeXml('<?xml version=\"1.0\"?><a/>')",
                            () -> XmlFormatter.looksLikeXml("<?xml version=\"1.0\"?><a/>")).toBe(true);
                })
                // The quirks of Stroom's formatter
                .story("GwtQuirks", context -> cases(
                        quirk("empty element gets a space", "<a><b attr=\"1\"/></a>"),
                        quirk("data element keeps its whitespace", "<a><b>  spaced  </b></a>"),
                        quirk("attribute values are untouched", "<a b=\"  keep   me  \"/>"),
                        quirk("comment", "<a><!-- hi --><b/></a>")))
                .withPlay(play -> {
                    // A space is inserted before the `/` of an empty element
                    play.expect("format('<a><b attr=\"1\"/></a>')", () -> format("<a><b attr=\"1\"/></a>"))
                            .toContain("<b attr=\"1\" />");
                    // A data element (start, content, end) keeps its content's whitespace as it is
                    play.expect("format('<a><b>  spaced  </b></a>')", () -> format("<a><b>  spaced  </b></a>"))
                            .toContain("<b>  spaced  </b>");
                    // Attribute values are never reformatted, inner runs of spaces included
                    play.expect("format('<a b=\"  keep   me  \"/>')", () -> format("<a b=\"  keep   me  \"/>"))
                            .toContain("b=\"  keep   me  \"");
                    // Comments get an extra blank line before them
                    play.expect("format('<a><!-- hi --><b/></a>')", () -> format("<a><!-- hi --><b/></a>"))
                            .toContain("\n\n");
                    // Whitespace between attributes collapses to a single space
                    play.expect("format('<a   b=\"1\"     c=\"2\"/>')", () -> format("<a   b=\"1\"     c=\"2\"/>"))
                            .toContain("<a b=\"1\" c=\"2\" />");
                })
                // Formatting is idempotent: formatting its own output changes nothing
                .story("IsIdempotent", context -> {
                    final String once = format(IDEMPOTENT_SOURCE);
                    return cases(new Case("format(format(x)) === format(x)", once, format(once)));
                })
                .withPlay(play -> play.expect("format(format(x))", () -> format(format(IDEMPOTENT_SOURCE)))
                        .toBe(format(IDEMPOTENT_SOURCE)));
    }

    /// GWT `EditorViewImpl.formatAsIfXml(text)`.
    private static String format(final String input) {
        return new XmlFormatter().format(input);
    }

    private static Case quirk(final String name, final String input) {
        return new Case(name, input, format(input));
    }

    /// The cases, in a monospaced column, with a `<div>` per case holding its name (bold), its
    /// input (a faded `<pre>`) and its output (a `<pre>`).
    private static Widget cases(final Case... cases) {
        final FlowPanel panel = new FlowPanel();
        final Style style = panel.getElement().getStyle();
        style.setProperty("display", "flex");
        style.setProperty("flexDirection", "column");
        style.setProperty("gap", "12px");
        style.setProperty("fontFamily", "monospace");
        style.setProperty("fontSize", "12px");
        for (final Case aCase : cases) {
            final FlowPanel casePanel = new FlowPanel();
            final Label name = new Label(aCase.name());
            name.getElement().getStyle().setProperty("fontWeight", "700");
            casePanel.add(name);
            casePanel.getElement().appendChild(pre(aCase.input(), "0.6"));
            casePanel.getElement().appendChild(pre(aCase.output(), null));
            panel.add(casePanel);
        }
        return panel;
    }

    private static PreElement pre(final String text, final String opacity) {
        final PreElement pre = Document.get().createPreElement();
        pre.setInnerText(text);
        pre.getStyle().setProperty("margin", "0");
        if (opacity != null) {
            pre.getStyle().setProperty("opacity", opacity);
        }
        return pre;
    }

    // --------------------------------------------------------------------------------


    /// A case shown by a story.
    ///
    /// @param name   The name of the case.
    /// @param input  The formatter's input.
    /// @param output The formatter's output.
    private record Case(String name, String input, String output) {

    }
}
