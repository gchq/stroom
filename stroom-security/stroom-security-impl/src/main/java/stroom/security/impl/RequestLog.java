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

package stroom.security.impl;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestLog {
    private static final Logger REQUEST_LOGGER = LoggerFactory.getLogger(RequestLog.class);
    private static final Logger HEADERS_LOGGER = LoggerFactory.getLogger(Headers.class);
    private static final Logger COOKIES_LOGGER = LoggerFactory.getLogger(Cookies.class);

    public static void log(final HttpServletRequest request) {
        if (REQUEST_LOGGER.isDebugEnabled()) {
            final StringBuilder sb = new StringBuilder();
            sb.append("\n====================== REQUEST ======================\n");
            sb
                    .append("Request{")
                    .append("requestURI=")
                    .append(request.getRequestURI())
                    .append(", remoteAddr=")
                    .append(request.getRemoteAddr())
                    .append(", remoteHost=")
                    .append(request.getRemoteHost())
                    .append(", remotePort=")
                    .append(request.getRemotePort())
                    .append(", remoteUser=")
                    .append(request.getRemoteUser())
                    .append(", servletPath=")
                    .append(request.getServletPath())
                    .append('}')
                    .append('\n');

            if (HEADERS_LOGGER.isDebugEnabled()) {
                Headers.append(sb, request);
            }
            if (COOKIES_LOGGER.isDebugEnabled()) {
                Cookies.append(sb, request);
            }
            sb.append("=====================================================\n");
            REQUEST_LOGGER.debug(sb.toString());
        } else {
            if (HEADERS_LOGGER.isDebugEnabled()) {
                final StringBuilder sb = new StringBuilder();
                Headers.append(sb, request);
                HEADERS_LOGGER.debug(sb.toString());
            }
            if (COOKIES_LOGGER.isDebugEnabled()) {
                final StringBuilder sb = new StringBuilder();
                Cookies.append(sb, request);
                COOKIES_LOGGER.debug(sb.toString());
            }
        }
    }

    public static class Headers {
        public static void append(final StringBuilder sb, final HttpServletRequest request) {
            request.getHeaderNames().asIterator().forEachRemaining(headerName ->
                    sb
                            .append("Header{")
                            .append("name=")
                            .append(headerName)
                            .append(", value=")
                            .append(request.getHeader(headerName))
                            .append('}')
                            .append('\n'));
        }
    }

    public static class Cookies {
        public static void append(final StringBuilder sb, final HttpServletRequest request) {
            if (request.getCookies() != null) {
                for (final Cookie cookie : request.getCookies()) {
                    sb
                            .append("Cookie{")
                            .append("name=")
                            .append(cookie.getName())
                            .append(", value=")
                            .append(cookie.getValue())
                            .append(", comment=")
                            .append(cookie.getComment())
                            .append(", domain=")
                            .append(cookie.getDomain())
                            .append(", path=")
                            .append(cookie.getPath())
                            .append(", httpOnly=")
                            .append(cookie.isHttpOnly())
                            .append(", secure=")
                            .append(cookie.getSecure())
                            .append(", maxAge=")
                            .append(cookie.getMaxAge())
                            .append(", version=")
                            .append(cookie.getVersion())
                            .append('}')
                            .append('\n');
                }
            }
        }
    }
}
