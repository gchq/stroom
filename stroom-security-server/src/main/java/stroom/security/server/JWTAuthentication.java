package stroom.security.server;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.base.Strings;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.util.WebUtils;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public class JWTAuthentication {
    protected static final String AUTHORIZATION_HEADER = "Authorization";

    public static Optional<String> getAuthHeader(ServletRequest request) {
        HttpServletRequest httpServletRequest = WebUtils.toHttp(request);
        return(getAuthHeader(httpServletRequest));
    }

    public static Optional<String> getAuthHeader(HttpServletRequest httpServletRequest){
        String authHeader = httpServletRequest.getHeader(AUTHORIZATION_HEADER);
        return Strings.isNullOrEmpty(authHeader) ? Optional.empty() : Optional.of(authHeader);
    }

    public static Optional<AuthenticationToken> createToken(ServletRequest request){
        if (JWTAuthentication.getAuthHeader(request).isPresent()) {
            String jwtToken = JWTAuthentication.getAuthHeader(request).get();
            return Optional.of(JWTAuthentication.createToken(jwtToken));
        }
        else {
            return Optional.empty();
        }
    }

    public static JWTAuthenticationToken createToken(String token) {
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
