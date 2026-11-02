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

package stroom.ai.impl.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSafeXPath {

    private static final String EVENTS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Events xmlns="event-logging:3">
              <Event><EventSource><Device><HostName>fw1</HostName></Device></EventSource></Event>
              <Event><EventSource><Device><HostName>fw2</HostName></Device></EventSource></Event>
            </Events>
            """;

    @TempDir
    Path tempDir;

    @Test
    void testQuery() throws IOException {
        final List<String> results = evaluate(EVENTS, "//e:HostName", Map.of("e", "event-logging:3"), false);

        assertThat(results).containsExactly("fw1", "fw2");
        assertThat(evaluate(EVENTS, "count(//e:Event)", Map.of("e", "event-logging:3"), false))
                .containsExactly("2");
        assertThat(evaluate(EVENTS, "(//e:Device)[1]", Map.of("e", "event-logging:3"), true).getFirst())
                .contains("<HostName>fw1</HostName>");
    }

    @Test
    void testExternalEntitiesAreRefused() throws IOException {
        final Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "TOP SECRET");
        final String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE a [<!ENTITY xxe SYSTEM "%s">]>
                <a>&xxe;</a>
                """.formatted(secret.toUri());

        assertThatThrownBy(() -> evaluate(xxe, "string(/a)", Map.of(), false))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageNotContaining("TOP SECRET");
    }

    @Test
    void testEntityExpansionIsRefused() {
        final String laughs = """
                <?xml version="1.0"?>
                <!DOCTYPE lolz [
                  <!ENTITY lol "lol">
                  <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
                  <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
                ]>
                <lolz>&lol3;</lolz>
                """;

        assertThatThrownBy(() -> evaluate(laughs, "string(/lolz)", Map.of(), false))
                .isInstanceOf(WorkspaceException.class);
    }

    @Test
    void testXIncludeIsNotProcessed() throws IOException {
        final Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "TOP SECRET");
        final String xinclude = """
                <a xmlns:xi="http://www.w3.org/2001/XInclude"><xi:include href="%s" parse="text"/></a>
                """.formatted(secret.toUri());

        assertThat(evaluate(xinclude, "string(/a)", Map.of(), false)).containsExactly("");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "doc('file:///etc/passwd')",
            "fn:doc('file:///etc/passwd')",
            "Q{http://www.w3.org/2005/xpath-functions}doc('file:///etc/passwd')",
            "doc ('file:///etc/passwd')",
            "doc#1('file:///etc/passwd')",
            "doc-available('file:///etc/passwd')",
            "collection('file:///etc')",
            "uri-collection('file:///etc')",
            "unparsed-text('file:///etc/passwd')",
            "unparsed-text-lines('file:///etc/passwd')",
            "unparsed-text-available('file:///etc/passwd')",
            "environment-variable('HOME')",
            "available-environment-variables()",
            "function-lookup(xs:QName('fn:doc'), 1)('file:///etc/passwd')",
            "parse-xml('<a/>')",
            "json-doc('file:///etc/passwd')",
            "system-property('user.home')",
            "matches('aaaa!', '(a+)+$')",
            "replace('a', 'a', 'b')",
            "tokenize('a b')",
            "analyze-string('a', 'a')"})
    void testFunctionsThatReachOutsideTheDocumentAreRefused(final String expression) {
        assertThatThrownBy(() -> SafeXPath.compile(expression, Map.of()))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("not available here");
    }

    @Test
    void testFunctionNamesThatOnlyLookAlikeAreAllowed() throws IOException {
        assertThat(evaluate("<a><doc>x</doc></a>", "string(/a/doc)", Map.of(), false)).containsExactly("x");
        assertThat(evaluate("<a><my-doc>y</my-doc></a>", "string(//my-doc)", Map.of(), false))
                .containsExactly("y");
    }

    @Test
    void testTheEngineRefusesToReadOutsideTheDocumentAnyway() throws IOException {
        final Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "TOP SECRET");
        final String uri = secret.toUri().toString();
        final Path xml = tempDir.resolve("a.xml");
        Files.writeString(xml, "<a/>");

        for (final String expression : List.of(
                "doc('" + uri + "')",
                "unparsed-text('" + uri + "')",
                "string-join(collection('" + tempDir.toUri() + "'), ',')",
                "string(environment-variable('PATH'))",
                "string-join(available-environment-variables(), ',')")) {
            final List<String> results = new ArrayList<>();
            try {
                SafeXPath.evaluate(SafeXPath.compileWithoutFunctionCheck(expression, Map.of()), SafeXPath.parse(xml),
                        false, results::add);
            } catch (final WorkspaceException e) {
                // Refused, as it should be.
            }
            assertThat(String.join(",", results))
                    .as(expression)
                    .doesNotContain("TOP SECRET")
                    .doesNotContain("/usr")
                    .doesNotContain("PATH");
        }
    }

    @Test
    void testBadExpressions() {
        assertThatThrownBy(() -> SafeXPath.compile("//[", Map.of())).hasMessageContaining("not a valid XPath");
        assertThatThrownBy(() -> SafeXPath.compile("", Map.of())).isInstanceOf(WorkspaceException.class);
        assertThatThrownBy(() -> SafeXPath.compile("/a".repeat(1_001), Map.of())).hasMessageContaining("at most");
    }

    private List<String> evaluate(final String xml,
                                  final String expression,
                                  final Map<String, String> namespaces,
                                  final boolean asXml) throws IOException {
        final Path file = Files.createTempFile(tempDir, "doc", ".xml");
        Files.writeString(file, xml.strip());
        final List<String> results = new ArrayList<>();
        SafeXPath.evaluate(SafeXPath.compile(expression, namespaces), SafeXPath.parse(file), asXml, results::add);
        return results;
    }
}
