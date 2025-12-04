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

import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.xsltfunctions.StateLookup;
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
import stroom.util.pipeline.scope.PipelineScoped;

import com.datastax.oss.driver.api.core.CqlSession;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@PipelineScoped
public class StateLookupImpl implements StateLookup {

    private static final ByteBuffer TRUE = ByteBuffer
            .wrap(Boolean.toString(true).getBytes(StandardCharsets.UTF_8));
    private static final ByteBuffer FALSE = ByteBuffer
            .wrap(Boolean.toString(false).getBytes(StandardCharsets.UTF_8));

    private final CqlSessionFactory cqlSessionFactory;
    private final StateDocCache stateDocCache;
    private final Cache<Key, Optional<TemporalState>> cache;
    private final Map<String, Optional<StateDoc>> stateDocMap = new HashMap<>();

    @Inject
    public StateLookupImpl(final CqlSessionFactory cqlSessionFactory,
                           final StateDocCache stateDocCache) {
        this.cqlSessionFactory = cqlSessionFactory;
        this.stateDocCache = stateDocCache;
        cache = Caffeine.newBuilder().maximumSize(1000).build();
    }

    @Override
    public void lookup(final LookupIdentifier lookupIdentifier,
                       final ReferenceDataResult result) {
        getValue(
                lookupIdentifier.getPrimaryMapName(),
                lookupIdentifier.getKey(),
                Instant.ofEpochMilli(lookupIdentifier.getEventTime()),
                result);
    }

    private void getValue(final String mapName,
                          final String keyName,
                          final Instant eventTime,
                          final ReferenceDataResult result) {
        final String tableName = mapName.toLowerCase(Locale.ROOT);
        final Optional<StateDoc> stateOptional = stateDocMap.computeIfAbsent(tableName, k ->
                Optional.ofNullable(stateDocCache.get(tableName)));
        stateOptional.ifPresent(stateDoc -> {
            final Key key = new Key(tableName, keyName, eventTime);
            final Optional<TemporalState> optional = cache.get(key,
                    k -> getState(stateDoc, tableName, keyName, eventTime));

            // If we found a result then add the value.
            if (optional.isPresent()) {
                final TemporalState state = optional.get();
                final RefStreamDefinition refStreamDefinition =
                        new RefStreamDefinition(stateDoc.asDocRef(), "0", -1);
                final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, tableName);
                result.addRefDataValueProxy(new StateValueProxy(state, mapDefinition));
            }
        });
    }

    private Optional<TemporalState> getState(final StateDoc doc,
                                             final String tableName,
                                             final String keyName,
                                             final Instant eventTime) {
        final Optional<TemporalState> optional;
        final Provider<CqlSession> sessionProvider = cqlSessionFactory.getSessionProvider(doc.getScyllaDbRef());

        switch (doc.getStateType()) {
            case STATE -> {
                final StateRequest request = new StateRequest(
                        tableName,
                        keyName);
                optional = new StateDao(sessionProvider, tableName).getState(request)
                        .map(state -> new TemporalState(state.key(),
                                Instant.ofEpochMilli(0),
                                state.typeId(),
                                state.value()));
            }
            case TEMPORAL_STATE -> {
                final TemporalStateRequest request = new TemporalStateRequest(
                        tableName,
                        keyName,
                        eventTime);
                optional = new TemporalStateDao(sessionProvider, tableName).getState(request);
            }
            case RANGED_STATE -> {
                final long longKey = Long.parseLong(keyName);
                final RangedStateRequest request = new RangedStateRequest(
                        tableName,
                        longKey);
                optional = new RangedStateDao(sessionProvider, tableName).getState(request)
                        .map(state -> new TemporalState(state.key(),
                                Instant.ofEpochMilli(0),
                                state.typeId(),
                                state.value()));
            }
            case TEMPORAL_RANGED_STATE -> {
                final long longKey = Long.parseLong(keyName);
                final TemporalRangedStateRequest request = new TemporalRangedStateRequest(
                        tableName,
                        longKey,
                        eventTime);
                optional = new TemporalRangedStateDao(sessionProvider, tableName).getState(request);
            }
            case SESSION -> {
                final TemporalStateRequest request = new TemporalStateRequest(
                        tableName,
                        keyName,
                        eventTime);
                final boolean inSession = new SessionDao(sessionProvider, tableName).inSession(request);
                final ByteBuffer byteBuffer = inSession
                        ? TRUE
                        : FALSE;
                final TemporalState state = new TemporalState(keyName, eventTime, StringValue.TYPE_ID, byteBuffer);
                optional = Optional.of(state);
            }
            default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
        }

        return optional;
    }

    private record Key(String tableName, String keyName, Instant eventTime) {

    }
}
