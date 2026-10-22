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

import stroom.ai.impl.tools.validation.XsltSafety.Unsafe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// The tools run and save nothing that reads the environment or a resource: ported from the Stroom MCP server's
/// `tests/test_saxon.py` (the XPath and XSLT refusals).
class TestXsltSafety {

    private static final String GEO = "http://example.com/geo";
    private static final String XSLT = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xpath-default-namespace="records:2" version="3.0">
              <xsl:template match="record">
                <Event><Description>Printed document (A4)</Description>{BODY}</Event>
              </xsl:template>
            </xsl:stylesheet>""";

    @ParameterizedTest
    @ValueSource(strings = {
            "environment-variable('STROOM_MCP_REQUEST_STATE_KEYS')",
            "fn:environment-variable('X')",
            "Q{http://www.w3.org/2005/xpath-functions}environment-variable('X')",
            "available-environment-variables()",
            "environment-variable#1('X')",
            "environment-variable (: a comment :) ('X')",
            "environment-variable(: (: nested :) :)('X')",
            "'(:', environment-variable (:x:) ('X'), ':)'",
            "function-lookup(xs:QName('fn:environment-variable'), 1)('X')",
            "transform(map{'stylesheet-text': '...'})",
            "EventSource/User/Id)) , doc('file:///etc/passwd') , ((.",
            "unparsed-text-lines('file:///etc/hosts')",
            "fn:document('a')",
            // With external functions allowed (as Stroom runs XSLT), a name in no namespace reads the JVM's.
            "system-property('java.home')",
            "fn:system-property('user.dir')",
            "available-system-properties()"})
    void testExpressionsReadingTheEnvironmentOrAResourceAreRefused(final String expression) {
        assertThat(XsltSafety.refused(expression, null)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "EventSource/System/Name", "json-to-xml(*[@key='event'])/*/*[@key='user']", "document-node()",
            "doc/id", "substring-after(data[@name='msg']/@value, 'user=')", "$doc('x')", "my-doc(1)",
            "matches(., 'transform-[a-z]+')", "format-dateTime(current-dateTime(), '[Y]')",
            // Stroom's own functions, network ones included: an xpath is only ever saved as XSLT, where they are
            // refused unless the XSLT replaced calls them (unsafeInXslt).
            "stroom:http-call('https://enrich/api', '', 'application/json', ., '')",
            "stroom:fetch-json('https://x/y', '')"})
    void testOrdinaryExpressionsPass(final String expression) {
        assertThat(XsltSafety.refused(expression, null)).isNull();
    }

    @Test
    void testANameInAStringIsNotACallButACommentHidesNothing() {
        assertThat(XsltSafety.refused("'Printed document (A4)'", null)).isNull();
        assertThat(XsltSafety.refused("concat('doc(', 'x')", null)).isNull();
        assertThat(XsltSafety.refused("(: it's :) environment-variable('X') (: ' :)", null)).isNotNull();
        // Unreadable: read as written.
        assertThat(XsltSafety.refused("environment-variable('unterminated", null)).isNotNull();
    }

    @Test
    void testTheRefusalSaysWhatIsCalledAndWhy() {
        assertThat(XsltSafety.refused("doc('f')", null))
                .isEqualTo("it calls doc(), which reads the environment, files or network, or looks a function "
                           + "up by name: the tools don't run or save it. Read the record's own values");
    }

    @Test
    void testValueTemplatesAreReadAsXsltReadsThem() {
        assertThat(XsltSafety.valueTemplateParts("a {x} b {{literal}} {map{'k': 1}?k}"))
                .containsExactly("x", "map{'k': 1}?k");
        assertThat(XsltSafety.valueTemplateParts("{concat('}', doc('f'))}")).containsExactly("concat('}', doc('f'))");
        assertThat(XsltSafety.valueTemplateParts("{x")).containsExactly("{x");
    }

    static Stream<Arguments> unsafeBodies() {
        return Stream.of(
                Arguments.of("<xsl:value-of select=\"environment-variable('X')\"/>", "environment-variable()"),
                // A character reference is a '('.
                Arguments.of("<xsl:value-of select=\"doc&#40;'file:///etc/passwd')\"/>", "doc()"),
                Arguments.of("<xsl:value-of select=\"map{}, unparsed-text('f')\"/>", "unparsed-text()"),
                Arguments.of("<Data Name=\"x\" Value=\"{unparsed-text('file:///etc/hosts')}\"/>", "unparsed-text()"),
                Arguments.of("<Data Name=\"x\" Value=\"{concat('}', doc('f'))}\"/>", "doc()"),
                Arguments.of("<Data xsl:use-when=\"doc-available('f')\" Name=\"x\" Value=\"y\"/>",
                        "doc-available()"),
                Arguments.of("<xsl:evaluate xpath=\"'1'\"/>", "xsl:evaluate"),
                Arguments.of("<xsl:result-document href=\"file:///tmp/x\"><a/></xsl:result-document>",
                        "xsl:result-document"),
                Arguments.of("<xsl:source-document href=\"f\"><a/></xsl:source-document>", "xsl:source-document"));
    }

    @ParameterizedTest
    @MethodSource("unsafeBodies")
    void testAnXsltReadingOrWritingAResourceIsRefused(final String body, final String what) {
        assertThat(whats(XsltSafety.unsafeInXslt(xslt(body)))).contains(what);
    }

    @Test
    void testOrdinaryXsltPassesAndImportsByNameOnly() {
        assertThat(XsltSafety.unsafeInXslt(xslt("<Data Name=\"doc (v1)\" Value=\"{data[@name='doc']/@value}\"/>"
                                                + "<xsl:value-of select=\"'see document (2)'\"/>"
                                                + "<!-- doc(x) in a comment -->"))).isEmpty();
        final String byUrl = XSLT.replace("version=\"3.0\">",
                "version=\"3.0\"><xsl:import href=\"file:///etc/x.xsl\"/>").replace("{BODY}", "");
        assertThat(whats(XsltSafety.unsafeInXslt(byUrl))).containsExactly("xsl:import file:///etc/x.xsl");
        final String byName = XSLT.replace("version=\"3.0\">",
                "version=\"3.0\"><xsl:import href=\"Stream-Meta-V1\"/>").replace("{BODY}", "");
        assertThat(XsltSafety.unsafeInXslt(byName)).isEmpty();
    }

    @Test
    void testAFunctionsRefusalSaysStroomWouldRunItWithItsOwnAccess() {
        final List<Unsafe> unsafe = XsltSafety.unsafeInXslt(xslt("<xsl:value-of select=\"doc('f')\"/>"));
        assertThat(unsafe).extracting(Unsafe::why)
                .containsExactly("xsl:value-of/@select calls doc(), which reads the environment, files or network, "
                                 + "or looks a function up by name: Stroom would run it with its own access, so the "
                                 + "tools don't save it");
    }

    @Test
    void testAUserDefinedFunctionOfTheSameNameIsTheXsltsOwn() {
        assertThat(XsltSafety.refused("geo:lookup-ip(@ip)", Map.of("geo", GEO))).isNull();
        assertThat(XsltSafety.refused("geo:document(@ip)", Map.of("geo", GEO))).isNull();
        assertThat(XsltSafety.refused("Q{" + GEO + "}doc(@ip)", null)).isNull();
        // fn under another prefix, the prefixes Saxon declares itself, and Saxon's own namespace are refused.
        assertThat(XsltSafety.refused("x:doc('f')", Map.of("x", XsltSafety.FN))).isNotNull();
        assertThat(XsltSafety.refused("fn:doc('f')", null)).isNotNull();
        assertThat(XsltSafety.refused("saxon:doc('f')", null)).isNotNull();
        assertThat(XsltSafety.refused("s:doc('f')", Map.of("s", XsltSafety.SAXON))).isNotNull();
        assertThat(XsltSafety.refused("geo:document(@ip), doc('f')", Map.of("geo", GEO))).isNotNull();
        // A prefix nothing binds can't compile anywhere: left to the check that says it isn't bound.
        assertThat(XsltSafety.refused("geo:document(@ip)", null)).isNull();
    }

    @Test
    void testAnXsltsPrefixesAreReadWhereTheExpressionIs() {
        final String imported = XSLT.replace("version=\"3.0\">",
                "version=\"3.0\" xmlns:geo=\"" + GEO + "\"><xsl:import href=\"GeoIP-V1\"/>");
        assertThat(XsltSafety.unsafeInXslt(imported.replace("{BODY}",
                "<xsl:value-of select=\"geo:document(@ip)\"/><xsl:value-of select=\"geo:lookup-ip(@ip)\"/>")))
                .isEmpty();
        final String aliased = XSLT.replace("version=\"3.0\">",
                "version=\"3.0\" xmlns:f=\"http://www.w3.org/2005/xpath-functions\">");
        assertThat(XsltSafety.unsafeInXslt(aliased.replace("{BODY}", "<xsl:value-of select=\"f:doc('x')\"/>")))
                .isNotEmpty();
        // A prefix rebound on an element counts in it, not in the text after it.
        final String rebound = XSLT.replace("version=\"3.0\">",
                "version=\"3.0\" xmlns:f=\"http://www.w3.org/2005/xpath-functions\" expand-text=\"yes\">");
        final String body = "<x xmlns:f=\"" + GEO + "\"/>{f:doc('secret')}";
        assertThat(whats(XsltSafety.unsafeInXslt(rebound.replace("{BODY}", body)))).containsExactly("doc()");
    }

    static Stream<Arguments> outsideBodies() {
        return Stream.of(
                Arguments.of("<xsl:value-of select=\"stroom:http-call('https://x/y', '', 'text/plain', ., '')\"/>",
                        "stroom:http-call()"),
                Arguments.of("<xsl:copy-of select=\"stroom:fetch-json(concat('https://x/', .), '')\"/>",
                        "stroom:fetch-json()"),
                Arguments.of("<xsl:value-of select=\"stroom:ask-ai('model', .)\"/>", "stroom:ask-ai()"),
                Arguments.of("<Data Name=\"h\" Value=\"{stroom:host-name(@ip)}\"/>", "stroom:host-name()"),
                Arguments.of("<xsl:value-of select=\"s:host-address(concat(., '.example.com'))\" "
                             + "xmlns:s=\"stroom\"/>", "stroom:host-address()"),
                Arguments.of("<xsl:value-of select=\"Q{stroom}http-call#5\"/>", "stroom:http-call()"));
    }

    @ParameterizedTest
    @MethodSource("outsideBodies")
    void testAnXsltCallingAStroomFunctionThatReachesOutsideStroomIsRefused(final String body, final String what) {
        final String sheet = XSLT.replace("version=\"3.0\">", "version=\"3.0\" xmlns:stroom=\"stroom\">");
        assertThat(whats(XsltSafety.unsafeInXslt(sheet.replace("{BODY}", body)))).containsExactly(what);
    }

    @Test
    void testAStroomFunctionThatReachesOutsideStroomSaysWhy() {
        final String sheet = XSLT.replace("version=\"3.0\">", "version=\"3.0\" xmlns:stroom=\"stroom\">");
        assertThat(XsltSafety.unsafeInXslt(sheet.replace("{BODY}",
                "<xsl:value-of select=\"stroom:http-call('https://x', '', 'text/plain', ., '')\"/>")))
                .extracting(Unsafe::why)
                .containsExactly("xsl:value-of/@select calls stroom:http-call(), which reaches outside Stroom (a "
                                 + "URL, a chat model or a DNS lookup) with what it is given: Stroom would run it on "
                                 + "its nodes for every record, so the tools don't save it unless the XSLT it "
                                 + "replaces already calls it");
        // Stroom's other functions, and one of the same name in another namespace, are the XSLT's business.
        assertThat(XsltSafety.unsafeInXslt(sheet.replace("{BODY}",
                "<xsl:value-of select=\"stroom:lookup('m', .), stroom:format-date(., 'x'), geo:host-name(.), "
                + "host-name\" xmlns:geo=\"" + GEO + "\"/>"))).isEmpty();
    }

    @Test
    void testWhatTheXsltReplacedAlreadyCalledIsKept() {
        final String sheet = XSLT.replace("version=\"3.0\">", "version=\"3.0\" xmlns:stroom=\"stroom\">");
        final String previous = sheet.replace("{BODY}",
                "<xsl:value-of select=\"stroom:http-call('https://enrich', '', 'text/plain', ., '')\"/>");
        final String code = sheet.replace("{BODY}",
                "<Data Value=\"{stroom:http-call('https://enrich', '', 'text/plain', @ip, '')}\"/>"
                + "<xsl:value-of select=\"stroom:fetch-json('https://other', '')\"/>");
        final List<Unsafe> unsafe = XsltSafety.unsafeInXslt(code);
        assertThat(whats(unsafe)).containsExactly("stroom:http-call()", "stroom:fetch-json()");
        assertThat(XsltSafety.keptBy(code, previous)).containsExactly(unsafe.getFirst().why());
        assertThat(XsltSafety.keptBy(code, null)).isEmpty();
        assertThat(XsltSafety.keptBy(code, "")).isEmpty();
    }

    @Test
    void testXmlThatIsNotWellFormedIsLeftToTheXsltCheck() {
        assertThat(XsltSafety.unsafeInXslt("<xsl:stylesheet")).isEmpty();
    }

    private static String xslt(final String body) {
        return XSLT.replace("{BODY}", body);
    }

    private static List<String> whats(final List<Unsafe> unsafe) {
        return unsafe.stream().map(Unsafe::what).toList();
    }
}
