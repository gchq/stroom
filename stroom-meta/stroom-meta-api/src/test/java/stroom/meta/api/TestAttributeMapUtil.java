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

package stroom.meta.api;

import stroom.test.common.TestUtil;
import stroom.util.cert.CertificateExtractor;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.concurrent.UniqueId;
import stroom.util.concurrent.UniqueId.NodeType;
import stroom.util.concurrent.UniqueIdGenerator;
import stroom.util.date.DateUtil;
import stroom.util.exception.ThrowingFunction;
import stroom.util.net.HostNameUtil;
import stroom.util.shared.NullSafe;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    void testRead_path(@TempDir final Path tempDir) throws IOException {
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
    void testRead_inputStream(@TempDir final Path tempDir) throws IOException {
        final String data = """
                three:four

                 Foo:Bar \s
                  FeEd: MY_FEED   \s
                 BAR:FOO \s
                TyPE:EVENTS
                one:two""";

        final Path file = tempDir.resolve("001.meta");

        Files.writeString(file, data, AttributeMapUtil.DEFAULT_CHARSET, StandardOpenOption.CREATE);

        try (final InputStream inputStream = Files.newInputStream(file)) {
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

    @Test
    void testMergeAttributeMaps1() {

        final UniqueIdGenerator uniqueIdGenerator = new UniqueIdGenerator(NodeType.STROOM, "node1");
        final String receiptId = uniqueIdGenerator.generateId().toString();
        final String receiptTime = DateUtil.createNormalDateTimeString();

        final AttributeMap attributeMap = AttributeMap.builder()
                .put(StandardHeaderArguments.FEED, "MY_FEED")
                .put(StandardHeaderArguments.TYPE, "EVENTS")
                .put(StandardHeaderArguments.RECEIPT_ID, receiptId)
                .put(StandardHeaderArguments.RECEIVED_TIME, receiptTime)
                .build();

        final AttributeMap attributeMap2 = AttributeMapUtil.mergeAttributeMaps(attributeMap, attrMap -> {
            // no-op
        });

        assertThat(attributeMap2)
                .isEqualTo(AttributeMap.builder()
                        .put(StandardHeaderArguments.FEED, "MY_FEED")
                        .put(StandardHeaderArguments.TYPE, "EVENTS")
                        .put(StandardHeaderArguments.RECEIPT_ID, receiptId)
                        .put(StandardHeaderArguments.RECEIPT_ID_PATH, receiptId) // Added
                        .put(StandardHeaderArguments.RECEIVED_TIME, receiptTime)
                        .put(StandardHeaderArguments.RECEIVED_TIME_HISTORY, receiptTime) // Added
                        .build());
    }

    @Test
    void testMergeAttributeMaps2() {
        final UniqueIdGenerator uniqueIdGenerator1 = new UniqueIdGenerator(NodeType.PROXY, "proxy1");
        final UniqueIdGenerator uniqueIdGenerator2 = new UniqueIdGenerator(NodeType.STROOM, "stroom1");

        final String receiptId1 = uniqueIdGenerator1.generateId().toString();
        final String receiptTime1 = DateUtil.createNormalDateTimeString();
        // Ensure we get two different times
        ThreadUtil.sleepIgnoringInterrupts(2);
        final String receiptId2 = uniqueIdGenerator2.generateId().toString();
        final String receiptTime2 = DateUtil.createNormalDateTimeString();
        final String host1 = "host1";
        final String host2 = "host2";

        final AttributeMap attributeMap = AttributeMap.builder()
                .put(StandardHeaderArguments.FEED, "MY_FEED")
                .put(StandardHeaderArguments.TYPE, "EVENTS")
                .put(StandardHeaderArguments.RECEIPT_ID, receiptId2)
                .put(StandardHeaderArguments.RECEIVED_TIME, receiptTime2)
                .put(StandardHeaderArguments.RECEIVED_PATH, host2)
                .build();

        final AttributeMap attributeMap2 = AttributeMapUtil.mergeAttributeMaps(attributeMap, attrMap -> {
            attrMap.put(StandardHeaderArguments.FEED, "OTHER_FEED");
            attrMap.put(StandardHeaderArguments.TYPE, "RAW_EVENTS");
            attrMap.put(StandardHeaderArguments.RECEIPT_ID, receiptId1);
            attrMap.put(StandardHeaderArguments.RECEIPT_ID_PATH, receiptId1);
            attrMap.put(StandardHeaderArguments.RECEIVED_TIME, receiptTime1);
            attrMap.put(StandardHeaderArguments.RECEIVED_TIME_HISTORY, receiptTime1);
            attrMap.put(StandardHeaderArguments.RECEIVED_PATH, host1);
        });

        assertThat(attributeMap2)
                .isEqualTo(AttributeMap.builder()
                        .put(StandardHeaderArguments.FEED, "OTHER_FEED")
                        .put(StandardHeaderArguments.TYPE, "RAW_EVENTS")
                        .put(StandardHeaderArguments.RECEIPT_ID, receiptId2)
                        .put(StandardHeaderArguments.RECEIPT_ID_PATH, receiptId1 + "," + receiptId2) // Added
                        .put(StandardHeaderArguments.RECEIVED_TIME, receiptTime2)
                        .put(StandardHeaderArguments.RECEIVED_TIME_HISTORY, receiptTime1 + "," + receiptTime2) // Added
                        .put(StandardHeaderArguments.RECEIVED_PATH, host1 + "," + host2) // Added
                        .build());
    }

    @Test
    void testValidateAndNormaliseCompression1() {
        final AttributeMap attributeMap = AttributeMap.builder()
                .build();

        final String compression = AttributeMapUtil.validateAndNormaliseCompression(
                attributeMap,
                compressionVal -> new RuntimeException("Bad"));

        assertThat(compression)
                .isNull();
    }

    @Test
    void testValidateAndNormaliseCompression2() {
        final AttributeMap attributeMap = AttributeMap.builder()
                .put(StandardHeaderArguments.COMPRESSION, "foo")
                .build();

        Assertions.assertThatThrownBy(
                        () -> AttributeMapUtil.validateAndNormaliseCompression(
                                attributeMap,
                                compressionVal -> new RuntimeException("Bad")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Bad");
    }

    @Test
    void testValidateAndNormaliseCompression3() {
        final AttributeMap attributeMap = AttributeMap.builder()
                .put(StandardHeaderArguments.COMPRESSION, "zIP")
                .build();

        final String compression = AttributeMapUtil.validateAndNormaliseCompression(
                attributeMap,
                compressionVal -> new RuntimeException("Bad"));

        assertThat(compression)
                .isEqualTo("ZIP");
    }

    @Test
    void testValidateAndNormaliseCompression4() {
        final AttributeMap attributeMap = AttributeMap.builder()
                .put(StandardHeaderArguments.COMPRESSION, "ZIP")
                .build();

        final String compression = AttributeMapUtil.validateAndNormaliseCompression(
                attributeMap,
                compressionVal -> new RuntimeException("Bad"));

        assertThat(compression)
                .isEqualTo("ZIP");
    }

    @Test
    void testCreate() {
        final HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        final CertificateExtractor mockCertificateExtractor = Mockito.mock(CertificateExtractor.class);
        final X509Certificate mockX509Certificate = Mockito.mock(X509Certificate.class);
        final Principal mockPrincipal = Mockito.mock(Principal.class);

        final Instant time = Instant.now();
        final UniqueIdGenerator uniqueIdGenerator = new UniqueIdGenerator(NodeType.PROXY, "proxy1");
        final UniqueId uniqueId = uniqueIdGenerator.generateId();
        final String subjectDn = "subDn";
        final Instant notAfter = time.plus(10, ChronoUnit.DAYS);
        final Date notAfterDate = new Date(notAfter.toEpochMilli());
        final Map<String, String> requestHeaders = Map.of(
                "Foo", "123",
                "Bar", "456");
        final String queryString = "name=ferret&colour=purple";
        final String remoteAddr = "192.168.0.1";
        final String remoteHost = "somehost.somedomain";
        final String hostname = HostNameUtil.determineHostName();

        Mockito.when(mockCertificateExtractor.extractCertificate(Mockito.any()))
                .thenReturn(Optional.of(mockX509Certificate));
        Mockito.when(mockX509Certificate.getSubjectDN())
                .thenReturn(mockPrincipal);
        Mockito.when(mockPrincipal.toString())
                .thenReturn(subjectDn);
        Mockito.when(mockX509Certificate.getNotAfter())
                .thenReturn(notAfterDate);

        Mockito.when(mockRequest.getHeaderNames())
                .thenReturn(Collections.enumeration(requestHeaders.keySet()));
        Mockito.when(mockRequest.getHeader(Mockito.anyString()))
                .thenAnswer(invocation -> {
                    final String key = invocation.getArgument(0, String.class);
                    return requestHeaders.get(key);
                });
        Mockito.when(mockRequest.getQueryString())
                .thenReturn(queryString);
        Mockito.when(mockRequest.getRemoteAddr())
                .thenReturn(remoteAddr);
        Mockito.when(mockRequest.getRemoteHost())
                .thenReturn(remoteHost);

        final AttributeMap attributeMap = AttributeMapUtil.create(
                mockRequest,
                mockCertificateExtractor,
                time,
                uniqueId);

        assertThat(attributeMap.get(StandardHeaderArguments.GUID))
                .isNotNull();
        assertThat(attributeMap.get(StandardHeaderArguments.REMOTE_ADDRESS))
                .isEqualTo(remoteAddr);
        assertThat(attributeMap.get(StandardHeaderArguments.REMOTE_HOST))
                .isEqualTo(remoteHost);
        // Request Headers
        assertThat(attributeMap.get("Foo"))
                .isEqualTo("123");
        assertThat(attributeMap.get("Bar"))
                .isEqualTo("456");
        // Request query params
        assertThat(attributeMap.get("name"))
                .isEqualTo("ferret");
        assertThat(attributeMap.get("colour"))
                .isEqualTo("purple");

        assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_PATH))
                .isEqualTo(hostname);
        assertThat(attributeMap.get(StandardHeaderArguments.RECEIPT_ID))
                .isNotNull();
        assertThat(attributeMap.get(StandardHeaderArguments.RECEIPT_ID_PATH))
                .isEqualTo(attributeMap.get(StandardHeaderArguments.RECEIPT_ID));
        assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_TIME))
                .isEqualTo(DateUtil.createNormalDateTimeString(time));
        assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_TIME_HISTORY))
                .isEqualTo(attributeMap.get(StandardHeaderArguments.RECEIVED_TIME));
    }

    @Test
    void testCreate2() {
        final HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        final CertificateExtractor mockCertificateExtractor = Mockito.mock(CertificateExtractor.class);
        final X509Certificate mockX509Certificate = Mockito.mock(X509Certificate.class);
        final Principal mockPrincipal = Mockito.mock(Principal.class);

        final Instant time1 = Instant.now();
        ThreadUtil.sleepIgnoringInterrupts(5);
        final Instant time2 = Instant.now();
        final UniqueIdGenerator uniqueIdGenerator = new UniqueIdGenerator(NodeType.PROXY, "proxy1");
        final UniqueId uniqueId1 = uniqueIdGenerator.generateId();
        final UniqueId uniqueId2 = uniqueIdGenerator.generateId();
        final String subjectDn = "subDn";
        final Instant notAfter = time2.plus(10, ChronoUnit.DAYS);
        final Date notAfterDate = new Date(notAfter.toEpochMilli());
        final String remoteAddr1 = "192.168.0.2";
        final String remoteHost1 = "somehost.someotherdomain";
        final String remoteAddr2 = "192.168.0.1";
        final String remoteHost2 = "somehost.somedomain";
        final String hostname1 = "host1";
        final String hostname2 = HostNameUtil.determineHostName();
        final Map<String, String> requestHeaders = Map.of(
                "Foo", "123",
                "Bar", "456",
                StandardHeaderArguments.GUID, "MY_GUID",
                StandardHeaderArguments.REMOTE_ADDRESS, remoteAddr1,
                StandardHeaderArguments.REMOTE_HOST, remoteHost1,
                StandardHeaderArguments.RECEIPT_ID, uniqueId1.toString(),
                StandardHeaderArguments.RECEIPT_ID_PATH, uniqueId1.toString(),
                StandardHeaderArguments.RECEIVED_TIME, DateUtil.createNormalDateTimeString(time1),
                StandardHeaderArguments.RECEIVED_TIME_HISTORY, DateUtil.createNormalDateTimeString(time1),
                StandardHeaderArguments.RECEIVED_PATH, hostname1);

        final String queryString = "name=ferret&colour=purple";

        Mockito.when(mockCertificateExtractor.extractCertificate(Mockito.any()))
                .thenReturn(Optional.of(mockX509Certificate));
        Mockito.when(mockX509Certificate.getSubjectDN())
                .thenReturn(mockPrincipal);
        Mockito.when(mockPrincipal.toString())
                .thenReturn(subjectDn);
        Mockito.when(mockX509Certificate.getNotAfter())
                .thenReturn(notAfterDate);

        Mockito.when(mockRequest.getHeaderNames())
                .thenReturn(Collections.enumeration(requestHeaders.keySet()));
        Mockito.when(mockRequest.getHeader(Mockito.anyString()))
                .thenAnswer(invocation -> {
                    final String key = invocation.getArgument(0, String.class);
                    return requestHeaders.get(key);
                });
        Mockito.when(mockRequest.getQueryString())
                .thenReturn(queryString);
        Mockito.when(mockRequest.getRemoteAddr())
                .thenReturn(remoteAddr2);
        Mockito.when(mockRequest.getRemoteHost())
                .thenReturn(remoteHost2);

        final AttributeMap attributeMap = AttributeMapUtil.create(
                mockRequest,
                mockCertificateExtractor,
                time2,
                uniqueId2);

        assertThat(attributeMap.get(StandardHeaderArguments.GUID))
                .isEqualTo("MY_GUID");
        assertThat(attributeMap.get(StandardHeaderArguments.REMOTE_ADDRESS))
                .isEqualTo(remoteAddr1);
        assertThat(attributeMap.get(StandardHeaderArguments.REMOTE_HOST))
                .isEqualTo(remoteHost1);
        // Request Headers
        assertThat(attributeMap.get("Foo"))
                .isEqualTo("123");
        assertThat(attributeMap.get("Bar"))
                .isEqualTo("456");
        // Request query params
        assertThat(attributeMap.get("name"))
                .isEqualTo("ferret");
        assertThat(attributeMap.get("colour"))
                .isEqualTo("purple");

        assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_PATH))
                .isEqualTo(String.join(",", hostname1, hostname2));
        assertThat(attributeMap.get(StandardHeaderArguments.RECEIPT_ID))
                .isEqualTo(uniqueId2.toString());
        assertThat(attributeMap.get(StandardHeaderArguments.RECEIPT_ID_PATH))
                .isEqualTo(String.join(",", uniqueId1.toString(), uniqueId2.toString()));
        assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_TIME))
                .isEqualTo(DateUtil.createNormalDateTimeString(time2));
        assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_TIME_HISTORY))
                .isEqualTo(String.join(",",
                        DateUtil.createNormalDateTimeString(time1), DateUtil.createNormalDateTimeString(time2)));
    }

    @Test
    void testCreate3() {
        final HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        final CertificateExtractor mockCertificateExtractor = Mockito.mock(CertificateExtractor.class);
        final X509Certificate mockX509Certificate = Mockito.mock(X509Certificate.class);
        final Principal mockPrincipal = Mockito.mock(Principal.class);

        final Instant time1 = Instant.now();
        ThreadUtil.sleepIgnoringInterrupts(5);
        final Instant time2 = Instant.now();
        final UniqueIdGenerator uniqueIdGenerator = new UniqueIdGenerator(NodeType.PROXY, "proxy1");
        final UniqueId uniqueId1 = uniqueIdGenerator.generateId();
        final UniqueId uniqueId2 = uniqueIdGenerator.generateId();
        final String subjectDn = "subDn";
        final Instant notAfter = time2.plus(10, ChronoUnit.DAYS);
        final Date notAfterDate = new Date(notAfter.toEpochMilli());
        final String remoteAddr1 = "192.168.0.2";
        final String remoteHost1 = "somehost.someotherdomain";
        final String remoteAddr2 = "192.168.0.1";
        final String remoteHost2 = "somehost.somedomain";
        final String hostname1 = "host1";
        final String hostname2 = HostNameUtil.determineHostName();
        // No receiptIdPath or receivedTimeHistory this time
        final Map<String, String> requestHeaders = Map.of(
                "Foo", "123",
                "Bar", "456",
                StandardHeaderArguments.GUID, "MY_GUID",
                StandardHeaderArguments.RECEIPT_ID, uniqueId1.toString(),
                StandardHeaderArguments.RECEIVED_TIME, DateUtil.createNormalDateTimeString(time1),
                StandardHeaderArguments.RECEIVED_PATH, hostname1);

        final String queryString = "name=ferret&colour=purple";

        Mockito.when(mockCertificateExtractor.extractCertificate(Mockito.any()))
                .thenReturn(Optional.of(mockX509Certificate));
        Mockito.when(mockX509Certificate.getSubjectDN())
                .thenReturn(mockPrincipal);
        Mockito.when(mockPrincipal.toString())
                .thenReturn(subjectDn);
        Mockito.when(mockX509Certificate.getNotAfter())
                .thenReturn(notAfterDate);

        Mockito.when(mockRequest.getHeaderNames())
                .thenReturn(Collections.enumeration(requestHeaders.keySet()));
        Mockito.when(mockRequest.getHeader(Mockito.anyString()))
                .thenAnswer(invocation -> {
                    final String key = invocation.getArgument(0, String.class);
                    return requestHeaders.get(key);
                });
        Mockito.when(mockRequest.getQueryString())
                .thenReturn(queryString);
        Mockito.when(mockRequest.getRemoteAddr())
                .thenReturn(remoteAddr2);
        Mockito.when(mockRequest.getRemoteHost())
                .thenReturn(remoteHost2);

        final AttributeMap attributeMap = AttributeMapUtil.create(
                mockRequest,
                mockCertificateExtractor,
                time2,
                uniqueId2);

        assertThat(attributeMap.get(StandardHeaderArguments.GUID))
                .isEqualTo("MY_GUID");
        // GUID was set, so it doesn't set remote host/addr
        assertThat(attributeMap.get(StandardHeaderArguments.REMOTE_ADDRESS))
                .isNull();
        assertThat(attributeMap.get(StandardHeaderArguments.REMOTE_HOST))
                .isNull();
        // Request Headers
        assertThat(attributeMap.get("Foo"))
                .isEqualTo("123");
        assertThat(attributeMap.get("Bar"))
                .isEqualTo("456");
        // Request query params
        assertThat(attributeMap.get("name"))
                .isEqualTo("ferret");
        assertThat(attributeMap.get("colour"))
                .isEqualTo("purple");

        assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_PATH))
                .isEqualTo(String.join(",", hostname1, hostname2));
        assertThat(attributeMap.get(StandardHeaderArguments.RECEIPT_ID))
                .isEqualTo(uniqueId2.toString());
        assertThat(attributeMap.get(StandardHeaderArguments.RECEIPT_ID_PATH))
                .isEqualTo(String.join(",", uniqueId1.toString(), uniqueId2.toString()));
        assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_TIME))
                .isEqualTo(DateUtil.createNormalDateTimeString(time2));
        assertThat(attributeMap.get(StandardHeaderArguments.RECEIVED_TIME_HISTORY))
                .isEqualTo(String.join(",",
                        DateUtil.createNormalDateTimeString(time1), DateUtil.createNormalDateTimeString(time2)));
    }
}
