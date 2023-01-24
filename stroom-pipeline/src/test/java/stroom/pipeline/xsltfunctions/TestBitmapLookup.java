package stroom.pipeline.xsltfunctions;

import stroom.data.shared.StreamTypeNames;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceData;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.xsltfunctions.AbstractLookup.SequenceMaker;
import stroom.pipeline.xsltfunctions.AbstractLookup.SequenceMakerFactory;
import stroom.test.common.util.xsltfunctions.XsltFunctionTestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Severity;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class TestBitmapLookup {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestBitmapLookup.class);

    protected static final String MAP = "MY_MAP";
    protected static final String DECIMAL_KEY = "10"; // 1010 in binary, bit positions 1,3 set
    protected static final String HEX_KEY = "A"; // 10 in decimal, 1010 in binary, bit positions 1,3 set

    @Mock
    private ReferenceData mockReferenceData;
    @Mock
    private MetaHolder mockMetaHolder;
    @Mock
    private XPathContext mockXPathContext;
    @Mock
    private LocationFactory mockLocationFactory;
    @Mock
    private ErrorReceiver mockErrorReceiver;
    @Mock
    private SequenceMakerFactory mockSequenceMakerFactory;

    @InjectMocks
    private BitmapLookup bitmapLookup;

    @Captor
    private ArgumentCaptor<Severity> severityCaptor;
    @Captor
    private ArgumentCaptor<String> messageCaptor;
    @Captor
    private ArgumentCaptor<LookupIdentifier> lookupIdentifierCaptor;

    @Test
    void doLookup_noRefLoaders() throws XPathException {
        final List<PipelineReference> pipelineReferences = new ArrayList<>();

        doLookup(pipelineReferences, DECIMAL_KEY);

        // Should call this once with the combined messages for the lookup
        Mockito.verify(mockErrorReceiver).log(
                severityCaptor.capture(), Mockito.any(), Mockito.any(), messageCaptor.capture(), Mockito.any());

        // Doesn't use this method
        Mockito.verify(mockErrorReceiver, Mockito.never()).logTemplate(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        assertLoggedTopLevelSeverity(Severity.ERROR, "no reference loaders");
    }

    @Test
    void doLookup_onePipeRef_noEffStrms() throws XPathException {
        final List<PipelineReference> pipelineReferences = List.of(
                new PipelineReference(
                        PipelineDoc.buildDocRef().randomUuid().name("MyPipe").build(),
                        FeedDoc.buildDocRef().randomUuid().name("MY_FEED").build(),
                        StreamTypeNames.REFERENCE));

        Mockito.doAnswer(
                        invocation -> {
                            final ReferenceDataResult result = invocation.getArgument(2);
                            return result;
                        }).when(mockReferenceData)
                .ensureReferenceDataAvailability(Mockito.any(), Mockito.any(), Mockito.any());

        doLookup(pipelineReferences, DECIMAL_KEY);

        // Should call this once with the combined messages for the lookup
        Mockito.verify(mockErrorReceiver, Mockito.times(2)).log(
                severityCaptor.capture(), Mockito.any(), Mockito.any(), messageCaptor.capture(), Mockito.any());

        // Doesn't use this method
        Mockito.verify(mockErrorReceiver, Mockito.never()).logTemplate(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        assertLoggedTopLevelSeverity(Severity.WARNING, "no effective streams");
    }

    @Test
    void doLookup_onePipeRef_mapNotInEffStrms() throws Exception {
        final List<PipelineReference> pipelineReferences = List.of(
                new PipelineReference(
                        PipelineDoc.buildDocRef().randomUuid().name("MyPipe").build(),
                        FeedDoc.buildDocRef().randomUuid().name("MY_FEED").build(),
                        StreamTypeNames.REFERENCE));

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123L);

        Mockito.doAnswer(
                        invocation -> {
                            final ReferenceDataResult result = invocation.getArgument(2);

                            // Add one effective stream
                            result.addEffectiveStream(pipelineReferences.get(0), refStreamDefinition);

                            return result;
                        }).when(mockReferenceData)
                .ensureReferenceDataAvailability(
                        Mockito.any(), lookupIdentifierCaptor.capture(), Mockito.any());

        doLookup(pipelineReferences, DECIMAL_KEY);

        // Should call this once with the combined messages for the lookup
        Mockito.verify(mockErrorReceiver, Mockito.times(2)).log(
                severityCaptor.capture(), Mockito.any(), Mockito.any(), messageCaptor.capture(), Mockito.any());

        // Doesn't use this method
        Mockito.verify(mockErrorReceiver, Mockito.never()).logTemplate(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        assertLoggedTopLevelSeverity(Severity.WARNING, "map not found in effective streams");
    }

    @Test
    void doLookup_onePipeRef_lookupNoValue() throws Exception {
        final List<PipelineReference> pipelineReferences = List.of(
                new PipelineReference(
                        PipelineDoc.buildDocRef().randomUuid().name("MyPipe").build(),
                        FeedDoc.buildDocRef().randomUuid().name("MY_FEED").build(),
                        StreamTypeNames.REFERENCE));

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123L);

        initSequenceMaker(true, false);

        Mockito.doAnswer(
                        invocation -> {
                            final ReferenceDataResult result = invocation.getArgument(2);

                            // Add one effective stream
                            result.addEffectiveStream(pipelineReferences.get(0), refStreamDefinition);

                            final ReferenceDataResult resultSpy = Mockito.spy(result);
                            final RefDataValueProxy mockRefDataValueProxy = Mockito.mock(RefDataValueProxy.class);

                            Mockito.doReturn(Optional.of(mockRefDataValueProxy))
                                    .when(resultSpy).getRefDataValueProxy();

                            return resultSpy;
                        }).when(mockReferenceData)
                .ensureReferenceDataAvailability(
                        Mockito.any(), lookupIdentifierCaptor.capture(), Mockito.any());

        doLookup(pipelineReferences, DECIMAL_KEY);

        // Key of "10" in decimal means bit positions 1 and 3 are set so we lookup with those
        Assertions.assertThat(lookupIdentifierCaptor.getAllValues())
                .map(LookupIdentifier::getKey)
                        .containsExactly("1", "3");

        // Should call this once with the combined messages for the lookup
        Mockito.verify(mockErrorReceiver, Mockito.times(2)).log(
                severityCaptor.capture(), Mockito.any(), Mockito.any(), messageCaptor.capture(), Mockito.any());

        // Doesn't use this method
        Mockito.verify(mockErrorReceiver, Mockito.never()).logTemplate(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        assertLoggedTopLevelSeverity(Severity.WARNING, "key not found");
    }

    @Test
    void doLookup_onePipeRef_lookupSuccess() throws Exception {
        final List<PipelineReference> pipelineReferences = List.of(
                new PipelineReference(
                        PipelineDoc.buildDocRef().randomUuid().name("MyPipe").build(),
                        FeedDoc.buildDocRef().randomUuid().name("MY_FEED").build(),
                        StreamTypeNames.REFERENCE));

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123L);

        initSequenceMaker(true, true);

        Mockito.doAnswer(
                        invocation -> {
                            final ReferenceDataResult result = invocation.getArgument(2);

                            // Add one effective stream
                            result.addEffectiveStream(pipelineReferences.get(0), refStreamDefinition);

                            final ReferenceDataResult resultSpy = Mockito.spy(result);
                            final RefDataValueProxy mockRefDataValueProxy = Mockito.mock(RefDataValueProxy.class);

                            Mockito.doReturn(Optional.of(mockRefDataValueProxy))
                                    .when(resultSpy).getRefDataValueProxy();

                            return resultSpy;
                        }).when(mockReferenceData)
                .ensureReferenceDataAvailability(
                        Mockito.any(), lookupIdentifierCaptor.capture(), Mockito.any());

        doLookup(pipelineReferences, DECIMAL_KEY);

        // Should call this once with the combined messages for the lookup
        Mockito.verify(mockErrorReceiver, Mockito.times(2)).log(
                severityCaptor.capture(), Mockito.any(), Mockito.any(), messageCaptor.capture(), Mockito.any());

        // Doesn't use this method
        Mockito.verify(mockErrorReceiver, Mockito.never()).logTemplate(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        assertLoggedTopLevelSeverity(Severity.INFO, "key found");
    }

    @Test
    void doLookup_onePipeRef_lookupSuccess_noTrace() throws Exception {
        final List<PipelineReference> pipelineReferences = List.of(
                new PipelineReference(
                        PipelineDoc.buildDocRef().randomUuid().name("MyPipe").build(),
                        FeedDoc.buildDocRef().randomUuid().name("MY_FEED").build(),
                        StreamTypeNames.REFERENCE));

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123L);

        initSequenceMaker(true, true);

        Mockito.doAnswer(
                        invocation -> {
                            final ReferenceDataResult result = invocation.getArgument(2);

                            // Add one effective stream
                            result.addEffectiveStream(pipelineReferences.get(0), refStreamDefinition);

                            final ReferenceDataResult resultSpy = Mockito.spy(result);
                            final RefDataValueProxy mockRefDataValueProxy = Mockito.mock(RefDataValueProxy.class);

                            Mockito.doReturn(Optional.of(mockRefDataValueProxy))
                                    .when(resultSpy).getRefDataValueProxy();

                            return resultSpy;
                        }).when(mockReferenceData)
                .ensureReferenceDataAvailability(Mockito.any(), Mockito.any(), Mockito.any());

        doLookup(pipelineReferences, DECIMAL_KEY, false, false);

        // Should call this once with the combined messages for the lookup
        Mockito.verify(mockErrorReceiver, Mockito.never()).log(
                severityCaptor.capture(), Mockito.any(), Mockito.any(), messageCaptor.capture(), Mockito.any());

        // Doesn't use this method
        Mockito.verify(mockErrorReceiver, Mockito.never()).logTemplate(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

//        assertLoggedTopLevelSeverity(Severity.INFO, "success");
    }

    private void assertLoggedTopLevelSeverity(final Severity expectedTopLevelSeverity) {
        assertLoggedTopLevelSeverity(expectedTopLevelSeverity, null);
    }

    private void assertLoggedTopLevelSeverity(final Severity expectedTopLevelSeverity,
                                              final String messagePart) {
        final int callCount = severityCaptor.getAllValues().size();
        for (int i = 0; i < callCount; i++) {
            LOGGER.debug("Checking call {}", i);
            final Severity loggedSeverity = severityCaptor.getAllValues().get(i);
            final String msg = messageCaptor.getAllValues().get(i);
            LOGGER.debug("Logged severity: {}, msg:\n{}", loggedSeverity, LogUtil.inBox(msg));

            Assertions.assertThat(loggedSeverity)
                    .isEqualTo(expectedTopLevelSeverity);

            if (messagePart != null) {
                Assertions.assertThat(msg)
                        .containsIgnoringCase(messagePart);
            }
        }
    }

    private void initSequenceMaker(final boolean doesLookup,
                                   final boolean isValueFound) throws XPathException {
        final SequenceMaker mockSequenceMaker = Mockito.mock(SequenceMaker.class);

        Mockito.when(mockSequenceMakerFactory.create(Mockito.any()))
                .thenReturn(mockSequenceMaker);

        if (doesLookup) {
            Mockito.when(mockSequenceMaker.consume(Mockito.any()))
                    .thenReturn(isValueFound);
        }
    }

    private void doLookup(final List<PipelineReference> pipelineReferences, final String key) {
        doLookup(pipelineReferences, key, false, true);
    }

    private void doLookup(final List<PipelineReference> pipelineReferences,
                          final String key,
                          final boolean isIgnoreWarnings,
                          final boolean isTrace) {

        bitmapLookup.configure(mockErrorReceiver, mockLocationFactory, pipelineReferences);

//        final LookupIdentifier lookupIdentifier = LookupIdentifier.of(MAP, key, Instant.now().toEpochMilli());

        final Sequence[] sequences = XsltFunctionTestUtil.buildFunctionArguments(
                MAP,
                key,
                Instant.now(),
                isIgnoreWarnings,
                isTrace);

        bitmapLookup.call(BitmapLookup.FUNCTION_NAME, mockXPathContext, sequences);

//        bitmapLookup.doLookup(mockXPathContext, isIgnoreWarnings, isTrace, lookupIdentifier);
    }
}
