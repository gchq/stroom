package stroom.planb.impl.pipeline;

import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.data.ShardManager;
import stroom.planb.impl.db.RangedState;
import stroom.planb.impl.db.RangedStateDb;
import stroom.planb.impl.db.RangedStateRequest;
import stroom.planb.impl.db.SessionDb;
import stroom.planb.impl.db.SessionRequest;
import stroom.planb.impl.db.State;
import stroom.planb.impl.db.StateDb;
import stroom.planb.impl.db.StateRequest;
import stroom.planb.impl.db.StateValue;
import stroom.planb.impl.db.TemporalRangedState;
import stroom.planb.impl.db.TemporalRangedStateDb;
import stroom.planb.impl.db.TemporalRangedStateRequest;
import stroom.planb.impl.db.TemporalState;
import stroom.planb.impl.db.TemporalStateDb;
import stroom.planb.impl.db.TemporalStateRequest;
import stroom.planb.shared.PlanBDoc;
import stroom.query.language.functions.StateProvider;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public class StateProviderImpl implements StateProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateProviderImpl.class);

    private final PlanBDocCache stateDocCache;
    private final Cache<Key, Val> cache;
    private final ShardManager shardManager;
    private final SecurityContext securityContext;

    @Inject
    public StateProviderImpl(final PlanBDocCache stateDocCache,
                             final ShardManager shardManager,
                             final SecurityContext securityContext) {
        this.stateDocCache = stateDocCache;
        this.shardManager = shardManager;
        this.securityContext = securityContext;
        cache = Caffeine.newBuilder().maximumSize(1000).build();
    }

    @Override
    public Val getState(final String mapName, final String keyName, final Instant effectiveTimeMs) {
        try {
            final String docName = mapName.toLowerCase(Locale.ROOT);
            final Optional<PlanBDoc> stateOptional = securityContext.useAsReadResult(() ->
                    Optional.ofNullable(stateDocCache.get(docName)));
            return stateOptional
                    .map(stateDoc -> {
                        final Key key = new Key(docName, keyName, effectiveTimeMs);
                        return cache.get(key,
                                k -> getState(stateDoc, docName, keyName, effectiveTimeMs));
                    })
                    .orElse(ValNull.INSTANCE);
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
            return null;
        }
    }

    private Val getState(final PlanBDoc doc,
                         final String mapName,
                         final String keyName,
                         final Instant eventTime) {
        try {
            return shardManager.get(mapName, reader -> {
                if (reader instanceof final StateDb db) {
                    final StateRequest request =
                            new StateRequest(keyName.getBytes(StandardCharsets.UTF_8));
                    return getVal(db
                            .getState(request)
                            .map(State::val));
                } else if (reader instanceof final TemporalStateDb db) {
                    final TemporalStateRequest request =
                            new TemporalStateRequest(keyName.getBytes(StandardCharsets.UTF_8),
                                    eventTime.toEpochMilli());
                    return getVal(db
                            .getState(request)
                            .map(TemporalState::val));
                } else if (reader instanceof final RangedStateDb db) {
                    final RangedStateRequest request =
                            new RangedStateRequest(Long.parseLong(keyName));
                    return getVal(db
                            .getState(request)
                            .map(RangedState::val));
                } else if (reader instanceof final TemporalRangedStateDb db) {
                    final TemporalRangedStateRequest request =
                            new TemporalRangedStateRequest(Long.parseLong(keyName), eventTime.toEpochMilli());
                    return getVal(db
                            .getState(request)
                            .map(TemporalRangedState::val));
                } else if (reader instanceof final SessionDb db) {
                    final SessionRequest request =
                            new SessionRequest(keyName.getBytes(StandardCharsets.UTF_8), eventTime.toEpochMilli());
                    return db
                            .getState(request)
                            .map(session -> ValBoolean.create(true))
                            .orElse(ValBoolean.create(false));
                }

                throw new RuntimeException("Unexpected state type: " + doc.getStateType());
            });
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
            return ValNull.INSTANCE;
        }
    }

    private Val getVal(final Optional<StateValue> optional) {
        return optional
                .map(state -> (Val) ValString.create(state.toString()))
                .orElse(ValNull.INSTANCE);
    }

    private record Key(String mapName, String keyName, Instant eventTime) {

    }
}
