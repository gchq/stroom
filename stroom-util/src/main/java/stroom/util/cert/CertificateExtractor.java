package stroom.util.cert;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;

/**
 * For extracting certificates and/or DNs from {@link HttpServletRequest}s.
 */
public interface CertificateExtractor {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CertificateExtractor.class);

    Optional<String> getCN(HttpServletRequest request);

    Optional<String> getDN(HttpServletRequest request);

    Optional<X509Certificate> extractCertificate(ServletRequest request);

    /**
     * Given a DN pull out the CN. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB" Would
     * return "some.server.co.uk"
     *
     * @return null or the CN name
     */
    static String extractCNFromDN(final String dn) {
        LOGGER.debug(() -> "extractCNFromDN DN = " + dn);

        if (dn == null) {
            return null;
        }
        final StringTokenizer attributes = new StringTokenizer(dn, ",");
        final Map<String, String> map = new HashMap<>();
        while (attributes.hasMoreTokens()) {
            final String token = attributes.nextToken();
            if (token.contains("=")) {
                final String[] parts = token.split("=");
                if (parts.length == 2) {
                    map.put(parts[0].trim().toUpperCase(), parts[1].trim());
                }
            }
        }
        final String cn = map.get("CN");
        LOGGER.debug(() -> "extractCNFromDN CN = " + cn);

        return cn;
    }
}
