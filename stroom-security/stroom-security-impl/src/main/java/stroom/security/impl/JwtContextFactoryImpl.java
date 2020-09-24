package stroom.security.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jose4j.jwt.consumer.JwtContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Singleton
class JwtContextFactoryImpl implements JwtContextFactory {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JwtContextFactoryImpl.class);
    private final List<JwtContextFactory> factories;

    @Inject
    JwtContextFactoryImpl(final InternalJwtContextFactory internalJwtContextFactory,
                          final StandardJwtContextFactory standardJwtContextFactory,
                          final AmznJwtContextFactory amznJwtClaimsResolver,
                          final OpenIdConfig openIdConfig) {
        factories = new ArrayList<>();

        // Always try and resolve with internal context factory first so that we can verify internal processing user etc
        factories.add(internalJwtContextFactory);

        if (!openIdConfig.isUseInternal()) {
            if (openIdConfig.getJwtClaimsResolver() != null &&
                    openIdConfig.getJwtClaimsResolver().toLowerCase().contains("amzn")) {
                factories.add(amznJwtClaimsResolver);
            } else {
                factories.add(standardJwtContextFactory);
            }
        }
    }

    @Override
    public Optional<JwtContext> getJwtContext(final HttpServletRequest request) {
        return tryFactories(factory -> factory.getJwtContext(request));
    }

    @Override
    public Optional<JwtContext> getJwtContext(final String jws) {
        return tryFactories(factory -> factory.getJwtContext(jws));
    }

    private Optional<JwtContext> tryFactories(final Function<JwtContextFactory, Optional<JwtContext>> function) {
        // Try each factory in turn to get the JWTContext.
        for (final JwtContextFactory factory : factories) {
            try {
                final Optional<JwtContext> optional = function.apply(factory);
                if (optional.isPresent()) {
                    return optional;
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
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
