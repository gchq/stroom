package stroom.planb.impl.pipeline;

import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.data.ReaderCache;
import stroom.planb.impl.io.AbstractLmdbReader;
import stroom.planb.impl.io.RangedState;
import stroom.planb.impl.io.RangedStateReader;
import stroom.planb.impl.io.RangedStateRequest;
import stroom.planb.impl.io.SessionReader;
import stroom.planb.impl.io.SessionRequest;
import stroom.planb.impl.io.State;
import stroom.planb.impl.io.StateReader;
import stroom.planb.impl.io.StateRequest;
import stroom.planb.impl.io.StateValue;
import stroom.planb.impl.io.TemporalRangedState;
import stroom.planb.impl.io.TemporalRangedStateReader;
import stroom.planb.impl.io.TemporalRangedStateRequest;
import stroom.planb.impl.io.TemporalState;
import stroom.planb.impl.io.TemporalStateReader;
import stroom.planb.impl.io.TemporalStateRequest;
import stroom.planb.shared.PlanBDoc;
import stroom.query.language.functions.StateProvider;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class StateProviderImpl implements StateProvider {

    private final PlanBDocCache stateDocCache;
    private final Cache<Key, Val> cache;
    private final Map<String, Optional<PlanBDoc>> stateDocMap = new HashMap<>();
    private final ReaderCache readerCache;

    @Inject
    public StateProviderImpl(final PlanBDocCache stateDocCache,
                             final ReaderCache readerCache) {
        this.stateDocCache = stateDocCache;
        this.readerCache = readerCache;
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
        final Optional<AbstractLmdbReader<?, ?>> optional = readerCache.get(mapName);
        if (optional.isEmpty()) {
            return ValNull.INSTANCE;
        }

        final AbstractLmdbReader<?, ?> reader = optional.get();
        if (reader instanceof final StateReader stateReader) {
            final StateRequest request =
                    new StateRequest(keyName.getBytes(StandardCharsets.UTF_8));
            return getVal(stateReader
                    .getState(request)
                    .map(State::value));
        } else if (reader instanceof final TemporalStateReader stateReader) {
            final TemporalStateRequest request =
                    new TemporalStateRequest(keyName.getBytes(StandardCharsets.UTF_8),
                            eventTime.toEpochMilli());
            return getVal(stateReader
                    .getState(request)
                    .map(TemporalState::value));
        } else if (reader instanceof final RangedStateReader stateReader) {
            final RangedStateRequest request =
                    new RangedStateRequest(Long.parseLong(keyName));
            return getVal(stateReader
                    .getState(request)
                    .map(RangedState::value));
        } else if (reader instanceof final TemporalRangedStateReader stateReader) {
            final TemporalRangedStateRequest request =
                    new TemporalRangedStateRequest(Long.parseLong(keyName), eventTime.toEpochMilli());
            return getVal(stateReader
                    .getState(request)
                    .map(TemporalRangedState::value));
        } else if (reader instanceof final SessionReader stateReader) {
            final SessionRequest request =
                    new SessionRequest(keyName.getBytes(StandardCharsets.UTF_8), eventTime.toEpochMilli());
            return stateReader
                    .getState(request)
                    .map(session -> ValBoolean.create(true))
                    .orElse(ValBoolean.create(false));
        }

        throw new RuntimeException("Unexpected state type: " + doc.getStateType());
    }

    private Val getVal(final Optional<StateValue> optional) {
        return optional
                .map(state -> (Val) ValString.create(state.toString()))
                .orElse(ValNull.INSTANCE);
    }

    private record Key(String mapName, String keyName, Instant eventTime) {

    }
}
