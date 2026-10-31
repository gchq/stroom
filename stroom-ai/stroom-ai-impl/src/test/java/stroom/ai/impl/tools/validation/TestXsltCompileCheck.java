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

import stroom.ai.impl.tools.validation.XsltCompileCheck.Compiled;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// An XSLT is compiled as Stroom compiles it: with Stroom's functions and its imports read by name. Ported from
/// the Stroom MCP server's `tests/test_saxon.py`
/// (`test_an_xslt_is_compiled_with_stroom_and_imported_functions_and_templates_stood_in_for`), where the
/// functions and imports were stood in for; here they are Stroom's own.
class TestXsltCompileCheck {

    private static final String GEO = "http://example.com/geo";
    private static final String SHEET = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                        + "version=\"3.0\" xmlns:stroom=\"stroom\" xmlns:geo=\"" + GEO + "\">"
                                        + "{imports}<xsl:template match=\"/\"><a>{body}</a></xsl:template>"
                                        + "</xsl:stylesheet>";
    private static final Map<String, String> IMPORTS = Map.of(
            "GeoIP-V1", "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\" "
                        + "xmlns:geo=\"" + GEO + "\"><xsl:function name=\"geo:document\"><xsl:param name=\"ip\"/>"
                        + "<xsl:sequence select=\"string($ip)\"/></xsl:function>"
                        + "<xsl:variable name=\"kv\" select=\"1\"/></xsl:stylesheet>",
            "Common-Event-V1", "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
                               + "<xsl:template name=\"eventSource\"><xsl:param name=\"ip\"/><b/></xsl:template>"
                               + "</xsl:stylesheet>");
    private static final String IMPORTED = "<xsl:import href=\"GeoIP-V1\"/><xsl:import href=\"Common-Event-V1\"/>";

    @TempDir
    Path dir;

    private final XsltCompileCheck check = new XsltCompileCheck(() -> new Imports(), TestXsltCompileCheck::stroom);

    @Test
    void testAnXsltIsCompiledWithStroomsFunctionsAndItsImports() {
        final String fine = "<xsl:value-of select=\"stroom:format-date('x', 'yyyy', 'UTC'), stroom:record-no(), "
                            + "geo:document(.)\"/><xsl:call-template name=\"eventSource\">"
                            + "<xsl:with-param name=\"ip\" select=\"1\"/></xsl:call-template>";
        final Compiled compiled = check.compile(sheet(IMPORTED, fine));
        assertThat(compiled.errors()).isEmpty();
        assertThat(compiled.ok()).isTrue();
        // An import's global variable is its own: declared.
        assertThat(check.compile(sheet(IMPORTED, "<xsl:value-of select=\"$kv\"/>")).errors()).isEmpty();
    }

    @Test
    void testTheXsltsOwnMistakesAreErrors() {
        // Seen: an XSLT saved with "Variable kv has not been declared", which only stepping found.
        final Compiled compiled = check.compile(sheet("", "<xsl:value-of select=\"$kv\"/>"
                                                          + "<xsl:value-of select=\"nosuch(1)\"/>"));
        assertThat(compiled.errors()).hasSize(2);
        assertThat(compiled.errors().get(0)).startsWith("XPST0008 ").contains("kv").contains("not been declared");
        assertThat(compiled.errors().get(1)).startsWith("XPST0017 ").contains("nosuch");
    }

    @Test
    void testAHigherOrderFunctionIsAnErrorSaidInStroomsTermsAndItsAlternativesCompile() {
        // Stroom runs Saxon-HE 9.9: Saxon says such a function needs Saxon-PE, which Stroom doesn't have.
        final Compiled folded = check.compile(sheet("", "<xsl:value-of select=\"fold-left((1, 2), 0, "
                                                        + "function($a, $b) { $a + $b })\"/>"));
        assertThat(folded.errors()).isNotEmpty()
                .anySatisfy(error -> assertThat(error).contains("Saxon-PE")
                        .contains("Saxon-HE 9.9) has no higher-order functions").contains("xsl:for-each-group"));
        // What to use instead compiles: users made distinct by their login name, the first mention of each kept.
        final String distinct = "<xsl:for-each-group select=\"('a', 'b', 'a')\" group-by=\".\">"
                                + "<u><xsl:value-of select=\"current-grouping-key()\"/></u></xsl:for-each-group>"
                                + "<xsl:iterate select=\"('a', 'b', 'a')\"><xsl:param name=\"seen\" select=\"()\"/>"
                                + "<xsl:if test=\"not(. = $seen)\"><u><xsl:value-of select=\".\"/></u></xsl:if>"
                                + "<xsl:next-iteration><xsl:with-param name=\"seen\" select=\"($seen, .)\"/>"
                                + "</xsl:next-iteration></xsl:iterate>";
        assertThat(check.compile(sheet("", distinct)).errors()).isEmpty();
    }

