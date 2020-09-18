package stroom.security.impl;

import org.jose4j.jwt.consumer.JwtContext;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

class JwtContextFactoryImpl implements JwtContextFactory {
    private final StandardJwtContextFactory standardJwtClaimsResolver;
    private final AmznJwtContextFactory amznJwtClaimsResolver;
    private final ResolvedOpenIdConfig resolvedOpenIdConfig;

    @Inject
    JwtContextFactoryImpl(final StandardJwtContextFactory standardJwtClaimsResolver,
                          final AmznJwtContextFactory amznJwtClaimsResolver,
                          final ResolvedOpenIdConfig resolvedOpenIdConfig) {
        this.standardJwtClaimsResolver = standardJwtClaimsResolver;
        this.amznJwtClaimsResolver = amznJwtClaimsResolver;
        this.resolvedOpenIdConfig = resolvedOpenIdConfig;
    }

    @Override
    public Optional<JwtContext> getJwtContext(final HttpServletRequest request) {
        return getFactory().getJwtContext(request);
    }

    @Override
    public Optional<JwtContext> getJwtContext(final String jws) {
        return getFactory().getJwtContext(jws);
    }

    private JwtContextFactory getFactory() {
        final String resolver = resolvedOpenIdConfig.getJwtClaimsResolver();
        if (resolver != null && resolver.toLowerCase().contains("amzn")) {
            return amznJwtClaimsResolver;
        }
        return standardJwtClaimsResolver;
    }
}
