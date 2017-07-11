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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.x500.X500Principal;
import javax.servlet.ServletRequest;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CertificateUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateUtil.class);

    /**
     * API into the request for the certificate details.
     */
    public static final String SERVLET_CERT_ARG = "javax.servlet.request.X509Certificate";

    /**
     * Do all the below in 1 go !
     */
    public static String extractCertificateDN(final ServletRequest request) {
        return extractDNFromCertificate(extractCertificate(request));
    }

    /**
     * Pull out the Subject from the certificate. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB"
     */
    public static java.security.cert.X509Certificate extractCertificate(final ServletRequest request) {
        final Object[] certs = (Object[]) request.getAttribute(CertificateUtil.SERVLET_CERT_ARG);

        return CertificateUtil.extractCertificate(certs);
    }

    /**
     * Pull out the Subject from the certificate. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB"
     *
     * @param certs ARGS from the SERVLET request.
     */
    public static java.security.cert.X509Certificate extractCertificate(final Object[] certs) {
        if (certs != null) {
            for (final Object certO : certs) {
                if (certO instanceof java.security.cert.X509Certificate) {
                    final java.security.cert.X509Certificate jCert = (java.security.cert.X509Certificate) certO;
                    return jCert;
                }
            }
        }
        return null;
    }

    /**
     * Given a cert pull out the DN. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB"
     *
     * @return null or the CN name
     */
    public static String extractDNFromCertificate(final X509Certificate cert) {
        if (cert == null) {
            return null;
        }
        return cert.getSubjectDN().getName();
    }

    /**
     * Given a cert pull out the expiry date.
     *
     * @return null or the CN name
     */
    public static Long extractExpiryDateFromCertificate(final X509Certificate cert) {
        if (cert != null) {
            final Date date = cert.getNotAfter();
            if (date != null) {
                return date.getTime();
            }
        }
        return null;
    }

    /**
     * Given a DN pull out the CN. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB" Would
     * return "some.server.co.uk"
     *
     * @return null or the CN name
     */
    public static String extractCNFromDN(final String dn) {
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
        return map.get("CN");
    }

    /**
     * User ID's are embedded in brackets at the end.
     */
    public static String extractUserIdFromCN(final String cn) {
        if (cn == null) {
            return null;
        }
        final int startPos = cn.indexOf('(');
        final int endPos = cn.indexOf(')');

        if (startPos != -1 && endPos != -1 && startPos < endPos) {
            return cn.substring(startPos + 1, endPos);
        }
        return cn;

    }

    /**
     * User ID's are embedded in brackets at the end.
     */
    public static String extractUserIdFromDN(final String dn, final Pattern pattern) {
        final String normalisedDN = dnToRfc2253(dn);
        final Matcher matcher = pattern.matcher(normalisedDN);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Normalise an RFC 2253 Distinguished Name so that it is consistent. Note
     * that the values in the fields should not be normalised - they are
     * case-sensitive.
     *
     * @param dn Distinguished Name to normalise. Must be RFC 2253-compliant
     * @return The DN in RFC 2253 format, with a consistent case for the field
     * names and separation
     */
    public static String dnToRfc2253(final String dn) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Normalising DN: " + dn);
        }

        if (dn == null) {
            return null;
        }

        if (dn.equalsIgnoreCase("anonymous")) {
            LOGGER.trace("Anonymous is a special case - returning as-is");
            return dn;
        }

        try {
            final X500Principal x500 = new X500Principal(dn);
            final String normalised = x500.getName();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Normalised DN: " + normalised);
            }
            return normalised;
        } catch (final IllegalArgumentException e) {
            LOGGER.error("Provided value is not a valid Distinguished Name; it will be returned as-is: " + dn, e);
            return dn;
        }
    }
}
