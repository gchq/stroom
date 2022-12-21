package stroom.meta.api;

import stroom.test.common.TestUtil;
import stroom.util.NullSafe;
import stroom.util.exception.ThrowingFunction;

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
import java.util.Map;
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
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(Map.of(
                "foo", "123"));

        final AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.putAll(Map.of(
                "foo", "123",
                "bar", "456"));

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
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCreate_fromInputStream() {
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.putAll(Map.of(
                "foo", "123"));

        final AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.putAll(Map.of(
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
