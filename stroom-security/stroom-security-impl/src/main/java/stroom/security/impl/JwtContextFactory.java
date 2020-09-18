package stroom.security.impl;

import org.jose4j.jwt.consumer.JwtContext;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public interface JwtContextFactory {
    Optional<JwtContext> getJwtContext(HttpServletRequest request);

    Optional<JwtContext> getJwtContext(String jws);
}
