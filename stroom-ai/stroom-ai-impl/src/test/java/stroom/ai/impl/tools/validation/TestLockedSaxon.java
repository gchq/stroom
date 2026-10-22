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

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The Saxon the tools evaluate XPath with reads nothing of the server's: no file, no URL, no external entity, no
/// environment variable. Ported from the Stroom MCP server's `tests/test_saxon.py`.
class TestLockedSaxon {

    @TempDir
    Path dir;

    @Test
    void testFilesAndUrlsAreRefusedByTheProcessor() throws IOException {
        final String secret = secretFile();
        for (final String expression : List.of("unparsed-text('" + secret + "')", "string(doc('" + secret + "'))",
                "string(json-doc('" + secret + "'))", "unparsed-text('http://127.0.0.1:9/x')",
                "count(collection('" + dir.toUri() + "'))")) {
            assertThatThrownBy(() -> evaluate(expression)).as(expression).isInstanceOf(SaxonApiException.class);
        }
    }

    @Test
    void testNoEnvironmentVariableIsSeen() throws SaxonApiException {
        assertThat(evaluate("string(environment-variable('PATH'))")).isEmpty();
        assertThat(evaluate("string(count(available-environment-variables()))")).isEqualTo("0");
    }

    @Test
    void testNoSystemPropertyIsSeen() throws SaxonApiException {
        // With Saxon's external functions allowed, XSLT's system-property() of a name in no namespace is read with
        // System.getProperty, so an XSLT could put the JVM's settings in its output or an error.
        assertThat(System.getProperty("java.home")).isNotEmpty();
        assertThat(transform("system-property('java.home')")).isEmpty();
        // The XSLT ones are still there.
        assertThat(transform("system-property('xsl:version')")).isEqualTo("3.0");
    }

    @Test
    void testIntegratedFunctionsStillWorkWithExternalFunctionsOff() throws SaxonApiException {
        // Stroom's functions are integrated ones, which Saxon trusts with external functions off.
        final Processor processor = LockedSaxon.newLocked();
        processor.registerExtensionFunction(new Greeting());
        final XPathSelector selector = processor.newXPathCompiler().compile("Q{stroom}greeting()").load();
        assertThat(selector.evaluateSingle().getStringValue()).isEqualTo("hello");
    }

    @Test
    void testASamplesExternalEntitiesAreNotLoaded() throws IOException, SaxonApiException {
        // A sample is log content: whoever the logs record wrote it.
        final String secret = secretFile();
        assertThat(LockedSaxon.parse("<!DOCTYPE r [<!ENTITY e SYSTEM \"" + secret + "\">]><r>&e;</r>")
                .getStringValue()).isEmpty();
        assertThat(LockedSaxon.parse("<!DOCTYPE r [<!ENTITY e \"inner\">]><r>&e;</r>").getStringValue())
                .isEqualTo("inner");
    }

    @Test
    void testAnXsltImportsNothingByHref() throws IOException {
        final String xslt = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                            + "<xsl:import href=\"" + secretFile() + "\"/></xsl:stylesheet>";
        assertThatThrownBy(() -> LockedSaxon.processor().newXsltCompiler()
                .compile(new StreamSource(new StringReader(xslt)))).isInstanceOf(SaxonApiException.class);
    }

    @Test
    void testRegexesAreReadAsXPathReadsThem() throws Exception {
        assertThat(LockedSaxon.regex("a\\sb", "").containsMatch("a b")).isTrue();
        // No-break space is not \s to XPath.
        assertThat(LockedSaxon.regex("a\\sb", "").containsMatch("a b")).isFalse();
        assertThat(LockedSaxon.regex("ABC", "i").containsMatch("xabc")).isTrue();
    }

    private String secretFile() throws IOException {
        final Path path = dir.resolve("secret.txt");
        Files.writeString(path, "the-secret");
        return path.toUri().toString();
    }

    // What an XSLT compiled and run by the locked-down processor writes for an expression.
    private static String transform(final String expression) throws SaxonApiException {
        final String xslt = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                            + "<xsl:template match=\"/\"><r><xsl:value-of select=\"" + expression + "\"/></r>"
                            + "</xsl:template></xsl:stylesheet>";
        final Xslt30Transformer transformer = LockedSaxon.processor().newXsltCompiler()
                .compile(new StreamSource(new StringReader(xslt))).load30();
        final XdmDestination destination = new XdmDestination();
        transformer.transform(new StreamSource(new StringReader("<a/>")), destination);
        return destination.getXdmNode().getStringValue();
    }

    private static String evaluate(final String expression) throws SaxonApiException {
        final XPathSelector selector = LockedSaxon.xpath(null).compile(expression).load();
        selector.setContextItem(LockedSaxon.parse("<a/>"));
        final XdmItem item = selector.evaluateSingle();
        return item == null
                ? null
                : item.getStringValue();
    }


    // --------------------------------------------------------------------------------


    // An integrated function in the stroom namespace, as Stroom's are.
    private static final class Greeting extends ExtensionFunctionDefinition {

        @Override
        public StructuredQName getFunctionQName() {
            return new StructuredQName("", "stroom", "greeting");
        }

        @Override
        public SequenceType[] getArgumentTypes() {
            return new SequenceType[0];
        }

        @Override
        public SequenceType getResultType(final SequenceType[] suppliedArgumentTypes) {
            return SequenceType.SINGLE_STRING;
        }

        @Override
        public ExtensionFunctionCall makeCallExpression() {
            return new ExtensionFunctionCall() {
                @Override
                public Sequence call(final XPathContext context, final Sequence[] arguments) {
                    return new StringValue("hello");
                }
            };
        }
    }
}
