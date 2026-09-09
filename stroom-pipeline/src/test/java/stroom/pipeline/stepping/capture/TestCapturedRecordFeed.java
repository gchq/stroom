package stroom.pipeline.stepping.capture;

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.capture.CapturedRecordFeed.Outcome;
import stroom.pipeline.stepping.capture.CapturedRecordFeed.RecordConsumer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The join between two stages, tested against a producer the test drives by hand.
 * <p>
 * These are liveness tests as much as correctness ones. A feed that gets this wrong does not usually serve a
 * wrong record - it stops delivering and the step that is waiting on it hangs, so anywhere the feed should
 * make progress the test asserts it makes it <b>promptly</b>, not merely eventually. Per the same lesson,
 * every "it returned true" style assertion here is backed by a bound on how long it took.
 */
class TestCapturedRecordFeed {

    private static final long META_ID = 7L;
    // Deliberately small so a wait that fails to be woken shows up as a timeout rather than as a pass.
    private static final long POLL_MS = 25;
    private static final long PROMPT_MS = 3_000;

    /**
     * Records what the feed delivered, in order, so part framing can be asserted as well as the records.
     */
    private static final class Recording implements RecordConsumer {

        private final List<String> events = new ArrayList<>();

        @Override
        public synchronized void startPart(final long partIndex) {
            events.add("start:" + partIndex);
        }

        @Override
        public synchronized void record(final StepLocation location) {
            events.add("record:" + location.getPartIndex() + ":" + location.getRecordIndex());
        }

        @Override
        public synchronized void endPart(final long partIndex) {
            events.add("end:" + partIndex);
        }

        synchronized List<String> events() {
            return List.copyOf(events);
        }
    }

    private static StepLocation loc(final long part, final long record) {
        return new StepLocation(META_ID, part, record);
    }

    private CapturedRecordFeed feed(final CaptureWatermark parent) {
        return feed(parent, () -> false);
    }

    private CapturedRecordFeed feed(final CaptureWatermark parent,
                                    final java.util.function.BooleanSupplier terminated) {
        return new CapturedRecordFeed(parent, META_ID, terminated, POLL_MS);
    }

    /**
     * Runs the feed on another thread and asserts it finished within {@link #PROMPT_MS} - the bound is the
     * point, since a feed that never notices its producer stopped would otherwise just run forever.
     */
    private Outcome consumePromptly(final CapturedRecordFeed feed, final Recording recording) {
        final CompletableFuture<Outcome> future = CompletableFuture.supplyAsync(() -> feed.consume(recording));
        return assertThat(future)
                .as("the feed finished promptly rather than hanging")
                .succeedsWithin(PROMPT_MS, TimeUnit.MILLISECONDS)
                .actual();
    }

    @Test
    void testProducerThatAlreadyFinished() {
        final CaptureWatermark parent = new CaptureWatermark();
        parent.recordCaptured(loc(0, 0));
        parent.recordCaptured(loc(0, 1));
        parent.markFullyCaptured();

        final Recording recording = new Recording();
        assertThat(consumePromptly(feed(parent), recording)).isEqualTo(Outcome.COMPLETED);
        assertThat(recording.events()).containsExactly("start:0", "record:0:0", "record:0:1", "end:0");
    }

    @Test
    void testProducerThatFinishedWithoutCapturingAnything() {
        final CaptureWatermark parent = new CaptureWatermark();
        parent.markFullyCaptured();

        final Recording recording = new Recording();
        assertThat(consumePromptly(feed(parent), recording)).isEqualTo(Outcome.COMPLETED);
        assertThat(recording.events()).as("no part is opened for a producer with no records").isEmpty();
    }

    @Test
    void testRecordsArrivingWhileTheFeedWaits() {
        // The case the whole class exists for: the consumer starts before the producer has anything, and has
        // to be woken by each record rather than poll its way to the end.
        final CaptureWatermark parent = new CaptureWatermark();
        final Recording recording = new Recording();
        final CompletableFuture<Outcome> future =
                CompletableFuture.supplyAsync(() -> feed(parent).consume(recording));

        parent.recordCaptured(loc(0, 0));
        parent.recordCaptured(loc(0, 1));
        parent.recordCaptured(loc(0, 2));
        parent.markFullyCaptured();

        assertThat(future).succeedsWithin(PROMPT_MS, TimeUnit.MILLISECONDS).isEqualTo(Outcome.COMPLETED);
        assertThat(recording.events())
                .containsExactly("start:0", "record:0:0", "record:0:1", "record:0:2", "end:0");
    }

    @Test
    void testProducerThatFailsPartWay() {
        // A failed producer must end the feed, not leave it waiting - and what it did capture is still
        // delivered, because a step served from those records is still the truth about them.
        final CaptureWatermark parent = new CaptureWatermark();
        parent.recordCaptured(loc(0, 0));
        parent.markError(new RuntimeException("producer died"));

        final Recording recording = new Recording();
        assertThat(consumePromptly(feed(parent), recording)).isEqualTo(Outcome.PARENT_FAILED);
        assertThat(recording.events()).containsExactly("start:0", "record:0:0", "end:0");
    }

