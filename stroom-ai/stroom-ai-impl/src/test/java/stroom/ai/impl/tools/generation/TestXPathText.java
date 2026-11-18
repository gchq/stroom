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

import stroom.ai.impl.tools.AwkwardValues;
import stroom.ai.impl.tools.validation.XmlTrees;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestXPathText {

    @Test
    void aLiteralDoublesItsQuotes() {
        assertThat(XPathText.literal("o'neil")).isEqualTo("'o''neil'");
    }

    @Test
    void aLiteralHoldsNoCharacterAnXsltCantHold() {
        // Found testing awkward values (2026-11-18): a source type's value holding a form feed was written into its
        // group's XSLT as it was, and the XSLT was not well-formed XML.
        for (final AwkwardValues.Value value : AwkwardValues.ALL) {
            final String literal = XPathText.literal(value.text());
            final String sheet = "<xsl:stylesheet xmlns:xsl=\"" + XmlTrees.XSL + "\" version=\"3.0\"><xsl:variable "
                                 + "name=\"v\" select=\"" + AwkwardValues.xml11Text(literal)
                                 + "\"/></xsl:stylesheet>";
            assertThat(XmlTrees.tryParse(sheet)).as(value.why()).isPresent();
        }
        assertThat(XPathText.literal("page one\fpage two \u001b[31m")).isEqualTo("'page onepage two [31m'");
        assertThat(XPathText.literal("a\tb\nc\rd\u0085")).isEqualTo("'a\tb\nc\rd\u0085'");
    }

    @Test
    void unescapeBackslashesTakesOutTheEscapeOfAQuoteOrABackslashOnly() throws SaxonApiException {
        // Found testing awkward values (2026-11-18): key=value values quoted with backslash escapes kept them.
        final String expression = XPathText.transformExpression("unescape_backslashes", "$v");
        assertThat(evaluate(expression, "say \\\"hi\\\" from C:\\\\temp")).isEqualTo("say \"hi\" from C:\\temp");
        // A backslash before anything else is the source's own: kept.
        assertThat(evaluate(expression, "C:\\temp\\new")).isEqualTo("C:\\temp\\new");
        assertThat(FieldMapping.TRANSFORMS).contains("unescape_backslashes");
    }

    private static String evaluate(final String expression, final String value) throws SaxonApiException {
        final Processor processor = new Processor(false);
        final XPathCompiler compiler = processor.newXPathCompiler();
        compiler.declareVariable(new QName("v"));
        final XPathSelector selector = compiler.compile(expression).load();
        selector.setVariable(new QName("v"), new XdmAtomicValue(value));
        return selector.evaluateSingle().getStringValue();
    }
}
