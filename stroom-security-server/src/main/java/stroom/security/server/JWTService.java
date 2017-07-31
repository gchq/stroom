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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

import static org.jose4j.jws.AlgorithmIdentifiers.HMAC_SHA256;

@Component
public class JWTService {
    private static final String BEARER = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final Logger LOGGER = LoggerFactory.getLogger(JWTService.class);
    private final String jwtSecret;
    private final String jwtIssuer;

    public JWTService(
            @Value("#{propertyConfigurer.getProperty('stroom.auth.jwt.secret')}") final String jwtSecret,
            @Value("#{propertyConfigurer.getProperty('stroom.auth.jwt.issuer')}") final String jwtIssuer) {
        this.jwtSecret = jwtSecret;
        this.jwtIssuer = jwtIssuer;

        if (jwtSecret == null) {
            throw new SecurityException("No JWT secret defined");
        }
        if (jwtIssuer == null) {
            throw new SecurityException("No JWT issuer defined");
        }
    }

    public static Optional<String> getAuthHeader(ServletRequest request) {
        HttpServletRequest httpServletRequest = WebUtils.toHttp(request);
        return (getAuthHeader(httpServletRequest));
    }

    public static Optional<String> getAuthHeader(HttpServletRequest httpServletRequest) {
        String authHeader = httpServletRequest.getHeader(AUTHORIZATION_HEADER);
        return Strings.isNullOrEmpty(authHeader) ? Optional.empty() : Optional.of(authHeader);
    }

    private static String toToken(byte[] key, JwtClaims claims) {
        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue(HMAC_SHA256);
        jws.setKey(new HmacKey(key));
        jws.setDoKeyValidation(false);

        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw Throwables.propagate(e);
        }
    }

    public Optional<AuthenticationToken> verifyToken(ServletRequest request) {
        if (getAuthHeader(request).isPresent()) {
            String bearerString = getAuthHeader(request).get();

            final String jwtToken;

            if (bearerString.startsWith(BEARER)) {
                // TODO This chops out 'Bearer'. We're dealing with unpredictable client data so we need a
                // way to do this that more robustly handles an error in the string.
                jwtToken = bearerString.substring(BEARER.length());
            } else {
                jwtToken = bearerString;
            }

            try {
                return Optional.of(verifyToken(jwtToken));
            } catch (Exception e) {
                LOGGER.error("Unable to verify token:", e.getMessage(), e);
                // If we get an exception verifying the token then we need to log the message
                // and continue as if the token wasn't provided.
                // TODO: decide if this should be handled by an exception and how
                return Optional.empty();
            }
        } else {
            // If there's no token then we've nothing to do.
            return Optional.empty();
        }
    }

    public String getTokenFor(String username) {
        return toToken(jwtSecret.getBytes(), getClaimsForUser(username));
    }

    private JWTAuthenticationToken verifyToken(String token) {
        try {
            String subject = null;
            if (token != null) {
                JWTVerifier verifier = JWT
                        .require(Algorithm.HMAC256(jwtSecret))
                        .withIssuer(jwtIssuer)
                        .build();
                DecodedJWT jwt = verifier.verify(token);
                subject = jwt.getSubject();
            }

            return new JWTAuthenticationToken(subject, token);

        } catch (final Exception e) {
            throw new AuthenticationException(e);
        }
    }

    private JwtClaims getClaimsForUser(String user) {
        final JwtClaims claims = new JwtClaims();
        claims.setExpirationTimeMinutesInTheFuture(5);
        claims.setSubject(user);
        claims.setIssuer(jwtIssuer);
        return claims;
    }
}
