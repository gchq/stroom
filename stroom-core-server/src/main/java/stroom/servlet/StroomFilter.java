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

package stroom.servlet;

import org.apache.log4j.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;

/**
 * <p>
 * Debug Filter.
 * </p>
 */
public class StroomFilter implements Filter {
    private static final Logger LOGGER = Logger.getLogger(StroomFilter.class);

    /**
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy() {
        LOGGER.info("destroy()");
    }

    /**
     * @param request
     *            NA
     * @param response
     *            NA
     * @param chain
     *            NA
     * @throws IOException
     *             NA
     * @throws ServletException
     *             NA
     *
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
     *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @SuppressWarnings("unchecked")
    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("doFilter()");
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            LOGGER.info("Incoming request requestURI=" + httpRequest.getRequestURI());

            Enumeration<String> headers = httpRequest.getHeaderNames();
            while (headers.hasMoreElements()) {
                String key = headers.nextElement();
                LOGGER.info("Incoming Header " + key + "=" + httpRequest.getHeader(key));
            }
            Enumeration<String> parameters = httpRequest.getParameterNames();
            while (parameters.hasMoreElements()) {
                String key = parameters.nextElement();
                LOGGER.info("Incoming Parameter " + key + "=" + httpRequest.getParameter(key));
            }

            Cookie[] cookies = httpRequest.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    LOGGER.info("Incoming Cookie domain=" + cookie.getDomain() + " maxAge=" + cookie.getMaxAge() + " "
                            + cookie.getName() + "=" + cookie.getValue());
                }
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * @param filterConfig
     *            NA
     * @throws ServletException
     *             NA
     *
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        LOGGER.info("init()");
    }

}
