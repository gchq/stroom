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
import stroom.state.impl.dao.RangedStateDao;
import stroom.state.impl.dao.SessionDao;
import stroom.state.impl.dao.StateDao;
import stroom.state.impl.dao.TemporalRangedStateDao;
import stroom.state.impl.dao.TemporalStateDao;
import stroom.state.shared.ScyllaDbDoc;
import stroom.state.shared.StateDoc;
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

    private final LoadingStroomCache<StateDoc, CqlSession> cache;

    private final ScyllaDbDocCache scyllaDbDocCache;
    private final StateDocCache stateDocCache;


    @Inject
    CqlSessionCacheImpl(final CacheManager cacheManager,
                        final Provider<StateConfig> stateConfigProvider,
                        final ScyllaDbDocCache scyllaDbDocCache,
                        final StateDocCache stateDocCache) {
        this.scyllaDbDocCache = scyllaDbDocCache;
        this.stateDocCache = stateDocCache;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> stateConfigProvider.get().getStateDocCache(),
                this::create,
                this::destroy);
    }

    private CqlSession create(final StateDoc stateDoc) {
        if (stateDoc.getScyllaDbRef() == null) {
            throw new RuntimeException("State doc scylla db ref not set: " + stateDoc);
        }
        final ScyllaDbDoc scyllaDbDoc = scyllaDbDocCache.get(stateDoc.getScyllaDbRef());
        if (scyllaDbDoc == null) {
            throw new RuntimeException("Scylla DB doc not found: " + stateDoc.getScyllaDbRef());
        }

        LOGGER.info("Creating keyspace...");
        LOGGER.logDurationIfInfoEnabled(() -> {
            try (final CqlSession session = ScyllaDbUtil.connect(scyllaDbDoc.getConnection())) {
                session.execute(stateDoc.getKeyspaceCql());
            }
        }, "creatingKeyspace()");

        final CqlSession session = ScyllaDbUtil.keyspace(
                scyllaDbDoc.getConnection(),
                stateDoc.getKeyspace());

        switch (stateDoc.getStateType()) {
            case STATE -> new StateDao(() -> session).createTables();
            case TEMPORAL_STATE -> new TemporalStateDao(() -> session).createTables();
            case RANGED_STATE -> new RangedStateDao(() -> session).createTables();
            case TEMPORAL_RANGED_STATE -> new TemporalRangedStateDao(() -> session).createTables();
            case SESSION -> new SessionDao(() -> session).createTables();
        }

        ScyllaDbUtil.printMetadata(() -> session, stateDoc.getKeyspace());
        return session;
    }

    private void destroy(StateDoc stateDoc, final CqlSession session) {
        session.close();
    }

    @Override
    public CqlSession get(final String keyspace) {
        Objects.requireNonNull(keyspace, "Null keyspace supplied");
        final StateDoc stateDoc = stateDocCache.get(keyspace);
        Objects.requireNonNull(keyspace, "Unable to find state doc");
        return cache.get(stateDoc);
    }

    @Override
    public void remove(final String keyspace) {
        final StateDoc stateDoc = stateDocCache.get(keyspace);
        if (stateDoc != null) {
            cache.invalidate(stateDoc);
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
