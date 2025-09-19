package stroom.util.cert;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.servlet.http.HttpServletRequest;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

/**
 * For extracting certificates and/or DNs from {@link HttpServletRequest}s.
 */
public interface CertificateExtractor {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CertificateExtractor.class);

    Optional<String> getCN(HttpServletRequest request);

    Optional<String> getDN(HttpServletRequest request);

    List<X509Certificate> extractCertificates(HttpServletRequest request);

    default Optional<X509Certificate> extractFirstCertificate(final HttpServletRequest request) {
        return NullSafe.stream(extractCertificates(request))
                .findFirst();
    }

    /**
     * Given a DN and {@link DNFormat} pull out the CN. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB" and {@link DNFormat#LDAP} Would
     * return "some.server.co.uk"
     *
     * @return The CN name or an empty {@link Optional}
     */
    Optional<String> extractCNFromDN(final String dn);

}
