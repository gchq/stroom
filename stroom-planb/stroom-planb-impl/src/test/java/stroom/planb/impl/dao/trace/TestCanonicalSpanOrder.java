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

package stroom.planb.impl.dao.trace;

import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.NanoDuration;
import stroom.pathways.shared.otel.trace.Span;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether the order handed to the key builder says what route a trace took rather than what the
 * scheduler did.
 *
 * <p>Times are written in microseconds and multiplied up, because a microsecond is the scale the spans
 * this exists for actually run at.
 */
class TestCanonicalSpanOrder {

    private static final long US = 1_000L;

    @Test
    void oneThreadsSpansKeepTheOrderItRanThem() {
        // Two threads doing the same four steps, woven together the way a trace records them. The
        // steps of each thread ran one after another, so that order is the program's; which thread
        // got there first is the scheduler's.
        final List<Span> woven = List.of(
                span("Ping", 100, 400, "A"),
                span("Ping", 150, 450, "B"),
                span("Set autocommit", 410, 700, "A"),
                span("Set autocommit", 460, 750, "B"),
                span("UPDATE lock", 710, 1000, "A"),
                span("UPDATE lock", 760, 1050, "B"),
                span("Commit", 1010, 1300, "A"),
                span("Commit", 1060, 1350, "B"));

        assertThat(names(noTolerance().sort(woven)))
                .as("each thread's own steps read in the order it ran them, one thread after another, "
                    + "rather than every name gathered together")
                .containsExactly(
                        "Ping", "Set autocommit", "UPDATE lock", "Commit",
                        "Ping", "Set autocommit", "UPDATE lock", "Commit");
    }

    @Test
    void whichThreadWentFirstDoesNotChangeTheAnswer() {
        final List<Span> aFirst = List.of(
                span("Ping", 100, 400, "A"),
                span("Commit", 410, 700, "A"),
                span("Ping", 150, 450, "B"),
                span("Commit", 460, 750, "B"));
        final List<Span> bFirst = List.of(
                span("Ping", 100, 400, "B"),
                span("Commit", 410, 700, "B"),
                span("Ping", 150, 450, "A"),
                span("Commit", 460, 750, "A"));

        assertThat(names(noTolerance().sort(aFirst)))
                .as("two threads that did the same work read the same however they were timed, which "
                    + "is what lets the work be seen as the repeat it is")
                .isEqualTo(names(noTolerance().sort(bFirst)));
    }

    @Test
    void aLongRunningSpanNoLongerSwallowsTheOrderOfEverythingAfterIt() {
        // The shape of a real ProcessPathways trace: one thread holds a long span while the others
        // carry on working, so overlap alone puts all of it in one group.
        final List<Span> spans = List.of(
                span("Ping", 100, 200, "A"),
                span("UPDATE lock", 210, 300, "A"),
                span("drain", 310, 9000, "A"),
                span("Ping", 150, 250, "B"),
                span("UPDATE lock", 260, 350, "B"),
                span("drain", 360, 9500, "B"));

        assertThat(names(noTolerance().sort(spans)))
                .as("the lock is taken before the drain that needs it, which ordering by name inside "
                    + "the group would have reversed")
                .containsExactly("Ping", "UPDATE lock", "drain", "Ping", "UPDATE lock", "drain");
    }

    @Test
    void aThreadsLastSpanIsNotStrandedByWhereTheGroupEnded() {
        // What a real ProcessPathways trace does: two threads doing the same four steps, but B's last
        // step starts just after the moment A's work finished. Ending the group there would leave B a
        // step short, and the two would no longer read as the same work done twice.
        final List<Span> spans = List.of(
                span("Ping", 100, 200, "A"),
                span("Ping", 110, 210, "B"),
                span("drain", 220, 900, "A"),
                span("drain", 230, 910, "B"),
                span("Commit", 905, 950, "A"),
                span("Commit", 960, 1000, "B"));

        assertThat(names(noTolerance().sort(spans)))
                .as("B's Commit belongs to the run B was already making, however late it started")
                .containsExactly("Ping", "drain", "Commit", "Ping", "drain", "Commit");
    }

    @Test
    void spansThatDoNotSayWhichThreadRanThemDoNotHoldAGroupOpen() {
        // Nothing says these belong to one run, so they must not chain: treating them as one thread
        // would put every span with no thread named into a single group.
        final List<Span> spans = List.of(
                span("b", 100, 200),
                span("a", 5000, 5100),
                span("c", 9000, 9100));

        assertThat(names(noTolerance().sort(spans)))
                .as("well apart in time, so these are three steps rather than one moment")
                .containsExactly("b", "a", "c");
    }

    @Test
    void spansThatDoNotSayWhichThreadRanThemAreOrderedByName() {
        final List<Span> spans = List.of(
                span("b", 100, 900),
                span("a", 200, 800));

        assertThat(names(noTolerance().sort(spans)))
                .as("nothing says these could not have run at the same time, so the only repeatable "
                    + "thing to do is order them by name")
                .containsExactly("a", "b");
    }

