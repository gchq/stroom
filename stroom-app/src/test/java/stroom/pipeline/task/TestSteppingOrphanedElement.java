/*
 * Copyright 2026 Crown Copyright
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

import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/// Reproduces gh-5930's running progress counter: a pipeline holding a steppable element that is not linked
/// to anything (an orphan) is never served its first record until the whole stream has been captured.
///
/// The orphan has no steppable parent, so `SteppingGraphBuilder` marks it as part of the record boundary. The
/// backbone (the capture truncated at the parser) never runs it, so it holds no records, and `StagePlanner`
/// reads that as a boundary change and asks for a full capture. The step therefore waits on the backbone for
/// the whole stream instead of materialising the record it asked for. On a large stream the backbone fills
/// the stepping store's byte cap first, and the user gets the cap's error instead of a record.
///
/// Every step here starts the way the UI does: a REFRESH at `[stream:0:0]`, with no session.
class TestSteppingOrphanedElement extends TranslationTest {

    private static final AtomicBoolean DONE_SETUP = new AtomicBoolean();

    private static final String FEED = "XML-EVENTS";
    private static final String ORPHAN_ELEMENT_ID = "orphanedXslt";
    private static final String ORPHAN_ELEMENT_TYPE = "XSLTFilter";
    // Big enough that the backbone is still running when the first record could have been served.
    private static final int RECORD_COUNT = 4_000;
    // Far less than the backbone of the whole stream needs (about 2 KB a record, so about 8 MB), standing in for
    // the 2 GiB default against a 1 GiB compressed stream. The backbone may fill only 90% of it, and the 10% it
    // leaves must hold the first record materialised through every element below the parser, which is tens of
    // KB, so the cap cannot be much smaller than this.
    private static final long SMALL_STORE_CAP_BYTES = 2_000_000L;

    @Inject
    private SteppingService steppingService;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocFinder docFinder;
    @Inject
    private Store store;

    @BeforeEach
    void setup() {
        if (!DONE_SETUP.get()) {
            importConfig();
            loadAllRefData();
            testTranslationTask(FEED, false, false);
            DONE_SETUP.set(true);
        }
        // Eager threshold zero so a completed backbone is never promoted to a whole-stream materialisation:
        // the first record must be served by materialising that record alone.
        setConfigValueMapper(SteppingConfig.class,
                config -> config.withSkeletonSweep(true).withEagerMaterialisationRecords(0));
    }

    @AfterEach
    void restoreConfig() {
        clearConfigValueMapper();
    }

    @Override
    protected boolean cleanupBetweenTests() {
        return false;
    }

    /// Control: without the orphan, the first record is materialised on its own behind the backbone, with no
    /// full sweep.
    @Test
    void firstRefreshIsServedWithoutAFullSweep() {
        final long metaId = GeneratedEventStream.load(store, FEED, RECORD_COUNT);
        final PipelineStepRequest base = requestFor(pipelineDoc(), metaId);

        final long fullSweeps = steppingService.getFullSweepLaunchCount();
        String sessionUuid = null;
        try {
            final SteppingResult first = steppingService.step(initialRefresh(base, metaId));
            sessionUuid = first.getSessionUuid();

            assertThat(first.getGeneralErrors()).as("general errors").isNullOrEmpty();
            assertThat(first.isFoundRecord()).as("the first record was found").isTrue();
            assertThat(first.getFoundLocation().getRecordIndex()).isZero();
            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("no full sweep was needed").isEqualTo(fullSweeps);
        } finally {
            terminate(base, sessionUuid);
        }
    }

    /// With an orphaned element in the pipeline, the first record should still be served without a full
    /// sweep. Today the step waits for the backbone to capture the whole stream, then starts a full sweep.
    @Test
    void firstRefreshWithAnOrphanedElementIsServedWithoutAFullSweep() {
        final long metaId = GeneratedEventStream.load(store, FEED, RECORD_COUNT);
        final PipelineStepRequest base = requestFor(withOrphanedElement(pipelineDoc()), metaId);

        final long fullSweeps = steppingService.getFullSweepLaunchCount();
        String sessionUuid = null;
        try {
            final SteppingResult first = steppingService.step(initialRefresh(base, metaId));
            sessionUuid = first.getSessionUuid();

            assertThat(first.getGeneralErrors()).as("general errors").isNullOrEmpty();
            assertThat(first.isFoundRecord()).as("the first record was found").isTrue();
            assertThat(first.getFoundLocation().getRecordIndex()).isZero();
            assertThat(steppingService.getFullSweepLaunchCount())
                    .as("no full sweep was needed").isEqualTo(fullSweeps);
        } finally {
            terminate(base, sessionUuid);
        }
    }

    /// The user's symptom: with an orphaned element and a stream bigger than the store's byte cap, the first
    /// step should still land on the first record. Today the backbone fills the store while the step waits,
    /// and the step fails with the cap's error.
    @Test
    void firstRefreshWithAnOrphanedElementIsServedBeforeTheStoreFills() {
        final long metaId = GeneratedEventStream.load(store, FEED, RECORD_COUNT);
        final PipelineStepRequest base = requestFor(withOrphanedElement(pipelineDoc()), metaId);
        setConfigValueMapper(SteppingConfig.class,
                config -> withMaxBytesPerStream(
                        config.withSkeletonSweep(true).withEagerMaterialisationRecords(0),
                        SMALL_STORE_CAP_BYTES));

        String sessionUuid = null;
        try {
            final SteppingResult first = steppingService.step(initialRefresh(base, metaId));
            sessionUuid = first.getSessionUuid();

            assertThat(first.getGeneralErrors()).as("general errors").isNullOrEmpty();
            assertThat(first.isFoundRecord()).as("the first record was found").isTrue();
            assertThat(first.getFoundLocation().getRecordIndex()).isZero();
        } finally {
            terminate(base, sessionUuid);
        }
    }

    private PipelineDoc pipelineDoc() {
        final DocRef pipelineRef = docFinder.findByName(PipelineDoc.TYPE, FEED).getFirst();
        return pipelineStore.readDocument(pipelineRef);
    }

    // The pipeline with an XSLT filter added but not linked to anything, as left behind when a user deletes
    // the links to an element but not the element itself. It is only in the request, not saved, as with an
    // unsaved pipeline in the stepping screen.
    private PipelineDoc withOrphanedElement(final PipelineDoc pipelineDoc) {
        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineDoc.getPipelineData());
        builder.addElement(new PipelineElement(ORPHAN_ELEMENT_ID, ORPHAN_ELEMENT_TYPE));
        return pipelineDoc.copy().pipelineData(builder.build()).build();
    }

    private PipelineStepRequest requestFor(final PipelineDoc pipelineDoc, final long metaId) {
        return PipelineStepRequest.builder()
                .pipelineDoc(pipelineDoc)
                .criteria(new FindMetaCriteria(ExpressionOperator.builder()
                        .addIdTerm(MetaFields.ID, Condition.EQUALS, metaId)
                        .build()))
                .timeout(Long.MAX_VALUE)
                .build();
    }

    // The step the UI sends when stepping starts on a stream: SteppingPresenter begins with a REFRESH at
    // part 0, record 0.
    private PipelineStepRequest initialRefresh(final PipelineStepRequest base, final long metaId) {
        return base.copy()
                .stepType(StepType.REFRESH)
                .stepLocation(new StepLocation(metaId, 0L, 0L))
                .build();
    }

    private void terminate(final PipelineStepRequest base, final String sessionUuid) {
        if (sessionUuid != null) {
            steppingService.terminateStepping(base.copy().sessionUuid(sessionUuid).build());
        }
    }

    private static SteppingConfig withMaxBytesPerStream(final SteppingConfig config, final long maxBytes) {
        return new SteppingConfig(
                config.getStoreSubDir(),
                config.getMaxRecordsPerStream(),
                maxBytes,
                config.getMaxRecordSizeBytes(),
                config.getMaxSweptStreamsPerSession(),
                config.getMaxRetainedFingerprintsPerElement(),
                config.getFilteredScanWindow(),
                config.getMaxSessionIdleTime(),
                config.getOrphanMaxAge(),
                config.isSkeletonSweep(),
                config.getEagerMaterialisationRecords(),
                config.getPrefetchWindow());
    }
}
