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
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proof that {@link ProxyPipelineStressTest} can actually fail.
 * <p>
 * An invariant checker that never fires is worse than no checker: it produces a
 * green run that reads as evidence. These tests break each invariant on purpose
 * and assert that the harness notices. They are deliberately <em>not</em> tagged
 * {@code stress} - they are fast, and they need to run on every build, because
 * the thing most likely to rot here is the detection rather than the pipeline.
 * </p>
 */
class TestStressHarnessDetectsRegressions extends StroomUnitTest {

    // -------------------------------------------------------------------------
    // The ledger's three invariants
    // -------------------------------------------------------------------------

    @Test
    void testTheLedgerDetectsLoss() {
        final DeliveryLedger ledger = new DeliveryLedger();
        ledger.recordSubmitted("a");
        ledger.recordSubmitted("b");
        ledger.recordDelivered(new StressPayload.Read("a", true, null));

        assertThat(ledger.isFullyDelivered()).isFalse();
        assertThat(ledger.getLost()).containsExactly("b");
    }

    @Test
    void testTheLedgerTreatsDuplicatesAsAcceptableButCountsThem() {
        final DeliveryLedger ledger = new DeliveryLedger();
        ledger.recordSubmitted("a");
        ledger.recordDelivered(new StressPayload.Read("a", true, null));
        ledger.recordDelivered(new StressPayload.Read("a", true, null));
        ledger.recordDelivered(new StressPayload.Read("a", true, null));

        // At-least-once: three deliveries of one submission is not a failure.
        assertThat(ledger.isFullyDelivered()).isTrue();
        assertThat(ledger.getLost()).isEmpty();
        assertThat(ledger.getDeliveredCount()).isEqualTo(1);
        assertThat(ledger.getDuplicateDeliveryCount()).isEqualTo(2);
    }

    @Test
    void testTheLedgerDetectsInvention() {
        final DeliveryLedger ledger = new DeliveryLedger();
        ledger.recordSubmitted("a");
        ledger.recordDelivered(new StressPayload.Read("a", true, null));
        ledger.recordDelivered(new StressPayload.Read("never-submitted", true, null));

        assertThat(ledger.getUnexpected()).containsExactly("never-submitted");
    }

    // -------------------------------------------------------------------------
    // Payload integrity
    // -------------------------------------------------------------------------

    @Test
    void testATruncatedBodyFailsItsOwnChecksum() throws Exception {
        final Path dir = getCurrentTestDir().resolve("payload");
        StressPayload.write(dir, "payload-1", 512);

        assertThat(StressPayload.read(dir).intact()).isTrue();

        Files.write(dir.resolve("proxy.zip"), new byte[]{1, 2, 3});

        final StressPayload.Read read = StressPayload.read(dir);
        assertThat(read.payloadId()).isEqualTo("payload-1");
        assertThat(read.intact()).isFalse();
        assertThat(read.problem()).contains("checksum mismatch");
    }

