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

package stroom.receive.common;

import stroom.test.common.TestUtil;
import stroom.util.cert.CertificateExtractor;
import stroom.util.cert.DNFormat;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestCertificateExtractorImpl {

    private ReceiveDataConfig receiveDataConfig = new ReceiveDataConfig();

    @Mock
    private HttpServletRequest mockHttpServletRequest;
    @Mock
    private X509Certificate mockX509Certificate;
    @Mock
    private Principal mockPrincipal;

    @BeforeEach
    void setUp() {
        receiveDataConfig = new ReceiveDataConfig();
    }

    @Test
    void getDN_fromX509CertHeader() {
        final CertificateExtractor certificateExtractor = new CertificateExtractorImpl(() -> receiveDataConfig);
        final String myDn = "this is my DN";
        Mockito.when(mockHttpServletRequest.getAttribute(receiveDataConfig.getX509CertificateHeader()))
                .thenReturn(new Object[]{mockX509Certificate});
        Mockito.when(mockX509Certificate.getSubjectDN())
                .thenReturn(mockPrincipal);
        Mockito.when(mockPrincipal.getName())
                .thenReturn(myDn);
        final Optional<String> optDn = certificateExtractor.getDN(mockHttpServletRequest);
        assertThat(optDn)
                .hasValue(myDn);
    }

    @Test
    void getDN_fromX509CertHeader_withAllowList_allowed() {
        receiveDataConfig = ReceiveDataConfig.copy(receiveDataConfig)
                .withAllowedCertificateProviders(Set.of("host1", "host2"))
                .build();

        final CertificateExtractor certificateExtractor = new CertificateExtractorImpl(() -> receiveDataConfig);
        final String myDn = "this is my DN";
        Mockito.when(mockHttpServletRequest.getAttribute(receiveDataConfig.getX509CertificateHeader()))
                .thenReturn(new Object[]{mockX509Certificate});
        Mockito.when(mockHttpServletRequest.getRemoteHost())
                .thenReturn("host1");
        Mockito.when(mockX509Certificate.getSubjectDN())
                .thenReturn(mockPrincipal);
        Mockito.when(mockPrincipal.getName())
                .thenReturn(myDn);
        final Optional<String> optDn = certificateExtractor.getDN(mockHttpServletRequest);
        assertThat(optDn)
                .hasValue(myDn);
    }

    @Test
    void getDN_fromX509CertHeader_withAllowList_notAllowed() {
        receiveDataConfig = ReceiveDataConfig.copy(receiveDataConfig)
                .withAllowedCertificateProviders(Set.of("host1", "host2"))
                .build();

        final CertificateExtractor certificateExtractor = new CertificateExtractorImpl(() -> receiveDataConfig);
        final String myDn = "this is my DN";
        Mockito.when(mockHttpServletRequest.getAttribute(receiveDataConfig.getX509CertificateHeader()))
                .thenReturn(new Object[]{mockX509Certificate});
        Mockito.when(mockHttpServletRequest.getRemoteHost())
                .thenReturn("bad-host");
        Mockito.when(mockHttpServletRequest.getRemoteAddr())
                .thenReturn("192.168.1.3");

        final Optional<String> optDn = certificateExtractor.getDN(mockHttpServletRequest);
        assertThat(optDn)
                .isEmpty();
    }

    @Test
    void getDN_fromServletHeader() {
        final CertificateExtractor certificateExtractor = new CertificateExtractorImpl(() -> receiveDataConfig);
        final String myDn = "this is my DN";
        Mockito.when(mockHttpServletRequest.getAttribute(receiveDataConfig.getX509CertificateHeader()))
                .thenReturn(null);
        Mockito.when(mockHttpServletRequest.getAttribute(CertificateExtractorImpl.SERVLET_CERT_ARG))
                .thenReturn(new Object[]{mockX509Certificate});
        Mockito.when(mockX509Certificate.getSubjectDN())
                .thenReturn(mockPrincipal);
        Mockito.when(mockPrincipal.getName())
                .thenReturn(myDn);
        final Optional<String> optDn = certificateExtractor.getDN(mockHttpServletRequest);
        assertThat(optDn)
                .hasValue(myDn);
    }

    @Test
    void getDN_fromDnHeader() {
        final CertificateExtractor certificateExtractor = new CertificateExtractorImpl(() -> receiveDataConfig);
        final String myDn = "this is my DN";
        Mockito.when(mockHttpServletRequest.getAttribute(receiveDataConfig.getX509CertificateHeader()))
                .thenReturn(null);
        Mockito.when(mockHttpServletRequest.getAttribute(CertificateExtractorImpl.SERVLET_CERT_ARG))
                .thenReturn(null);
        Mockito.when(mockHttpServletRequest.getHeader(receiveDataConfig.getX509CertificateDnHeader()))
                .thenReturn(myDn);
        final Optional<String> optDn = certificateExtractor.getDN(mockHttpServletRequest);
        assertThat(optDn)
                .hasValue(myDn);
    }

    @TestFactory
    Stream<DynamicTest> testExtractCNFromDN() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<String, DNFormat>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final CertificateExtractor certificateExtractor = new CertificateExtractorImpl(() ->
                            ReceiveDataConfig.builder()
                                    .withX509CertificateDnFormat(testCase.getInput()._2)
                                    .build());

                    return certificateExtractor.extractCNFromDN(testCase.getInput()._1)
                            .orElse(null);
                })
                .withSimpleEqualityAssertion()
                .addNamedCase(
                        "testSpaceInCN",
                        Tuple.of("CN=John Smith (johnsmith), OU=ouCode1, OU=ouCode2, O=oValue, C=GB", DNFormat.LDAP),
                        "John Smith (johnsmith)")
                .addNamedCase(
                        "testExtractCNLDAPFormatWithOverlappingDelimiter",
                        Tuple.of("CN=John Smith //(johnsmith), OU=ouCode1, OU=ouCode2, O=oValue, C=GB", DNFormat.LDAP),
                        "John Smith //(johnsmith)")
                .addNamedCase(
                        "testExtractCNOpenSSLOnelineFormat",
                        Tuple.of("/C=UK/L=Test Locality/O=Test Organization/CN=Log Sender", DNFormat.OPEN_SSL),
                        "Log Sender")
                .addNamedCase(
                        "testExtractCNOpenSSLOnelineFormatWithOverlappingDelimiter",
                        Tuple.of("/C=UK/L=Test ,Locality/O=Test Organization/CN=Log Sender", DNFormat.OPEN_SSL),
                        "Log Sender")
                .addNamedCase(
                        "testExtractCNFromNullDN",
                        Tuple.of(null, DNFormat.LDAP),
                        null)
                .addNamedCase(
                        "testExtractCNFromMalformedDN",
                        Tuple.of("CNJohn Doe,OU=Users,O=Example", DNFormat.LDAP),
                        null)
                .addNamedCase(
                        "testExtractCNWithExtraSpacesAndMixedCase",
                        Tuple.of(" cn = Jane Smith , ou = Users , o = Example ", DNFormat.LDAP),
                        "Jane Smith")
                .build();
    }
}
