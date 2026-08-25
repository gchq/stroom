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

package stroom.proxy.app.pipeline.stress;

import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The proxy pipeline under sustained load and deliberate failure.
 * <p>
 * These are tagged {@code stress} and excluded from the normal {@code test} task
 * because they run for tens of seconds and deliberately thrash the disk. Run
 * them with:
 * </p>
 * <pre>
 * ./gradlew :stroom-proxy:stroom-proxy-app:stressTest
 * </pre>
 *
 * <h2>What these assert, and what they deliberately do not</h2>
 * <p>
 * The pipeline's contract is <strong>at-least-once</strong>. Every scenario
 * therefore asserts that nothing was lost and nothing was corrupted, and reports
 * duplicates without failing on them. Only the baseline scenario - the one with
 * no faults injected - is entitled to demand exactly-once, and it does, because
 * a pipeline that duplicates work when nothing has gone wrong is broken.
 * </p>
 * <p>
 * Every fault scenario also asserts that faults were actually injected. A stress
 * test that quietly stopped injecting anything would otherwise pass forever
 * while testing nothing, which is the most likely way for a suite like this to
 * rot.
 * </p>
 *
 * @see TestStressHarnessDetectsRegressions for proof that these assertions can
 * actually fail.
 */
@Tag("stress")
class ProxyPipelineStressTest extends StroomUnitTest {

    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(60);

    /**
     * How long to let a quiesced pipeline catch up before concluding that
     * something is stuck rather than merely slow, and restarting it.
     */
    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(20);

    // -------------------------------------------------------------------------
    // 1. Baseline
    // -------------------------------------------------------------------------

    @Test
    void testBaselineDeliversEveryPayloadExactlyOnceAndDrainsCompletely() throws Exception {
        final int payloadCount = StressScale.payloads(2_000);

        try (final StressPipeline pipeline = new StressPipeline(dir("baseline"), FaultPolicy.NONE, 2)) {
            pipeline.start();
            pipeline.submit(payloadCount);

            assertThat(pipeline.awaitFullDelivery(DRAIN_TIMEOUT))
                    .as("baseline run should drain: " + pipeline.getLedger().describe())
                    .isTrue();

            final DeliveryLedger ledger = pipeline.getLedger();

            assertThat(ledger.getSubmittedCount()).isEqualTo(payloadCount);
            assertThat(ledger.getLost()).isEmpty();
            assertThat(ledger.getUnexpected()).isEmpty();
            assertThat(ledger.getCorruptions()).isEmpty();

            // With nothing going wrong, duplication would be a bug in its own right.
            assertThat(ledger.getDuplicateDeliveryCount())
                    .as("a fault-free run must not duplicate: " + ledger.describe())
                    .isZero();

            assertThat(pipeline.getReceiveRetryCount())
                    .as("no receive should have needed retrying")
                    .isZero();

            // Every stage deletes its input after publishing, so a drained
            // pipeline owns no data at all.
            assertThat(pipeline.countStoredFileGroups().values())
                    .as("no file group should be left in any store: " + pipeline.countStoredFileGroups())
                    .allMatch(count -> count == 0L);
            assertThat(pipeline.countQueuedMessages())
                    .as("no message should be left in any queue")
                    .isZero();
        }
    }

    // -------------------------------------------------------------------------
    // 2. Store faults
    // -------------------------------------------------------------------------

