package stroom.state.impl.pipeline;

import stroom.docref.DocRef;
import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.xsltfunctions.StateLookup;
import stroom.state.impl.CqlSessionFactory;
import stroom.state.impl.RangedStateDao;
import stroom.state.impl.RangedStateRequest;
import stroom.state.impl.State;
import stroom.state.impl.StateDao;
import stroom.state.impl.StateRequest;
import stroom.util.pipeline.scope.PipelineScoped;

import com.datastax.oss.driver.api.core.CqlSession;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Optional;

@PipelineScoped
public class StateLookupImpl implements StateLookup {

    private final CqlSessionFactory cqlSessionFactory;
    private final Cache<Key, Optional<State>> cache;

    @Inject
    public StateLookupImpl(final CqlSessionFactory cqlSessionFactory) {
        this.cqlSessionFactory = cqlSessionFactory;
        cache = Caffeine.newBuilder().maximumSize(1000).build();
    }

    @Override
    public void lookup(final DocRef docRef,
                       final LookupIdentifier lookupIdentifier,
                       final ReferenceDataResult result) {
        getValue(
                docRef,
                lookupIdentifier.getPrimaryMapName(),
                lookupIdentifier.getKey(),
                Instant.ofEpochMilli(lookupIdentifier.getEventTime()),
                result);
    }

    private void getValue(final DocRef docRef,
                          final String mapName,
                          final String keyName,
                          final Instant eventTime,
                          final ReferenceDataResult result) {
        final Key key = new Key(docRef, mapName, keyName, eventTime);
        final Optional<State> optional = cache.get(key, k -> getState(docRef, mapName, keyName, eventTime));

        // If we found a result then add the value.
        if (optional.isPresent()) {
            final State state = optional.get();
            final RefStreamDefinition refStreamDefinition =
                    new RefStreamDefinition(docRef, "0", -1);
            final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
            result.addRefDataValueProxy(new StateValueProxy(state, mapDefinition));
        }
    }

    private Optional<State> getState(final DocRef docRef,
                                     final String mapName,
                                     final String keyName,
                                     final Instant eventTime) {
        Optional<State> optional = Optional.empty();
        final CqlSession session = cqlSessionFactory.getSession(docRef);
        // First try a range lookup.
        try {
            final long longKey = Long.parseLong(keyName);
            final RangedStateRequest request = new RangedStateRequest(
                    mapName,
                    longKey,
                    eventTime);
            optional = RangedStateDao.getState(session, request);
        } catch (final NumberFormatException e) {
            // Expected.
        }

        // Then try and exact key lookup.
        if (optional.isEmpty()) {
            final StateRequest request = new StateRequest(
                    mapName,
                    keyName,
                    eventTime);
            optional = StateDao.getState(session, request);
        }

        return optional;
    }

    private record Key(DocRef docRef, String mapName, String keyName, Instant eventTime) {

    }
}