    @Test
    void testAMissingFileGroupMemberIsNotIntact() throws Exception {
        final Path dir = getCurrentTestDir().resolve("payload");
        StressPayload.write(dir, "payload-1", 128);
        Files.delete(dir.resolve("proxy.entries"));

        assertThat(StressPayload.read(dir).intact()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Fault policy
    // -------------------------------------------------------------------------

    @Test
    void testAPolicyWithNothingEnabledInjectsNothing() throws Exception {
        for (final FaultPoint faultPoint : FaultPoint.values()) {
            FaultPolicy.NONE.maybeFail(faultPoint);
        }
        assertThat(FaultPolicy.NONE.getInjectedCount()).isZero();
    }

    @Test
    void testAnEnabledPolicyInjectsAndCounts() {
        final FaultPolicy policy = FaultPolicy.builder()
                .seed(42L)
                .failureRate(1.0d)
                .inject(FaultPoint.QUEUE_PUBLISH)
                .build();

        int failures = 0;
        for (int i = 0; i < 10; i++) {
            try {
                policy.maybeFail(FaultPoint.QUEUE_PUBLISH);
            } catch (final InjectedFaultException e) {
                failures++;
            }
        }

        assertThat(failures).isEqualTo(10);
        assertThat(policy.getInjectedCount(FaultPoint.QUEUE_PUBLISH)).isEqualTo(10);
        assertThat(policy.getInjectedCount(FaultPoint.QUEUE_ACK))
                .as("a point that was not enabled must never fire")
                .isZero();
    }

    @Test
    void testQuiescingStopsInjection() throws Exception {
        final FaultPolicy policy = FaultPolicy.builder()
                .failureRate(1.0d)
                .inject(FaultPoint.QUEUE_PUBLISH)
                .build();

        policy.quiesce();
        policy.maybeFail(FaultPoint.QUEUE_PUBLISH);

        assertThat(policy.getInjectedCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // End to end
    // -------------------------------------------------------------------------

    @Test
    void testCorruptionInFlightIsCaughtAtTheFarEnd() throws Exception {
        try (final StressPipeline pipeline =
                     new StressPipeline(getCurrentTestDir().resolve("corrupt"), FaultPolicy.NONE)) {

            // Submit with the consumers stopped so the payload is reachable on disk.
            pipeline.submit(1);

            final Path body = findFile(
                    pipeline.storeRootFor(ProxyPipelineConfig.RECEIVE_STORE),
                    "proxy.zip");
            assertThat(body).as("the submitted body should be in the receive store").isNotNull();

            // Rot a byte, the way a bad disk or a half-finished copy would.
            Files.write(body, "not the bytes that were written".getBytes(StandardCharsets.UTF_8));

            pipeline.start();
            pipeline.awaitFullDelivery(Duration.ofSeconds(30));

            assertThat(pipeline.getLedger().getCorruptions())
                    .as("the forward stage must notice that what arrived is not what was sent")
                    .isNotEmpty();
        }
    }

    /**
     * The regression this harness exists to catch, reproduced directly.
     * <p>
     * {@link LocalFileGroupQueue} names pending files from a counter persisted on
     * clean close. After an unclean stop that counter is stale, so a restart used
     * to hand out ids that were already in use and the new message silently
     * overwrote a queued one - loss with no error anywhere. The queue now seeds
     * its allocator from the greater of the persisted counter and the highest id
     * actually present in pending, in-flight or failed.
     * </p>
     */
    @Test
    void testAnUncleanQueueReopenDoesNotOverwriteQueuedMessages() throws Exception {
        final Path root = getCurrentTestDir().resolve("seq");

        // First run: publish and then vanish without closing, so nothing is
        // persisted about how far the counter got.
        final LocalFileGroupQueue first = new LocalFileGroupQueue("q", root);
        for (int i = 0; i < 5; i++) {
            first.publish(message("before-" + i, root));
        }

        // Second run over the same directory.
        final LocalFileGroupQueue second = new LocalFileGroupQueue("q", root);
        for (int i = 0; i < 5; i++) {
            second.publish(message("after-" + i, root));
        }

        final Set<String> drained = new HashSet<>();
        Optional<FileGroupQueueItem> next;
        while ((next = second.next()).isPresent()) {
            try (final FileGroupQueueItem item = next.get()) {
                drained.add(item.getMessage().fileGroupId());
                item.acknowledge();
            }
        }

        assertThat(drained)
                .as("every published message should still be there after an unclean reopen")
                .hasSize(10);
    }

    // -------------------------------------------------------------------------

    private static FileGroupQueueMessage message(final String fileGroupId, final Path root) {
        return FileGroupQueueMessage.create(
                "q",
                fileGroupId,
                FileStoreLocation.localFileSystem("store", root.resolve(fileGroupId)),
                "test",
                "test-node",
                null,
                Map.of());
    }

    private static Path findFile(final Path root, final String fileName) throws IOException {
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (final Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(path -> Files.isRegularFile(path)
                                    && fileName.equals(path.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        }
    }
}
