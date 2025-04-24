package stroom.meta.api;

import stroom.test.common.TestUtil;
import stroom.util.NullSafe;
import stroom.util.exception.ThrowingFunction;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
        final AttributeMap attributeMap1 = new AttributeMap(Map.of(
                "foo", "123"));

        final AttributeMap attributeMap2 = new AttributeMap(Map.of(
                "foo", "123",
                "bar", "456"));
        final AttributeMap attributeMap3 = new AttributeMap(Map.of(
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
        final AttributeMap attributeMap1 = new AttributeMap(Map.of(
                "foo", "123"));

        final AttributeMap attributeMap2 = new AttributeMap(Map.of(
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

    @TestFactory
    Stream<DynamicTest> testReadKeys() {
        final String data1 = """
                three:four

                 Foo:Bar \s
                  FeEd: MY_FEED   \s
                 BAR:FOO \s
                TyPE:EVENTS
                one:two""";
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<String, List<String>>>() {
                })
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(ThrowingFunction.unchecked(testCase -> {
                    final String data = testCase.getInput()._1;
                    final List<String> keys = testCase.getInput()._2;
                    return AttributeMapUtil.readKeys(data, keys);
                }))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, List.of("Feed", "type")), List.of())
                .addCase(Tuple.of("", List.of("Feed", "type")), List.of())
                .addCase(Tuple.of(data1, List.of("Feed")), List.of("MY_FEED"))
                .addCase(Tuple.of(data1, List.of("FEED")), List.of("MY_FEED"))
                .addCase(Tuple.of(data1, List.of("type")), List.of("EVENTS"))
                .addCase(Tuple.of(data1, List.of("TYPE")), List.of("EVENTS"))
                .addCase(Tuple.of(data1, List.of("FEED", "TYPE")), List.of("MY_FEED", "EVENTS"))
                .addCase(Tuple.of(data1, List.of("feed", "type")), List.of("MY_FEED", "EVENTS"))
                .addCase(Tuple.of(data1, List.of("type", "feed")), List.of("EVENTS", "MY_FEED"))
                .addCase(Tuple.of(data1, List.of("notHere")), Collections.singletonList(null))
                .addCase(Tuple.of(data1, List.of("notHere", "notThere")), Arrays.asList(null, null))
                .build();
    }

    @Test
    void testRead_path(@TempDir Path tempDir) throws IOException {
        final String data = """
                three:four

                 Foo:Bar \s
                  FeEd: MY_FEED   \s
                 BAR:FOO \s
                TyPE:EVENTS
                one:two""";

        final Path file = tempDir.resolve("001.meta");

        Files.writeString(file, data, AttributeMapUtil.DEFAULT_CHARSET, StandardOpenOption.CREATE);

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read(file, attributeMap);

        assertThat(attributeMap)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "three", "four",
                        "Foo", "Bar",
                        "FeEd", "MY_FEED",
                        "BAR", "FOO",
                        "TyPE", "EVENTS",
                        "one", "two"));
    }

    @Test
    void testRead_inputStream(@TempDir Path tempDir) throws IOException {
        final String data = """
                three:four

                 Foo:Bar \s
                  FeEd: MY_FEED   \s
                 BAR:FOO \s
                TyPE:EVENTS
                one:two""";

        final Path file = tempDir.resolve("001.meta");

        Files.writeString(file, data, AttributeMapUtil.DEFAULT_CHARSET, StandardOpenOption.CREATE);

        try (InputStream inputStream = Files.newInputStream(file)) {
            final AttributeMap attributeMap = new AttributeMap();
            AttributeMapUtil.read(inputStream, attributeMap);

            assertThat(attributeMap)
                    .containsExactlyInAnyOrderEntriesOf(Map.of(
                            "three", "four",
                            "Foo", "Bar",
                            "FeEd", "MY_FEED",
                            "BAR", "FOO",
                            "TyPE", "EVENTS",
                            "one", "two"));
        }
    }

    @Test
    void testRead_string() {
        final String data = """
                three:four

                 Foo:Bar \s
                  FeEd: MY_FEED   \s
                 BAR:FOO \s
                TyPE:EVENTS
                one:two""";

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read(data, attributeMap);

        assertThat(attributeMap)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "three", "four",
                        "Foo", "Bar",
                        "FeEd", "MY_FEED",
                        "BAR", "FOO",
                        "TyPE", "EVENTS",
                        "one", "two"));
    }
}
