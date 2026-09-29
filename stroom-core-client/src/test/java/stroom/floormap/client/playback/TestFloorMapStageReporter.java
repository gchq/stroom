/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.playback;

import stroom.floormap.client.playback.FloorMapStageReporter.Stage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The classification is the easy half. What earns its place is the *filtering*: the guards
/// this replaces existed because reporting on first sight is wrong, and a version that reports
/// eagerly would be worse than the silence it fixes.
class TestFloorMapStageReporter {

    private static final long TICK = 300L;   // the playback throttle
    private static final long T0 = 1_700_000_000_000L;


    // -----------------------------------------------------------------------
    // Classification
    // -----------------------------------------------------------------------

    @Test
    void testSomethingDrawnIsNotAFailure() {
        assertThat(FloorMapStageReporter.classify(10, 4, 9, 4)).isEqualTo(Stage.NONE);
    }

    @Test
    void testNoRowsNamesTheQuery() {
        assertThat(FloorMapStageReporter.classify(0, 0, 9, 0)).isEqualTo(Stage.NO_EVENT_ROWS);
    }

    /// Rows but no entities is the column-name mismatch, which is the commonest misconfiguration.
    @Test
    void testRowsButNoEntitiesNamesTheParse() {
        assertThat(FloorMapStageReporter.classify(10, 0, 9, 0))
                .isEqualTo(Stage.NO_ENTITIES_PARSED);
    }

    @Test
    void testEntitiesButNoFactsNamesTheFacts() {
        assertThat(FloorMapStageReporter.classify(10, 4, 0, 0)).isEqualTo(Stage.NO_FACTS);
    }

    @Test
    void testEntitiesAndFactsButNoPlacementsNamesThePlacement() {
        assertThat(FloorMapStageReporter.classify(10, 4, 9, 0)).isEqualTo(Stage.NO_PLACEMENTS);
    }

    /// Empty facts with entities drawn is normal, not a fault.
    ///
    /// An entity whose location is literal coordinates needs no facts at all, so a map made
    /// entirely of those has none — and must not be told it is broken.
    @Test
    void testNoFactsIsNotReportedWhenEntitiesAreStillDrawn() {
        assertThat(FloorMapStageReporter.classify(10, 4, 0, 4)).isEqualTo(Stage.NONE);
    }

    /// The cascade stops at the first empty stage; later ones were never reached.
    @Test
    void testEverythingEmptyNamesTheQueryRatherThanTheLastStage() {
        assertThat(FloorMapStageReporter.classify(0, 0, 0, 0)).isEqualTo(Stage.NO_EVENT_ROWS);
    }

    // -----------------------------------------------------------------------
    // Filtering — the half that matters
    // -----------------------------------------------------------------------

    /// The transient startup sequence: facts arrive a tick after events. Must stay silent.
    @Test
    void testTheTransientFactsAfterEventsSequenceReportsNothing() {
        final FloorMapStageReporter reporter = new FloorMapStageReporter();

        // Two ticks with events but no facts yet, then the facts land - all inside the threshold.
        assertThat(reporter.observe(10, 4, 0, 0, T0)).isNull();
        assertThat(reporter.observe(10, 4, 0, 0, T0 + TICK)).isNull();
        assertThat(reporter.observe(10, 4, 9, 4, T0 + 2 * TICK)).isNull();
    }

    @Test
    void testAPersistentStageIsReportedOnceTheThresholdIsReached() {
        final FloorMapStageReporter reporter = new FloorMapStageReporter();
        assertThat(reporter.observe(10, 0, 9, 0, T0))
                .as("the first observation only starts the clock")
                .isNull();
        assertThat(reporter.observe(10, 0, 9, 0, T0 + FloorMapStageReporter.PERSISTENCE_MS - 1))
                .as("one millisecond short")
                .isNull();
        assertThat(reporter.observe(10, 0, 9, 0, T0 + FloorMapStageReporter.PERSISTENCE_MS))
                .isEqualTo(Stage.NO_ENTITIES_PARSED);
    }

    /// The regression this design exists for: a **paused** map observes once and then not again
    /// until something asks. Two observations a long way apart must report, because the stage has
    /// genuinely persisted - a count of observations could never satisfy this.
    @Test
    void testTwoObservationsFarApartReportWithoutATickStream() {
        final FloorMapStageReporter reporter = new FloorMapStageReporter();
        assertThat(reporter.observe(10, 4, 0, 0, T0)).isNull();
        assertThat(reporter.observe(10, 4, 0, 0, T0 + 10_000))
                .as("ten seconds later, via the facts heartbeat rather than a playback tick")
                .isEqualTo(Stage.NO_FACTS);
    }

    /// One observation is never enough, however late it is - there is nothing to compare it to.
    @Test
    void testASingleObservationNeverReports() {
        final FloorMapStageReporter reporter = new FloorMapStageReporter();
        assertThat(reporter.observe(10, 0, 9, 0, T0 + 60_000)).isNull();
    }

