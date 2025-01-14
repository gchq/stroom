package stroom.planb.impl.pipeline;

import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.xsltfunctions.PlanBLookup;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.data.ReaderCache;
import stroom.planb.impl.io.RangedStateReader;
import stroom.planb.impl.io.RangedStateRequest;
import stroom.planb.impl.io.SessionReader;
import stroom.planb.impl.io.SessionRequest;
import stroom.planb.impl.io.StateReader;
import stroom.planb.impl.io.StateRequest;
import stroom.planb.impl.io.StateValue;
import stroom.planb.impl.io.TemporalRangedStateReader;
import stroom.planb.impl.io.TemporalRangedStateRequest;
import stroom.planb.impl.io.TemporalState;
import stroom.planb.impl.io.TemporalStateReader;
import stroom.planb.impl.io.TemporalStateRequest;
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

@PipelineScoped
public class PlanBLookupImpl implements PlanBLookup {

    private final PlanBDocCache stateDocCache;
    private final Cache<Key, Optional<TemporalState>> cache;
    private final ReaderCache readerCache;
    private final Map<String, Optional<PlanBDoc>> stateDocMap = new HashMap<>();

    @Inject
    public PlanBLookupImpl(final PlanBDocCache stateDocCache,
                           final ReaderCache readerCache) {
        this.stateDocCache = stateDocCache;
        this.readerCache = readerCache;
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
        final String name = mapName.toLowerCase(Locale.ROOT);
        final Optional<PlanBDoc> stateOptional = stateDocMap.computeIfAbsent(name, k ->
                Optional.ofNullable(stateDocCache.get(name)));
        stateOptional.ifPresent(stateDoc -> {
            final Key key = new Key(name, keyName, eventTime);
            final Optional<TemporalState> optional = cache.get(key,
                    k -> getState(stateDoc, name, keyName, eventTime));

            // If we found a result then add the value.
            if (optional.isPresent()) {
                final TemporalState state = optional.get();
                final RefStreamDefinition refStreamDefinition =
                        new RefStreamDefinition(stateDoc.asDocRef(), "0", -1);
                final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, name);
                result.addRefDataValueProxy(new StateValueProxy(state, mapDefinition));
            }
        });
    }

    private Optional<TemporalState> getState(final PlanBDoc doc,
                                             final String mapName,
                                             final String keyName,
                                             final Instant eventTime) {
        return readerCache.get(mapName, reader -> {
            if (reader instanceof final StateReader stateReader) {
                final StateRequest request =
                        new StateRequest(keyName.getBytes(StandardCharsets.UTF_8));
                return stateReader
                        .getState(request)
                        .map(state -> new TemporalState(TemporalState
                                .Key
                                .builder()
                                .name(state.key().bytes())
                                .effectiveTime(0)
                                .build(),
                                state.value()));
            } else if (reader instanceof final TemporalStateReader stateReader) {
                final TemporalStateRequest request =
                        new TemporalStateRequest(keyName.getBytes(StandardCharsets.UTF_8),
                                eventTime.toEpochMilli());
                return stateReader
                        .getState(request);
            } else if (reader instanceof final RangedStateReader stateReader) {
                final RangedStateRequest request =
                        new RangedStateRequest(Long.parseLong(keyName));
                return stateReader
                        .getState(request)
                        .map(state -> new TemporalState(TemporalState
                                .Key
                                .builder()
                                .name(keyName)
                                .effectiveTime(0)
                                .build(),
                                state.value()));
            } else if (reader instanceof final TemporalRangedStateReader stateReader) {
                final TemporalRangedStateRequest request =
                        new TemporalRangedStateRequest(Long.parseLong(keyName), eventTime.toEpochMilli());
                return stateReader
                        .getState(request)
                        .map(state -> new TemporalState(TemporalState
                                .Key
                                .builder()
                                .name(keyName)
                                .effectiveTime(0)
                                .build(),
                                state.value()));
            } else if (reader instanceof final SessionReader stateReader) {
                final SessionRequest request =
                        new SessionRequest(keyName.getBytes(StandardCharsets.UTF_8), eventTime.toEpochMilli());
                return stateReader
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
