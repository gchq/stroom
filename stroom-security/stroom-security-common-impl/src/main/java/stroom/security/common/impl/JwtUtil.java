package stroom.security.common.impl;

import stroom.security.openid.api.OpenId;
import stroom.util.NullSafe;
import stroom.util.exception.ThrowingFunction;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.jose4j.json.internal.json_simple.parser.ContainerFactory;
import org.jose4j.json.internal.json_simple.parser.JSONParser;
import org.jose4j.json.internal.json_simple.parser.ParseException;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.JwtContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public final class JwtUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JwtUtil.class);

    private static final String BEARER = "Bearer ";
    private static final String CORP_PREFIX = "corp_";
    private static final String IDENTITIES = "identities";
    private static final String USER_ID = "userId";
    private static final String USER_NAME = "username";

    private static final ContainerFactory CONTAINER_FACTORY = new ContainerFactory() {
        public List<Object> creatArrayContainer() {
            return new ArrayList<>();
        }

        public Map<String, Object> createObjectContainer() {
            return new LinkedHashMap<>();
        }
    };

    private JwtUtil() {
    }

    /**
     * Get the JSON Web Signature from the specified request header
     */
    public static Optional<String> getJwsFromHeader(final HttpServletRequest request, final String headerName) {
        Optional<String> jws = Optional.empty();
        final String value = request.getHeader(headerName);
        if (value != null && !value.isBlank()) {
            if (value.startsWith(BEARER)) {
                // This chops out 'Bearer' so we get just the token.
                jws = Optional.of(value.substring(BEARER.length()));
            } else {
                jws = Optional.of(value);
            }
            jws.ifPresent(s -> LOGGER.debug(() -> "Found auth header in request: {" + headerName + "=" + s + "}"));
        }
        return jws;
    }

    public static String getEmail(final JwtClaims jwtClaims) {
        LOGGER.debug(() -> "Claim value " + OpenId.SCOPE__EMAIL + "=" + jwtClaims.getClaimValue(OpenId.SCOPE__EMAIL));
        return (String) jwtClaims.getClaimValue(OpenId.SCOPE__EMAIL);
    }

    public static String getUserName(final JwtClaims jwtClaims) {
        LOGGER.debug(() -> "Claim value " + USER_NAME + "=" + jwtClaims.getClaimValue(USER_NAME));
        final String userId = (String) jwtClaims.getClaimValue(USER_NAME);
        return removePrefix(userId);
    }

    public static String removePrefix(final String userId) {
        if (userId != null) {
            int index = userId.indexOf(CORP_PREFIX);
            if (index != -1) {
                return userId.substring(CORP_PREFIX.length());
            }
        }
        return userId;
    }

    public static String getUserIdFromIdentities(final JwtClaims jwtClaims) {
        String userId = null;
        final String identities = (String) jwtClaims.getClaimValue(IDENTITIES);
        if (identities != null) {
            userId = getUserIdFromIdentities(identities);
        }
        return userId;
    }

    @SuppressWarnings("unchecked")
    public static String getUserIdFromIdentities(final String identities) {
        String userId = null;
        try {
            if (identities != null) {
                final JSONParser jsonParser = new JSONParser();
                final List<Object> list = (List<Object>) jsonParser.parse(identities, CONTAINER_FACTORY);
                if (list != null && list.size() > 0) {
                    final Map<String, Object> identity = (Map<String, Object>) list.get(0);
                    userId = (String) identity.get(USER_ID);
                }
            }
        } catch (final ParseException | RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return userId;
    }

    public static String getSubject(final JwtClaims jwtClaims) {
        LOGGER.debug(() -> {
            try {
                return "Subject=" + jwtClaims.getSubject();
            } catch (final MalformedClaimException e) {
                LOGGER.debug(e.getMessage(), e);
            }
            return "MalformedClaimException";
        });

        String subject = null;
        try {
            subject = jwtClaims.getSubject();
        } catch (final MalformedClaimException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return subject;
    }

    public static Optional<String> getClaimValue(final JwtContext jwtContext, final String claim) {
        try {
            return NullSafe.getAsOptional(
                    jwtContext,
                    JwtContext::getJwtClaims,
                    ThrowingFunction.unchecked(jwtClaims ->
                            jwtClaims.getClaimValue(claim, String.class)));
        } catch (Exception e) {
            LOGGER.debug(() -> LogUtil.message("Error getting claim {}: {}", claim, e.getMessage()), e);
            return Optional.empty();
        }
    }

    public static Optional<String> getClaimValue(final JwtClaims jwtClaims, final String claim) {
        try {
            return NullSafe.getAsOptional(
                    jwtClaims,
                    ThrowingFunction.unchecked(jwtClaims2 ->
                            jwtClaims2.getClaimValue(claim, String.class)));
        } catch (Exception e) {
            LOGGER.debug(() -> LogUtil.message("Error getting claim {}: {}", claim, e.getMessage()), e);
            return Optional.empty();
        }
    }
}