    /// Once per episode, not once per tick — otherwise it nags for as long as the map is open.
    @Test
    void testAPersistentStageIsReportedOnlyOnce() {
        final FloorMapStageReporter reporter = new FloorMapStageReporter();
        int reports = 0;
        for (int i = 0; i < 50; i++) {
            if (reporter.observe(10, 0, 9, 0, T0 + i * TICK) != null) {
                reports++;
            }
        }
        assertThat(reports).isEqualTo(1);
    }

    /// A different failure is a different episode and gets its own message.
    @Test
    void testADifferentStageIsReportedSeparately() {
        final FloorMapStageReporter reporter = new FloorMapStageReporter();
        Stage first = null;
        for (int i = 0; i < 5; i++) {
            final Stage s = reporter.observe(10, 0, 9, 0, T0 + i * TICK);
            if (s != null) {
                first = s;
            }
        }
        assertThat(first).isEqualTo(Stage.NO_ENTITIES_PARSED);

        Stage second = null;
        for (int i = 0; i < 5; i++) {
            final Stage s = reporter.observe(0, 0, 9, 0, T0 + (10 + i) * TICK);
            if (s != null) {
                second = s;
            }
        }
        assertThat(second).isEqualTo(Stage.NO_EVENT_ROWS);
    }

    /// Recovering and failing again is a new episode, so it speaks again.
    @Test
    void testRecoveringAndFailingAgainReportsAgain() {
        final FloorMapStageReporter reporter = new FloorMapStageReporter();
        for (int i = 0; i < 5; i++) {
            reporter.observe(10, 0, 9, 0, T0 + i * TICK);
        }
        reporter.observe(10, 4, 9, 4, T0 + 5 * TICK);            // recovered

        int reports = 0;
        for (int i = 0; i < 5; i++) {
            if (reporter.observe(10, 0, 9, 0, T0 + (10 + i) * TICK) != null) {
                reports++;
            }
        }
        assertThat(reports).isEqualTo(1);
    }

    /// A scrub must not accumulate evidence across unrelated instants.
    ///
    /// Without the reset, dragging through a sparse stretch would count one empty observation per
    /// position visited and eventually report a configuration problem where there is simply no data
    /// at those times.
    @Test
    void testResetOnTimeChangeStartsTheCountAgain() {
        final FloorMapStageReporter reporter = new FloorMapStageReporter();
        for (int i = 0; i < 6; i++) {
            reporter.observe(0, 0, 9, 0, T0 + i * TICK);
            reporter.reset();
        }
        assertThat(reporter.observe(0, 0, 9, 0, T0 + 60_000))
                .as("each observation was for a different instant, so none of them accumulate")
                .isNull();
    }

    // ---- the wording carried on each stage ----

    @Test
    void everyEmptyStageHasStatusTextAndNoneHasNone() {
        assertThat(Stage.NONE.getStatusText()).isNull();
        for (final Stage stage : Stage.values()) {
            if (Stage.NONE != stage) {
                assertThat(stage.getStatusText())
                        .as("status text for " + stage)
                        .isNotNull()
                        .isNotBlank();
            }
        }
    }

    @Test
    void onlyNoEventRowsIsNotAFault() {
        // The distinction the on-canvas styling turns on: the timeline being outside the data is
        // not a fault, and styling it like one would make the common case read as breakage.
        assertThat(Stage.NONE.isFault()).isFalse();
        assertThat(Stage.NO_EVENT_ROWS.isFault()).isFalse();
        assertThat(Stage.NO_ENTITIES_PARSED.isFault()).isTrue();
        assertThat(Stage.NO_FACTS.isFault()).isTrue();
        assertThat(Stage.NO_PLACEMENTS.isFault()).isTrue();
    }

    @Test
    void statusTextIsShortEnoughForAStatusLine() {
        // A line that wraps over a floor plan is worse than no line. The console messages carry
        // the remedy; these carry the diagnosis.
        for (final Stage stage : Stage.values()) {
            if (Stage.NONE != stage) {
                assertThat(stage.getStatusText().length())
                        .as("length of " + stage + " status text")
                        .isLessThanOrEqualTo(70);
            }
        }
    }

    /// No status line may name a single column setting.
    ///
    /// [Stage#NO_ENTITIES_PARSED] used to say "no entity matched the Entity ID column", and
    /// the first time it appeared in anger the Entity ID column was correct — both *location*
    /// roles were the problem. A line that sends the reader to the wrong setting is worse than
    /// silence, so the canvas names the stage and the console names the specifics.
    @Test
    void noStatusTextBlamesOneParticularColumn() {
        for (final Stage stage : Stage.values()) {
            final String text = stage.getStatusText();
            if (text != null) {
                assertThat(text)
                        .as("status text for " + stage + " must not single out a column setting")
                        .doesNotContain("Entity ID column")
                        .doesNotContain("Location ID column");
            }
        }
    }
}
