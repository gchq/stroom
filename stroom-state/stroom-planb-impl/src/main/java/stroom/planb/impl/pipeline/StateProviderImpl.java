package stroom.planb.impl.pipeline;

import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.dao.TemporalState;
import stroom.planb.shared.PlanBDoc;
import stroom.query.language.functions.StateProvider;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class StateProviderImpl implements StateProvider {

    private final PlanBDocCache stateDocCache;
    private final Cache<Key, Val> cache;
    private final Map<String, Optional<PlanBDoc>> stateDocMap = new HashMap<>();

    @Inject
    public StateProviderImpl(final PlanBDocCache stateDocCache) {
        this.stateDocCache = stateDocCache;
        cache = Caffeine.newBuilder().maximumSize(1000).build();
    }

    @Override
    public Val getState(final String mapName, final String keyName, final Instant effectiveTimeMs) {
        final String keyspace = mapName.toLowerCase(Locale.ROOT);
        final Optional<PlanBDoc> stateOptional = stateDocMap.computeIfAbsent(keyspace, k ->
                Optional.ofNullable(stateDocCache.get(keyspace)));
        return stateOptional
                .map(stateDoc -> {
                    final Key key = new Key(keyspace, keyName, effectiveTimeMs);
                    return cache.get(key,
                            k -> getState(stateDoc, keyspace, keyName, effectiveTimeMs));
                })
                .orElse(ValNull.INSTANCE);
    }

    private Val getState(final PlanBDoc doc,
                         final String mapName,
                         final String keyName,
                         final Instant eventTime) {
        return null;
//        final Provider<CqlSession> sessionProvider = cqlSessionFactory.getSessionProvider(doc.getScyllaDbRef());
//        switch (doc.getStateType()) {
//            case STATE -> {
//                final StateRequest request = new StateRequest(
//                        mapName,
//                        keyName);
//                return getVal(new impl.dao.PlanBStateDao(sessionProvider, mapName).getState(request)
//                        .map(state -> new TemporalState(state.key(),
//                                Instant.ofEpochMilli(0),
//                                state.typeId(),
//                                state.value())));
//            }
//            case TEMPORAL_STATE -> {
//                final TemporalStateRequest request = new TemporalStateRequest(
//                        mapName,
//                        keyName,
//                        eventTime);
//                return getVal(new TemporalStateDao(sessionProvider, mapName).getState(request));
//            }
//            case RANGED_STATE -> {
//                final long longKey = Long.parseLong(keyName);
//                final RangedStateRequest request = new RangedStateRequest(
//                        mapName,
//                        longKey);
//                return getVal(new RangedStateDao(sessionProvider, mapName).getState(request)
//                        .map(state -> new TemporalState(state.key(),
//                                Instant.ofEpochMilli(0),
//                                state.typeId(),
//                                state.value())));
//            }
//            case TEMPORAL_RANGED_STATE -> {
//                final long longKey = Long.parseLong(keyName);
//                final TemporalRangedStateRequest request = new TemporalRangedStateRequest(
//                        mapName,
//                        longKey,
//                        eventTime);
//                return getVal(new TemporalRangedStateDao(sessionProvider, mapName).getState(request));
//            }
//            case SESSION -> {
//                final TemporalStateRequest request = new TemporalStateRequest(
//                        mapName,
//                        keyName,
//                        eventTime);
//                return ValBoolean.create(new SessionDao(sessionProvider, mapName).inSession(request));
//            }
//            default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
//        }
    }

    private Val getVal(final Optional<TemporalState> optional) {
        return optional
                .map(state -> (Val) ValString.create(state.value().toString()))
                .orElse(ValNull.INSTANCE);
    }

    private record Key(String mapName, String keyName, Instant eventTime) {

    }
}
