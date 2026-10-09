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

package stroom.floormap.client.playback;

import stroom.floormap.client.model.FloorMapFactHistory;

/// Says which stage of the events pipeline came up empty, and whether it has been empty long enough
/// to be worth reporting.
///
/// ### Why this exists
///
/// Four stages can each produce nothing — the query returns no rows, the rows parse to no
/// entities, the entities resolve to no positions, or there are no facts to resolve against — and
/// every one of them looks identical on screen: an empty map. Three of the four used to say nothing
/// at all, and the two most likely first-run failures were among them. The cost was measured in
/// hours: "nothing is drawn and nothing says why" is the most expensive class of problem this
/// feature has.
///
/// ### Why it filters on persistence rather than on emptiness
///
/// Reporting the moment a stage is empty is wrong, and the guards this replaces were added
/// because of it. Facts and events arrive from independent queries, so on startup — and after every
/// scrub — there is normally a tick or two where events have landed and facts have not. That is
/// transient and self-correcting, and logging it would produce noise on every single startup, which
/// trains people to ignore the one message that matters.
///
/// So a stage must be empty continuously for [#PERSISTENCE_MS] before it is reported, and
/// each episode is reported once rather than repeatedly. A stage that changes, or a
/// [#reset()] from a time change, starts the clock again.
///
/// GWT-free with the counting explicit, so the filtering can be tested without a canvas — the
/// same shape as [FloorMapFactHistory] and [FloorMapQueryThrottle].
public final class FloorMapStageReporter {

    /// How long a stage must stay empty, continuously, before it is reported.
    ///
    /// A second — long enough that the normal facts-after-events startup sequence passes
    /// unremarked, and short enough that a genuinely broken configuration is named while the user is
    /// still looking at it.
    ///
    /// **This was a count of observations until 2026-09-10, and that was wrong.** Three
    /// observations against a ~300 ms playback tick is about the same second, so the two agree while
    /// something is playing — but a count assumes a stream of observations, and a *paused*
    /// timeline produces exactly one. So the threshold was unreachable precisely when the map was
    /// sitting still in front of someone wondering why it was empty, which is the case worth
    /// reporting. Elapsed time holds in both.
    ///
    /// The counterpart is that the caller must keep observing while paused, or nothing
    /// re-evaluates the elapsed time and the message still never arrives. That is what the Map tab's
    /// facts heartbeat does.
    public static final long PERSISTENCE_MS = 1_000L;

    /// Which stage produced nothing, and what to say about it on the map.
    ///
    /// Each stage carries its own short status text and whether it is a **fault**. The
    /// distinction is the whole design of the on-canvas line: [#NO_EVENT_ROWS] is most often
    /// not a fault at all — the timeline is simply somewhere the data does not cover — so it reads
    /// as a statement of fact and is styled quietly. The other three are almost always
    /// misconfiguration and are styled to draw the eye. A single uniform warning style would make
    /// the common, harmless case look like breakage, which is worse than the silence it replaces.
    ///
    /// The text is deliberately much shorter than the console messages, which stay as they are:
    /// a status line has to be readable at a glance and cannot carry a paragraph of remedy.
    public enum Stage {
        /// Something reached the canvas.
        NONE(null, false),
        /// The events query completed and returned no rows at all.
        NO_EVENT_ROWS("No events at this time", false),
        /// Rows came back, but none parsed into an entity.
        ///
        /// **The text deliberately names no single column.** It used to say "no entity matched
        /// the Entity ID column", which is one of at least three causes and was the wrong one the
        /// first time it appeared in anger: the Entity ID column matched perfectly and it was both
        /// *location* roles that pointed at names the query did not select. A message that
        /// sends the reader to the wrong setting is worse than the silence this whole finding
        /// replaced. The console message lists the mapping role by role alongside the result's
        /// actual columns, which is where the specifics belong.
        ///
        /// The three causes: the entity role names a column the query does not select; both
        /// location roles do; or every row's entity value is null.
        NO_ENTITIES_PARSED("Events found, but no entity could be read — check the column mapping",
                true),
        /// Entities exist and none could be placed, and there are no facts to place them against.
        NO_FACTS("No floor plan at this time, so entities have nowhere to be placed", true),
        /// Entities and facts both exist, but no entity's location matches a fact key.
        NO_PLACEMENTS("Entities reference locations that are not on this floor plan", true);

