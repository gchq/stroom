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

package stroom.state.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.state.shared.ScyllaDbDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

@Singleton
public class CqlSessionCacheImpl implements CqlSessionCache, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CqlSessionCacheImpl.class);

    private static final String CACHE_NAME = "CQL Session Cache";

    private final LoadingStroomCache<ScyllaDbDoc, CqlSession> cache;

    private final ScyllaDbDocCache scyllaDbDocCache;


    @Inject
    CqlSessionCacheImpl(final CacheManager cacheManager,
                        final Provider<StateConfig> stateConfigProvider,
                        final ScyllaDbDocCache scyllaDbDocCache) {
        this.scyllaDbDocCache = scyllaDbDocCache;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> stateConfigProvider.get().getSessionCache(),
                this::create,
                this::destroy);
    }

    private CqlSession create(final ScyllaDbDoc doc) {
        final String keyspace = doc.getKeyspace();
        LOGGER.info("Creating keyspace: " + keyspace);
        LOGGER.logDurationIfInfoEnabled(() -> {
            try (final CqlSession session = ScyllaDbUtil.connect(doc.getConnection())) {
                session.execute(doc.getKeyspaceCql());
            }
        }, "creatingKeyspace: " + keyspace);

        final CqlSession session = ScyllaDbUtil.keyspace(
                doc.getConnection(),
                keyspace);
        ScyllaDbUtil.printMetadata(() -> session, keyspace);
        return session;
    }

    private void destroy(final ScyllaDbDoc doc, final CqlSession session) {
        session.close();
    }

    @Override
    public CqlSession get(final DocRef scyllaDbDocRef) {
        Objects.requireNonNull(scyllaDbDocRef, "Null scyllaDbDocRef supplied");
        final ScyllaDbDoc scyllaDbDoc = scyllaDbDocCache.get(scyllaDbDocRef);
        Objects.requireNonNull(scyllaDbDoc, "Unable to find ScyllaDb doc");
        return cache.get(scyllaDbDoc);
    }

    @Override
    public void remove(final DocRef scyllaDbDocRef) {
        Objects.requireNonNull(scyllaDbDocRef, "Null scyllaDbDocRef supplied");
        final ScyllaDbDoc scyllaDbDoc = scyllaDbDocCache.get(scyllaDbDocRef);
        if (scyllaDbDoc != null) {
            cache.invalidate(scyllaDbDoc);
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
