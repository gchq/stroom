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

package stroom.core.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Enumeration;

/**
 * <p>
 * Debug Filter.
 * </p>
 */
public class StroomFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomFilter.class);

    /**
     * @see jakarta.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
        LOGGER.info("destroy()");
    }

    /**
     * @param request  NA
     * @param response NA
     * @param chain    NA
     * @throws IOException      NA
     * @throws ServletException NA
     * @see jakarta.servlet.Filter#doFilter(jakarta.servlet.ServletRequest,
     * jakarta.servlet.ServletResponse, jakarta.servlet.FilterChain)
     */
    @SuppressWarnings("unchecked")
    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("doFilter()");
            final HttpServletRequest httpRequest = (HttpServletRequest) request;

            LOGGER.info("Incoming request requestURI=" + httpRequest.getRequestURI());

            final Enumeration<String> headers = httpRequest.getHeaderNames();
            while (headers.hasMoreElements()) {
                final String key = headers.nextElement();
                LOGGER.info("Incoming Header " + key + "=" + httpRequest.getHeader(key));
            }
            final Enumeration<String> parameters = httpRequest.getParameterNames();
            while (parameters.hasMoreElements()) {
                final String key = parameters.nextElement();
                LOGGER.info("Incoming Parameter " + key + "=" + httpRequest.getParameter(key));
            }

            final Cookie[] cookies = httpRequest.getCookies();
            if (cookies != null) {
                for (final Cookie cookie : cookies) {
                    LOGGER.info("Incoming Cookie domain=" + cookie.getDomain() + " maxAge=" + cookie.getMaxAge() + " "
                            + cookie.getName() + "=" + cookie.getValue());
                }
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * @param filterConfig NA
     * @throws ServletException NA
     * @see jakarta.servlet.Filter#init(jakarta.servlet.FilterConfig)
     */
    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        LOGGER.info("init()");
    }

}
