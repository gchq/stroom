package stroom.planb.impl.pipeline;

import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.xsltfunctions.StateLookupProvider;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.dao.TemporalState;
import stroom.planb.shared.PlanBDoc;
import stroom.util.pipeline.scope.PipelineScoped;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// TODO : FIXME

@PipelineScoped
public class StateLookupProviderImpl implements StateLookupProvider {

    private static final ByteBuffer TRUE = ByteBuffer
            .wrap(Boolean.toString(true).getBytes(StandardCharsets.UTF_8));
    private static final ByteBuffer FALSE = ByteBuffer
            .wrap(Boolean.toString(false).getBytes(StandardCharsets.UTF_8));

    private final PlanBDocCache stateDocCache;
    private final Cache<Key, Optional<TemporalState>> cache;
    private final Map<String, Optional<PlanBDoc>> stateDocMap = new HashMap<>();

    @Inject
    public StateLookupProviderImpl(final PlanBDocCache stateDocCache) {
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
        final Optional<PlanBDoc> stateOptional = stateDocMap.computeIfAbsent(tableName, k ->
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
//                result.addRefDataValueProxy(new StateValueProxy(state, mapDefinition));
            }
        });
    }

    private Optional<TemporalState> getState(final PlanBDoc doc,
                                             final String tableName,
                                             final String keyName,
                                             final Instant eventTime) {
        Optional<TemporalState> optional;

        switch (doc.getStateType()) {
//            case STATE -> {
//                final StateRequest request = new StateRequest(
//                        tableName,
//                        keyName);
//                optional = new impl.dao.PlanBStateDao(sessionProvider, tableName).getState(request)
//                        .map(state -> new TemporalState(state.key(),
//                                Instant.ofEpochMilli(0),
//                                state.typeId(),
//                                state.value()));
//            }
//            case TEMPORAL_STATE -> {
//                final TemporalStateRequest request = new TemporalStateRequest(
//                        tableName,
//                        keyName,
//                        eventTime);
//                optional = new TemporalStateDao(sessionProvider, tableName).getState(request);
//            }
//            case RANGED_STATE -> {
//                final long longKey = Long.parseLong(keyName);
//                final RangedStateRequest request = new RangedStateRequest(
//                        tableName,
//                        longKey);
//                optional = new RangedStateDao(sessionProvider, tableName).getState(request)
//                        .map(state -> new TemporalState(state.key(),
//                                Instant.ofEpochMilli(0),
//                                state.typeId(),
//                                state.value()));
//            }
//            case TEMPORAL_RANGED_STATE -> {
//                final long longKey = Long.parseLong(keyName);
//                final TemporalRangedStateRequest request = new TemporalRangedStateRequest(
//                        tableName,
//                        longKey,
//                        eventTime);
//                optional = new TemporalRangedStateDao(sessionProvider, tableName).getState(request);
//            }
//            case SESSION -> {
//                final TemporalStateRequest request = new TemporalStateRequest(
//                        tableName,
//                        keyName,
//                        eventTime);
//                final boolean inSession = new SessionDao(sessionProvider, tableName).inSession(request);
//                final ByteBuffer byteBuffer = inSession
//                        ? TRUE
//                        : FALSE;
//                final TemporalState state = new TemporalState(keyName, eventTime, StringValue.TYPE_ID, byteBuffer);
//                optional = Optional.of(state);
//            }
            default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
        }
//
//        return optional;
    }

    private record Key(String tableName, String keyName, Instant eventTime) {

    }
}
