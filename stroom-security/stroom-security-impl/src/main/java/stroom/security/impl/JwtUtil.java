package stroom.security.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public final class JwtUtil {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JwtUtil.class);

    private static final String BEARER = "Bearer ";

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
}
