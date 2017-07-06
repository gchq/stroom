/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.server;

import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.BasicHttpAuthenticationFilter;
import stroom.util.cert.CertificateUtil;
import stroom.util.logging.StroomLogger;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class CertificateAuthenticationFilter extends BasicHttpAuthenticationFilter {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(CertificateAuthenticationFilter.class);

    @Override
    protected boolean isLoginAttempt(final ServletRequest request, final ServletResponse response) {
        final String certificateDn = CertificateUtil.extractCertificateDN(request);
        if (certificateDn != null) {
            return true;
        }

        return super.isLoginAttempt(request, response);
    }

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        final String certificateDn = CertificateUtil.extractCertificateDN(request);

        if (certificateDn != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("loginWithCertificate() - certificateDn=" + certificateDn);
            }

            // Create the authentication token from the certificate
            final CertificateAuthenticationToken token = new CertificateAuthenticationToken(certificateDn, isRememberMe(request),
                    request.getRemoteHost());
            return token;
        }

        return super.createToken(request, response);
    }
}