    @Test
    void testProducerThatFailsWhileTheFeedIsWaiting() {
        final CaptureWatermark parent = new CaptureWatermark();
        parent.recordCaptured(loc(0, 0));

        final Recording recording = new Recording();
        final CompletableFuture<Outcome> future =
                CompletableFuture.supplyAsync(() -> feed(parent).consume(recording));

        parent.markError(new RuntimeException("producer died"));

        assertThat(future).succeedsWithin(PROMPT_MS, TimeUnit.MILLISECONDS).isEqualTo(Outcome.PARENT_FAILED);
        assertThat(recording.events()).containsExactly("start:0", "record:0:0", "end:0");
    }

    @Test
    void testTerminationIsNoticedWhileWaiting() {
        // Nothing signals the watermark when a consumer is cancelled, so this only works because the feed
        // waits in bounded slices. If it blocked on one long await it would hang here.
        final CaptureWatermark parent = new CaptureWatermark();
        parent.recordCaptured(loc(0, 0));
        final AtomicBoolean terminated = new AtomicBoolean();

        final Recording recording = new Recording();
        final CompletableFuture<Outcome> future =
                CompletableFuture.supplyAsync(() -> feed(parent, terminated::get).consume(recording));

        // Let it get as far as waiting for more, or this would be testing the "terminated before it started"
        // case instead - which is the next test.
        assertThat(pollUntil(() -> recording.events().contains("record:0:0")))
                .as("the feed consumed the available record and is now waiting").isTrue();
        terminated.set(true);

        assertThat(future).succeedsWithin(PROMPT_MS, TimeUnit.MILLISECONDS).isEqualTo(Outcome.TERMINATED);
        assertThat(recording.events())
                .as("the open part is still closed, so the consumer can shut down cleanly")
                .containsExactly("start:0", "record:0:0", "end:0");
    }

    @Test
    void testTerminationBeforeAnythingIsConsumed() {
        final CaptureWatermark parent = new CaptureWatermark();
        parent.recordCaptured(loc(0, 0));

        final Recording recording = new Recording();
        assertThat(consumePromptly(feed(parent, () -> true), recording)).isEqualTo(Outcome.TERMINATED);
        assertThat(recording.events()).as("no part was opened, so none is closed").isEmpty();
    }

    @Test
    void testRecordIndicesAreNotAssumedToStartAtZero() {
        // Reader and text elements are 1-based where SAX elements are 0-based, so the feed starts wherever the
        // producer actually started.
        final CaptureWatermark parent = new CaptureWatermark();
        parent.recordCaptured(loc(0, 1));
        parent.recordCaptured(loc(0, 2));
        parent.markFullyCaptured();

        final Recording recording = new Recording();
        assertThat(consumePromptly(feed(parent), recording)).isEqualTo(Outcome.COMPLETED);
        assertThat(recording.events()).containsExactly("start:0", "record:0:1", "record:0:2", "end:0");
    }

    @Test
    void testMultiplePartsAreFramedSeparately() {
        final CaptureWatermark parent = new CaptureWatermark();
        parent.recordCaptured(loc(0, 0));
        parent.recordCaptured(loc(1, 0));
        parent.recordCaptured(loc(1, 1));
        parent.markFullyCaptured();

        final Recording recording = new Recording();
        assertThat(consumePromptly(feed(parent), recording)).isEqualTo(Outcome.COMPLETED);
        assertThat(recording.events()).containsExactly(
                "start:0", "record:0:0", "end:0",
                "start:1", "record:1:0", "record:1:1", "end:1");
    }

    @Test
    void testAPartIsClosedAsSoonAsTheNextOneAppears() {
        // Without this the feed would hold part 0 open until the whole producer finished, and a consumer that
        // flushes on endPart would not flush until then.
        final CaptureWatermark parent = new CaptureWatermark();
        parent.recordCaptured(loc(0, 0));

        final Recording recording = new Recording();
        final CompletableFuture<Outcome> future =
                CompletableFuture.supplyAsync(() -> feed(parent).consume(recording));

        parent.recordCaptured(loc(1, 0));
        // Part 0 must be closed on the strength of part 1 appearing, before the producer stops at all.
        assertThat(pollUntil(() -> recording.events().contains("end:0")))
                .as("part 0 closed once part 1 appeared").isTrue();

        parent.markFullyCaptured();
        assertThat(future).succeedsWithin(PROMPT_MS, TimeUnit.MILLISECONDS).isEqualTo(Outcome.COMPLETED);
        assertThat(recording.events()).containsExactly(
                "start:0", "record:0:0", "end:0",
                "start:1", "record:1:0", "end:1");
    }

    private boolean pollUntil(final BooleanCheck check) {
        final long deadline = System.currentTimeMillis() + PROMPT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (check.get()) {
                return true;
            }
            try {
                Thread.sleep(5);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private interface BooleanCheck {

        boolean get();
    }
}
