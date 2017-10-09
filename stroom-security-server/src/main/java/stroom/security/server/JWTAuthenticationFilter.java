/*
 * Copyright 2017 Crown Copyright
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

package stroom.security.server;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.servlet.ShiroHttpServletRequest;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.config.StroomProperties;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import java.io.IOException;

public class JWTAuthenticationFilter extends AuthenticatingFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JWTAuthenticationFilter.class);

    private JWTService jwtService;
    //TODO Use an API gateway
    private final String LOGIN_URL_PROPERTY_NAME = "stroom.security.login.url";

    public JWTAuthenticationFilter(final JWTService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        // We need to allow CORS preflight requests
        String httpMethod = (((ShiroHttpServletRequest) request).getMethod());
        if(httpMethod.toUpperCase().equals(HttpMethod.OPTIONS)) {
            return true;
        }

        boolean loggedIn = false;

        if (JWTService.getAuthHeader(request).isPresent()
            || JWTService.getAuthParam(request).isPresent()) {
            LOGGER.info("About to attempt login");
            loggedIn = executeLogin(request, response);
        }

        if (!loggedIn) {
            // We need to distinguish between requests from an API client and from the GWT front-end.
            // If a request is from the GWT front-end and fails authentication then we need to redirect to the login page.
            // If a request is from an API client and fails authentication then we need to return HTTP 403 UNAUTHORIZED.
            String servletPath = ((ShiroHttpServletRequest) request).getServletPath();
            boolean isApiRequest = servletPath.contains("/api");

            if(isApiRequest) {
                LOGGER.debug("API request is unauthorised.");
                HttpServletResponse httpResponse = WebUtils.toHttp(response);
                httpResponse.setStatus(Response.Status.UNAUTHORIZED.getStatusCode());
            }
            else {
                String loginUrl = StroomProperties.getProperty(LOGIN_URL_PROPERTY_NAME);
                //TODO referrer to the login URL.
                String jSessionId = ((ShiroHttpServletRequest) request).getSession().getId();
//                String jSessionId = this.getThreadLocalRequest().getSession().getId();
                String redirectionUrl = String.format(loginUrl + "?session_id=%s", jSessionId);
                LOGGER.info("Redirecting to login at: '{}'", redirectionUrl);
                HttpServletResponse httpResponse = WebUtils.toHttp(response);
                httpResponse.sendRedirect(redirectionUrl);
            }
        }

        return loggedIn;
    }

    @Override
    protected JWTAuthenticationToken createToken(ServletRequest request, ServletResponse response) throws IOException {
        return jwtService.verifyToken(request).orElse(null);
    }

    @Override
    protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException e, ServletRequest request, ServletResponse response) {
        HttpServletResponse httpResponse = WebUtils.toHttp(response);
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}