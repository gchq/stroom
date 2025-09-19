package stroom.receive.common;

import stroom.test.common.TestUtil;
import stroom.util.cert.CertificateExtractor;
import stroom.util.cert.DNFormat;
import stroom.util.cert.X509CertificateHelper;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.security.auth.x500.X500Principal;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestCertificateExtractorImpl {

    private ReceiveDataConfig receiveDataConfig = new ReceiveDataConfig();

    @Mock
    private HttpServletRequest mockHttpServletRequest;
    @Mock
    private X509Certificate mockX509Certificate;
    @Mock
    private X509CertificateHelper mockX509CertificateHelper;
    @Mock
    private X500Principal mockPrincipal;
    @TempDir
    private Path tempTestDir;

    @BeforeEach
    void setUp() {
        receiveDataConfig = new ReceiveDataConfig();
    }

    @Test
    void getDN_fromX509CertHeader() {
        final CertificateExtractor certificateExtractor = createCertificateExtractor();
        final String myDn = "this is my DN";
//        Mockito.when(mockHttpServletRequest.getAttribute(receiveDataConfig.getX509CertificateHeader()))
//                .thenReturn(new Object[]{mockX509Certificate});
        Mockito.when(mockHttpServletRequest.getHeader(receiveDataConfig.getX509CertificateHeader()))
                .thenReturn("mock cert");
        Mockito.when(mockX509CertificateHelper.parseX509Certificates(Mockito.anyString(), Mockito.any()))
                .thenReturn(List.of(mockX509Certificate));
        Mockito.when(mockX509Certificate.getSubjectX500Principal())
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

        final CertificateExtractor certificateExtractor = createCertificateExtractor();
        final String myDn = "this is my DN";
//        Mockito.when(mockHttpServletRequest.getHeader(receiveDataConfig.getX509CertificateHeader()))
//                .thenReturn(new Object[]{mockX509Certificate});
        Mockito.when(mockHttpServletRequest.getHeader(receiveDataConfig.getX509CertificateHeader()))
                .thenReturn("mock cert");
        Mockito.when(mockX509CertificateHelper.parseX509Certificates(Mockito.anyString(), Mockito.any()))
                .thenReturn(List.of(mockX509Certificate));
        Mockito.when(mockHttpServletRequest.getRemoteHost())
                .thenReturn("host1");
        Mockito.when(mockX509Certificate.getSubjectX500Principal())
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

        final CertificateExtractor certificateExtractor = createCertificateExtractor();
        final String myDn = "this is my DN";
//        Mockito.when(mockHttpServletRequest.getAttribute(receiveDataConfig.getX509CertificateHeader()))
//                .thenReturn(new Object[]{mockX509Certificate});
        Mockito.when(mockHttpServletRequest.getHeader(receiveDataConfig.getX509CertificateHeader()))
                .thenReturn("mock cert");
        Mockito.when(mockX509CertificateHelper.parseX509Certificates(Mockito.anyString(), Mockito.any()))
                .thenReturn(List.of(mockX509Certificate));
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
        final CertificateExtractor certificateExtractor = createCertificateExtractor();
        final String myDn = "this is my DN";
        Mockito.when(mockHttpServletRequest.getHeader(receiveDataConfig.getX509CertificateHeader()))
                .thenReturn("mock cert");
//        Mockito.when(mockHttpServletRequest.getAttribute(CertificateExtractorImpl.SERVLET_CERT_ARG))
//                .thenReturn(new Object[]{mockX509Certificate});
        Mockito.when(mockX509CertificateHelper.parseX509Certificates(Mockito.anyString(), Mockito.any()))
                .thenReturn(List.of(mockX509Certificate));
        Mockito.when(mockX509Certificate.getSubjectX500Principal())
                .thenReturn(mockPrincipal);
        Mockito.when(mockPrincipal.getName())
                .thenReturn(myDn);
        final Optional<String> optDn = certificateExtractor.getDN(mockHttpServletRequest);
        assertThat(optDn)
                .hasValue(myDn);
    }

    @Test
    void getDN_fromDnHeader() {
        final CertificateExtractor certificateExtractor = createCertificateExtractor();
        final String myDn = "this is my DN";
        Mockito.when(mockHttpServletRequest.getHeader(Mockito.eq(receiveDataConfig.getX509CertificateHeader())))
                .thenReturn(null);
        Mockito.when(mockHttpServletRequest.getAttribute(Mockito.eq(CertificateExtractorImpl.SERVLET_CERT_ARG)))
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
                                    .build(),
                            createPathCreator(),
                            mockX509CertificateHelper);

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

    private CertificateExtractorImpl createCertificateExtractor() {
        return new CertificateExtractorImpl(
                () -> receiveDataConfig,
                createPathCreator(),
                mockX509CertificateHelper);
    }

    private PathCreator createPathCreator() {
        return new SimplePathCreator(
                () -> tempTestDir.resolve("home"),
                () -> tempTestDir.resolve("temp"));
    }

    @Test
    void name() {

        final Provider[] providers = Security.getProviders();
        for (final Provider provider : providers) {
            System.out.println(provider.getName());
        }
    }
}
