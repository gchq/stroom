package stroom.security.impl;

import org.jose4j.jwt.consumer.JwtContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
class JwtContextFactoryImpl implements JwtContextFactory {
    private final List<JwtContextFactory> factories;

    @Inject
    JwtContextFactoryImpl(final StandardJwtContextFactory standardJwtContextFactory,
                          final AmznJwtContextFactory amznJwtClaimsResolver,
                          final InternalJwtContextFactory internalJwtContextFactory,
                          final OpenIdConfig openIdConfig) {
        factories = new ArrayList<>();
        if (!openIdConfig.isUseInternal()) {
            if (openIdConfig.getJwtClaimsResolver() != null &&
                    openIdConfig.getJwtClaimsResolver().toLowerCase().contains("amzn")) {
                factories.add(amznJwtClaimsResolver);
            } else {
                factories.add(standardJwtContextFactory);
            }
        }
        // Always try and resolve with internal context factory as a last resort so that we can verify internal
        // processing user etc.
        factories.add(internalJwtContextFactory);
    }

    @Override
    public Optional<JwtContext> getJwtContext(final HttpServletRequest request) {
        for (final JwtContextFactory factory : factories) {
            final Optional<JwtContext> optional = factory.getJwtContext(request);
            if (optional.isPresent()) {
                return optional;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<JwtContext> getJwtContext(final String jws) {
        for (final JwtContextFactory factory : factories) {
            final Optional<JwtContext> optional = factory.getJwtContext(jws);
            if (optional.isPresent()) {
                return optional;
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isTokenExpectedInRequest() {
        for (final JwtContextFactory factory : factories) {
            return factory.isTokenExpectedInRequest();
        }
        return false;
    }
}
