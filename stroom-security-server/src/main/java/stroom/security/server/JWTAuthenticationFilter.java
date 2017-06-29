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

import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.ExternalService;
import stroom.ServiceDiscoverer;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

public class JWTAuthenticationFilter extends AuthenticatingFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JWTAuthenticationFilter.class);

    private JWTService jwtService;
    private ServiceDiscoverer serviceDiscoverer;

    public JWTAuthenticationFilter(final JWTService jwtService, final ServiceDiscoverer serviceDiscoverer) {
        this.jwtService = jwtService;
        this.serviceDiscoverer = serviceDiscoverer;
        updatedLoginUrl();
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        boolean loggedIn = false;

        if (JWTService.getAuthHeader(request).isPresent()
                || JWTService.getAuthParam(request).isPresent()) {
            LOGGER.info("About to attempt login");
            loggedIn = executeLogin(request, response);
        }

        if (!loggedIn) {
            LOGGER.info("Redirecting to login");
            // We always want to make sure we've got the most up-to-date login URL. If we can't get it then
            // we'll still attempt to re-direct, because we might have a valid URL cached already.
            try {
                updatedLoginUrl();
            } catch(RuntimeException e){
               LOGGER.error("Unable to get the login URL - falling back on existing URL: {}", e);
            }

            HttpServletResponse httpResponse = WebUtils.toHttp(response);
            httpResponse.sendRedirect(getLoginUrl());
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

    /**
     * This checks with service discovery to find a login URL, and then sets it.
     */
    private void updatedLoginUrl() {
        Optional<ServiceInstance<String>> loginUiService = serviceDiscoverer.getServiceInstance(ExternalService.LOGIN_UI);
        if(loginUiService.isPresent()){
            setLoginUrl(loginUiService.get().getAddress());
        }
        else{
            throw new RuntimeException(
                    "I cannot find the login service so I do not know where to send you to log in.");
        }
    }
}