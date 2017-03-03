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

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.util.WebUtils;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JWTAuthenticationFilter extends AuthenticatingFilter {

    public static final String USER_ID = "userId";
    public static final String PASSWORD = "password";

    protected static final String AUTHORIZATION_HEADER = "Authorization";

    public JWTAuthenticationFilter() {
        setLoginUrl(DEFAULT_LOGIN_URL);
    }

    @Override
    public void setLoginUrl(String loginUrl) {
        String previous = getLoginUrl();
        if (previous != null) {
            this.appliedPaths.remove(previous);
        }
        super.setLoginUrl(loginUrl);
        this.appliedPaths.put(getLoginUrl(), null);
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        boolean loggedIn = false;

        if (isLoginRequest(request, response) || isLoggedAttempt(request, response)) {
            loggedIn = executeLogin(request, response);
        }

        if (!loggedIn) {
            HttpServletResponse httpResponse = WebUtils.toHttp(response);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        return loggedIn;
    }


    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) throws IOException {

//        if (isLoginRequest(request, response)) {
//            String json = IOUtils.toString(request.getInputStream());
//
//            if (json != null && !json.isEmpty()) {
//
//                try (JsonReader jr = Json.createReader(new StringReader(json))) {
//                    JsonObject object = jr.readObject();
//                    String username = object.getString(USER_ID);
//                    String password = object.getString(PASSWORD);
//                    return new UsernamePasswordToken(username, password);
//                }
//
//            }
//        }

        if (isLoggedAttempt(request, response)) {
            String jwtToken = getAuthzHeader(request);
            if (jwtToken != null) {
                return createToken(jwtToken);
            }
        }

        return new UsernamePasswordToken();
    }

    @Override
    protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException e, ServletRequest request, ServletResponse response) {

        HttpServletResponse httpResponse = WebUtils.toHttp(response);
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        return false;
    }

    protected boolean isLoggedAttempt(ServletRequest request, ServletResponse response) {
        String authzHeader = getAuthzHeader(request);
        return authzHeader != null;
    }

    protected String getAuthzHeader(ServletRequest request) {
        HttpServletRequest httpRequest = WebUtils.toHttp(request);
        return httpRequest.getHeader(AUTHORIZATION_HEADER);
    }

    public JWTAuthenticationToken createToken(String token) {
        try {
            String subject = null;
            if (token != null) {
                JWTVerifier verifier = JWT.require(Algorithm.HMAC256(SecurityContextImpl.SECRET)).withIssuer(SecurityContextImpl.ISSUER).build();
                DecodedJWT jwt = verifier.verify(token);
                subject = jwt.getSubject();
            }

            return new JWTAuthenticationToken(subject, token);

        } catch (final Exception e) {
            throw new AuthenticationException(e);
        }
    }
}


//
//
//
//
//
//
//        implements Filter {
//    private static final Logger LOGGER = LoggerFactory.getLogger(JWTSecurityFilter.class);
//
//    private SecurityManager securityManager;
//
//    @Override
//    public void destroy() {
//    }
//
//    @Override
//    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
//            throws IOException, ServletException {
//        try {
//
//            if (request != null && request instanceof HttpServletRequest) {
//                HttpServletRequest httpRequest = (HttpServletRequest) request;
//                final String token = httpRequest.getHeader("Authorization");
//                if (token != null) {
//                    JWTVerifier verifier = JWT.require(Algorithm.HMAC256("secret")).withIssuer("auth0").build();
//                    DecodedJWT jwt = verifier.verify(token);
//                    final String subject = jwt.getSubject();
//                    if (subject != null) {
//                        securityManager.login()
//                    }
//                }
//            }
//
//        } catch (final Exception e) {
//            LOGGER.error(e.getMessage(), e);
//        }
//
//        // Carry on.
//        chain.doFilter(request, response);
//    }
//
//    @Override
//    public void init(final FilterConfig arg0) throws ServletException {
//    }
//
//    public void setSecurityManager(final SecurityManager securityManager) {
//        this.securityManager = securityManager;
//    }
//}