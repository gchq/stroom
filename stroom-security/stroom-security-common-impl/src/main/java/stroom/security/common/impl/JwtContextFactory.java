package stroom.security.common.impl;

import org.jose4j.jwt.consumer.JwtContext;

import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface JwtContextFactory {

    boolean hasToken(HttpServletRequest request);

    void removeAuthorisationEntries(final Map<String, String> headers);

    Optional<JwtContext> getJwtContext(HttpServletRequest request);

    Optional<JwtContext> getJwtContext(String jws);
}
