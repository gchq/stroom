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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * <p>
 * Filter to avoid posts to the wrong place (e.g. the root of the app)
 * </p>
 */
public class RejectPostFilter implements Filter {

    private static final String REJECT_URI_REGEX = "rejectUri";
    private static final String POST_METHOD = "POST";
    private Pattern pattern = null;

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        if (POST_METHOD.equals(((HttpServletRequest) request).getMethod())) {
            if (rejectUri(((HttpServletRequest) request).getRequestURI())) {
                ((HttpServletResponse) response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                        "Data must be posted to the datafeed URL");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    public boolean rejectUri(final String uri) {
        return pattern.matcher(uri).matches();
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        initRejectPattern(filterConfig.getInitParameter(REJECT_URI_REGEX));
    }

    public void initRejectPattern(final String init) {
        pattern = Pattern.compile(init);
    }
}
