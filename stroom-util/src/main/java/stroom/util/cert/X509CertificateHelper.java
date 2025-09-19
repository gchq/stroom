package stroom.util.cert;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import org.eclipse.jetty.util.security.CertificateValidator;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class X509CertificateHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(X509CertificateHelper.class);

    public static List<X509Certificate> parseX509Certificates(final String str,
                                                              final EncodingType encodingType) {
        try {
            if (NullSafe.isNonEmptyString(str)) {
                final byte[] decodedHeaderBytes = switch (encodingType) {
                    case URL_ENCODED -> URLDecoder.decode(str, StandardCharsets.UTF_8)
                            .getBytes(StandardCharsets.UTF_8);
                    case BASE64_ENCODED -> Base64.getDecoder().decode(str);
                    case PLAIN_TEXT -> str.getBytes(StandardCharsets.UTF_8);
                };
                LOGGER.debug("extractCertificateFromHeader() - headerName: {}, decodedHeaderValue: {}",
                        str, decodedHeaderBytes);

                final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(decodedHeaderBytes);
                final Collection<? extends Certificate> certs = certificateFactory.generateCertificates(
                        byteArrayInputStream);
                final List<X509Certificate> x509Certs = NullSafe.stream(certs)
                        .map(obj -> switch (obj) {
                            case final X509Certificate x509Certificate -> {
                                LOGGER.debug(() -> LogUtil.message(
                                        "extractCertificateFromHeader() - Extracted certificate '{}', validTo: {}",
                                        x509Certificate.getSubjectX500Principal().getName(),
                                        x509Certificate.getNotAfter()));
                                yield x509Certificate;
                            }
                            case null, default -> null;
                        })
                        .filter(Objects::nonNull)
                        .toList();

                LOGGER.debug(() -> LogUtil.message(
                        "extractCertificateFromHeader() - Extracted {} certificates",
                        x509Certs.size()));
                return x509Certs;
            } else {
                return Collections.emptyList();
            }
        } catch (final CertificateException e) {
            throw new RuntimeException("Error extracting certificate from string '"
                                       + str + "' - " + LogUtil.exceptionMessage(e), e);
        }
    }

    public void validateCertificates(final List<X509Certificate> certificates,
                                     final KeyStore trustStore,
                                     final CertVerificationConfig certVerificationConfig) {
        if (NullSafe.hasItems(certificates)) {
            if (certVerificationConfig.isValidateClientCertificateExpiry()) {
                for (final X509Certificate cert : certificates) {
                    try {
                        cert.checkValidity();
                    } catch (final CertificateExpiredException e) {
                        throw new RuntimeException(
                                "Client certificate '" + cert.getSubjectX500Principal().getName() + "' has expired", e);
                    } catch (final CertificateNotYetValidException e) {
                        throw new RuntimeException(
                                "Client certificate '"
                                + cert.getSubjectX500Principal().getName() + "' is not yet valid", e);
                    }
                }
            }
            if (trustStore != null) {
//            final CertPathValidator certPathValidator = CertPathValidator.getInstance("PKIX");
//            certPathValidator.getRevocationChecker();
//            certPathValidator.getRevocationChecker()

                final CertificateValidator certificateValidator = new CertificateValidator(
                        trustStore,
                        Collections.emptyList());
                try {
                    // TODO copy the code in CertificateValidator so we have more control of what it is doing
                    //  and the re-use of the revocation checker.
                    // TODO It appears the PKIXRevocationChecker caches revocation checks but we need
                    //  to make sure CertificateValidator is using the same revocation checker
                    // TODO add a prop for a list of CRL files
                    // TODO add a prop to explicitly set the OCSP responder URL
                    // TODO add an enum prop for revocation checking (OCSP_AND_CRL, OCSP, CRL, NONE)
                    // TODO add a prop for max chain length
                    certificateValidator.setMaxCertPathLength(-1);
//                    certificateValidator.setOcspResponderURL("TODO");
                    certificateValidator.setEnableOCSP(true);
                    certificateValidator.setEnableCRLDP(false);
                    certificateValidator.validate(certificates.toArray(Certificate[]::new));
                } catch (final CertificateException e) {
                    final List<String> certNames = certificates.stream()
                            .map(cert -> cert.getSubjectX500Principal().getName())
                            .toList();
                    throw new RuntimeException(LogUtil.message("Certificates {} failed validation: {}",
                            certNames, LogUtil.exceptionMessage(e)), e);
                }
            }
        }
    }
}
