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

package stroom.util.cert;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * For extracting certificates and/or DNs from {@link HttpServletRequest}s.
 */
public interface CertificateExtractor {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CertificateExtractor.class);

    Optional<String> getCN(HttpServletRequest request);

    Optional<String> getDN(HttpServletRequest request);

    Optional<X509Certificate> extractCertificate(ServletRequest request);

    /**
     * Given a DN and {@link DNFormat} pull out the CN. E.g.
     * "CN=some.server.co.uk, OU=servers, O=some organisation, C=GB" and {@link DNFormat#LDAP} Would
     * return "some.server.co.uk"
     *
     * @return The CN name or an empty {@link Optional}
     */
    Optional<String> extractCNFromDN(final String dn);

}