    @Test
    void testAStroomFunctionThatDoesNotExistIsAnError() {
        final Compiled compiled = check.compile(sheet("", "<xsl:value-of select=\"stroom:lookp('m', 'k')\"/>"));
        assertThat(compiled.errors()).singleElement().asString().startsWith("XPST0017 ").contains("lookp");
    }

    @Test
    void testAnImportThatCantBeReadIsTheError() {
        // The Stroom MCP server stood in for imports, and said a variable nothing declared was fine only if an
        // import declared it; here the import is read, so one that can't be is the error.
        final Compiled compiled = check.compile(sheet("<xsl:import href=\"Missing-V1\"/>",
                "<xsl:value-of select=\"$kv\"/>"));
        assertThat(compiled.errors()).containsExactly("Resource not found: \"Missing-V1\"");
    }

    @Test
    void testAnXsltThatReadsTheEnvironmentIsNotCompiled() {
        // Compiling runs its static expressions, so it is refused before Saxon sees it, whatever the caller checked.
        final XsltCompileCheck.Compiled compiled = check.compile(sheet("",
                "<xsl:value-of select=\"environment-variable('HOME')\"/>"));

        assertThat(compiled.ok()).isFalse();
        assertThat(compiled.errors()).singleElement().asString()
                .startsWith("not compiled: ")
                .contains("environment-variable()");
    }

    @Test
    void testItCompilesLockedDownAsTheXPathEvaluationIs() throws IOException, SaxonApiException {
        // Compiling runs static expressions (use-when), so whatever XsltSafety might miss reads nothing: no system
        // property, environment variable or file, while Stroom's functions and imports by name still work.
        final List<Configuration> used = new ArrayList<>();
        final XsltCompileCheck locked = new XsltCompileCheck(() -> new Imports(), config -> {
            used.add(config);
            stroom(config);
        });
        assertThat(locked.compile(sheet(IMPORTED, "<xsl:value-of select=\"stroom:record-no(), geo:document(.)\"/>"))
                .errors()).isEmpty();

        final Configuration config = used.getFirst();
        assertThat(config.getBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS)).isFalse();
        final Path secret = dir.resolve("secret.txt");
        Files.writeString(secret, "the-secret");
        final Processor processor = new Processor(config);
        assertThatThrownBy(() -> processor.newXPathCompiler()
                .evaluate("unparsed-text('" + secret.toUri() + "')", null))
                .isInstanceOf(SaxonApiException.class);
        assertThat(processor.newXPathCompiler().evaluate("string(environment-variable('PATH'))", null)
                .toString()).isEmpty();
    }

    @Test
    void testXmlThatIsNotWellFormedSaysSo() {
        assertThat(check.compile("<xsl:stylesheet").errors()).singleElement().asString()
                .startsWith("not well-formed XML: ");
    }

    private static String sheet(final String imports, final String body) {
        return SHEET.replace("{imports}", imports).replace("{body}", body);
    }

    // Stand-ins for two of Stroom's functions, registered as its function library registers them.
    private static void stroom(final Configuration config) {
        config.registerExtensionFunction(new Function("format-date", 1, 5));
        config.registerExtensionFunction(new Function("record-no", 0, 0));
        config.registerExtensionFunction(new Function("lookup", 2, 5));
    }


    // --------------------------------------------------------------------------------


    // Reads imports by name, as Stroom's resolver reads XSLT documents.
    private static final class Imports implements URIResolver {

        @Override
        public Source resolve(final String href, final String base)
                throws TransformerException {
            final String xslt = IMPORTS.get(href);
            if (xslt == null) {
                throw new TransformerException(new IOException("Resource not found: \"" + href + "\""));
            }
            return new StreamSource(new StringReader(xslt));
        }
    }

    // A function in the stroom namespace that gives nothing.
    private static final class Function extends ExtensionFunctionDefinition {

        private final String name;
        private final int min;
        private final int max;

        private Function(final String name, final int min, final int max) {
            this.name = name;
            this.min = min;
            this.max = max;
        }

        @Override
        public StructuredQName getFunctionQName() {
            return new StructuredQName("", "stroom", name);
        }

        @Override
        public int getMinimumNumberOfArguments() {
            return min;
        }

        @Override
        public int getMaximumNumberOfArguments() {
            return max;
        }

        @Override
        public SequenceType[] getArgumentTypes() {
            final SequenceType[] types = new SequenceType[Math.max(1, max)];
            Arrays.fill(types, SequenceType.OPTIONAL_ATOMIC);
            return types;
        }

        @Override
        public SequenceType getResultType(final SequenceType[] suppliedArgumentTypes) {
            return SequenceType.OPTIONAL_STRING;
        }

        @Override
        public ExtensionFunctionCall makeCallExpression() {
            return new ExtensionFunctionCall() {
                @Override
                public Sequence call(final XPathContext context, final Sequence[] arguments) {
                    return EmptySequence.getInstance();
                }
            };
        }
    }
}
