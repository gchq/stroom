package stroom.security.common.impl;

import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.OpenIdConfiguration.IdpType;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey.Factory;
import org.jose4j.lang.JoseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;

@Singleton
public class OpenIdPublicKeysSupplier implements Supplier<JsonWebKeySet> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenIdPublicKeysSupplier.class);

    private final OpenIdConfiguration openIdConfiguration;
    private final Client jerseyClient;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;

    private final Map<String, KeySetWrapper> cache = new ConcurrentHashMap<>();

    // In case a stroom node stays up for a very long time or the public keys change, force
    // a refresh every day.
    private static final Duration MAX_KEY_SET_AGE = Duration.ofDays(1);
    private static final long MAX_JITTER_MILLIS = Math.min(
            Duration.ofSeconds(60).toMillis(),
            MAX_KEY_SET_AGE.toMillis());

    @Inject
    OpenIdPublicKeysSupplier(final OpenIdConfiguration openIdConfiguration,
                             final Client jerseyClient,
                             final DefaultOpenIdCredentials defaultOpenIdCredentials) {
        this.openIdConfiguration = openIdConfiguration;
        this.jerseyClient = jerseyClient;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
    }

    @Override
    public JsonWebKeySet get() {
        return get(openIdConfiguration.getJwksUri());
    }

    private KeySetWrapper buildHardCodedKeySet() {
        final String json = defaultOpenIdCredentials.getPublicKeyJson();
        try {
            final PublicJsonWebKey publicJsonWebKey = Factory.newPublicJwk(json);
            JsonWebKeySet jsonWebKeySet = new JsonWebKeySet(publicJsonWebKey);
            return new KeySetWrapper(jsonWebKeySet, Long.MAX_VALUE);
        } catch (JoseException e) {
            LOGGER.error("Unable to create RsaJsonWebKey from json:\n{}", json, e);
            throw new RuntimeException(e);
        }
    }

    private boolean hasKeySetExpired(final KeySetWrapper keySetWrapper) {
        // Add a jitter, so it is less likely multiple threads will pile in at the same time
        // as only one will win, so it takes the load of the IDP
        final long jitterMs = ThreadLocalRandom.current().nextLong(MAX_JITTER_MILLIS);

        LOGGER.debug("Using jitterMillis: {}", jitterMs);
        return System.currentTimeMillis() > (keySetWrapper.expiryEpochMs + jitterMs);
    }

    private JsonWebKeySet get(final String jwksUri) {
        KeySetWrapper keySetWrapper = cache.computeIfAbsent(jwksUri, this::fetchKeys);

        if (keySetWrapper != null) {
            if (hasKeySetExpired(keySetWrapper)) {
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
        if (IdpType.TEST.equals(openIdConfiguration.getIdentityProviderType())) {
            LOGGER.debug("Using default public json web keys");
            return buildHardCodedKeySet();
        } else {
            String json = null;
            try {
                // Use Client instead of WebTargetFactory so we do it un-authenticated
                final Response res = jerseyClient
                        .target(jwksUri)
                        .request()
                        .get();
                json = res.readEntity(String.class);
                // Each call to the service should get the same result so we can overwrite
                // the value from another thread, thus avoiding any locking.
                final long expiryEpochMs = Instant.now()
                        .plus(MAX_KEY_SET_AGE)
                        .toEpochMilli();
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
    }


    // --------------------------------------------------------------------------------


    private record KeySetWrapper(
            JsonWebKeySet jsonWebKeySet,
            long expiryEpochMs) {
    }
}
