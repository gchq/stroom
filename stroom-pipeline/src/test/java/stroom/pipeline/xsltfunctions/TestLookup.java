/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.xsltfunctions;

import stroom.data.shared.StreamTypeNames;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.refdata.ReferenceData;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.xsltfunctions.AbstractLookup.SequenceMaker;
import stroom.pipeline.xsltfunctions.AbstractLookup.SequenceMakerFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
class TestLookup extends AbstractXsltFunctionTest<Lookup> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLookup.class);

    protected static final String MAP = "MY_MAP";
    protected static final String KEY = "MY_KEY";

    @Mock
    private ReferenceData mockReferenceData;

    @SuppressWarnings("unused") // Used by @InjectMocks
    @Mock
    private MetaHolder mockMetaHolder;
    @Mock
    private SequenceMakerFactory mockSequenceMakerFactory;
    @Mock
    private TaskContextFactory mockTaskContextFactory;
    @Mock
    private TaskContext mockTaskContext;

    @InjectMocks
    private Lookup lookup;

    @Captor
    private ArgumentCaptor<Severity> severityCaptor;
    @Captor
    private ArgumentCaptor<String> messageCaptor;

    private List<PipelineReference> pipelineReferences;

    @BeforeEach
    void setUp() {
        Mockito.when(mockTaskContextFactory.current())
                .thenReturn(mockTaskContext);
        Mockito.when(mockTaskContext.isTerminated())
                .thenReturn(false);
    }

    @Test
    void doLookup_noRefLoaders() throws XPathException {
        pipelineReferences = new ArrayList<>();

        doLookup();

        // Should call this once with the combined messages for the lookup
        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.ERROR);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("no reference loaders");

        // Doesn't use this method
        Mockito.verify(getMockErrorReceiver(), Mockito.never()).logTemplate(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void doLookup_onePipeRef_noEffStrms() throws XPathException {
        pipelineReferences = List.of(
                new PipelineReference(
                        PipelineDoc.buildDocRef().randomUuid().name("MyPipe").build(),
                        FeedDoc.buildDocRef().randomUuid().name("MY_FEED").build(),
                        StreamTypeNames.REFERENCE));

        initSequenceMaker(false, false);

        Mockito.doAnswer(
                        invocation -> {
                            final ReferenceDataResult result = invocation.getArgument(2);
                            return result;
                        }).when(mockReferenceData)
                .ensureReferenceDataAvailability(Mockito.any(), Mockito.any(), Mockito.any());

        doLookup();

        // Should call this once with the combined messages for the lookup
        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.WARNING);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("no effective streams");

        // Doesn't use this method
        Mockito.verify(getMockErrorReceiver(), Mockito.never()).logTemplate(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void doLookup_onePipeRef_mapNotInEffStrms() throws Exception {
        pipelineReferences = List.of(
                new PipelineReference(
                        PipelineDoc.buildDocRef().randomUuid().name("MyPipe").build(),
                        FeedDoc.buildDocRef().randomUuid().name("MY_FEED").build(),
                        StreamTypeNames.REFERENCE));

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123L);

        initSequenceMaker(false, false);

        Mockito.doAnswer(
                        invocation -> {
                            final ReferenceDataResult result = invocation.getArgument(2);

                            // Add one effective stream
                            result.addEffectiveStream(pipelineReferences.get(0), refStreamDefinition);

                            return result;
                        }).when(mockReferenceData)
                .ensureReferenceDataAvailability(Mockito.any(), Mockito.any(), Mockito.any());

        doLookup();

        // Should call this once with the combined messages for the lookup
        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.WARNING);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("map not found in effective streams");

        // Doesn't use this method
        Mockito.verify(getMockErrorReceiver(), Mockito.never()).logTemplate(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void doLookup_onePipeRef_lookupNoValue() throws Exception {
        pipelineReferences = List.of(
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
                .ensureReferenceDataAvailability(Mockito.any(), Mockito.any(), Mockito.any());

        doLookup();

        // Should call this once with the combined messages for the lookup
        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.WARNING);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("key not found");

        // Doesn't use this method
        Mockito.verify(getMockErrorReceiver(), Mockito.never()).logTemplate(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void doLookup_onePipeRef_lookupSuccess() throws Exception {
        pipelineReferences = List.of(
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

        doLookup();

        // Should call this once with the combined messages for the lookup
        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.INFO);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("key found");

        // Doesn't use this method
        Mockito.verify(getMockErrorReceiver(), Mockito.never()).logTemplate(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void doLookup_onePipeRef_lookupSuccess_noTrace() throws Exception {
        pipelineReferences = List.of(
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

        doLookup(false, false);

        // Should call this once with the combined messages for the lookup
        verifyNoLogCalls();

        // Doesn't use this method
        Mockito.verify(getMockErrorReceiver(), Mockito.never()).logTemplate(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private void assertLoggedTopLevelSeverity(final Severity expectedTopLevelSeverity) {
        assertLoggedTopLevelSeverity(expectedTopLevelSeverity, null);
    }

    private void assertLoggedTopLevelSeverity(final Severity expectedTopLevelSeverity,
                                              final String messagePart) {
        final Severity loggedSeverity = severityCaptor.getValue();
        final String msg = messageCaptor.getValue();
        LOGGER.debug("Logged severity: {}, msg:\n{}", loggedSeverity, LogUtil.inBox(msg));

        Assertions.assertThat(loggedSeverity)
                .isEqualTo(expectedTopLevelSeverity);

        if (messagePart != null) {
            Assertions.assertThat(msg)
                    .containsIgnoringCase(messagePart);
        }
    }

    private void initSequenceMaker(final boolean doesLookup,
                                   final boolean isValueFound) throws XPathException {
        final SequenceMaker mockSequenceMaker = Mockito.mock(SequenceMaker.class);

        Mockito.when(mockSequenceMakerFactory.create(Mockito.any()))
                .thenReturn(mockSequenceMaker);

        if (doesLookup) {
            Mockito.when(mockSequenceMaker.consume(Mockito.any(RefDataValueProxy.class)))
                    .thenReturn(isValueFound);
        }
    }

    private void doLookup() throws XPathException {
        doLookup(false, true);
    }

    private void doLookup(final boolean isIgnoreWarnings,
                          final boolean isTrace) throws XPathException {

        final Sequence sequence = callFunctionWithSimpleArgs(
                MAP,
                KEY,
                Instant.now(),
                isIgnoreWarnings,
                isTrace);

        LOGGER.info("result: {} (class: {})",
                sequence, NullSafe.get(sequence, Object::getClass, Class::getSimpleName));
    }

    @Override
    Lookup getXsltFunction() {
        return lookup;
    }

    @Override
    String getFunctionName() {
        return Lookup.FUNCTION_NAME;
    }

    @Override
    public List<PipelineReference> getPipelineReferences() {
        return pipelineReferences;
    }
}
