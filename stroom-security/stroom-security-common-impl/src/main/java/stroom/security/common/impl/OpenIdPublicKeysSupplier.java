package stroom.security.common.impl;

import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.lang.JoseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;

@Singleton
public class OpenIdPublicKeysSupplier implements Supplier<JsonWebKeySet> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenIdPublicKeysSupplier.class);

    private final OpenIdConfiguration openIdConfiguration;
    private final WebTargetFactory webTargetFactory;

    private final Map<String, KeySetWrapper> cache = new ConcurrentHashMap<>();

    // In case a stroom node stays up for a very long time or the public keys change, force
    // a refresh every day.
//    private static final Duration MAX_KEY_SET_AGE = Duration.ofDays(1);
    private static final Duration MAX_KEY_SET_AGE = Duration.ofSeconds(10);

    @Inject
    OpenIdPublicKeysSupplier(final OpenIdConfiguration openIdConfiguration,
                             final WebTargetFactory webTargetFactory) {
        this.openIdConfiguration = openIdConfiguration;
        this.webTargetFactory = webTargetFactory;
    }

    @Override
    public JsonWebKeySet get() {
        return get(openIdConfiguration.getJwksUri());
    }

    public JsonWebKeySet get(final String jwksUri) {
        KeySetWrapper keySetWrapper = cache.computeIfAbsent(jwksUri, this::fetchKeys);

        if (keySetWrapper != null) {
            if (System.currentTimeMillis() > keySetWrapper.expiryEpochMs) {
                LOGGER.info("Refreshing JsonWebKeySet for {}", jwksUri);
                final KeySetWrapper keySetWrapper2 = fetchKeys(jwksUri);
                if (keySetWrapper2 != null) {
                    cache.put(jwksUri, keySetWrapper2);
                    keySetWrapper = keySetWrapper2;
                } else {
                    cache.remove(jwksUri);
                }
            }
            return keySetWrapper.jsonWebKeySet;
        } else {
            return null;
        }
    }

    private KeySetWrapper fetchKeys(final String jwksUri) {
        String json = null;
        try {
            final Response res = webTargetFactory
                    .create(jwksUri)
                    .request()
                    .get();
            json = res.readEntity(String.class);
            // Each call to the service should get the same result so we can overwrite
            // the value from another thread, thus avoiding any locking.
            final long expiryEpochMs = Instant.now().plus(MAX_KEY_SET_AGE).toEpochMilli();
            LOGGER.info("Fetched jsonWebKeySet for uri {}", jwksUri);
            return new KeySetWrapper(new JsonWebKeySet(json), expiryEpochMs);
        } catch (JoseException e) {
            LOGGER.error("Error building JsonWebKeySet from json: {}", json, e);
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error fetching Open ID public keys from {}", jwksUri), e);
        }
        return null;
    }

    private record KeySetWrapper(
            JsonWebKeySet jsonWebKeySet,
            long expiryEpochMs) {
    }
}
