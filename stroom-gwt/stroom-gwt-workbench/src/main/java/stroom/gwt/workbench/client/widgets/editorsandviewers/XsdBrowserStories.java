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

package stroom.gwt.workbench.client.widgets.editorsandviewers;

import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.xsdbrowser.client.XSDModel;
import stroom.widget.xsdbrowser.client.XSDNode;
import stroom.widget.xsdbrowser.client.component.XSDBrowser;
import stroom.widget.xsdbrowser.client.component.XSDBrowserController;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's XSD browser ([XSDBrowserController], the box diagram shown by the XML
/// schema editor).
public final class XsdBrowserStories {

    // Arg names
    private static final String XSD_TEXT = "xsdText";
    private static final String OPEN = "open";

    // A small but representative sample schema: named types, documentation, an enumeration
    // facet, a pattern facet, a ref, a choice within a sequence and a type extending another
    private static final String SAMPLE_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://example.com/library"
                       xmlns="http://example.com/library"
                       elementFormDefault="qualified">

              <xs:annotation>
                <xs:documentation>A small sample library schema for the XSD browser.</xs:documentation>
              </xs:annotation>

              <xs:element name="library">
                <xs:annotation>
                  <xs:documentation>The root library element, containing books.</xs:documentation>
                </xs:annotation>
                <xs:complexType>
                  <xs:sequence>
                    <xs:element ref="book" minOccurs="0" maxOccurs="unbounded"/>
                  </xs:sequence>
                  <xs:attribute name="name" type="xs:string" use="required"/>
                </xs:complexType>
              </xs:element>

              <xs:element name="book">
                <xs:annotation>
                  <xs:documentation>A single book entry with a title, author and genre.</xs:documentation>
                </xs:annotation>
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="title" type="xs:string"/>
                    <xs:element name="author" type="AuthorType"/>
                    <xs:element name="genre" type="GenreType"/>
                    <xs:element name="isbn" type="IsbnType" minOccurs="0"/>
                    <xs:choice>
                      <xs:element name="price" type="xs:decimal"/>
                      <xs:element name="free" type="xs:boolean"/>
                    </xs:choice>
                  </xs:sequence>
                  <xs:attribute name="id" type="xs:integer" use="required"/>
                </xs:complexType>
              </xs:element>

              <xs:complexType name="AuthorType">
                <xs:annotation>
                  <xs:documentation>An author's given and family names.</xs:documentation>
                </xs:annotation>
                <xs:sequence>
                  <xs:element name="firstName" type="xs:string"/>
                  <xs:element name="lastName" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>

              <xs:complexType name="EditorType">
                <xs:annotation>
                  <xs:documentation>An author who also edits, for a publisher.</xs:documentation>
                </xs:annotation>
                <xs:complexContent>
                  <xs:extension base="AuthorType">
                    <xs:sequence>
                      <xs:element name="publisher" type="xs:string"/>
                    </xs:sequence>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>

              <xs:simpleType name="GenreType">
                <xs:annotation>
                  <xs:documentation>Permitted book genres.</xs:documentation>
                </xs:annotation>
                <xs:restriction base="xs:string">
                  <xs:enumeration value="fiction"/>
                  <xs:enumeration value="non-fiction"/>
                  <xs:enumeration value="reference"/>
                </xs:restriction>
              </xs:simpleType>

              <xs:simpleType name="IsbnType">
                <xs:annotation>
                  <xs:documentation>A thirteen-digit ISBN.</xs:documentation>
                </xs:annotation>
                <xs:restriction base="xs:string">
                  <xs:pattern value="[0-9]{13}"/>
                </xs:restriction>
              </xs:simpleType>

            </xs:schema>""";

    // The sample's documentation of the schema and of 'book', shown for what is selected
    private static final String SCHEMA_DOCUMENTATION = "A small sample library schema for the XSD browser.";
    private static final String BOOK_DOCUMENTATION = "A single book entry with a title, author and genre.";

    // A sequence and a choice with their occurrences given, shown over their images
    private static final String OCCURRENCES_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="shelf">
                <xs:complexType>
                  <xs:sequence minOccurs="0" maxOccurs="unbounded">
                    <xs:element name="label" type="xs:string"/>
                    <xs:choice minOccurs="1" maxOccurs="3">
                      <xs:element name="book" type="xs:string"/>
                      <xs:element name="magazine" type="xs:string"/>
                    </xs:choice>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>""";

