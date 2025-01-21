package stroom.receive.common;

import stroom.util.cert.CertificateExtractor;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.Set;

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

}
