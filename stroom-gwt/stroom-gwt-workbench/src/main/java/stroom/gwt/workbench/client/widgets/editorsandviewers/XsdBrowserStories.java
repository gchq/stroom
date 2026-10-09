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

import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.xsdbrowser.client.presenter.XSDBrowserPresenter;
import stroom.widget.xsdbrowser.client.view.XSDBrowserViewImpl;
import stroom.widget.xsdbrowser.client.view.XSDModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.SimpleEventBus;

/// Stories for Stroom's XSD browser ([XSDBrowserPresenter], the box diagram shown by the XML
/// schema editor).
public final class XsdBrowserStories {

    // Arg names
    private static final String XSD_TEXT = "xsdText";

    // A small but representative sample schema: named types, documentation, an enumeration
    // facet, a pattern facet and a ref
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
                .args(Args.of(XSD_TEXT, SAMPLE_XSD))
                // The box diagram browser: double-click to drill in, single-click to see the
                // documentation and constraints; Home / Back / Forward
                .story("Browse", XsdBrowserStories::fromArgs)
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
        final XSDBrowserPresenter presenter = new XSDBrowserPresenter(
                new SimpleEventBus(),
                new XSDBrowserViewImpl(GWT.create(XSDBrowserViewImpl.Binder.class)));
        final XSDModel model = new XSDModel();
        presenter.setModel(model);
        model.setContents(context.getArgs().getString(XSD_TEXT, ""));

        // A 480px high frame with a rounded border
        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("height", "480px");
        frame.getElement().getStyle().setProperty("border", "1px solid var(--border-color,#444)");
        frame.getElement().getStyle().setProperty("borderRadius", "4px");
        frame.getElement().getStyle().setProperty("overflow", "hidden");
        frame.getElement().getStyle().setProperty("position", "relative");
        frame.add(presenter.getWidget());
        return frame;
    }
}
