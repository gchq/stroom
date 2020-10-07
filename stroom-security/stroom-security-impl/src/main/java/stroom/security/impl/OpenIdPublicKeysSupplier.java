package stroom.security.impl;

import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.lang.JoseException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Singleton
public class OpenIdPublicKeysSupplier implements Supplier<JsonWebKeySet> {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenIdPublicKeysSupplier.class);

    private final ResolvedOpenIdConfig openIdConfig;
    private final WebTargetFactory webTargetFactory;

    private final Map<String, JsonWebKeySet> cache = new ConcurrentHashMap<>();

    @Inject
    OpenIdPublicKeysSupplier(final ResolvedOpenIdConfig openIdConfig,
                             final WebTargetFactory webTargetFactory) {
        this.openIdConfig = openIdConfig;
        this.webTargetFactory = webTargetFactory;
    }

    @Override
    public JsonWebKeySet get() {
        return get(openIdConfig.getJwksUri());
    }

    public JsonWebKeySet get(final String jwksUri) {
        return cache.computeIfAbsent(jwksUri, k -> {
            String json = null;
            try {
                final Response res = webTargetFactory
                        .create(jwksUri)
                        .request()
                        .get();
                json = res.readEntity(String.class);
                // Each call to the service should get the same result so we can overwrite
                // the value from another thread.
                return new JsonWebKeySet(json);
            } catch (JoseException e) {
                LOGGER.error("Error building JsonWebKeySet from json: {}", json, e);
            } catch (Exception e) {
                throw new RuntimeException(LogUtil.message(
                        "Error fetching Open ID public keys from {}", jwksUri), e);
            }
            return null;
        });
    }
}
