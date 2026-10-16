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

package stroom.pipeline.task;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.SessionStepResolver;
import stroom.pipeline.stepping.read.SessionStepResolver.SessionStepResult;
import stroom.pipeline.stepping.read.StoreStepResolver;
import stroom.pipeline.stepping.session.SteppingSession;
import stroom.pipeline.stepping.store.StepDataStoreManager;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers what a stepping session puts on disk and when it takes it away again: streams are swept only as
 * steps reach them, and closing a session leaves nothing behind.
 * <p>
 * These live in their own class because they assert on the number of streams in the selection, and
 * {@code testTranslationTask} adds more each time it runs - sharing a database with other stepping tests
 * would make the counts depend on test order.
 */
class TestSteppingSessionLifecycle extends TranslationTest {

    private static final String FEED_NAME = "XML-EVENTS";
    private static final long TIMEOUT_MS = 60_000L;

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;
    @Inject
    private StepDataStoreManager stepDataStoreManager;

    private final SessionStepResolver resolver = new SessionStepResolver(new StoreStepResolver());

    @Test
    void testStreamsAreSweptLazilyAndDeletedOnClose() {
        importConfig();
        loadAllRefData();
        testTranslationTask(FEED_NAME, false, false);

        final PipelineStepRequest baseRequest = baseRequest();
        final ElementFingerprints fingerprints = steppingService.computeFingerprints(baseRequest);
        final SteppingSession session = steppingService.createSession(baseRequest);
        final Path sessionDir = stepDataStoreManager.getSessionDir(session.getSessionId());
        try {
            final List<Long> streamIds = session.getStreamIdList();
            assertThat(streamIds).as("feed should span several streams").hasSizeGreaterThan(1);

            // Nothing stepped yet, so nothing read.
            assertThat(sweptStreamDirs(sessionDir)).isEmpty();

            final SessionStepResult first = resolver.resolve(
                    session, baseRequest.copy().stepType(StepType.FIRST).build(), fingerprints, TIMEOUT_MS);
            assertThat(first.foundRecord()).isTrue();
            final long firstStreamId = first.foundLocation().getMetaId();

            // Only the stream the step landed in was swept. Sweeping the whole selection up front is what
            // makes stepping a large selection unusable, so this is the property to protect.
            assertThat(sweptStreamDirs(sessionDir))
                    .as("only the stepped stream should be swept")
                    .containsExactly(String.valueOf(firstStreamId));

            // Reaching into another stream sweeps that one, and only that one.
            final SessionStepResult last = resolver.resolve(
                    session, baseRequest.copy().stepType(StepType.LAST).build(), fingerprints, TIMEOUT_MS);
            assertThat(last.foundRecord()).isTrue();
            final long lastStreamId = last.foundLocation().getMetaId();
            assertThat(lastStreamId).isNotEqualTo(firstStreamId);
            assertThat(sweptStreamDirs(sessionDir))
                    .as("only the streams actually stepped into should be swept")
                    .containsExactlyInAnyOrder(
                            String.valueOf(firstStreamId), String.valueOf(lastStreamId));
            assertThat(sweptStreamDirs(sessionDir)).hasSizeLessThan(streamIds.size());
        } finally {
            session.close();
        }

        // Stepping data is temporary, but it is not small; closing must not leave it behind.
        assertThat(Files.exists(sessionDir)).isFalse();
    }

    private List<String> sweptStreamDirs(final Path sessionDir) {
        if (!Files.isDirectory(sessionDir)) {
            return List.of();
        }
        try (final Stream<Path> children = Files.list(sessionDir)) {
            return children.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PipelineStepRequest baseRequest() {
        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED_NAME).getFirst();
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED_NAME)
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_REFERENCE)
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                        .build())
                .build();
        return PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc)
                .criteria(new FindMetaCriteria(expression))
                .timeout(Long.MAX_VALUE)
                .build();
    }
}
