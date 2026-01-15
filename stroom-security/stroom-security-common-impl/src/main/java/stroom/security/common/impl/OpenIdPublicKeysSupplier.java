/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.common.impl;

import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey.Factory;
import org.jose4j.lang.JoseException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Singleton
public class OpenIdPublicKeysSupplier implements Supplier<JsonWebKeySet> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenIdPublicKeysSupplier.class);

    private final Provider<OpenIdConfiguration> openIdConfigProvider;
    private final JerseyClientFactory jerseyClientFactory;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;

    private final Map<String, KeySetWrapper> cache = new ConcurrentHashMap<>();

    // In case a stroom node stays up for a very long time or the public keys change, force
    // a refresh every day.
    private static final Duration MAX_KEY_SET_AGE = Duration.ofDays(1);
    private static final long MAX_JITTER_MILLIS = Math.min(
            Duration.ofSeconds(60).toMillis(),
            MAX_KEY_SET_AGE.toMillis());

    @Inject
    OpenIdPublicKeysSupplier(final Provider<OpenIdConfiguration> openIdConfigProvider,
                             final JerseyClientFactory jerseyClientFactory,
                             final DefaultOpenIdCredentials defaultOpenIdCredentials) {
        this.openIdConfigProvider = openIdConfigProvider;
        this.jerseyClientFactory = jerseyClientFactory;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
    }

    @Override
    public JsonWebKeySet get() {
        final OpenIdConfiguration openIdConfiguration = openIdConfigProvider.get();
        if (IdpType.TEST_CREDENTIALS.equals(openIdConfiguration.getIdentityProviderType())) {
            return buildHardCodedKeySet();
        } else {
            return get(openIdConfiguration.getJwksUri());
        }
    }

    private JsonWebKeySet buildHardCodedKeySet() {
        final String json = defaultOpenIdCredentials.getPublicKeyJson();
        try {
            final PublicJsonWebKey publicJsonWebKey = Factory.newPublicJwk(json);
            return new JsonWebKeySet(publicJsonWebKey);
        } catch (final JoseException e) {
            LOGGER.error("Unable to create RsaJsonWebKey from hard coded json:\n{}", json, e);
            throw new RuntimeException(e);
        }
    }

//    private KeySetWrapper buildHardCodedKeySet() {
//        final String json = defaultOpenIdCredentials.getPublicKeyJson();
//        try {
//            final PublicJsonWebKey publicJsonWebKey = Factory.newPublicJwk(json);
//            JsonWebKeySet jsonWebKeySet = new JsonWebKeySet(publicJsonWebKey);
//            return new KeySetWrapper(jsonWebKeySet, Long.MAX_VALUE);
//        } catch (JoseException e) {
//            LOGGER.error("Unable to create RsaJsonWebKey from json:\n{}", json, e);
//            throw new RuntimeException(e);
//        }
//    }

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
        String json = null;
        try {
            // Use Client instead of WebTargetFactory so we do it un-authenticated
            final Response res = jerseyClientFactory.createWebTarget(JerseyClientName.OPEN_ID, jwksUri)
                    .request()
                    .get();
            json = res.readEntity(String.class);
            // Each call to the service should get the same result so we can overwrite
            // the value from another thread, thus avoiding any locking.
            final long expiryEpochMs = Instant.now()
                    .plus(MAX_KEY_SET_AGE)
                    .toEpochMilli();
            LOGGER.info("Fetched jsonWebKeySet for uri {}", jwksUri);
            final JsonWebKeySet jsonWebKeySet = new JsonWebKeySet(json);
            LOGGER.debug(() -> LogUtil.message("Fetched the following keys for uri {}\n{}",
                    jwksUri, dumpJsonWebKeySet(jsonWebKeySet)));
            return new KeySetWrapper(jsonWebKeySet, expiryEpochMs);
        } catch (final JoseException e) {
            LOGGER.error("Error building JsonWebKeySet from json: {}: {}", json, e.getMessage(), e);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error fetching Open ID public keys from {}: {}", jwksUri, e.getMessage()), e);
        }
        return null;
    }

    private String dumpJsonWebKeySet(final JsonWebKeySet jsonWebKeySet) {
        if (jsonWebKeySet == null) {
            return "";
        } else {
            final List<String> lines = jsonWebKeySet.getJsonWebKeys()
                    .stream()
                    .map(jsonWebKey -> {
                        return LogUtil.message("{}: {}, {}: {}",
                                JsonWebKey.KEY_TYPE_PARAMETER,
                                jsonWebKey.getKeyType(),
                                JsonWebKey.ALGORITHM_PARAMETER,
                                jsonWebKey.getAlgorithm());
                    })
                    .toList();
            return LogUtil.toPaddedMultiLine("  ", lines);
        }
    }


    // --------------------------------------------------------------------------------


    private record KeySetWrapper(
            JsonWebKeySet jsonWebKeySet,
            long expiryEpochMs) {

    }
}
