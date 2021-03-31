/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.cert;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

public class CertificateUtil {

    /**
     * API into the request for the certificate details.
     */
    private static final String X_SSL_CERT = "X-SSL-CERT";
    private static final String SERVLET_CERT_ARG = "javax.servlet.request.X509Certificate";
    private static final String X_SSL_CLIENT_S_DN = "X-SSL-CLIENT-S-DN";
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CertificateUtil.class);

    public static Optional<String> getCN(final HttpServletRequest request) {
        return getDN(request).map(CertificateUtil::extractCNFromDN);
    }

    public static Optional<String> getDN(final HttpServletRequest request) {
        // First see if we have a cert we can use.
        final Optional<X509Certificate> cert = extractCertificate(request);
        if (cert.isPresent()) {
            LOGGER.debug(() -> "Found certificate");
            return extractDNFromCertificate(cert.get());
        }

        final String clientDn = request.getHeader(X_SSL_CLIENT_S_DN);
        LOGGER.debug(() -> X_SSL_CLIENT_S_DN + " = " + clientDn);
        return Optional.ofNullable(clientDn);
    }

    /**
     * Pull out the Subject from the certificate. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB"
     */
    public static Optional<X509Certificate> extractCertificate(final ServletRequest request) {
        return extractCertificate(request, CertificateUtil.X_SSL_CERT)
                .or(() -> extractCertificate(request, SERVLET_CERT_ARG));
    }

    private static Optional<X509Certificate> extractCertificate(final ServletRequest request,
                                                                final String attribute) {
        final Object[] certs = (Object[]) request.getAttribute(attribute);
        if (certs != null) {
            LOGGER.debug(() -> "Found certificate using " + attribute + " header");
            return extractCertificate(certs);
        }
        return Optional.empty();
    }

    /**
     * Pull out the Subject from the certificate. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB"
     *
     * @param certs ARGS from the SERVLET request.
     */
    private static Optional<X509Certificate> extractCertificate(final Object[] certs) {
        for (final Object cert : certs) {
            if (cert instanceof X509Certificate) {
                return Optional.of((X509Certificate) cert);
            }
        }
        return Optional.empty();
    }

    /**
     * Given a cert pull out the DN. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB"
     *
     * @return null or the CN name
     */
    private static Optional<String> extractDNFromCertificate(final X509Certificate cert) {
        return Optional.ofNullable(cert.getSubjectDN().getName());
    }

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

//    /**
//     * User ID's are embedded in brackets at the end.
//     */
//    private static String extractUserIdFromCN(final String cn) {
//        if (cn == null) {
//            return null;
//        }
//        final int startPos = cn.indexOf('(');
//        final int endPos = cn.indexOf(')');
//
//        if (startPos != -1 && endPos != -1 && startPos < endPos) {
//            return cn.substring(startPos + 1, endPos);
//        }
//        return cn;
//
//    }
//
//    /**
//     * User ID's are embedded in brackets at the end.
//     */
//    private static String extractUserIdFromDN(final String dn, final Pattern pattern) {
//        final String normalisedDN = dnToRfc2253(dn);
//        final Matcher matcher = pattern.matcher(normalisedDN);
//        if (matcher.find()) {
//            return matcher.group(1);
//        }
//
//        return null;
//    }
//
//    /**
//     * Normalise an RFC 2253 Distinguished Name so that it is consistent. Note
//     * that the values in the fields should not be normalised - they are
//     * case-sensitive.
//     *
//     * @param dn Distinguished Name to normalise. Must be RFC 2253-compliant
//     * @return The DN in RFC 2253 format, with a consistent case for the field
//     * names and separation
//     */
//    private static String dnToRfc2253(final String dn) {
//        if (LOGGER.isTraceEnabled()) {
//            LOGGER.trace("Normalising DN: " + dn);
//        }
//
//        if (dn == null) {
//            return null;
//        }
//
//        if (dn.equalsIgnoreCase("anonymous")) {
//            LOGGER.trace("Anonymous is a special case - returning as-is");
//            return dn;
//        }
//
//        try {
//            final X500Principal x500 = new X500Principal(dn);
//            final String normalised = x500.getName();
//            if (LOGGER.isTraceEnabled()) {
//                LOGGER.trace("Normalised DN: " + normalised);
//            }
//            return normalised;
//        } catch (final IllegalArgumentException e) {
//            LOGGER.error("Provided value is not a valid Distinguished Name; it will be returned as-is: " + dn, e);
//            return dn;
//        }
//    }
}
