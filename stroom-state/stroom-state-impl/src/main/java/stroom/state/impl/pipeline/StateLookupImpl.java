package stroom.state.impl.pipeline;

import stroom.docref.DocRef;
import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.xsltfunctions.StateLookup;
import stroom.state.impl.CqlSessionFactory;
import stroom.state.impl.RangedStateDao;
import stroom.state.impl.RangedStateRequest;
import stroom.state.impl.State;
import stroom.state.impl.StateDao;
import stroom.state.impl.StateRequest;
import stroom.state.shared.StateDoc;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class StateLookupImpl implements StateLookup {

    private final CqlSessionFactory cqlSessionFactory;


    @Inject
    public StateLookupImpl(final CqlSessionFactory cqlSessionFactory) {
        this.cqlSessionFactory = cqlSessionFactory;
    }

    /**
     * <p>
     * Given a {@link LookupIdentifier} and a list of ref data pipelines, ensure that
     * the data required to perform the lookup is in the ref store. This method will not
     * perform the lookup, instead it will populate the {@link ReferenceDataResult} with
     * a proxy object that can later be used to do the lookup.
     * </p>
     *
     * @param pipelineReferences The references to look for reference data in.
     * @param lookupIdentifier   The identifier to lookup in the reference data
     * @param result             The reference result object containing the proxy object for performing the lookup
     * @return The passed result object
     */
    @Override
    public ReferenceDataResult ensureReferenceDataAvailability(final List<PipelineReference> pipelineReferences,
                                                               final LookupIdentifier lookupIdentifier,
                                                               final ReferenceDataResult result) {
        if (result.getEffectiveStreams() == null || result.getEffectiveStreams().isEmpty()) {
            for (final PipelineReference pipelineReference : pipelineReferences) {
                // TODO : @66 FIX TEMPORARY ABUSE OF PIPELINE REF
                final DocRef docRef = pipelineReference.getPipeline();
                if (docRef != null &&
                        StateDoc.DOCUMENT_TYPE.equals(docRef.getType())) {

                    final CqlSession session = cqlSessionFactory.getSession(docRef);
                    final String mapName = lookupIdentifier.getMap();
                    final String keyName = lookupIdentifier.getKey();
                    final Instant eventTime = Instant.ofEpochMilli(lookupIdentifier.getEventTime());
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
        }
        return result;
    }
}
