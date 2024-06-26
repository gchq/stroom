package stroom.state.impl.pipeline;

import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.xsltfunctions.StateLookup;
import stroom.state.impl.CqlSessionFactory;
import stroom.state.impl.StateDocCache;
import stroom.state.impl.dao.RangedStateDao;
import stroom.state.impl.dao.RangedStateRequest;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@PipelineScoped
public class StateLookupImpl implements StateLookup {

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
        final String keyspace = mapName.toLowerCase(Locale.ROOT);
        final Optional<StateDoc> stateOptional = stateDocMap.computeIfAbsent(keyspace, k ->
                Optional.ofNullable(stateDocCache.get(keyspace)));
        stateOptional.ifPresent(stateDoc -> {
            final Key key = new Key(keyspace, keyName, eventTime);
            final Optional<TemporalState> optional = cache.get(key,
                    k -> getState(stateDoc, keyspace, keyName, eventTime));

            // If we found a result then add the value.
            if (optional.isPresent()) {
                final TemporalState state = optional.get();
                final RefStreamDefinition refStreamDefinition =
                        new RefStreamDefinition(stateDoc.asDocRef(), "0", -1);
                final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, keyspace);
                result.addRefDataValueProxy(new StateValueProxy(state, mapDefinition));
            }
        });
    }

    private Optional<TemporalState> getState(final StateDoc stateDoc,
                                             final String mapName,
                                             final String keyName,
                                             final Instant eventTime) {
        Optional<TemporalState> optional = Optional.empty();
        final Provider<CqlSession> sessionProvider = cqlSessionFactory.getSessionProvider(stateDoc.getKeyspace());

        switch (stateDoc.getStateType()) {
            case STATE -> {
                final StateRequest request = new StateRequest(
                        mapName,
                        keyName);
                optional = new StateDao(sessionProvider).getState(request)
                        .map(state -> new TemporalState(state.key(),
                                Instant.ofEpochMilli(0),
                                state.typeId(),
                                state.value()));
            }
            case TEMPORAL_STATE -> {
                final TemporalStateRequest request = new TemporalStateRequest(
                        mapName,
                        keyName,
                        eventTime);
                optional = new TemporalStateDao(sessionProvider).getState(request);
            }
            case RANGED_STATE -> {
                final long longKey = Long.parseLong(keyName);
                final RangedStateRequest request = new RangedStateRequest(
                        mapName,
                        longKey);
                optional = new RangedStateDao(sessionProvider).getState(request)
                        .map(state -> new TemporalState(state.key(),
                                Instant.ofEpochMilli(0),
                                state.typeId(),
                                state.value()));
            }
            case TEMPORAL_RANGED_STATE -> {
                final long longKey = Long.parseLong(keyName);
                final TemporalRangedStateRequest request = new TemporalRangedStateRequest(
                        mapName,
                        longKey,
                        eventTime);
                optional = new TemporalRangedStateDao(sessionProvider).getState(request);
            }
            case SESSION -> throw new RuntimeException("Unsupported");
        }

        return optional;
    }

    private record Key(String mapName, String keyName, Instant eventTime) {

    }
}
