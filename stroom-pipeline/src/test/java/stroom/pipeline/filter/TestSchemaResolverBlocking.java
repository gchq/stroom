/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.ls.LSResourceResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the mechanism used by the {@link LSResourceResolverImpl} hardening: returning a non-null empty input
 * for a schema reference that is NOT in the store blocks the off-box fetch (XXE/SSRF), while references the
 * resolver serves (as the store does for every legitimate include/import, including the XML namespace's
 * {@code xml.xsd}) still compile.
 */
class TestSchemaResolverBlocking {

    private static final String NS = "http://example.com/test";
    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";

    private static final String INCLUDED_SCHEMA = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="%s" xmlns="%s" elementFormDefault="qualified">
              <xs:element name="child" type="xs:string"/>
            </xs:schema>""".formatted(NS, NS);

    // A minimal stand-in for the XML namespace's xml.xsd, defining xml:lang.
    private static final String XML_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="%s" xmlns:xml="%s">
              <xs:attribute name="lang" type="xs:string"/>
            </xs:schema>""".formatted(XML_NS, XML_NS);

    /**
     * Mirrors the production resolver: serve a known reference from the "store" on a hit; on a miss return an
     * empty input (never null, which would trigger an off-box fetch).
     */
    private static LSResourceResolver blockingResolver(final boolean serveFromStore) {
        return (type, namespaceURI, publicId, systemId, baseURI) -> {
            if (serveFromStore && systemId != null) {
                if (systemId.endsWith("included.xsd")) {
                    return new LSInputImpl(INCLUDED_SCHEMA, systemId, publicId, baseURI);
                }
                if (systemId.endsWith("xml.xsd")) {
                    return new LSInputImpl(XML_XSD, systemId, publicId, baseURI);
                }
            }
            return new LSInputImpl("", systemId, publicId, baseURI);
        };
    }

    private static SchemaFactory factory(final boolean serveFromStore) {
        final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setResourceResolver(blockingResolver(serveFromStore));
        return factory;
    }

    private static void compile(final SchemaFactory factory, final String schema) throws Exception {
        factory.newSchema(new StreamSource(new java.io.StringReader(schema)));
    }

    @Test
    void storeServedIncludeStillCompiles() {
        final String main = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="%s" xmlns="%s" elementFormDefault="qualified">
                  <xs:include schemaLocation="included.xsd"/>
                  <xs:element name="root" type="xs:string"/>
                </xs:schema>""".formatted(NS, NS);

        assertThatCode(() -> compile(factory(true), main)).doesNotThrowAnyException();
    }

    @Test
    void storeServedXmlNamespaceImportCompiles() {
        // A schema that uses xml:lang must import the XML namespace; when its xml.xsd comes from the store the
        // resolver serves it and compilation succeeds - so xml:lang schemas are not broken by the hardening.
        final String main = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xml="%s"
                           targetNamespace="%s" xmlns="%s" elementFormDefault="qualified">
                  <xs:import namespace="%s" schemaLocation="xml.xsd"/>
                  <xs:element name="root">
                    <xs:complexType>
                      <xs:attribute ref="xml:lang"/>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>""".formatted(XML_NS, NS, NS, XML_NS);

        assertThatCode(() -> compile(factory(true), main)).doesNotThrowAnyException();
    }

    @Test
    void externalIncludeIsBlocked(@TempDir final Path dir) throws Exception {
        final Path external = dir.resolve("evil.xsd");
        Files.writeString(external, INCLUDED_SCHEMA);
        final String main = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="%s" xmlns="%s" elementFormDefault="qualified">
                  <xs:include schemaLocation="%s"/>
                  <xs:element name="root" type="xs:string"/>
                </xs:schema>""".formatted(NS, NS, external.toUri());

        // The file exists and is valid, but the resolver denies it (not in the store) so it must NOT be
        // fetched - compilation fails rather than reading the file off-box.
        assertThatThrownBy(() -> compile(factory(false), main)).isInstanceOf(Exception.class);
    }
}
