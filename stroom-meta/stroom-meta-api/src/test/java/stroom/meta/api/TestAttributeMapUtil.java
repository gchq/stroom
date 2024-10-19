/*
 * Copyright 2024 Crown Copyright
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

package stroom.meta.api;

import stroom.test.common.TestUtil;
import stroom.util.NullSafe;
import stroom.util.exception.ThrowingFunction;
import stroom.util.shared.string.CIKey;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestAttributeMapUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAttributeMapUtil.class);

    @Test
    void testDefaultLocale() {
        LOGGER.info("Default locale = " + Locale.getDefault(Locale.Category.FORMAT));
    }

    @Test
    void testDataFormatter1() {
        final String inputDateStr = "Sep  9 16:16:45 2018 GMT";
        final LocalDateTime outputDateTime = LocalDateTime.parse(
                inputDateStr, AttributeMapUtil.CERT_EXPIRY_DATE_FORMATTER);
        final LocalDateTime expectedDateTime = LocalDateTime.of(
                2018, 9, 9, 16, 16, 45);
        assertThat(outputDateTime).isEqualTo(expectedDateTime);
    }

    @Test
    void testDataFormatter2() {
        final String inputDateStr = "Sep 10 06:39:20 2292 GMT";
        final LocalDateTime outputDateTime = LocalDateTime.parse(
                inputDateStr, AttributeMapUtil.CERT_EXPIRY_DATE_FORMATTER);
        final LocalDateTime expectedDateTime = LocalDateTime.of(
                2292, 9, 10, 6, 39, 20);
        assertThat(outputDateTime).isEqualTo(expectedDateTime);
    }

    @TestFactory
    Stream<DynamicTest> testCreate_fromString() {
        final AttributeMap attributeMap1 = new AttributeMap(CIKey.mapOf(
                "foo", "123"));

        final AttributeMap attributeMap2 = new AttributeMap(CIKey.mapOf(
                "foo", "123",
                "bar", "456"));
        final AttributeMap attributeMap3 = new AttributeMap(CIKey.mapOf(
                "files", "/some/path/file1,/some/path/file2,/some/path/file3",
                "foo", "123"));

        final AttributeMap attributeMapEmpty = new AttributeMap();

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(AttributeMap.class)
                .withTestFunction(testCase ->
                        AttributeMapUtil.create(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, attributeMapEmpty)
                .addCase("", attributeMapEmpty)
                .addCase(" ", attributeMapEmpty)
                .addCase("foo:123", attributeMap1)
                .addCase(" foo : 123 ", attributeMap1)
                .addCase(" FOO : 123 ", attributeMap1)
                .addCase("""

                         FOO : 123

                        """, attributeMap1)
                .addCase("""
                         foo:123
                        FOO :   123  """, attributeMap1) // dup key, same val
                .addCase("""
                        foo:999
                        FOO:123""", attributeMap1) // dup key, diff val
                .addCase("""
                        FOO:123
                        BAR:456""", attributeMap2)
                .addCase("""
                        FOO:999
                          BAR : 456
                        foo:123""", attributeMap2) // dup key
                .addCase("""

                        FOO:123
                        BAR:456

                        """, attributeMap2) // empty lines
                .addCase("""
                        files:/some/path/file1,/some/path/file2,/some/path/file3
                        foo:123
                        """, attributeMap3) // empty lines
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCreate_fromInputStream() {
        final AttributeMap attributeMap1 = new AttributeMap(CIKey.mapOf(
                "foo", "123"));

        final AttributeMap attributeMap2 = new AttributeMap(CIKey.mapOf(
                "foo", "123",
                "bar", "456"));

        final AttributeMap attributeMapEmpty = new AttributeMap();

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(AttributeMap.class)
                .withTestFunction(ThrowingFunction.unchecked(testCase -> {
                    final InputStream inputStream = NullSafe.get(
                            testCase.getInput(),
                            input -> IOUtils.toInputStream(input, Charset.defaultCharset()));

                    return AttributeMapUtil.create(inputStream);
                }))
                .withSimpleEqualityAssertion()
                .addCase(null, attributeMapEmpty)
                .addCase("", attributeMapEmpty)
                .addCase(" ", attributeMapEmpty)
                .addCase("foo:123", attributeMap1)
                .addCase(" foo : 123 ", attributeMap1)
                .addCase(" FOO : 123 ", attributeMap1)
                .addCase("""
                        FOO:123
                        BAR:456""", attributeMap2)
                .build();
    }
}
