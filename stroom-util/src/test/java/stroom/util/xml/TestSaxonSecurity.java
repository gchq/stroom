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

package stroom.util.xml;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.jaxp.TemplatesImpl;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.xpath.XPathEvaluator;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSaxonSecurity {

    private static final String FILE_CONTENT = "file-content";
    private static final String ALLOWED_TEXT = "allowed-text-value";
    private static final String REFUSED = "not allowed";

    // Only checked in files that use Saxon, so jOOQ's Processor table and Hadoop's Configuration don't match.
    private static final Pattern USES_SAXON = Pattern.compile("net\\.sf\\.saxon\\.");

    // What is not allowed, and the one file allowed to do each.
    private static final Map<Pattern, String> FORBIDDEN_CREATION = Map.of(
            Pattern.compile("new\\s+(net\\.sf\\.saxon\\.s9api\\.)?Processor\\s*\\("), "SaxonSecurity.java",
            Pattern.compile("new\\s+(net\\.sf\\.saxon\\.)?Configuration\\s*\\("), "SaxonSecurity.java",
            Pattern.compile("Configuration\\s*\\.\\s*(newConfiguration|makeLicensedConfiguration|readConfiguration)"
                            + "\\s*\\("), "SaxonSecurity.java",
            Pattern.compile("new\\s+(net\\.sf\\.saxon\\.xpath\\.)?XPathEvaluator\\s*\\(\\s*\\)"), "SaxonSecurity.java",
            // Each of these makes its own default configuration when given none.
            Pattern.compile("new\\s+(net\\.sf\\.saxon\\.sxpath\\.)?IndependentContext\\s*\\(\\s*\\)"),
            "SaxonSecurity.java",
            Pattern.compile("new\\s+(net\\.sf\\.saxon\\.dom\\.)?DocumentBuilder(Factory)?Impl\\s*\\("),
            "SaxonSecurity.java",
            Pattern.compile("new\\s+(net\\.sf\\.saxon\\.)?(TransformerFactoryImpl|BasicTransformerFactory"
                            + "|xpath\\.XPathFactoryImpl|XPathFactoryImpl)\\s*\\("), "SaxonSecurity.java",
            Pattern.compile("(TransformerFactory|XPathFactory)\\s*\\.\\s*newInstance\\s*\\("), "SaxonSecurity.java");

    private static final Set<String> SKIPPED_DIRS = Set.of("build", "node_modules", "out", "bin");

    @TempDir
    Path tempDir;

    private Path allowedDir;
    private Path dataFile;
    private String allowedUri;
    private String dataUri;

    @BeforeEach
    void setUp() throws IOException {
        allowedDir = Files.createDirectories(tempDir.resolve("allowed"));
        Files.writeString(allowedDir.resolve("a.txt"), ALLOWED_TEXT);
        Files.writeString(allowedDir.resolve("a.xml"), "<a>" + ALLOWED_TEXT + "</a>");
        dataFile = tempDir.resolve("data.txt");
        Files.writeString(dataFile, FILE_CONTENT);
        Files.writeString(tempDir.resolve("data.xml"), "<s>" + FILE_CONTENT + "</s>");
        allowedUri = allowedDir.toUri().toString();
        dataUri = dataFile.toUri().toString();
    }

    // Each XPath reads something outside the policy. The XSLT, s9api XPath and JAXP XPath routes are all tested
    // because Stroom uses all three.
    static Stream<String> refusedXPaths() {
        return Stream.of(
                "unparsed-text('DATA_URI')",
                "string-join(unparsed-text-lines('DATA_URI'), '')",
                "string(doc('DATA_XML_URI'))",
                "string(count(uri-collection('TEMP_DIR_URI')))",
                "string(count(collection('TEMP_DIR_URI')))",
                "environment-variable('PATH')",
                "string-join(available-environment-variables(), ',')",
                "unparsed-text('http://127.0.0.1:1/')",
                "string(parse-xml('<!DOCTYPE a [<!ENTITY e SYSTEM \"DATA_URI\">]><a>&e;</a>'))",
                "string(parse-xml('<!DOCTYPE a SYSTEM \"http://127.0.0.1:1/a.dtd\"><a/>'))",
                "string(Q{http://saxon.sf.net/}doc('DATA_XML_URI', map{}))");
    }

    @ParameterizedTest
    @MethodSource("refusedXPaths")
    void testXsltRefused(final String xpath) {
        final String result = runXslt(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                "<xsl:value-of select=\"" + escapeAttribute(substitute(xpath)) + "\"/>");
        assertRefused(result);
    }

    @ParameterizedTest
    @MethodSource("refusedXPaths")
    void testS9apiXPathRefused(final String xpath) {
        final String result = runS9apiXPath(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED), substitute(xpath));
        assertRefused(result);
    }

    @ParameterizedTest
    @MethodSource("refusedXPaths")
    void testJaxpXPathRefused(final String xpath) {
        final String result = runJaxpXPath(SaxonSecurity.newConfiguration(SaxonPolicy.LOCKED), substitute(xpath));
        assertRefused(result);
    }

    @Test
    void testOpenConfigurationReadsFile() {
        // Shows the tests above would fail if the configuration were not secured.
        final String result = runS9apiXPath(new Processor(false), substitute("unparsed-text('DATA_URI')"));
        assertThat(result).isEqualTo(FILE_CONTENT);
    }

    @Test
    void testSaxonDocReadsFileWhenOpen() {
        // Shows saxon:doc() in the tests above would read the file if it were not removed.
        final String result = runXslt(new Processor(false),
                substitute("<xsl:value-of select=\"Q{http://saxon.sf.net/}doc('DATA_XML_URI', map{})\"/>"));
        assertThat(result).isEqualTo(FILE_CONTENT);
    }

    static Stream<String> xpathsNamingClass() {
        return Stream.of(
                "string(compare('a', 'b', 'http://saxon.sf.net/collation?class=MARKER'))",
                "string-join(sort(('b', 'a'), 'http://saxon.sf.net/collation?lang=en;class=MARKER'), '')",
                "string(compare('a', 'b', 'http://saxon.sf.net/collation?lang=en;CLASS=MARKER'))",
                "serialize(parse-xml('<a/>'), map{'method': 'Q{urn:x}MARKER'})");
    }

    @ParameterizedTest
    @MethodSource("xpathsNamingClass")
    void testClassNotCreated(final String xpath) {
        CreatedMarker.created = false;
        final String expression = xpath.replace("MARKER", CreatedMarker.class.getName());
        assertThat(runS9apiXPath(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED), expression))
                .startsWith("ERROR")
                .contains("not allowed");
        assertThat(runXslt(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                "<xsl:value-of select=\"" + escapeAttribute(expression) + "\"/>"))
                .startsWith("ERROR");
        assertThat(CreatedMarker.created).isFalse();
    }

    @Test
    void testClassCreatedWhenOpen() {
        // Shows the test above would find the class created if Saxon were not stopped.
        CreatedMarker.created = false;
        runS9apiXPath(new Processor(false), "string(compare('a', 'b', 'http://saxon.sf.net/collation?class="
                                            + CreatedMarker.class.getName() + "'))");
        assertThat(CreatedMarker.created).isTrue();
    }

    @Test
    void testXsltSortNamingClassRefused() {
        CreatedMarker.created = false;
        final String result = runXslt(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                "<xsl:for-each select=\"('b', 'a')\"><xsl:sort select=\".\" "
                + "collation=\"http://saxon.sf.net/collation?class=" + CreatedMarker.class.getName() + "\"/>"
                + "<xsl:value-of select=\".\"/></xsl:for-each>");
        assertThat(result).startsWith("ERROR");
        assertThat(CreatedMarker.created).isFalse();
    }

    @Test
    void testCollationsAndSerializeStillWork() {
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.LOCKED);
        assertThat(runS9apiXPath(processor, "string(compare('a', 'A', "
                                            + "'http://www.w3.org/2013/collation/UCA?strength=primary'))"))
                .isEqualTo("0");
        assertThat(runS9apiXPath(processor, "string(compare('a', 'b', "
                                            + "'http://saxon.sf.net/collation?lang=en;ignore-case=yes'))"))
                .isEqualTo("-1");
        assertThat(runS9apiXPath(processor, "serialize(parse-xml('<a>b</a>'), map{'method': 'xml', "
                                            + "'omit-xml-declaration': true()})"))
                .isEqualTo("<a>b</a>");
        assertThat(runXslt(processor, "<xsl:message>hello</xsl:message><xsl:value-of select=\"'ok'\"/>"))
                .isEqualTo("ok");
    }

    @Test
    void testXsltDocumentRefused() {
        final String result = runXslt(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                "<xsl:value-of select=\"document('" + tempDir.resolve("data.xml").toUri() + "')\"/>");
        assertRefused(result);
    }

    @Test
    void testXsltSourceDocumentRefused() {
        final String result = runXslt(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                "<xsl:source-document href=\"" + tempDir.resolve("data.xml").toUri() + "\" streamable=\"no\">" +
                "<xsl:value-of select=\".\"/></xsl:source-document>");
        assertRefused(result);
    }

    @Test
    void testXsltTransformFunctionRefused() {
        final String result = runXslt(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                "<xsl:value-of select=\"transform(map{'stylesheet-location':'" + dataUri + "'})?output\"/>");
        assertThat(result).doesNotContain(FILE_CONTENT);
        assertThat(result).startsWith("ERROR");
    }

    // transform() is not available in a secured configuration.
    static Stream<String> transformCalls() {
        final String inner = "'<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                             + "<xsl:template name=\"xsl:initial-template\"><out>"
                             + "<xsl:value-of select=\"unparsed-text(&apos;DATA_URI&apos;)\"/>"
                             + "</out></xsl:template></xsl:stylesheet>'";
        final String options = "map{'stylesheet-text': " + inner + ", 'initial-template': "
                               + "QName('http://www.w3.org/1999/XSL/Transform', 'initial-template'), "
                               + "'vendor-options': map{QName('http://saxon.sf.net/', 'configuration'): "
                               + "parse-xml('<configuration xmlns=\"http://saxon.sf.net/ns/configuration\" "
                               + "edition=\"HE\"/>')}}";
        return Stream.of(
                "string(transform(" + options + ")?output)",
                "string(load-xquery-module('urn:x', map{'location-hints': 'DATA_URI'}))");
    }

    @ParameterizedTest
    @MethodSource("transformCalls")
    void testTransformRefused(final String xpath) {
        final String substituted = substitute(xpath);
        assertThat(runS9apiXPath(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED), substituted))
                .as("s9api XPath").startsWith("ERROR").doesNotContain(FILE_CONTENT);
        assertThat(runJaxpXPath(SaxonSecurity.newConfiguration(SaxonPolicy.LOCKED), substituted))
                .as("JAXP XPath").startsWith("ERROR").doesNotContain(FILE_CONTENT);
        assertThat(runXslt(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                "<xsl:value-of select=\"" + escapeAttribute(substituted) + "\"/>"))
                .as("XSLT").startsWith("ERROR").doesNotContain(FILE_CONTENT);
    }

    // Each puts a transform() call in a different part of an XSLT.
    static Stream<String> xsltCallingTransform() {
        final String call = "transform(map{})";
        return Stream.of(
                "<xsl:variable name='g' select=\"" + call + "\"/>",
                "<xsl:param name='p' select=\"" + call + "\"/>",
                "<xsl:function name='f:f'><xsl:sequence select=\"" + call + "\"/></xsl:function>",
                "<xsl:function name='f:f'><xsl:sequence select=\"fn:transform(map{})\"/></xsl:function>",
                "<xsl:function name='f:f'><xsl:sequence select=\"Q{http://www.w3.org/2005/xpath-functions}"
                + "transform(map{})\"/></xsl:function>",
                "<xsl:template match='b' mode='m'><xsl:sequence select=\"" + call + "\"/></xsl:template>",
                "<xsl:template match=\"b[exists(" + call + ")]\"/>",
                "<xsl:template name='t'><xsl:if test=\"exists(" + call + ")\">x</xsl:if></xsl:template>",
                "<xsl:key name='k' match='b' use=\"string(exists(" + call + "))\"/>",
                "<xsl:attribute-set name='a'><xsl:attribute name='x' select=\"exists(" + call
                + ")\"/></xsl:attribute-set>",
                "<xsl:accumulator name='acc' initial-value=\"exists(" + call + ")\"><xsl:accumulator-rule "
                + "match='b' select='1'/></xsl:accumulator>",
                "<xsl:function name='f:f'><xsl:sequence select=\"load-xquery-module('urn:x')\"/></xsl:function>");
    }

    @ParameterizedTest
    @MethodSource("xsltCallingTransform")
    void testXsltCallingTransformRefused(final String declaration) {
        final String xslt = "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='3.0' "
                            + "xmlns:f='urn:f' xmlns:fn='http://www.w3.org/2005/xpath-functions'>"
                            + declaration
                            + "<xsl:template match='/'>x</xsl:template></xsl:stylesheet>";
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.LOCKED);

        assertThatThrownBy(() -> processor.newXsltCompiler().compile(new StreamSource(new StringReader(xslt))))
                .isInstanceOf(SaxonApiException.class);
        // And it is the function that is missing.
        assertThat(runXslt(processor, "<xsl:value-of select=\"function-available('transform', 1)\"/>"))
                .isEqualTo("false");
    }

    @Test
    void testXsltOtherFunctionsWork() throws SaxonApiException {
        final String xslt = "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='3.0' "
                            + "xmlns:f='urn:f'>"
                            + "<xsl:function name='f:transform'><xsl:sequence select=\"upper-case(string(.))\"/>"
                            + "</xsl:function>"
                            + "<xsl:template match='/'><xsl:value-of select=\"translate('a', 'a', 'b')\"/>"
                            + "</xsl:template></xsl:stylesheet>";
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.LOCKED);

        assertThat(processor.newXsltCompiler().compile(new StreamSource(new StringReader(xslt)))).isNotNull();
        assertThat(runXslt(processor, "<xsl:value-of select=\"upper-case('a'), function-available('upper-case', 1)"
                                      + "\"/>"))
                .isEqualTo("A true");
    }

    @Test
    void testTransformWithVendorOptionsReadsFileWhenOpen() {
        // Shows testTransformRefused would fail if transform() were available.
        final String xpath = substitute(transformCalls().findFirst().orElseThrow());
        assertThat(runS9apiXPath(new Processor(false), xpath)).contains(FILE_CONTENT);
    }

    @Test
    void testXsltResultDocumentRefused() throws IOException {
        final Path written = tempDir.resolve("written.txt");
        final String result = runXslt(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                "<xsl:result-document href=\"" + written.toUri() + "\" method=\"text\">x</xsl:result-document>");
        assertThat(result).startsWith("ERROR");
        assertThat(written).doesNotExist();
    }

    @Test
    void testXsltSystemPropertyLimited() {
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.LOCKED);
        assertThat(runXslt(processor, "<xsl:value-of select=\"system-property('user.home')\"/>"))
                .isEmpty();
        assertThat(runXslt(processor, "<xsl:value-of select=\"system-property('xsl:version')\"/>"))
                .isEqualTo("3.0");
    }

    @Test
    void testJavaCallRefused() {
        final String result = runXslt(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                "<xsl:value-of select=\"Q{java:java.lang.System}getProperty('user.home')\"/>");
        assertThat(result).startsWith("ERROR");
    }

    @Test
    void testRegisteredExtensionFunctionStillWorks() {
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.LOCKED);
        processor.getUnderlyingConfiguration().registerExtensionFunction(new HelloFunction());
        final String result = runXslt(processor,
                "<xsl:value-of select=\"Q{stroom-test}hello()\"/>");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void testInternalDtdStillRefused() {
        // parse-xml() of XML with any DOCTYPE is refused, as Stroom's own parsers do.
        final String result = runS9apiXPath(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                "string(parse-xml('<!DOCTYPE a [<!ENTITY e \"x\">]><a>&e;</a>'))");
        assertThat(result).startsWith("ERROR");
    }

    @Test
    void testParseXmlWithoutDoctypeWorks() {
        final String result = runS9apiXPath(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                "string(parse-xml('<a>b</a>'))");
        assertThat(result).isEqualTo("b");
    }

    @Test
    void testXsltExternalEntityRefused() {
        final String xslt = xsltWithDoctype("<!ENTITY s SYSTEM \"" + dataUri + "\">", "&s;");
        assertThat(runStylesheet(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED), xslt, null))
                .doesNotContain(FILE_CONTENT);
    }

    @Test
    void testXsltExternalEntityReadsFileWhenOpen() {
        // Shows the test above would read the file if the entity were loaded.
        final String xslt = xsltWithDoctype("<!ENTITY s SYSTEM \"" + dataUri + "\">", "&s;");
        assertThat(runStylesheet(SaxonSecurity.newUnsecuredProcessor(), xslt, null))
                .isEqualTo(FILE_CONTENT);
    }

    @Test
    void testImportedXsltExternalEntityRefused() {
        final String imported = "<!DOCTYPE xsl:stylesheet [<!ENTITY s SYSTEM \"" + dataUri + "\">]>"
                                + "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
                                + "<xsl:template name=\"t\">&s;</xsl:template></xsl:stylesheet>";
        final String xslt = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
                            + "<xsl:import href=\"imported\"/><xsl:output method=\"text\"/>"
                            + "<xsl:template match=\"/\">[<xsl:call-template name=\"t\"/>]</xsl:template>"
                            + "</xsl:stylesheet>";
        assertThat(runStylesheet(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED), xslt, imported))
                .doesNotContain(FILE_CONTENT);
        assertThat(runStylesheet(SaxonSecurity.newUnsecuredProcessor(), xslt, imported))
                .isEqualTo("[" + FILE_CONTENT + "]");
    }

    @Test
    void testXsltExternalDtdNotLoaded() {
        // Nothing listens on port 1, so loading the DTD would fail the compile.
        final String xslt = "<!DOCTYPE xsl:stylesheet SYSTEM \"http://127.0.0.1:1/a.dtd\">"
                            + xsltWithDoctype(null, "ok");
        assertThat(runStylesheet(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED), xslt, null))
                .isEqualTo("ok");
    }

    @Test
    void testXsltInternalEntityWorks() {
        final String xslt = xsltWithDoctype("<!ENTITY greeting \"hello\">", "&greeting;");
        assertThat(runStylesheet(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED), xslt, null))
                .isEqualTo("hello");
    }

    @Test
    void testAllowListReadsAllowedFiles() {
        final SaxonPolicy policy = SaxonPolicy.allowing(List.of(allowedUri));
        final Processor processor = SaxonSecurity.newProcessor(policy);
        final String textUri = allowedDir.resolve("a.txt").toUri().toString();
        final String xmlUri = allowedDir.resolve("a.xml").toUri().toString();

        assertThat(runXslt(processor, "<xsl:value-of select=\"unparsed-text('" + textUri + "')\"/>"))
                .isEqualTo(ALLOWED_TEXT);
        assertThat(runXslt(processor, "<xsl:value-of select=\"doc('" + xmlUri + "')\"/>"))
                .isEqualTo(ALLOWED_TEXT);
        assertThat(runXslt(processor, "<xsl:value-of select=\"count(uri-collection('" + allowedUri + "'))\"/>"))
                .isEqualTo("2");
        assertThat(runJaxpXPath(SaxonSecurity.newConfiguration(policy), "unparsed-text('" + textUri + "')"))
                .isEqualTo(ALLOWED_TEXT);
    }

    @Test
    void testUnparsedTextAvailableFalse() {
        final String result = runS9apiXPath(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                substitute("string(unparsed-text-available('DATA_URI'))"));
        assertThat(result).isEqualTo("false");
    }

    @Test
    void testRefusedMessage() {
        final String result = runS9apiXPath(SaxonSecurity.newProcessor(SaxonPolicy.LOCKED),
                substitute("unparsed-text('DATA_URI')"));
        assertThat(result).contains(REFUSED);
    }

    @Test
    void testAllowListRefusesOtherFiles() {
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.allowing(List.of(allowedUri)));
        assertRefused(runXslt(processor, "<xsl:value-of select=\"unparsed-text('" + dataUri + "')\"/>"));
        assertRefused(runXslt(processor,
                "<xsl:value-of select=\"count(uri-collection('" + tempDir.toUri() + "'))\"/>"));
    }

    @Test
    void testCollectionOfCatalogueRefused() throws IOException {
        // Only directories can be collections.
        final Path catalogue = allowedDir.resolve("catalogue.xml");
        Files.writeString(catalogue, "<collection><doc href=\"" + tempDir.resolve("data.xml").toUri()
                                     + "\"/></collection>");
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.allowing(List.of(allowedUri)));

        assertRefused(runXslt(processor, "<xsl:value-of select=\"collection('" + catalogue.toUri() + "')\"/>"));
    }

    @Test
    void testCollectionLeavesOutLinksOut() throws IOException {
        Files.createSymbolicLink(allowedDir.resolve("link.xml"), tempDir.resolve("data.xml"));
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.allowing(List.of(allowedUri)));

        assertThat(runXslt(processor, "<xsl:value-of select=\"count(uri-collection('" + allowedUri + "'))\"/>"))
                .isEqualTo("2");
        assertThat(runXslt(processor,
                "<xsl:value-of select=\"string-join(collection('" + allowedUri + "?select=*.xml'), ',')\"/>"))
                .isEqualTo(ALLOWED_TEXT);
    }

    /// A file left out of a collection is never opened.
    @Test
    @Timeout(30)
    void testCollectionDoesNotOpenLeftOutFiles() throws Exception {
        final Path fifo = tempDir.resolve("fifo.xml");
        final boolean made = new ProcessBuilder("mkfifo", fifo.toString()).start().waitFor() == 0;
        Assumptions.assumeThat(made).as("mkfifo is available").isTrue();
        Files.createSymbolicLink(allowedDir.resolve("fifo.xml"), fifo);
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.allowing(List.of(allowedUri)));

        assertThat(runXslt(processor,
                "<xsl:value-of select=\"string-join(collection('" + allowedUri + "?select=*.xml'), ',')\"/>"))
                .isEqualTo(ALLOWED_TEXT);
    }

    /// Files are read through the real paths that were checked.
    @Test
    void testCollectionReadsRealPaths() throws IOException {
        Files.createDirectories(allowedDir.resolve("sub"));
        Files.createSymbolicLink(allowedDir.resolve("sub").resolve("link.xml"), allowedDir.resolve("a.xml"));
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.allowing(List.of(allowedUri)));

        assertThat(runXslt(processor, "<xsl:value-of select=\"collection('" + allowedUri
                                      + "sub/?select=*.xml')/document-uri(.)\"/>"))
                .isEqualTo(allowedDir.resolve("a.xml").toRealPath().toUri().toString());
    }

    @Test
    void testCollectionOptionsRefused() throws IOException {
        // Only the supported collection options are accepted.
        Files.writeString(allowedDir.resolve("inc.xml"), "<a xmlns:xi=\"http://www.w3.org/2001/XInclude\">"
                + "<xi:include href=\"" + dataUri + "\" parse=\"text\"/></a>");
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.allowing(List.of(allowedUri)));

        for (final String option : List.of("xinclude=yes", "parser=java.lang.Object", "validation=strict",
                "select=*.xml;xinclude=yes", "select=*.xml&amp;stable=yes")) {
            final String result = runXslt(processor,
                    "<xsl:value-of select=\"string-join(collection('" + allowedUri + "?" + option + "'), ',')\"/>");
            assertThat(result).as(option).doesNotContain(FILE_CONTENT).startsWith("ERROR").contains("is not allowed");
        }
    }

    @Test
    void testCollectionAllowedOptions() {
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.allowing(List.of(allowedUri)));
        assertThat(runXslt(processor, "<xsl:value-of select=\"count(collection('" + allowedUri
                                      + "?select=*.xml;recurse=yes;on-error=ignore'))\"/>"))
                .isEqualTo("1");
    }

    @Test
    void testSuppliedReaderKeepsItsFeatures() throws Exception {
        // A shared reader whose administrator has allowed DOCTYPE declarations keeps that setting.
        final XMLReader reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        reader.setFeature(SAXParserFactoryFactory.FEATURE_DISALLOW_DOCTYPE, false);
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.LOCKED);

        processor.newDocumentBuilder().build(new SAXSource(reader, new InputSource(new StringReader("<a/>"))));
        assertThat(reader.getFeature(SAXParserFactoryFactory.FEATURE_DISALLOW_DOCTYPE)).isFalse();
    }

    @Test
    void testPooledParsersStayHardened() {
        // Each parse-xml() and doc() call reuses Saxon's parsers, which must still refuse a DOCTYPE.
        final Processor processor = SaxonSecurity.newProcessor(SaxonPolicy.LOCKED);
        for (int i = 0; i < 3; i++) {
            assertThat(runS9apiXPath(processor, "string(parse-xml('<a>b</a>'))")).isEqualTo("b");
            assertThat(runS9apiXPath(processor, "string(parse-xml('<!DOCTYPE a [<!ENTITY e \"x\">]><a>&e;</a>'))"))
                    .startsWith("ERROR");
        }
    }

    /// Fails if main code creates a Saxon processor, configuration or factory other than through
    /// SaxonSecurity.
    @Test
    void testSaxonOnlyCreatedThroughSaxonSecurity() throws IOException {
        final List<String> problems = new ArrayList<>();
        for (final Path sourceRoot : findMainSourceRoots()) {
            try (final Stream<Path> paths = Files.walk(sourceRoot)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> checkSource(path.toString(), readString(path), problems));
            }
        }
        assertThat(problems)
                .as("Create Saxon processors and configurations with SaxonSecurity so XSLT and XPath are secured")
                .isEmpty();
    }

    @Test
    void testCreationCheckFindsUnsecuredCreation() {
        final String source = """
                import net.sf.saxon.s9api.Processor;
                class A {
                    Processor p = new Processor(false);
                    Object c = net.sf.saxon.Configuration.newConfiguration();
                    Object t = javax.xml.transform.TransformerFactory.newInstance();
                }
                """;
        final List<String> problems = new ArrayList<>();
        checkSource("A.java", source, problems);
        assertThat(problems).hasSize(3);
    }

    @Test
    void testCreationCheckFindsFullyQualifiedCreation() {
        // No Saxon import, so only the fully qualified names show it uses Saxon.
        final String source = """
                class A {
                    Object p = new net.sf.saxon.s9api.Processor(licensed);
                    Object c = new net.sf.saxon.Configuration();
                    Object e = new net.sf.saxon.xpath.XPathEvaluator();
                    Object i = new net.sf.saxon.sxpath.IndependentContext();
                    Object d = new net.sf.saxon.dom.DocumentBuilderImpl();
                }
                """;
        final List<String> problems = new ArrayList<>();
        checkSource("A.java", source, problems);
        assertThat(problems).hasSize(5);
    }

    @Test
    void testCreationCheckIgnoresOtherLibraries() {
        // jOOQ has a Processor table and Hadoop a Configuration.
        final String source = """
                import org.apache.hadoop.conf.Configuration;
                class A {
                    Configuration c = new Configuration();
                    Processor p = new Processor();
                }
                """;
        final List<String> problems = new ArrayList<>();
        checkSource("A.java", source, problems);
        assertThat(problems).isEmpty();
    }

    private static void checkSource(final String name, final String source, final List<String> problems) {
        final boolean usesSaxon = USES_SAXON.matcher(source).find();
        final String fileName = Path.of(name).getFileName().toString();
        FORBIDDEN_CREATION.forEach((pattern, allowedIn) -> {
            // JAXP factories pick Saxon from the classpath, so they are checked in every file.
            final boolean jaxp = pattern.pattern().contains("newInstance");
            if ((usesSaxon || jaxp) && !fileName.equals(allowedIn) && pattern.matcher(source).find()) {
                problems.add(name + " matches " + pattern.pattern());
            }
        });
    }

    // The src/main/java folder of every Gradle module in this repository. Only these are walked, so other
    // worktrees, build output and node_modules below the repository root are never scanned.
    private static List<Path> findMainSourceRoots() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) {
            root = root.getParent();
        }
        assertThat(root).as("Repository root").isNotNull();

        final List<Path> sourceRoots = new ArrayList<>();
        final Path repoRoot = root;
        Files.walkFileTree(repoRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
                final String name = dir.getFileName() == null
                        ? ""
                        : dir.getFileName().toString();
                if (!dir.equals(repoRoot)
                    && (name.startsWith(".") || SKIPPED_DIRS.contains(name))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                final Path mainJava = dir.resolve("src/main/java");
                if (Files.isDirectory(mainJava)) {
                    sourceRoots.add(mainJava);
                }
                // Source folders hold no further modules.
                return "src".equals(name)
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(final Path file, final IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
        assertThat(sourceRoots).as("Main source folders").isNotEmpty();
        return sourceRoots;
    }

    private static String readString(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String substitute(final String xpath) {
        return xpath
                .replace("DATA_XML_URI", tempDir.resolve("data.xml").toUri().toString())
                .replace("DATA_URI", dataUri)
                .replace("TEMP_DIR_URI", tempDir.toUri().toString());
    }

    private static String escapeAttribute(final String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // Refused reads fail; hidden environment variables come back empty.
    private static void assertRefused(final String result) {
        assertThat(result).doesNotContain(FILE_CONTENT);
        assertThat(result.isEmpty() || result.startsWith("ERROR"))
                .as("Expected an error or nothing but got: %s", result)
                .isTrue();
    }

    // Compiles and runs the XSLT the way XsltPoolImpl and XsltFilter do. Returns the output, or "ERROR "
    // followed by the messages of the exception and its causes.
    private static String runXslt(final Processor processor, final String templateBody) {
        final String xslt = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">" +
                            "<xsl:output method=\"text\"/>" +
                            "<xsl:template match=\"/\">" + templateBody + "</xsl:template></xsl:stylesheet>";
        try {
            final XsltCompiler compiler = processor.newXsltCompiler();
            compiler.setURIResolver((href, base) -> {
                throw new TransformerException("No XSLT found for " + href);
            });
            final XsltExecutable executable = compiler.compile(new StreamSource(new StringReader(xslt)));
            final StringWriter writer = new StringWriter();
            new TemplatesImpl(executable).newTransformer()
                    .transform(new StreamSource(new StringReader("<a/>")), new StreamResult(writer));
            return writer.toString();
        } catch (final Exception e) {
            return error(e);
        }
    }

    // A stylesheet whose template outputs the text, with a DOCTYPE holding the declarations if there are any.
    private static String xsltWithDoctype(final String declarations, final String text) {
        final String doctype = declarations == null
                ? ""
                : "<!DOCTYPE xsl:stylesheet [" + declarations + "]>";
        return doctype
               + "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
               + "<xsl:output method=\"text\"/>"
               + "<xsl:template match=\"/\">" + text + "</xsl:template></xsl:stylesheet>";
    }

    // Runs a whole stylesheet. Any xsl:import or xsl:include gets the imported XSLT, as Stroom's resolver does.
    private static String runStylesheet(final Processor processor, final String xslt, final String imported) {
        try {
            final XsltCompiler compiler = processor.newXsltCompiler();
            compiler.setURIResolver((href, base) -> {
                if (imported == null) {
                    throw new TransformerException("No XSLT found for " + href);
                }
                return new StreamSource(new StringReader(imported));
            });
            final XsltExecutable executable = compiler.compile(new StreamSource(new StringReader(xslt)));
            final StringWriter writer = new StringWriter();
            new TemplatesImpl(executable).newTransformer()
                    .transform(new StreamSource(new StringReader("<a/>")), new StreamResult(writer));
            return writer.toString();
        } catch (final Exception e) {
            return error(e);
        }
    }

    private static String runS9apiXPath(final Processor processor, final String xpath) {
        try {
            final XPathSelector selector = processor.newXPathCompiler().compile(xpath).load();
            selector.setContextItem(processor.newDocumentBuilder()
                    .build(new StreamSource(new StringReader("<a/>"))));
            final StringBuilder sb = new StringBuilder();
            for (final XdmItem item : selector) {
                sb.append(item.getStringValue());
            }
            return sb.toString();
        } catch (final Exception e) {
            return error(e);
        }
    }

    private static String runJaxpXPath(final Configuration configuration, final String xpath) {
        try {
            return (String) new XPathEvaluator(configuration)
                    .compile(xpath)
                    .evaluate(new InputSource(new StringReader("<a/>")), XPathConstants.STRING);
        } catch (final Exception e) {
            return error(e);
        }
    }

    private static String error(final Throwable throwable) {
        final StringBuilder sb = new StringBuilder("ERROR");
        Throwable t = throwable;
        while (t != null) {
            sb.append(' ').append(t.getMessage());
            t = t.getCause() == t
                    ? null
                    : t.getCause();
        }
        return sb.toString();
    }

    // --------------------------------------------------------------------------------


    // Records whether Saxon created it from its class name.
    public static class CreatedMarker implements Comparator<Object> {

        private static volatile boolean created;

        public CreatedMarker() {
            created = true;
        }

        @Override
        public int compare(final Object o1, final Object o2) {
            return 0;
        }
    }


    // --------------------------------------------------------------------------------


    private static class HelloFunction extends ExtensionFunctionDefinition {

        @Override
        public StructuredQName getFunctionQName() {
            return new StructuredQName("", "stroom-test", "hello");
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
                    return StringValue.makeStringValue("hello");
                }
            };
        }
    }
}
