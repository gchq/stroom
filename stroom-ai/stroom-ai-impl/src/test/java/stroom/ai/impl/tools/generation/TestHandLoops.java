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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.validation.XmlTrees;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A loop written by hand of one element per value: which loops are, the item variable's name, and each read of the
/// current value put as the variable's, the element itself left as it was.
class TestHandLoops {

    private static final XPathTreeParser PARSER = XPathTreeParser.parserFor(Map.of("stroom", "stroom"));

    @Test
    void aLoopWritingOneElementWithElementsOfItsOwnIsOne() {
        assertThat(HandLoops.element(loop("<Email><From><EmailAddress><xsl:value-of select=\".\"/></EmailAddress>"
                                          + "</From></Email>"))).isNotNull()
                .satisfies(element -> assertThat(element.localName()).isEqualTo("Email"));
        // A leaf, a Data, two elements, a guard around it, a loop or a template call inside: not one.
        assertThat(HandLoops.element(loop("<EmailAddress><xsl:value-of select=\".\"/></EmailAddress>"))).isNull();
        assertThat(HandLoops.element(loop("<Data Name=\"to\" Value=\"{.}\"/>"))).isNull();
        assertThat(HandLoops.element(loop("<User><Id>a</Id></User><User><Id>b</Id></User>"))).isNull();
        assertThat(HandLoops.element(loop("<xsl:if test=\".\"><User><Id>a</Id></User></xsl:if>"))).isNull();
        assertThat(HandLoops.element(loop("<User><xsl:for-each select=\"x\"><Id>a</Id></xsl:for-each></User>")))
                .isNull();
        assertThat(HandLoops.element(loop("<User><xsl:call-template name=\"t\"/><Id>a</Id></User>"))).isNull();
    }

    @Test
    void eachReadOfTheCurrentValueIsPutAsTheVariablesAndTheElementIsLeftAsItWas() {
        final XmlElement loop = loop("<User><Id><xsl:value-of select=\"upper-case(.)\"/></Id>"
                                     + "<Domain><xsl:value-of select=\"replace(., '[^@]*@', '')[normalize-space(.)]"
                                     + "\"/></Domain><Name><xsl:value-of select=\"tokenize(., '@') ! upper-case(.)\""
                                     + "/></Name><Data Name=\"at\" Value=\"{.}@{{x}}\"/></User>");
        final XmlElement element = HandLoops.element(loop);

        final XmlElement itemised = HandLoops.itemise(element, "item", PARSER);

        assertThat(itemised).isNotNull();
        final String written = XmlSerialiser.toXml(itemised);
        // In a predicate, or after a simple map's !, the dot is another focus's.
        assertThat(written).contains("upper-case($item)", "replace($item, '[^@]*@', '')[normalize-space(.)]",
                "tokenize($item, '@') ! upper-case(.)", "Value=\"{$item}@{{x}}\"");
        assertThat(XmlSerialiser.toXml(element)).contains("upper-case(.)").doesNotContain("$item");
    }

    @Test
    void aLoopReadingBelowItsValueIsNotItemised() {
        for (final String select : new String[]{"@value", "Address/@user", "child::x", "..", "*", "text()",
                "concat(., @host)"}) {
            final XmlElement element = HandLoops.element(loop("<User><Id><xsl:value-of select=\"" + select
                                                              + "\"/></Id></User>"));
            assertThat(HandLoops.itemise(element, "item", PARSER)).as(select).isNull();
        }
        // A path from the root, or a variable's, isn't the value's: read as it is.
        final XmlElement element = HandLoops.element(loop("<User><Id><xsl:value-of select=\"concat($sender, "
                                                          + "/records/@id, .)\"/></Id></User>"));
        assertThat(XmlSerialiser.toXml(HandLoops.itemise(element, "item", PARSER)))
                .contains("concat($sender, /records/@id, $item)");
    }

    @Test
    void theVariablesNameIsOneUsedNowhereThere() {
        assertThat(HandLoops.variableFor(loop("<User><Id><xsl:value-of select=\".\"/></Id></User>"), Map.of()))
                .isEqualTo("item");
        assertThat(HandLoops.variableFor(loop("<User><Id><xsl:value-of select=\"$item\"/></Id></User>"),
                Map.of("value", "x"))).isEqualTo("each");
    }

    private static XmlElement loop(final String body) {
        return XmlParser.parse("<xsl:for-each xmlns:xsl=\"" + XmlTrees.XSL + "\" xmlns=\"" + XsltGenerator.EVT
                               + "\" select=\"tokenize(data[@name='to']/@value, ',')\">" + body
                               + "</xsl:for-each>");
    }
}
