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

package stroom.security.common.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

public class ContentSecurityFilter implements Filter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentSecurityFilter.class);

    private static final String HEADER_IE_X_CONTENT_SECURITY_POLICY = "X-Content-Security-Policy";
    private static final String USER_AGENT_IE_10 = "MSIE 10";
    private static final String USER_AGENT_IE_11 = "rv:11.0";

    private final Provider<ContentSecurityConfig> configProvider;

    @Inject
    public ContentSecurityFilter(final Provider<ContentSecurityConfig> configProvider) {
        Objects.requireNonNull(configProvider);
        this.configProvider = configProvider;
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        LOGGER.debug(() -> "Initialising " + getClass().getSimpleName());
    }

    @Override
    public void destroy() {
        LOGGER.debug(() -> "Destroying " + getClass().getSimpleName());
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        Objects.requireNonNull(request);
        Objects.requireNonNull(response);
        Objects.requireNonNull(chain);

        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            addHeaders((HttpServletRequest) request, (HttpServletResponse) response);
        }

        chain.doFilter(request, response);
    }

    private void addHeaders(final HttpServletRequest request, final HttpServletResponse response) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(response);

        final ContentSecurityConfig config = configProvider.get();
        final String contentSecurityPolicy = config.getContentSecurityPolicy();

        if (!Strings.isNullOrEmpty(contentSecurityPolicy)) {
            response.setHeader(HttpHeaders.CONTENT_SECURITY_POLICY, contentSecurityPolicy);

            NullSafe.consume(request.getHeader(HttpHeaders.USER_AGENT), userAgent -> {
                // Send the CSP header so that IE10 and IE11 recognise it
                if (userAgent.contains(USER_AGENT_IE_10) || userAgent.contains(USER_AGENT_IE_11)) {
                    response.setHeader(HEADER_IE_X_CONTENT_SECURITY_POLICY, contentSecurityPolicy);
                }
            });
        }
        setResponseHeader(response, HttpHeaders.X_CONTENT_TYPE_OPTIONS, config::getContentTypeOptions);
        setResponseHeader(response, HttpHeaders.X_FRAME_OPTIONS, config::getFrameOptions);
        setResponseHeader(response, HttpHeaders.X_XSS_PROTECTION, config::getXssProtection);

        // HTTP header: 'Strict-Transport-Security'
        // Once the browser sees this header on an HTTPS request, ANY/ALL future HTTP requests on this domain
        // will be redirected by the browser (307) to HTTPS. Clearing the browser cache will not clear it. In
        // chrome visit chrome://net-internals/#hsts and use 'Query HSTS/PKP domain' and
        // 'Delete domain security policies'.
        // A note for dev testing. HSTS only works if the cert is valid and the domain cannot be an IP address.
        // I.e. the public FQDN of stroom needs to be in the cert SAN list and the CA cert needs to be imported
        // into the browser. Also, you can't test it with superdev. To test in a docker stack, add this
        //   extra_hosts:
        //     - "my-host-name:192.168.1.1" # Where 192.168.1.1 is the IP of your host & resolvable in containers
        // To stroom.yml and nginx.yml docker compose files. Also, in the stack env file set
        //   export HOST_IP="my-host-name"
        // Also ensure my-host-name is in the SAN list of the server cert (see server.ssl.conf) and the CA cert
        // is imported in the browser. Simples.
        setResponseHeader(response, HttpHeaders.STRICT_TRANSPORT_SECURITY, config::getStrictTransportSecurity);
    }

    private void setResponseHeader(final HttpServletResponse response,
                                   final String headerName,
                                   final Supplier<String> valueSupplier) {
        if (valueSupplier != null && !Strings.isNullOrEmpty(headerName)) {
            final String headerValue = valueSupplier.get();
            if (!Strings.isNullOrEmpty(headerValue)) {
                response.setHeader(headerName, headerValue);
            }
        }
    }
}
