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

package stroom.query.language.functions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionParserUris extends AbstractExpressionParserTest {

    @Test
    void testExtractAuthorityFromUri() {
        test("extractAuthorityFromUri(${val1})",
                "http://www.example.com:1234/this/is/a/path",
                "www.example.com:1234");
    }

    @Test
    void testExtractFragmentFromUri() {
        test("extractFragmentFromUri(${val1})",
                "http://www.example.com:1234/this/is/a/path#frag",
                "frag");
    }

    @Test
    void testExtractHostFromUri() {
        test("extractHostFromUri(${val1})",
                "http://www.example.com:1234/this/is/a/path",
                "www.example.com");
    }

    @Test
    void testExtractPathFromUri() {
        test("extractPathFromUri(${val1})",
                "http://www.example.com:1234/this/is/a/path",
                "/this/is/a/path");
    }

    @Test
    void testExtractPortFromUri() {
        test("extractPortFromUri(${val1})",
                "http://www.example.com:1234/this/is/a/path",
                "1234");
    }

    @Test
    void testExtractQueryFromUri() {
        test("extractQueryFromUri(${val1})",
                "http://www.example.com:1234/this/is/a/path?this=that&foo=bar",
                "this=that&foo=bar");
    }

    @Test
    void testExtractSchemeFromUri() {
        test("extractSchemeFromUri(${val1})",
                "http://www.example.com:1234/this/is/a/path",
                "http");
    }

    @Test
    void testExtractSchemeSpecificPartFromUri() {
        test("extractSchemeSpecificPartFromUri(${val1})",
                "http://www.example.com:1234/this/is/a/path",
                "//www.example.com:1234/this/is/a/path");
    }

    @Test
    void testExtractUserInfoFromUri() {
        test("extractUserInfoFromUri(${val1})",
                "http://john:doe@example.com:81/",
                "john:doe");
    }

    private void test(final String expression, final String value, final String expected) {
        compute(expression,
                Val.of(value),
                out -> assertThat(out.toString()).isEqualTo(expected));
    }
}
