package stroom.state.impl.pipeline;

import stroom.docref.DocRef;
import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.xsltfunctions.StateLookup;
import stroom.state.impl.CqlSessionFactory;
import stroom.state.impl.RangedStateDao;
import stroom.state.impl.RangedStateRequest;
import stroom.state.impl.State;
import stroom.state.impl.StateDao;
import stroom.state.impl.StateRequest;
import stroom.state.shared.StateDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class StateLookupImpl implements StateLookup {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateLookupImpl.class);

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
            final Instant eventTime = Instant.ofEpochMilli(lookupIdentifier.getEventTime());
            getValue(
                    pipelineReferences,
                    lookupIdentifier.getPrimaryMapName(),
                    lookupIdentifier.getKey(),
                    eventTime,
                    result);

            if (lookupIdentifier.isMapNested()) {
                LOGGER.trace("lookupIdentifier is nested {}", lookupIdentifier);

                final Optional<RefDataValue> optValue = result.getRefDataValueProxy()
                        .flatMap(RefDataValueProxy::supplyValue);
                // This is a nested map so we are expecting the value of the first map to be a simple
                // string so we can use it as the key for the next map. The next map could also be nested.

                if (optValue.isEmpty()) {
                    LOGGER.trace("sub-map not found for {}", lookupIdentifier);
                    // map broken ... no link found
                    result
                            .logSimpleTemplate(Severity.WARNING, "No map found for '{}'",
                                    lookupIdentifier);
                } else {
                    final RefDataValue refDataValue = optValue.get();
                    try {
                        final String nextKey = ((StringValue) refDataValue).getValue();
                        LOGGER.trace("Found value to use as next key {}", nextKey);

                        logMapLocations(result);

                        // use the value from this lookup as the key for the nested map
                        final LookupIdentifier nestedIdentifier = lookupIdentifier.getNestedLookupIdentifier(nextKey);

                        // As we are recursing, make sure the result is in a clean state for the next level of
                        // the recursion
                        result.setCurrentLookupIdentifier(nestedIdentifier);

                        result.logLazyTemplate(
                                Severity.INFO,
                                "Nested lookup using previous lookup value as new key: '{}' - " +
                                        "(primary map: {}, secondary map: {}, nested lookup: {})",
                                () -> Arrays.asList(
                                        nextKey,
                                        nestedIdentifier.getPrimaryMapName(),
                                        Objects.requireNonNullElse(nestedIdentifier.getSecondaryMapName(), ""),
                                        nestedIdentifier.isMapNested()));

                        // Recurse with the nested lookup
                        ensureReferenceDataAvailability(pipelineReferences, nestedIdentifier, result);
                    } catch (ClassCastException e) {
                        result.logLazyTemplate(Severity.ERROR,
                                "Value is the wrong type, expected: {}, found: {}",
                                () -> Arrays.asList(StringValue.class.getName(),
                                        refDataValue.getClass().getName()));
                    }
                }
            }
        }
        return result;
    }

    private void getValue(final List<PipelineReference> pipelineReferences,
                          final String mapName,
                          final String keyName,
                          final Instant eventTime,
                          final ReferenceDataResult result) {
        if (result.getEffectiveStreams() == null || result.getEffectiveStreams().isEmpty()) {
            for (final PipelineReference pipelineReference : pipelineReferences) {
                // TODO : @66 FIX TEMPORARY ABUSE OF PIPELINE REF
                final DocRef docRef = pipelineReference.getPipeline();
                if (docRef != null &&
                        StateDoc.DOCUMENT_TYPE.equals(docRef.getType())) {

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
        }
    }

    private void logMapLocations(final ReferenceDataResult result) {
        // We need to compute these values now rather than in the lamba, else
        // they will change when the ReferenceDataResult is changed during recursion.
        // May want to consider storing the chain of results so we can get at any of the
        // result levels.
        final String mapName = result.getRefDataValueProxy()
                .map(RefDataValueProxy::getMapName)
                .orElse(null);
        final List<RefStreamDefinition> qualifyingStreams = new ArrayList<>(result.getQualifyingStreams());
        final int effectiveStreamCount = result.getEffectiveStreams().size();

        result.logLazyTemplate(Severity.INFO,
                "Map '{}' found in {} out of {} effective streams: [{}]",
                () -> {
                    final String streamsStr = qualifyingStreams
                            .stream()
                            .map(RefStreamDefinition::getStreamId)
                            .map(String::valueOf)
                            .collect(Collectors.joining(", "));
                    return Arrays.asList(
                            mapName,
                            qualifyingStreams.size(),
                            effectiveStreamCount,
                            streamsStr);
                });
    }
}
