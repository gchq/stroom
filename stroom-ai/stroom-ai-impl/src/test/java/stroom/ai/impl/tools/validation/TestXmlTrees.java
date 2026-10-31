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

package stroom.ai.impl.tools.validation;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// XML read as namespace-aware DOM trees, safely.
class TestXmlTrees {

    private static final String XSLT = "<xsl:stylesheet xmlns:xsl=\"" + XmlTrees.XSL + "\" version=\"2.0\">"
                                       + "<xsl:template match=\"/\"><out><xsl:value-of select=\"a\"/></out>"
                                       + "</xsl:template></xsl:stylesheet>";

    @Test
    void textThatIsNotXmlIsNoTree() {
        assertThat(XmlTrees.tryParse(null)).isEmpty();
        assertThat(XmlTrees.tryParse("  ")).isEmpty();
        assertThat(XmlTrees.tryParse("<a>")).isEmpty();
        // A DOCTYPE is refused: nothing outside the text is loaded.
        assertThat(XmlTrees.tryParse("<!DOCTYPE a [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><a>&x;</a>")).isEmpty();
        assertThatThrownBy(() -> XmlTrees.parse("<a>")).isInstanceOf(XmlTrees.NotWellFormedException.class);
        assertThat(XmlTrees.tryParse(XSLT)).hasValueSatisfying(root ->
                assertThat(root.getLocalName()).isEqualTo("stylesheet"));
    }

    @Test
    void elementsAreFoundByNamespaceAndName() {
        final Element root = XmlTrees.tryParse(XSLT).orElseThrow();
        final Optional<Element> template = XmlTrees.child(root, XmlTrees.XSL, "template");
        assertThat(template).hasValueSatisfying(found -> assertThat(XmlTrees.is(found, XmlTrees.XSL, "template"))
                .isTrue());
        assertThat(XmlTrees.child(root, XmlTrees.XSL, "function")).isEmpty();
        assertThat(XmlTrees.child(root, null, "template")).isEmpty();
        // An element in no namespace is found with a null namespace.
        final Element out = XmlTrees.child(template.orElseThrow(), null, "out").orElseThrow();
        assertThat(XmlTrees.is(out, null, "out")).isTrue();
        assertThat(XmlTrees.is(out, XmlTrees.XSL, "out")).isFalse();
        assertThat(XmlTrees.named(root, null, "out")).containsExactly(out);
        assertThat(XmlTrees.named(root, XmlTrees.XSL, "*")).hasSize(3);
    }
}