    @Test
    void concurrentChildrenComeBackInTheSameOrderWhicheverStartedFirst() {
        // Two spans that overlap, seen both ways round.
        final List<Span> aFirst = List.of(
                span("a", 100, 900),
                span("b", 200, 800));
        final List<Span> bFirst = List.of(
                span("b", 100, 900),
                span("a", 200, 800));

        assertThat(names(noTolerance().sort(aFirst)))
                .as("overlapping spans are named in one order regardless of which started first")
                .isEqualTo(names(noTolerance().sort(bFirst)))
                .containsExactly("a", "b");
    }

    @Test
    void childrenThatRanOneAfterAnotherKeepTheirOrder() {
        // Well clear of the tolerance, so these are two separate steps rather than one moment.
        final List<Span> spans = List.of(
                span("save", 10_000, 11_000),
                span("validate", 100, 900));

        assertThat(names(noTolerance().sort(spans)))
                .as("a real sequence is behaviour, not noise, so it is kept")
                .containsExactly("validate", "save");
    }

    /**
     * The case this class exists for: short calls made from several threads at once land close together
     * without necessarily touching, and arrive in a different order every run.
     */
    @Test
    void aBurstFromSeveralThreadsSettlesOnOneOrderHoweverItIsShuffled() {
        final List<Span> burst = new ArrayList<>(List.of(
                span("Ping", 0, 400),
                span("Commit", 460, 800),
                span("Prepare statement", 500, 900),
                span("Ping", 700, 1_100),
                span("Set autocommit", 950, 1_400)));

        final List<String> first = names(order().sort(burst));
        final Random random = new Random(1);
        for (int i = 0; i < 20; i++) {
            Collections.shuffle(burst, random);
            assertThat(names(order().sort(burst)))
                    .as("shuffle %d produced a different key", i)
                    .isEqualTo(first);
        }

        assertThat(first).containsExactly(
                "Commit", "Ping", "Ping", "Prepare statement", "Set autocommit");
    }

    @Test
    void separateBurstsStayInTimeOrderAndAreOrderedByNameWithin() {
        final List<Span> spans = List.of(
                // Second burst, deliberately listed first.
                span("x", 50_000, 50_400),
                span("b", 50_100, 50_500),
                // First burst.
                span("z", 100, 500),
                span("a", 200, 600));

        assertThat(names(noTolerance().sort(spans)))
                .as("bursts are ordered by when they happened, names only within a burst")
                .containsExactly("a", "z", "b", "x");
    }

    @Test
    void aChainOfOverlapsIsOneBurstEvenWhereTheEndsDoNotTouch() {
        // c does not reach a, but b bridges them, so all three are one moment.
        final List<Span> spans = List.of(
                span("a", 0, 1_000),
                span("b", 900, 2_000),
                span("c", 1_900, 3_000));

        assertThat(names(noTolerance().sort(spans))).containsExactly("a", "b", "c");
        assertThat(names(noTolerance().sort(List.of(
                span("c", 0, 1_000),
                span("b", 900, 2_000),
                span("a", 1_900, 3_000)))))
                .as("the same three spans, names dealt out differently, still form one burst")
                .containsExactly("a", "b", "c");
    }

    @Test
    void theToleranceGathersSpansThatMissEachOtherNarrowly() {
        // A 20us gap: two threads, not one sequence. Inside the default 1ms tolerance.
        final List<Span> spans = List.of(
                span("b", 0, 400),
                span("a", 420, 800));

        assertThat(names(order().sort(spans)))
                .as("a gap this small is the scheduler, not a step")
                .containsExactly("a", "b");

        assertThat(names(new CanonicalSpanOrder(NanoDuration.ofNanos(0)).sort(spans)))
                .as("with no tolerance only real overlap groups, so the gap separates them")
                .containsExactly("b", "a");
    }

    @Test
    void emptyAndSingleInputsAreReturnedAsTheyAre() {
        assertThat(noTolerance().sort(List.of())).isEmpty();
        assertThat(noTolerance().sort(null)).isEmpty();
        assertThat(names(noTolerance().sort(List.of(span("only", 0, 100))))).containsExactly("only");
    }

    // The tolerance a document carries when nobody has changed it, which is none: the settings view
    // offers zero nanoseconds and that is what gets stored. Said here so that what these tests check
    // is what the server does. Left out, the duration falls back to a default of a millisecond, and
    // every gap written below — tens of nanoseconds — disappears inside it, so the grouping is never
    // asked the questions these tests are about.
    private static CanonicalSpanOrder noTolerance() {
        return new CanonicalSpanOrder(NanoDuration.ofNanos(0));
    }

    // A millisecond of give, which is only what the tolerance test needs.
    private static CanonicalSpanOrder order() {
        return new CanonicalSpanOrder((stroom.util.shared.time.SimpleDuration) null);
    }

    private static List<String> names(final List<Span> spans) {
        return spans.stream().map(Span::getName).toList();
    }

    private static Span span(final String name, final long startMicros, final long endMicros) {
        return Span.builder()
                .name(name)
                .startTimeUnixNano(Long.toString(startMicros * US))
                .endTimeUnixNano(Long.toString(endMicros * US))
                .build();
    }

    private static Span span(final String name,
                             final long startMicros,
                             final long endMicros,
                             final String thread) {
        return Span.builder()
                .name(name)
                .startTimeUnixNano(Long.toString(startMicros * US))
                .endTimeUnixNano(Long.toString(endMicros * US))
                .attributes(List.of(new KeyValue("thread.name", AnyValue.stringValue(thread))))
                .build();
    }
}