        private final String statusText;
        private final boolean fault;

        Stage(final String statusText, final boolean fault) {
            this.statusText = statusText;
            this.fault = fault;
        }

        /// Short text for the on-canvas status line, or `null` for [#NONE].
        ///
        /// @return the text, or `null` if there is nothing to say
        public String getStatusText() {
            return statusText;
        }

        /// Whether this stage indicates something is wrong, as opposed to merely empty.
        ///
        /// @return `true` for a stage that almost always means misconfiguration
        public boolean isFault() {
            return fault;
        }
    }

    private Stage current = Stage.NONE;
    /// When [#current] was first observed, or `0` when there is no run.
    private long stageSinceMs;
    private boolean reportedCurrent;

    /// Classifies one observation of the pipeline.
    ///
    /// The cascade follows the data: no rows means the later stages were never reached, so there
    /// is no point naming them. The one non-obvious case is [Stage#NO_FACTS], which is only
    /// reached when placement produced nothing *and* there are no facts — because an entity
    /// carrying literal coordinates needs no facts at all, so empty facts with something drawn is
    /// perfectly normal and must not be reported.
    ///
    /// @param eventRows rows the events query returned
    /// @param entities  entities parsed from those rows
    /// @param facts     facts currently held
    /// @param placed    entities that resolved to a position
    /// @return the stage that came up empty, or [Stage#NONE]
    public static Stage classify(final int eventRows,
                                 final int entities,
                                 final int facts,
                                 final int placed) {
        if (placed > 0) {
            return Stage.NONE;
        }
        if (eventRows <= 0) {
            return Stage.NO_EVENT_ROWS;
        }
        if (entities <= 0) {
            return Stage.NO_ENTITIES_PARSED;
        }
        if (facts <= 0) {
            return Stage.NO_FACTS;
        }
        return Stage.NO_PLACEMENTS;
    }

    /// Records one observation and returns the stage to report, if any.
    ///
    /// @param nowMs wall-clock millis, passed in so the filtering stays testable without a clock
    /// @return the stage, the first time it has been continuously empty for [#PERSISTENCE_MS];
    ///         otherwise `null`
    public Stage observe(final int eventRows,
                         final int entities,
                         final int facts,
                         final int placed,
                         final long nowMs) {
        final Stage stage = classify(eventRows, entities, facts, placed);

        if (stage != current) {
            current = stage;
            stageSinceMs = nowMs;
            reportedCurrent = false;
        }

        if (Stage.NONE == stage || reportedCurrent || nowMs - stageSinceMs < PERSISTENCE_MS) {
            return null;
        }
        reportedCurrent = true;
        return stage;
    }

    /// Forgets the current run.
    ///
    /// Called on a **discontinuity** — a scrub, a step, a stop-at-end, or a document read —
    /// not on every playback tick. Without it a scrub through a sparse stretch would accumulate
    /// observations of the same empty stage from unrelated instants and report a configuration
    /// problem where there is merely no data at those times.
    ///
    /// **Calling it per tick defeats the filter entirely,** which is what it originally did.
    /// A reset on every tick restarted the run each time, so the threshold was never reached and
    /// nothing was ever reported. Successive playback ticks are not unrelated evidence: a run of
    /// them with no rows is a real second of emptiness, and saying so is the point.
    public void reset() {
        current = Stage.NONE;
        stageSinceMs = 0;
        reportedCurrent = false;
    }
}
