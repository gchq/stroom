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

package stroom.state.impl.pipeline;

import stroom.query.language.functions.StateProvider;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.state.impl.CqlSessionFactory;
import stroom.state.impl.StateDocCache;
import stroom.state.impl.dao.RangedStateDao;
import stroom.state.impl.dao.RangedStateRequest;
import stroom.state.impl.dao.SessionDao;
import stroom.state.impl.dao.StateDao;
import stroom.state.impl.dao.StateRequest;
import stroom.state.impl.dao.TemporalRangedStateDao;
import stroom.state.impl.dao.TemporalRangedStateRequest;
import stroom.state.impl.dao.TemporalState;
import stroom.state.impl.dao.TemporalStateDao;
import stroom.state.impl.dao.TemporalStateRequest;
import stroom.state.shared.StateDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Singleton
public class StateProviderImpl implements StateProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateProviderImpl.class);

    private final CqlSessionFactory cqlSessionFactory;
    private final StateDocCache stateDocCache;
    private final Cache<Key, Val> cache;
    private final Map<String, Optional<StateDoc>> stateDocMap = new HashMap<>();

    @Inject
    public StateProviderImpl(final CqlSessionFactory cqlSessionFactory,
                             final StateDocCache stateDocCache) {
        this.cqlSessionFactory = cqlSessionFactory;
        this.stateDocCache = stateDocCache;
        cache = Caffeine.newBuilder().maximumSize(1000).build();
    }

    @Override
    public Val getState(final String mapName, final String keyName, final long effectiveTimeMs) {
        try {
            final String keyspace = mapName.toLowerCase(Locale.ROOT);
            final Optional<StateDoc> stateOptional = stateDocMap.computeIfAbsent(keyspace, k ->
                    Optional.ofNullable(stateDocCache.get(keyspace)));
            return stateOptional
                    .map(stateDoc -> {
                        final Key key = new Key(keyspace, keyName, Instant.ofEpochMilli(effectiveTimeMs));
                        return cache.get(key,
                                k -> getState(stateDoc, keyspace, keyName, Instant.ofEpochMilli(effectiveTimeMs)));
                    })
                    .orElse(ValNull.INSTANCE);
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
            return null;
        }
    }

    private Val getState(final StateDoc doc,
                         final String mapName,
                         final String keyName,
                         final Instant eventTime) {
        final Provider<CqlSession> sessionProvider = cqlSessionFactory.getSessionProvider(doc.getScyllaDbRef());
        switch (doc.getStateType()) {
            case STATE -> {
                final StateRequest request = new StateRequest(
                        mapName,
                        keyName);
                return getVal(new StateDao(sessionProvider, mapName).getState(request)
                        .map(state -> new TemporalState(state.key(),
                                Instant.ofEpochMilli(0),
                                state.typeId(),
                                state.value())));
            }
            case TEMPORAL_STATE -> {
                final TemporalStateRequest request = new TemporalStateRequest(
                        mapName,
                        keyName,
                        eventTime);
                return getVal(new TemporalStateDao(sessionProvider, mapName).getState(request));
            }
            case RANGED_STATE -> {
                final long longKey = Long.parseLong(keyName);
                final RangedStateRequest request = new RangedStateRequest(
                        mapName,
                        longKey);
                return getVal(new RangedStateDao(sessionProvider, mapName).getState(request)
                        .map(state -> new TemporalState(state.key(),
                                Instant.ofEpochMilli(0),
                                state.typeId(),
                                state.value())));
            }
            case TEMPORAL_RANGED_STATE -> {
                final long longKey = Long.parseLong(keyName);
                final TemporalRangedStateRequest request = new TemporalRangedStateRequest(
                        mapName,
                        longKey,
                        eventTime);
                return getVal(new TemporalRangedStateDao(sessionProvider, mapName).getState(request));
            }
            case SESSION -> {
                final TemporalStateRequest request = new TemporalStateRequest(
                        mapName,
                        keyName,
                        eventTime);
                return ValBoolean.create(new SessionDao(sessionProvider, mapName).inSession(request));
            }
            default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
        }
    }

    private Val getVal(final Optional<TemporalState> optional) {
        return optional
                .map(state -> (Val) ValString.create(state.getValueAsString()))
                .orElse(ValNull.INSTANCE);
    }

    private record Key(String mapName, String keyName, Instant eventTime) {

    }
}
