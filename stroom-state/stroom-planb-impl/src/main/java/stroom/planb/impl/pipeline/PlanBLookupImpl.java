package stroom.planb.impl.pipeline;

import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.xsltfunctions.PlanBLookup;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.data.ShardManager;
import stroom.planb.impl.db.RangedStateDb;
import stroom.planb.impl.db.RangedStateRequest;
import stroom.planb.impl.db.SessionDb;
import stroom.planb.impl.db.SessionRequest;
import stroom.planb.impl.db.StateDb;
import stroom.planb.impl.db.StateRequest;
import stroom.planb.impl.db.StateValue;
import stroom.planb.impl.db.TemporalRangedStateDb;
import stroom.planb.impl.db.TemporalRangedStateRequest;
import stroom.planb.impl.db.TemporalState;
import stroom.planb.impl.db.TemporalStateDb;
import stroom.planb.impl.db.TemporalStateRequest;
import stroom.planb.shared.PlanBDoc;
import stroom.security.api.SecurityContext;
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

@PipelineScoped
public class PlanBLookupImpl implements PlanBLookup {

    private final PlanBDocCache stateDocCache;
    private final Cache<Key, Optional<TemporalState>> cache;
    private final ShardManager shardManager;
    private final Map<String, Optional<PlanBDoc>> stateDocMap = new HashMap<>();
    private final SecurityContext securityContext;

    @Inject
    public PlanBLookupImpl(final PlanBDocCache stateDocCache,
                           final ShardManager shardManager,
                           final SecurityContext securityContext) {
        this.stateDocCache = stateDocCache;
        this.shardManager = shardManager;
        this.securityContext = securityContext;
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
        final String docName = mapName.toLowerCase(Locale.ROOT);
        final Optional<PlanBDoc> stateOptional = stateDocMap.computeIfAbsent(docName, k ->
                securityContext.useAsReadResult(() ->
                        Optional.ofNullable(stateDocCache.get(docName))));
        stateOptional.ifPresent(stateDoc -> {
            final Key key = new Key(docName, keyName, eventTime);
            final Optional<TemporalState> optional = cache.get(key,
                    k -> getState(stateDoc, docName, keyName, eventTime));

            // If we found a result then add the value.
            if (optional.isPresent()) {
                final TemporalState state = optional.get();
                final RefStreamDefinition refStreamDefinition =
                        new RefStreamDefinition(stateDoc.asDocRef(), "0", -1);
                final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, docName);
                result.addRefDataValueProxy(new StateValueProxy(state, mapDefinition));
            }
        });
    }

    private Optional<TemporalState> getState(final PlanBDoc doc,
                                             final String mapName,
                                             final String keyName,
                                             final Instant eventTime) {
        return shardManager.get(mapName, reader -> {
            if (reader instanceof final StateDb db) {
                final StateRequest request =
                        new StateRequest(keyName.getBytes(StandardCharsets.UTF_8));
                return db
                        .getState(request)
                        .map(state -> new TemporalState(TemporalState
                                .Key
                                .builder()
                                .name(state.key().bytes())
                                .effectiveTime(0)
                                .build(),
                                state.val()));
            } else if (reader instanceof final TemporalStateDb db) {
                final TemporalStateRequest request =
                        new TemporalStateRequest(keyName.getBytes(StandardCharsets.UTF_8),
                                eventTime.toEpochMilli());
                return db
                        .getState(request);
            } else if (reader instanceof final RangedStateDb db) {
                final RangedStateRequest request =
                        new RangedStateRequest(Long.parseLong(keyName));
                return db
                        .getState(request)
                        .map(state -> new TemporalState(TemporalState
                                .Key
                                .builder()
                                .name(keyName)
                                .effectiveTime(0)
                                .build(),
                                state.val()));
            } else if (reader instanceof final TemporalRangedStateDb db) {
                final TemporalRangedStateRequest request =
                        new TemporalRangedStateRequest(Long.parseLong(keyName), eventTime.toEpochMilli());
                return db
                        .getState(request)
                        .map(state -> new TemporalState(TemporalState
                                .Key
                                .builder()
                                .name(keyName)
                                .effectiveTime(0)
                                .build(),
                                state.val()));
            } else if (reader instanceof final SessionDb db) {
                final SessionRequest request =
                        new SessionRequest(keyName.getBytes(StandardCharsets.UTF_8), eventTime.toEpochMilli());
                return db
                        .getState(request)
                        .map(state -> new TemporalState(TemporalState
                                .Key
                                .builder()
                                .name(keyName)
                                .effectiveTime(0)
                                .build(),
                                StateValue
                                        .builder()
                                        .typeId(StringValue.TYPE_ID)
                                        .byteBuffer(ByteBuffer.wrap(state.key()))
                                        .build()));
            }

            throw new RuntimeException("Unexpected state type: " + doc.getStateType());
        });
    }

    private record Key(String tableName, String keyName, Instant eventTime) {

    }
}
