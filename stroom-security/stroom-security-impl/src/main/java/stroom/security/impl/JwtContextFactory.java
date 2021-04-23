package stroom.security.impl;

import org.jose4j.jwt.consumer.JwtContext;

import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface JwtContextFactory {

    Optional<JwtContext> getJwtContext(HttpServletRequest request);

    Optional<JwtContext> getJwtContext(String jws);
}
