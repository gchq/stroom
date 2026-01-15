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

import stroom.util.cert.CertificateExtractor;
import stroom.util.cert.DNFormat;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;

public class CertificateExtractorImpl implements CertificateExtractor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CertificateExtractorImpl.class);

    static final String SERVLET_CERT_ARG = "jakarta.servlet.request.X509Certificate";

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;

    @Inject
    public CertificateExtractorImpl(final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
    }

    @Override
    public Optional<String> getCN(final HttpServletRequest request) {
        return getDN(request)
                .flatMap(this::extractCNFromDN);
    }

    @Override
    public Optional<String> getDN(final HttpServletRequest request) {
        // First see if we have a cert we can use.
        final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
        final boolean isRemoteHostTrusted = isRemoteHostTrustedCertProvider(request, receiveDataConfig);
        final Optional<X509Certificate> cert = extractCertificate(request, receiveDataConfig, isRemoteHostTrusted);
        if (cert.isPresent()) {
            LOGGER.debug(() -> "Found certificate " + cert.get());
            return extractDNFromCertificate(cert.get());
        } else {
            return extractDNFromRequest(request, receiveDataConfig, isRemoteHostTrusted);
        }
    }

    /**
     * Pull out the Subject from the certificate. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB"
     */
    @Override
    public Optional<X509Certificate> extractCertificate(final ServletRequest request) {
        final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
        final boolean isRemoteHostTrusted = isRemoteHostTrustedCertProvider(request, receiveDataConfig);
        return extractCertificate(request, receiveDataConfig, isRemoteHostTrusted);
    }

    /**
     * Given a DN and {@link DNFormat} pull out the CN. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB" and {@link DNFormat#LDAP} Would
     * return "some.server.co.uk"
     *
     * @return null or the CN name
     */
    @Override
    public Optional<String> extractCNFromDN(final String dn) {
        final DNFormat dnFormat = Objects.requireNonNullElse(
                receiveDataConfigProvider.get().getX509CertificateDnFormat(),
                ReceiveDataConfig.DEFAULT_X509_CERT_DN_FORMAT);
        LOGGER.debug("extractCNFromDN dnFormat: {}, DN: '{}'", dnFormat, dn);

        if (dn == null) {
            return Optional.empty();
        }
        final StringTokenizer attributes = new StringTokenizer(dn, dnFormat.getDelimiter());

        if (attributes.hasMoreTokens()) {
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
            LOGGER.debug("extractCNFromDN DN: '{}', CN: '{}'", dn, cn);
            return Optional.ofNullable(cn);
        }
        return Optional.empty();
    }

    private Optional<String> extractDNFromRequest(final HttpServletRequest request,
                                                  final ReceiveDataConfig receiveDataConfig,
                                                  final boolean isRemoteHostTrusted) {
        final String x509CertificateDnHeader = receiveDataConfig.getX509CertificateDnHeader();
        final String clientDn = request.getHeader(x509CertificateDnHeader);
        LOGGER.debug(() -> x509CertificateDnHeader + " = " + clientDn);
        Optional<String> optDn = NullSafe.isNonBlankString(clientDn)
                ? Optional.of(clientDn)
                : Optional.empty();

        if (optDn.isPresent() && !isRemoteHostTrusted) {
            logUntrustedHostWarning(request, receiveDataConfig, x509CertificateDnHeader);
            optDn = Optional.empty();
        }
        return optDn;
    }

    private boolean isRemoteHostTrustedCertProvider(final ServletRequest request,
                                                    final ReceiveDataConfig receiveDataConfig) {
        final Set<String> allowedCertificateProviders = receiveDataConfig.getAllowedCertificateProviders();
        if (NullSafe.hasItems(allowedCertificateProviders)) {
            final String remoteHost = request.getRemoteHost();

            boolean isTrusted = false;
            if (NullSafe.isNonBlankString(remoteHost)) {
                isTrusted = allowedCertificateProviders.contains(remoteHost);
            }
            if (!isTrusted) {
                final String remoteAddr = request.getRemoteAddr();
                if (NullSafe.isNonBlankString(remoteAddr)) {
                    isTrusted = allowedCertificateProviders.contains(remoteAddr);
                }
            }
            LOGGER.debug("isTrusted: {}, remoteHost: {}, remoteAddr: {}, allowedCertificateProviders: {}",
                    isTrusted, request.getRemoteHost(), request.getRemoteAddr(), allowedCertificateProviders);
            return isTrusted;
        } else {
            return true;
        }
    }

    private Optional<X509Certificate> extractCertificate(final ServletRequest request,
                                                         final ReceiveDataConfig receiveDataConfig,
                                                         final boolean isRemoteHostTrusted) {
        final String x509CertificateHeader = receiveDataConfig.getX509CertificateHeader();
        Optional<X509Certificate> optCert;
        // First try and get a cert placed in the header by the load balancer (if trusted)
        optCert = extractCertificate(request, x509CertificateHeader);
        if (!isRemoteHostTrusted && optCert.isPresent()) {
            logUntrustedHostWarning(request, receiveDataConfig, x509CertificateHeader);
            optCert = Optional.empty();
        }
        // Fall back to one placed there by the servlet container, i.e. DropWiz does the termination
        return optCert
                .or(() -> extractCertificate(request, SERVLET_CERT_ARG));
    }

    private void logUntrustedHostWarning(final ServletRequest request,
                                         final ReceiveDataConfig receiveDataConfig,
                                         final String headerKey) {
        LOGGER.warn("Untrusted host {} ({}) using header {}. The header will be ignored. " +
                    "If the host should be trusted then add the host/IP to the " +
                    "configuration property '{}'",
                request.getRemoteHost(),
                request.getRemoteAddr(),
                headerKey,
                receiveDataConfig.getFullPath(ReceiveDataConfig.PROP_NAME_ALLOWED_CERTIFICATE_PROVIDERS));
    }

    private static Optional<X509Certificate> extractCertificate(final ServletRequest request,
                                                                final String attributeName) {
        final Object[] certs = (Object[]) request.getAttribute(attributeName);
        return Optional.ofNullable(certs)
                .flatMap(certs2 -> {
                    LOGGER.debug(() -> "Found certificate using " + attributeName + " header");
                    return extractCertificate(certs);
                });
    }

    /**
     * Pull out the Subject from the certificate. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB"
     *
     * @param certs ARGS from the SERVLET request.
     */
    private static Optional<X509Certificate> extractCertificate(final Object[] certs) {
        for (final Object cert : certs) {
            if (cert instanceof final X509Certificate x509Certificate) {
                return Optional.of(x509Certificate);
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