    @Test
    void testStoreFaultsCostDuplicatesAndOrphansButNeverData() throws Exception {
        final int payloadCount = StressScale.payloads(500);

        final FaultPolicy policy = FaultPolicy.builder()
                .seed(1_001L)
                .failureRate(0.12d)
                .inject(FaultPoint.STORE_NEW_WRITE,
                        FaultPoint.STORE_COMMIT,
                        FaultPoint.STORE_COMMIT_AFTER,
                        FaultPoint.STORE_DELETE,
                        FaultPoint.STORE_RESOLVE)
                .build();

        try (final StressPipeline pipeline = new StressPipeline(dir("store-faults"), policy, 2)) {
            pipeline.start();
            pipeline.submit(payloadCount);

            assertRecovers(pipeline, policy);

            // STORE_COMMIT_AFTER leaves committed data nobody references. That
            // costs disk and nothing else - it must never surface as a delivery.
            assertThat(policy.getInjectedCount(FaultPoint.STORE_COMMIT_AFTER))
                    .as("the orphan-producing fault should have fired")
                    .isPositive();
            assertThat(pipeline.getLedger().getUnexpected())
                    .as("an orphan must never be delivered")
                    .isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // 3. Queue faults
    // -------------------------------------------------------------------------

    @Test
    void testQueueFaultsCostDuplicatesButNeverData() throws Exception {
        final int payloadCount = StressScale.payloads(500);

        final FaultPolicy policy = FaultPolicy.builder()
                .seed(2_002L)
                .failureRate(0.12d)
                .inject(FaultPoint.QUEUE_PUBLISH,
                        FaultPoint.QUEUE_PUBLISH_AFTER,
                        FaultPoint.QUEUE_NEXT,
                        FaultPoint.QUEUE_FAIL)
                .build();

        try (final StressPipeline pipeline = new StressPipeline(dir("queue-faults"), policy, 2)) {
            pipeline.start();
            pipeline.submit(payloadCount);

            assertRecovers(pipeline, policy);

            // A publish that lands and then reports failure is the textbook
            // at-least-once case: the producer republishes and the consumer sees
            // the group twice. If this stopped producing duplicates the fault
            // would no longer be modelling anything.
            assertThat(policy.getInjectedCount(FaultPoint.QUEUE_PUBLISH_AFTER))
                    .as("publish-then-fail should have fired")
                    .isPositive();
            assertThat(pipeline.getLedger().getDuplicateDeliveryCount())
                    .as("duplicates are the expected cost here: "
                        + pipeline.getLedger().describe())
                    .isPositive();
        }
    }

    // -------------------------------------------------------------------------
    // 4. Everything at once
    // -------------------------------------------------------------------------

    @Test
    void testEverythingAtOnceAcrossBothRoutesWithConcurrentSubmitters() throws Exception {
        final int submitters = 4;
        final int perSubmitter = StressScale.payloads(150);

        final FaultPolicy policy = FaultPolicy.builder()
                .seed(3_003L)
                .failureRate(0.06d)
                .delays(0.10d, 8)
                .inject(FaultPoint.QUEUE_PUBLISH,
                        FaultPoint.QUEUE_PUBLISH_AFTER,
                        FaultPoint.QUEUE_NEXT,
                        FaultPoint.QUEUE_FAIL,
                        FaultPoint.STORE_NEW_WRITE,
                        FaultPoint.STORE_COMMIT,
                        FaultPoint.STORE_COMMIT_AFTER,
                        FaultPoint.STORE_DELETE,
                        FaultPoint.STORE_RESOLVE)
                .build();

        try (final StressPipeline pipeline = new StressPipeline(dir("everything"), policy, 3)) {
            pipeline.start();

            final ExecutorService pool = Executors.newFixedThreadPool(submitters);
            final CountDownLatch startLine = new CountDownLatch(1);
            final List<Future<?>> futures = new ArrayList<>();

            try {
                for (int i = 0; i < submitters; i++) {
                    // Half the submitters write multi-feed entries, which routes
                    // them through the split-zip stage instead of straight to
                    // pre-aggregate.
                    final boolean viaSplitZip = i % 2 == 0;
                    futures.add(pool.submit(() -> {
                        startLine.await();
                        pipeline.submit(perSubmitter, viaSplitZip);
                        return null;
                    }));
                }

                startLine.countDown();
                for (final Future<?> future : futures) {
                    future.get(DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                }
            } finally {
                pool.shutdownNow();
            }

            assertRecovers(pipeline, policy);

            assertThat(pipeline.getLedger().getSubmittedCount())
                    .isEqualTo(submitters * perSubmitter);
            assertThat(policy.getDelayCount())
                    .as("delays should have fired too - slow is a failure mode of its own")
                    .isPositive();
            assertThat(pipeline.getReceiveRetryCount())
                    .as("receive should have rejected some submissions and kept the source")
                    .isPositive();
        }
    }

    // -------------------------------------------------------------------------
    // 5. Unclean restart
    // -------------------------------------------------------------------------

    @Test
    void testInFlightWorkIsRecoveredAfterAnUncleanRestart() throws Exception {
        final int payloadCount = StressScale.payloads(200);
        final int strandedCount = 25;

        try (final StressPipeline pipeline = new StressPipeline(dir("restart"), FaultPolicy.NONE, 2)) {
            // Submit with the consumers stopped, so everything queues up where we
            // can reach it.
            pipeline.submit(payloadCount);

            assertThat(pipeline.countPending(ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE))
                    .isEqualTo(payloadCount);

            // Lease some items and abandon them without acknowledging - exactly
            // what a kill -9 mid-process leaves behind.
            final List<AutoCloseable> leased = new ArrayList<>();
            try {
                for (int i = 0; i < strandedCount; i++) {
                    leased.add(pipeline.getQueue(ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE)
                            .next()
                            .orElseThrow());
                }
            } finally {
                for (final AutoCloseable item : leased) {
                    item.close();
                }
            }

            assertThat(pipeline.countInFlight(ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE))
                    .as("leased but unacknowledged items should be in flight")
                    .isEqualTo(strandedCount);

            // Reopen without a clean close. The queue must recover in-flight
            // messages back to pending, and must re-derive its id allocator by
            // scanning rather than trusting a counter that was never persisted.
            pipeline.crashAndReopen();

            assertThat(pipeline.countInFlight(ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE))
                    .as("nothing should still be in flight after recovery")
                    .isZero();
            assertThat(pipeline.countPending(ProxyPipelineConfig.PRE_AGGREGATE_INPUT_QUEUE))
                    .as("every stranded item should be back in pending")
                    .isEqualTo(payloadCount);

            pipeline.start();

            assertThat(pipeline.awaitFullDelivery(DRAIN_TIMEOUT))
                    .as("recovered work should complete: " + pipeline.getLedger().describe())
                    .isTrue();

            final DeliveryLedger ledger = pipeline.getLedger();
            assertThat(ledger.getLost()).isEmpty();
            assertThat(ledger.getCorruptions()).isEmpty();
            assertThat(ledger.getDuplicateDeliveryCount())
                    .as("abandoning before any work was done should not duplicate: " + ledger.describe())
                    .isZero();
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Stop injecting, let the pipeline catch up - restarting it if it has
     * stranded work - and then assert the contract.
     * <p>
     * The restart matters to the meaning of the assertion. An item whose
     * {@code acknowledge()} or {@code fail()} throws stays leased forever: the
     * local queue has no lease expiry and no reaper, so only reopening it moves
     * in-flight messages back to pending. Without the restart this method would
     * report a stall as data loss, which is both wrong and the more alarming of
     * the two.
     * </p>
     */
    private static void assertRecovers(final StressPipeline pipeline,
                                       final FaultPolicy policy) throws Exception {
        final StressPipeline.Recovery recovery =
                pipeline.quiesceAndDrain(SETTLE_TIMEOUT, DRAIN_TIMEOUT);

        final DeliveryLedger ledger = pipeline.getLedger();

        // Where the residue actually is matters more than how much there is. Data
        // still sitting in a queue or a store is work in progress; data in neither
        // is the only thing that deserves the word "lost".
        final String context = ledger.describe() + " / " + policy.describe()
                               + " / restartNeeded=" + recovery.restartWasNeeded()
                               + ", strandedInFlight=" + recovery.strandedInFlight()
                               + ", queued=" + pipeline.countQueuedMessages()
                               + ", failedMessages=" + pipeline.countFailedMessages()
                               + ", stores=" + pipeline.countStoredFileGroups();

        assertThat(policy.getInjectedCount())
                .as("no faults were injected, so this scenario tested nothing: " + policy.describe())
                .isPositive();

        assertThat(ledger.getLost())
                .as("payloads submitted but never delivered - " + context)
                .isEmpty();

        assertThat(recovery.drained())
                .as("pipeline should have caught up - " + context)
                .isTrue();

        assertThat(ledger.getCorruptions())
                .as("deliveries that did not match their own checksum - " + context)
                .isEmpty();

        assertThat(ledger.getUnexpected())
                .as("delivered something that was never submitted - " + context)
                .isEmpty();
    }

    private Path dir(final String name) {
        return getCurrentTestDir().resolve(name);
    }
}
