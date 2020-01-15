/*
 * Copyright 2019 Crown Copyright
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

package stroom.security.impl;

import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

public class ContentSecurityFilter implements Filter {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentSecurityFilter.class);

    private static final String HEADER_IE_X_CONTENT_SECURITY_POLICY = "X-Content-Security-Policy";
    private static final String USER_AGENT_IE_10 = "MSIE 10";
    private static final String USER_AGENT_IE_11 = "rv:11.0";

    private final ContentSecurityConfig config;

    @Inject
    public ContentSecurityFilter(final ContentSecurityConfig config) {
        Objects.requireNonNull(config);
        this.config = config;
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
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
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

        if (!Strings.isNullOrEmpty(config.getContentSecurityPolicy())) {
            response.setHeader(HttpHeaders.CONTENT_SECURITY_POLICY, config.getContentSecurityPolicy());

            String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
            if (userAgent != null) {
                // Send the CSP header so that IE10 and IE11 recognise it
                if (userAgent.contains(USER_AGENT_IE_10) || userAgent.contains(USER_AGENT_IE_11)) {
                    response.setHeader(HEADER_IE_X_CONTENT_SECURITY_POLICY, config.getContentSecurityPolicy());
                }
            }
        }

        if (!Strings.isNullOrEmpty(config.getContentTypeOptions())) {
            response.setHeader(HttpHeaders.X_CONTENT_TYPE_OPTIONS, config.getContentTypeOptions());
        }

        if (!Strings.isNullOrEmpty(config.getFrameOptions())) {
            response.setHeader(HttpHeaders.X_FRAME_OPTIONS, config.getFrameOptions());
        }

        if (!Strings.isNullOrEmpty(config.getXssProtection())) {
            response.setHeader(HttpHeaders.X_XSS_PROTECTION, config.getXssProtection());
        }
    }
}