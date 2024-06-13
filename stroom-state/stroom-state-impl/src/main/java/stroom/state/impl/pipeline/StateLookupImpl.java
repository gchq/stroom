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

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Optional;

public class StateLookupImpl implements StateLookup {

    private final CqlSessionFactory cqlSessionFactory;


    @Inject
    public StateLookupImpl(final CqlSessionFactory cqlSessionFactory) {
        this.cqlSessionFactory = cqlSessionFactory;
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
        final CqlSession session = cqlSessionFactory.getSession(docRef);
        Optional<State> optional = Optional.empty();

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

        // If we found a result then add the value.
        if (optional.isPresent()) {
            final State state = optional.get();
            final RefStreamDefinition refStreamDefinition =
                    new RefStreamDefinition(docRef, "0", -1);
            final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
            result.addRefDataValueProxy(new StateValueProxy(state, mapDefinition));
        }
    }
}
