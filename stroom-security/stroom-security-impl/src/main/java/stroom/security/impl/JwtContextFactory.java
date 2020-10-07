package stroom.security.impl;

import org.jose4j.jwt.consumer.JwtContext;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public interface JwtContextFactory {
    Optional<JwtContext> getJwtContext(HttpServletRequest request);

    Optional<JwtContext> getJwtContext(String jws);

    /**
     * We expect some configurations to always pass a token in the request so this flag tells us if the configuration is
     * expected to do so.
     *
     * @return True if the context factory always expects a token in the request, i.e. it expects all requests to be pre
     * authenticated.
     */
    boolean isTokenExpectedInRequest();
}
