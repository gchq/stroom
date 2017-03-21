package stroom.security.server;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.util.WebUtils;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.keys.HmacKey;
import org.jose4j.lang.JoseException;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

import static org.jose4j.jws.AlgorithmIdentifiers.HMAC_SHA256;

public class JWTUtils {
    protected static final String AUTHORIZATION_HEADER = "Authorization";

    // TODO Move these to config
    public static final String ISSUER = "stroom";
    public static final String SECRET = "some-secret";

    public static Optional<String> getAuthHeader(ServletRequest request) {
        HttpServletRequest httpServletRequest = WebUtils.toHttp(request);
        return(getAuthHeader(httpServletRequest));
    }

    public static Optional<String> getAuthHeader(HttpServletRequest httpServletRequest){
        String authHeader = httpServletRequest.getHeader(AUTHORIZATION_HEADER);
        return Strings.isNullOrEmpty(authHeader) ? Optional.empty() : Optional.of(authHeader);
    }

    public static Optional<AuthenticationToken> verifyToken(ServletRequest request){
        if (JWTUtils.getAuthHeader(request).isPresent()) {
            String jwtToken = JWTUtils.getAuthHeader(request).get();
            return Optional.of(JWTUtils.verifyToken(jwtToken));
        }
        else {
            return Optional.empty();
        }
    }

    public static String getTokenFor(String username) {
        return toToken(SECRET.getBytes(), getClaimsForUser(username));
    }

    private static JWTAuthenticationToken verifyToken(String token) {
        try {
            String subject = null;
            if (token != null) {
                JWTVerifier verifier = JWT
                        .require(Algorithm.HMAC256(JWTUtils.SECRET))
                        .withIssuer(JWTUtils.ISSUER)
                        .build();
                DecodedJWT jwt = verifier.verify(token);
                subject = jwt.getSubject();
            }

            return new JWTAuthenticationToken(subject, token);

        } catch (final Exception e) {
            throw new AuthenticationException(e);
        }
    }

    private static String toToken(byte[] key, JwtClaims claims) {
        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue(HMAC_SHA256);
        jws.setKey(new HmacKey(key));
        jws.setDoKeyValidation(false);

        try {
            return jws.getCompactSerialization();
        }
        catch (JoseException e) { throw Throwables.propagate(e); }
    }

    private static JwtClaims getClaimsForUser(String user) {
        final JwtClaims claims = new JwtClaims();
        claims.setExpirationTimeMinutesInTheFuture(5);
        claims.setSubject(user);
        return claims;
    }
}