    // Malformed: the <xs:element> start tag is never closed
    private static final String MALFORMED_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="oops">
            </xs:schema>""";

    private XsdBrowserStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Editors & Viewers/XsdBrowser", XsdBrowserStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .argType(ArgType.text(XSD_TEXT).description("The raw schema text (GWT XSDModel.setContents(...))."))
                .argType(ArgType.text(OPEN).description("The name of a global element or type to open "
                                                        + "(as a double-click does), or blank for the schema."))
                .args(Args.of(XSD_TEXT, SAMPLE_XSD, OPEN, ""))
                // The box diagram browser: double-click to drill in, single-click to see the
                // documentation and constraints; Home / Back / Forward
                .story("Browse", XsdBrowserStories::fromArgs)
                // An element: its box beside its type's, whose children (a sequence holding a
                // choice) are joined by tree lines, with its attributes above a separator
                .story("Element", XsdBrowserStories::fromArgs)
                .withArgs(Args.of(OPEN, "book"))
                // A type extending another: the base type's box above an arrow, above its own
                .story("ExtendedType", XsdBrowserStories::fromArgs)
                .withArgs(Args.of(OPEN, "EditorType"))
                // Home, Back and Forward are buttons in a named group, which the keyboard reaches
                // and presses (they were labels with click handlers, mouse only). Home is named
                // "Schema Home", as "Home" alone could be taken for Stroom's home page
                .story("KeyboardNavigation", XsdBrowserStories::fromArgs)
                .withArgs(Args.of(OPEN, "book"))
                .withPlay(play -> {
                    final Query documentation = play.querySelector(".xsd-browser__documentation");
                    play.waitFor(() -> play.expect(documentation).toHaveTextContent(BOOK_DOCUMENTATION));
                    final Play navigation = play.within(play.getByRole("group", "Schema navigation"));
                    final Query home = navigation.getByRole("button", "Schema Home");
                    final Query back = navigation.getByRole("button", "Back");
                    final Query forward = navigation.getByRole("button", "Forward");
                    // They are the first tab stops, in order
                    play.tab();
                    play.expect(home).toHaveFocus();
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(documentation).toHaveTextContent(SCHEMA_DOCUMENTATION));
                    play.tab();
                    play.expect(back).toHaveFocus();
                    play.tab();
                    play.expect(forward).toHaveFocus();
                    play.keyboard(" ");
                    play.waitFor(() -> play.expect(documentation).toHaveTextContent(BOOK_DOCUMENTATION));
                    play.tab(true);
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(documentation).toHaveTextContent(SCHEMA_DOCUMENTATION));
                })
                // A sequence's and a choice's occurrences are small labels over their images
                // (they lost their class in wave 8 and were shown as plain text below them)
                .story("Occurrences", XsdBrowserStories::fromArgs)
                .withArgs(Args.of(XSD_TEXT, OCCURRENCES_XSD, OPEN, "shelf"))
                .withPlay(play -> {
                    final Query sequence = play.getByText("0..*");
                    play.waitFor(() -> play.expect(sequence).toBeVisible());
                    play.expect(sequence).toHaveClass("xsd-browser__occurrence-label");
                    play.expect(sequence).toHaveStyle("position", "absolute");
                    play.expect(play.getByText("1..3")).toHaveClass("xsd-browser__occurrence-label");
                })
                // A type restricted to listed values: its constraints are headed "Enumeration"
                .story("EnumerationConstraint", XsdBrowserStories::fromArgs)
                .withArgs(Args.of(OPEN, "GenreType"))
                .withPlay(play -> {
                    final Query heading = play.querySelector(".xsd-browser__restriction-type");
                    play.waitFor(() -> play.expect(heading).toHaveTextContent("Enumeration"));
                    play.expect(play.getByText("non-fiction")).toBeVisible();
                })
                // A type restricted to a pattern: its constraints are headed "Pattern" (they were
                // headed "Enumeration", as every restriction's were)
                .story("PatternConstraint", XsdBrowserStories::fromArgs)
                .withArgs(Args.of(OPEN, "IsbnType"))
                .withPlay(play -> {
                    final Query heading = play.querySelector(".xsd-browser__restriction-type");
                    play.waitFor(() -> play.expect(heading).toHaveTextContent("Pattern"));
                    play.expect(play.getByText("[0-9]{13}")).toBeVisible();
                })
                // A malformed schema: the parse error is shown, not the partial schema (Chrome's
                // XML parser recovers, and GWT's XSDModel once showed what it recovered)
                .story("ParseError", XsdBrowserStories::fromArgs)
                .withArgs(Args.of(XSD_TEXT, MALFORMED_XSD))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText(TextMatch.containingIgnoreCase("error on line")))
                            .toBeInTheDocument());
                    play.expect(play.queryByText("oops")).toBeNull();
                });
    }

    private static Widget fromArgs(final StoryContext context) {
        final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.none());
        final XSDBrowserController browser = harness.own(new XSDBrowserController(harness.getEventBus(),
                new XSDBrowser()));
        final XSDModel model = new XSDModel();
        browser.setModel(model);
        model.setContents(context.getArgs().getString(XSD_TEXT, ""));
        open(model, context.getArgs().getString(OPEN, ""));

        // A 480px high frame with a rounded border
        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("height", "480px");
        frame.getElement().getStyle().setProperty("border", "1px solid var(--border-color,#444)");
        frame.getElement().getStyle().setProperty("borderRadius", "4px");
        frame.getElement().getStyle().setProperty("overflow", "hidden");
        frame.getElement().getStyle().setProperty("position", "relative");
        frame.add(browser);
        harness.add(frame);
        return harness.asWidget();
    }

    // Opens the global element or type with this name, if there is one
    private static void open(final XSDModel model, final String name) {
        if (name.isEmpty() || model.getGlobalElementMap() == null) {
            return;
        }
        XSDNode node = model.getGlobalElementMap().get(name);
        if (node == null) {
            node = model.getGlobalTypeMap().get(name);
        }
        if (node != null) {
            model.setSelectedItem(node, true);
        }
    }
}
