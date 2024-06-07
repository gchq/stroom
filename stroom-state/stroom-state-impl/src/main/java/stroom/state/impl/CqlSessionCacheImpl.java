/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.state.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
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

    @Inject
    CqlSessionCacheImpl(final CacheManager cacheManager,
                        final Provider<StateConfig> stateConfigProvider) {
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> stateConfigProvider.get().getStateDocCache(),
                this::create,
                this::destroy);
    }

    private CqlSession create(final ScyllaDbDoc scyllaDbDoc) {
        LOGGER.info("Creating keyspace...");
        LOGGER.logDurationIfInfoEnabled(() -> {
            try (final CqlSession session = ScyllaDbUtil.connect(scyllaDbDoc.getConnection())) {
                ScyllaDbUtil.createKeyspace(session, scyllaDbDoc.getKeyspaceCql());
            }
        }, "creatingKeyspace()");

        final CqlSession session = ScyllaDbUtil.keyspace(
                scyllaDbDoc.getConnection(),
                scyllaDbDoc.getKeyspace());
        StateDao.createTables(session);
        RangedStateDao.createTables(session);
        ScyllaDbUtil.printMetadata(session, scyllaDbDoc.getKeyspace());
        return session;
    }

    private void destroy(final ScyllaDbDoc scyllaDbDoc, final CqlSession session) {
        session.close();
    }

    @Override
    public CqlSession get(final ScyllaDbDoc scyllaDbDoc) {
        Objects.requireNonNull(scyllaDbDoc, "Null scyllaDbDoc supplied");
        return cache.get(scyllaDbDoc);
    }

    @Override
    public void remove(final ScyllaDbDoc scyllaDbDoc) {
        cache.invalidate(scyllaDbDoc);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
