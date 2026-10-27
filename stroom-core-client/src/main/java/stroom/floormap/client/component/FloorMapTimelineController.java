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

package stroom.floormap.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.editor.client.presenter.ChangeCurrentPreferencesEvent;
import stroom.floormap.client.FloorMapEditorHelp;
import stroom.floormap.client.playback.FloorMapPlaybackRange;
import stroom.floormap.client.playback.FloorMapQueryThrottle;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.Controller;
import stroom.widget.histogram.client.HistogramLayout;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import com.google.gwt.animation.client.AnimationScheduler;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/// The floor map's timeline ([FloorMapTimeline]): plays and steps through the time range, which it
/// tells its owner of as it changes.
/// Provides a timeline bar with step-back/play-pause/step-forward buttons, a progress scrubber, date labels,
/// a speed badge that opens a playback-speed menu when clicked, and a settings icon that opens a popup
/// for date range and loop options.
public class FloorMapTimelineController extends Controller {

    private static final double SPEED_MULTIPLIER = 1000.0;

    /// Playback speed multipliers offered in the speed badge menu.
    private static final List<Double> SPEED_OPTIONS =
            Arrays.asList(0.5, 1.0, 10.0, 100.0, 1_000.0, 10_000.0);

    /// Minimum wall-clock interval (ms) between data query fires during playback.
    /// The visual position updates every animation frame; queries are throttled to this rate
    /// so the server is not overwhelmed at high playback speeds.
    private static final double PLAYBACK_QUERY_INTERVAL_MS = 300.0;

    private final FloorMapTimeline view;
    private final FloorMapTimelineSettingsController settings;

    private long startTime;
    private long endTime;
    private long currentTime;

    /// Earliest timestamp observed in histogram data — used by Show All.
    private long dataRangeMin = Long.MAX_VALUE;

    /// Latest timestamp observed in histogram data — used by Show All.
    private long dataRangeMax = Long.MIN_VALUE;

    /// Number of histogram bins, learned from the data supplied to
    /// [#setHistogramData(int\[\], HistogramLayout)]. Sizes a single step-back/forward
    /// where no bucket width is known (see [#histogramBucketWidthMs]). Defaults to the
    /// histogram's bin count until data arrives.
    private int histogramBinCount = 100;

    /// The time one histogram bar spans, from the layout given to
    /// [#setHistogramData(int\[\], HistogramLayout)], or `0` until bucketed bars arrive.
    /// Where known, a step is exactly this.
    private long histogramBucketWidthMs;

    /// The pending playback animation frame, or `null` when none is scheduled.
    ///
    /// Retained so pause can cancel it. Clearing [#playing] alone does not stop
    /// the loop — it only makes the *next* callback decline to continue — so
    /// pressing play again before that callback fired used to leave two callbacks in
    /// flight, each re-requesting, and playback ran on two concurrent loops.
    ///
    /// That did not double the speed, but only by accident: both callbacks share
    /// [#lastFrameTime], so whichever ran second in a frame computed a delta of
    /// about zero and advanced nothing. Making frame timing per-callback — an innocuous
    /// refactor — would have turned it into a silent speed multiplier. Cancelling removes
    /// the coincidence the correctness rested on.
    private AnimationScheduler.AnimationHandle playbackHandle;

    private boolean playing;
    private double playbackSpeed;

    /// Tracks whether the last programmatic setCurrentTime() was out of the visible range.
    private OutOfRange outOfRange = OutOfRange.NONE;
    private double lastFrameTime;

    /// Rate limit on the data queries playback issues, kept separate from the
    /// per-frame visual updates. See [FloorMapQueryThrottle] for why this is a
    /// class rather than a timestamp field.
    private final FloorMapQueryThrottle queryThrottle =
            new FloorMapQueryThrottle(PLAYBACK_QUERY_INTERVAL_MS);

    /// Optional callback fired whenever the timeline transitions between playing and paused.
    private Consumer<Boolean> playStateChangeHandler;

    /// Optional callback fired whenever the current time jumps non-continuously (scrub, step, loop).
    private Runnable clearAnimationStateHandler;

    /// Optional callback fired at the *discrete* jumps only. See [#setOnDiscontinuity].
    private Runnable discontinuityHandler;

    /// Optional callback fired when the user changes the visible time range via the settings popup.
    private Runnable timeRangeChangeHandler;


    private final DateTimeFormatter dateTimeFormatter;

    // Told the time the user moves the timeline to
    private Consumer<Long> onTimeChange = time -> {
    };

    /// @param onTimeChange What to run with the time (ms since the epoch) when the user moves the
    ///                     timeline, or it plays on.
    public void setOnTimeChange(final Consumer<Long> onTimeChange) {
        this.onTimeChange = onTimeChange;
    }

    /// Made by GIN, with its timeline.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The timeline.
    /// @param settings          The playback settings' popup.
    /// @param dateTimeFormatter Formats the times shown.
    @Inject
    public FloorMapTimelineController(final EventBus eventBus,
                                      final FloorMapTimeline view,
                                      final FloorMapTimelineSettingsController settings,
                                      final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.settings = own(settings);
        this.dateTimeFormatter = dateTimeFormatter;

        view.setOnScrub(percentage -> {
            // Visual-only update during drag — no data query fired.
            // Manual scrub is always in-range, so clear any out-of-range indicator.
            final long duration = endTime - startTime;
            final long newTime = startTime + (long) (duration * (percentage / 100.0));
            clearOutOfRange();
            // setCurrentTime -> updateProgress refreshes the scrub text (and with
            // it aria-valuetext), so there is nothing to set here.
            setCurrentTime(newTime);
        });

        view.setOnCommit(percentage -> {
            // User released the scrubber: commit the position and fire a data query.
            // Clear any in-flight animations since the time has jumped discontinuously.
            if (clearAnimationStateHandler != null) {
                clearAnimationStateHandler.run();
            }
            fireDiscontinuity();
            final long duration = endTime - startTime;
            final long newTime = startTime + (long) (duration * (percentage / 100.0));
            clearOutOfRange();
            setCurrentTime(newTime);
            onTimeChange.accept(newTime);
        });

        // The histogram's bars are painted into a canvas, so they do not re-style
        // themselves when the theme class changes the way the rest of the strip
        // does — they hold the colour they were painted with until something
        // repaints them. Same pattern the dashboard's visualisations use.
        //noinspection unused e
        listen(ChangeCurrentPreferencesEvent.getType(), e -> {
            view.redrawHistogram();
            // Every time on the strip is rendered from the user's date/time
            // preference, so a change to it has to be re-applied to the ones
            // already on screen — the axis labels and the scrubber pill — rather
            // than waiting for the next thing that happens to move the time.
            updateDateLabels();
            updateProgress();
        });

        // Forward date changes from the settings popup back to the timeline.
        settings.setOnStartChange(() -> applyRange(settings.getStartTime(), this.endTime));
        settings.setOnEndChange(() -> applyRange(this.startTime, settings.getEndTime()));

        view.setOnPlayPause(() -> {
            // applyRange and setTimeRange both refuse an unusable range, so one can
            // normally never be in force here. The exception is a timeline that has
            // never been given a usable one — both boundaries still 0 — where play
            // would appear to do nothing at all. Say so rather than looking broken.
            if (!playing && !FloorMapPlaybackRange.isUsable(startTime, endTime)) {
                AlertEvent.fireWarn(this,
                        "The timeline has no range to play. Set a start and end time "
                        + "in the timeline settings, or use Show All to fit the range "
                        + "to the data.",
                        null);
                return;
            }
            playing = !playing;
            if (playing) {
                view.setPlayPauseIcon(SvgImage.PAUSE, "Pause");
                startPlayback();
            } else {
                view.setPlayPauseIcon(SvgImage.PLAY, "Play");
                cancelPlayback();
            }
            if (playStateChangeHandler != null) {
                playStateChangeHandler.accept(playing);
            }
        });

        // Step-back: jump one histogram bin backward.
        view.setOnStepBack(() -> {
            if (clearAnimationStateHandler != null) {
                clearAnimationStateHandler.run();
            }
            stepBy(-1);
        });

        // Keyboard scrubbing on the focused bar. Same path as the step buttons —
        // arrow keys and the buttons are the same operation, so they must not be
        // able to disagree about where "one step" lands.
        view.setOnNudge(bins -> {
            if (clearAnimationStateHandler != null) {
                clearAnimationStateHandler.run();
            }
            clearOutOfRange();
            stepBy(bins);
        });

        // Step-forward: jump one histogram bin forward.
        view.setOnStepForward(() -> {
            if (clearAnimationStateHandler != null) {
                clearAnimationStateHandler.run();
            }
            stepBy(1);
        });

        // Settings button opens the popup anchored above the settings icon.
        view.setOnSettings(() -> settings.show(view.getSettingsButton()));

        // Speed badge opens a menu of playback speeds anchored above the badge.
        view.setOnSpeedBadge(this::showSpeedMenu);

        // Loop/stop-at-end toggle: default to looping.
        settings.setLoopPlayback(true);

        // Wire the Show All button: disabled until we have histogram data with a valid range.
        settings.setShowAllEnabled(false);
        settings.setOnShowAll(() -> {
            if (FloorMapPlaybackRange.canFitTo(dataRangeMin, dataRangeMax)) {
                // Apply a small 5% padding on each side so the first/last events are not
                // flush against the edges of the histogram.
                final long padding = Math.max(1, (dataRangeMax - dataRangeMin) / 20);
                final long newStart = dataRangeMin - padding;
                final long newEnd = dataRangeMax + padding;
                setTimeRange(newStart, newEnd);
                if (timeRangeChangeHandler != null) {
                    timeRangeChangeHandler.run();
                }
                // Onto the data itself, not merely into the padded range: a playhead parked on
                // the padding past the last event can still be far enough from it for the event
                // expiry to hide every entity, which is the blank map Show All is pressed to fix.
                reconcilePlayhead(dataRangeMin, dataRangeMax);
            }
        });

        setPlaybackSpeed(1.0);
    }

    /// Shows the playback-speed menu anchored above the speed badge. The currently
    /// selected speed is marked with a tick.
    private void showSpeedMenu() {
        final List<Item> items = new ArrayList<>();
        int priority = 0;
        for (final Double speed : SPEED_OPTIONS) {
            final boolean selected = speed == playbackSpeed;
            final IconMenuItem.Builder builder = new IconMenuItem.Builder()
                    .priority(priority++)
                    .text(formatSpeed(speed))
                    .command(() -> setPlaybackSpeed(speed));
            if (selected) {
                builder.icon(SvgImage.TICK).highlight(true);
            }
            items.add(builder.build());
        }
        final Rect relativeRect = new Rect(view.getSpeedBadge().getElement()).grow(3);
        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.ABOVE);
        ShowMenuEvent.builder()
                .name("Playback Speed")
                .items(items)
                .popupPosition(popupPosition)
                .fire(this);
    }

    /// Applies a new playback speed and updates the badge label to match.
    private void setPlaybackSpeed(final double speed) {
        this.playbackSpeed = speed;
        view.setSpeedBadge(formatSpeed(speed));
    }

    /// Applies a range edited in the settings popup, rejecting one that cannot be used.
    ///
    /// On rejection the picker is put back to the range actually in force, so the
    /// boxes never show a range the timeline is not using. Reverting rather than
    /// coercing is deliberate: silently moving the boundary the user did *not*
    /// touch is more surprising than declining the one they did.
    ///
    /// Restoring the picker cannot loop back into this method —
    /// `DateTimeBox.setValue(Long)` delegates to `setValue(value, false)`
    /// and fires no change event.
    private void applyRange(final long start, final long end) {
        if (!FloorMapPlaybackRange.isUsable(start, end)) {
            settings.setStartTime(this.startTime);
            settings.setEndTime(this.endTime);
            return;
        }
        this.startTime = start;
        this.endTime = end;
        updateProgress();
        updateDateLabels();
        if (timeRangeChangeHandler != null) {
            timeRangeChangeHandler.run();
        }
        reconcilePlayhead(start, end);
    }

    /// Moves the playhead into the range from `lower` to `upper` after a user-initiated range
    /// change, and re-reads if it moved.
    ///
    /// Without this a range change left the playhead where it was — outside the new range, with
    /// the thumb clamped to the edge so it looked parked at the end of the data, and the canvas
    /// still showing the read taken at the old position. Setting the range to span the data, or
    /// pressing Show All, then appeared to do nothing.
    ///
    /// A playhead already inside the range is left alone and nothing is re-read: the user only
    /// changed what is visible around it.
    ///
    /// Only for the user's own range changes ([#applyRange] and Show All), not
    /// [#setTimeRange]. That is also how the owning tabs initialise the timeline, and they set
    /// the time themselves straight afterwards; a read fired from inside it would be for a
    /// position about to be replaced.
    ///
    /// @param lower the earliest time the playhead may be left at, in epoch milliseconds
    /// @param upper the latest time the playhead may be left at, in epoch milliseconds
    private void reconcilePlayhead(final long lower, final long upper) {
        final long newTime = FloorMapPlaybackRange.clampInto(currentTime, lower, upper);
        if (newTime == currentTime) {
            return;
        }
        // The same jump a step makes, so it discards in-flight animation and is classified the
        // same way by the discontinuity handler.
        if (clearAnimationStateHandler != null) {
            clearAnimationStateHandler.run();
        }
        fireDiscontinuity();
        setCurrentTime(newTime);
        onTimeChange.accept(newTime);
    }

    /// Sets the total time range visible on the timeline.
    ///
    /// @param start Start time in milliseconds.
    /// @param end   End time in milliseconds.
    public void setTimeRange(final long start, final long end) {
        if (!FloorMapPlaybackRange.isUsable(start, end)) {
            // Keep whatever range is currently in force. Storing an unusable one makes
            // the progress bar and the step buttons silently do nothing, and makes
            // playback wrap on every frame.
            return;
        }
        this.startTime = start;
        this.endTime = end;
        settings.setStartTime(start);
        settings.setEndTime(end);
        // Re-evaluate: if the current time now falls within the new range, clear the indicator.
        if (currentTime >= start && currentTime <= end) {
            clearOutOfRange();
        }
        updateProgress();
        updateDateLabels();
    }

    /// Sets the current selected time on the timeline.
    ///
    /// If the time falls outside the visible range, from [#startTime] to
    /// [#endTime], it is clamped to the nearest boundary and the view is
    /// notified with an [OutOfRange] indicator so a warning chevron can
    /// be shown at the corresponding end of the bar.
    ///
    /// @param time The selected time in milliseconds.
    public void setCurrentTime(final long time) {
        if (endTime > startTime) {
            if (time < startTime) {
                setOutOfRange(OutOfRange.BEFORE);
                this.currentTime = startTime;
            } else if (time > endTime) {
                setOutOfRange(OutOfRange.AFTER);
                this.currentTime = endTime;
            } else {
                clearOutOfRange();
                this.currentTime = time;
            }
        } else {
            // Range not yet initialised — store as-is.
            this.currentTime = time;
        }
        updateProgress();
    }

    /// Registers a callback to be invoked when the timeline transitions between playing and
    /// paused.  The boolean argument is `true` when playback starts, `false`
    /// when it stops for any reason.
    ///
    /// @param handler Called with `true` on play, `false` on pause/stop.
    public void setOnPlayStateChange(final Consumer<Boolean> handler) {
        this.playStateChangeHandler = handler;
    }

    /// Pauses playback if the timeline is currently playing.
    ///
    /// This is a no-op when the timeline is already paused. The button preset is
    /// updated to reflect the paused state and the play-state-change handler is notified,
    /// matching the same logic used by the play/pause toggle button.
    public void pause() {
        if (playing) {
            playing = false;
            cancelPlayback();
            view.setPlayPauseIcon(SvgImage.PLAY, "Play");
            if (playStateChangeHandler != null) {
                playStateChangeHandler.accept(false);
            }
        }
    }

    /// Registers a callback to be invoked whenever the current time jumps non-continuously
    /// (scrub commit, step, stop-at-end, loop-around).  Used by the canvas presenter to
    /// discard any in-flight movement animations and trail data.
    ///
    /// @param handler Called on every discontinuous time jump.
    public void setOnClearAnimationState(final Runnable handler) {
        this.clearAnimationStateHandler = handler;
    }

    /// Sets a callback for the **discrete** time jumps: a committed scrub, a step (button or
    /// keyboard), and stopping at the end of the range.
    ///
    /// Distinct from [#setOnClearAnimationState] on purpose, and the difference is the
    /// loop wrap. That fires **per frame** while looping — which is fine for discarding animation
    /// state, and ruinous for anything that starts a query, as the throttle comment on the wrap
    /// explains. So a consumer that must re-read on a jump needs a signal the wrap does not raise.
    /// A backward wrap is self-evident from the time going down; a *forward* scrub or step is
    /// not, which is what this callback exists to report.
    ///
    /// Fired **before** the time change (`onTimeChange`) at each site, so a handler that arms state
    /// has done so by the time the read for the new position is decided.
    ///
    /// Also fired when Show All or a range edited in the settings popup moves a playhead that the
    /// new range left outside it — see `reconcilePlayhead`. A range change that leaves the
    /// playhead where it was fires nothing, and neither does `setTimeRange`: no
    /// time change is told in either case, so there is no read to classify.
    ///
    /// @param handler the callback, or `null` to remove it
    public void setOnDiscontinuity(final Runnable handler) {
        this.discontinuityHandler = handler;
    }

    private void fireDiscontinuity() {
        if (discontinuityHandler != null) {
            discontinuityHandler.run();
        }
    }

    /// Registers a callback to be invoked when the user changes the visible time range
    /// via the settings popup. Used by `FloorMapMapController` to re-run the histogram
    /// query over the new range.
    ///
    /// @param handler Called whenever start or end time changes.
    public void setOnTimeRangeChange(final Runnable handler) {
        this.timeRangeChangeHandler = handler;
    }

    /// @return The current timeline start time in milliseconds.
    public long getStartTime() {
        return startTime;
    }

    /// @return The current timeline end time in milliseconds.
    public long getEndTime() {
        return endTime;
    }

    private void updateProgress() {
        if (endTime > startTime) {
            final double percentage = ((double) (currentTime - startTime) / (endTime - startTime)) * 100;
            // Defence-in-depth clamp — setCurrentTime should already keep us in bounds,
            // but guard against any future caller that bypasses it.
            view.setProgressPct(Math.max(0.0, Math.min(100.0, percentage)));
            // The scrub text is also the bar's aria-valuetext, so it has to be
            // refreshed on *every* path that moves the time — keyboard, playback
            // and external seeks, not just a mouse drag. Setting it only while
            // dragging left a screen reader announcing the position from the last
            // drag, or "0" on a bar that had never been dragged, while reporting a
            // correct aria-valuenow beside it.
            view.setScrubTooltip(formatTime(currentTime));
        }
    }

    private void updateDateLabels() {
        view.setStartDateLabel(formatTime(startTime));
        view.setEndDateLabel(formatTime(endTime));
    }

    /// Sets the out-of-range state and notifies the view to show the indicator.
    /// Only notifies the view if the state actually changes to avoid redundant DOM updates.
    private void setOutOfRange(final OutOfRange direction) {
        if (this.outOfRange != direction) {
            this.outOfRange = direction;
            view.setOutOfRange(direction == OutOfRange.BEFORE, direction == OutOfRange.AFTER);
        }
    }

    /// Clears the out-of-range indicator if one is currently showing.
    private void clearOutOfRange() {
        if (this.outOfRange != OutOfRange.NONE) {
            this.outOfRange = OutOfRange.NONE;
            view.setOutOfRange(false, false);
        }
    }

    /// Steps the timeline by the given number of histogram bins and fires a data query.
    /// Positive values step forward; negative values step backward.
    ///
    /// @param bins Number of bins to step (positive = forward, negative = backward).
    private void stepBy(final int bins) {
        if (endTime <= startTime) {
            return;
        }
        // Step by one histogram bar: the bucket width where the bars are bucketed, otherwise the
        // range divided by the bar count (see setHistogramData).
        final long stepMs = FloorMapPlaybackRange.stepMs(
                startTime, endTime, histogramBinCount, histogramBucketWidthMs) * bins;
        final long newTime = FloorMapPlaybackRange.clampInto(currentTime + stepMs, startTime, endTime);
        // Covers the step buttons and the keyboard nudge alike, since both funnel through here.
        fireDiscontinuity();
        setCurrentTime(newTime);
        onTimeChange.accept(newTime);
    }

    /// Formats a millisecond timestamp as a short display string for the timeline labels.
    ///
    /// Public because it is the canonical rendering of a timeline instant: the
    /// canvas's accessible summary and its spoken time announcements have to read
    /// the same as the labels under the bar, or a screen-reader user and a sighted
    /// user comparing notes are looking at two different clocks.
    ///
    /// Rendered through the user's own date/time preference, like every other
    /// time in Stroom — in the shortened form that drops the seconds and the
    /// zone, since the axis and the scrubber pill have no room for them. Field
    /// order, separators and the 12/24-hour choice are the user's throughout, so
    /// this is the same format as the rest of the application in a narrower
    /// space, not a second format of its own.
    public String formatTime(final long millis) {
        if (millis <= 0) {
            return "";
        }
        final String formatted = dateTimeFormatter.formatCompact(millis);
        return formatted != null ? formatted : "";
    }

    /// Formats a playback speed value as a badge string, e.g. `"x1"`, `"x0.5"` or
    /// `"x1,000"` - an ASCII `x` prefix, not a multiplication sign.
    /// Large values are comma-formatted (e.g. `"×1,000"`).
    private static String formatSpeed(final double speed) {
        if (speed >= 1000) {
            // Format with thousands separator — GWT has no String.format %,d so we do it manually.
            final long rounded = Math.round(speed);
            final String raw = String.valueOf(rounded);
            final int len = raw.length();
            if (len > 3) {
                return "x" + raw.substring(0, len - 3) + "," + raw.substring(len - 3);
            }
            return "x" + raw;
        }
        // For values < 1000: show as integer where possible.
        if (speed == Math.floor(speed)) {
            return "x" + (int) speed;
        }
        return "x" + speed;
    }



    /// Starts the playback loop, cancelling any frame already pending so there is never
    /// more than one callback in flight.
    private void startPlayback() {
        cancelPlayback();
        lastFrameTime = 0;
        playbackHandle = AnimationScheduler.get().requestAnimationFrame(playbackCallback);
    }

    /// Cancels the pending playback frame, if any. Safe to call when none is scheduled.
    private void cancelPlayback() {
        if (playbackHandle != null) {
            playbackHandle.cancel();
            playbackHandle = null;
        }
    }

    private final AnimationScheduler.AnimationCallback playbackCallback = new AnimationScheduler.AnimationCallback() {
        @Override
        public void execute(final double timestamp) {
            // This invocation consumes the frame that scheduled it, so the field is only
            // non-null while a frame is genuinely pending.
            playbackHandle = null;
            if (playing) {
                if (lastFrameTime > 0) {
                    final double delta = timestamp - lastFrameTime;
                    long newTime = currentTime + (long) (delta * playbackSpeed * SPEED_MULTIPLIER);

                    if (newTime > endTime) {
                        if (settings.isLoopPlayback()) {
                            // Loop: wrap back to the start.
                            newTime = startTime;
                            // Deliberately NOT resetting the query throttle here. Forcing a
                            // query on every wrap looks harmless but is the storm: at high
                            // speed, or over a short or degenerate range, the timeline wraps
                            // every frame, so the reset fired every frame and the rate limit
                            // never applied — two searches per frame, each rebuilding a
                            // server-side result store. The scrubber still moves smoothly
                            // because the visual position updates every frame regardless;
                            // fresh data follows within one throttle interval.
                            // Discard in-flight animations — positions jump discontinuously.
                            if (clearAnimationStateHandler != null) {
                                clearAnimationStateHandler.run();
                            }
                        } else {
                            // Stop at end: park at the end time and pause.
                            newTime = endTime;
                            playing = false;
                            if (playStateChangeHandler != null) {
                                playStateChangeHandler.accept(false);
                            }
                            view.setPlayPauseIcon(SvgImage.PLAY, "Play");
                            setCurrentTime(newTime);
                            lastFrameTime = 0;
                            queryThrottle.reset();
                            // Clear and signal BEFORE firing, matching the scrub commit and the
                            // loop wrap. Firing first would let the resulting read be classified
                            // as a continuation of playback rather than the jump it is.
                            if (clearAnimationStateHandler != null) {
                                clearAnimationStateHandler.run();
                            }
                            fireDiscontinuity();
                            onTimeChange.accept(newTime);
                            return;
                        }
                    }

                    // Always update the visual position (smooth 60 fps).
                    setCurrentTime(newTime);

                    // Only fire a data query if enough wall-clock time has elapsed since
                    // the last one, preventing the server from being overwhelmed.
                    if (queryThrottle.shouldQuery(timestamp)) {
                        onTimeChange.accept(newTime);
                    }
                }

                lastFrameTime = timestamp;
                playbackHandle = AnimationScheduler.get().requestAnimationFrame(this);
            } else {
                lastFrameTime = 0;
                queryThrottle.reset();
            }
        }
    };

    /// Provides histogram bin counts to be displayed above the scrubber.
    ///
    /// @param binCounts Array of event counts per bin.
    /// @param layout    Where each bin belongs across the visible range, and the time it spans,
    ///                  which sizes a step. `null` draws the bins as an even division of the range.
    public void setHistogramData(final int[] binCounts, final HistogramLayout layout) {
        if (binCounts != null && binCounts.length > 0) {
            histogramBinCount = binCounts.length;
        }
        histogramBucketWidthMs = layout == null
                ? 0
                : layout.getBucketWidthMs();
        view.setHistogramData(binCounts, layout);
    }

    /// Records the earliest and latest event times in the data, which is the range "Show All"
    /// fits the timeline to, and enables the button.
    ///
    /// Called by `FloorMapMapController` when its extent query completes and by
    /// `FloorMapEditorController` from the facts store's range.
    ///
    /// An extent Show All cannot fit to — a store whose events share one timestamp — is
    /// treated as no extent at all: the button is disabled rather than offered as a no-op.
    ///
    /// @param min Earliest event timestamp in the data (milliseconds).
    /// @param max Latest event timestamp in the data (milliseconds).
    public void setDataRange(final long min, final long max) {
        if (FloorMapPlaybackRange.canFitTo(min, max)) {
            this.dataRangeMin = min;
            this.dataRangeMax = max;
            settings.setShowAllEnabled(true);
        } else {
            clearDataRange();
        }
    }

    /// Forgets the data extent and disables "Show All" until a new one is supplied.
    ///
    /// For a document (re-)read: the store behind it may have changed, and an extent left over
    /// from the previous read would keep Show All enabled and fit the timeline to data that is no
    /// longer there.
    public void clearDataRange() {
        this.dataRangeMin = Long.MAX_VALUE;
        this.dataRangeMax = Long.MIN_VALUE;
        settings.setShowAllEnabled(false);
    }

    /// Whether a data extent is known, i.e. whether "Show All" is enabled.
    ///
    /// @return `true` once [#setDataRange(long, long)] has been given an extent Show All can
    ///         fit to, and until [#clearDataRange()] is next called
    public boolean hasDataRange() {
        return FloorMapPlaybackRange.canFitTo(dataRangeMin, dataRangeMax);
    }

    /// Adds a help button to the timeline's right-hand controls.
    ///
    /// Called by the Editor tab only, so the read-only Map tab (which shares
    /// this presenter) shows no help button. Clicking the button (or activating
    /// it from the keyboard) opens the standard in-app help popup.
    ///
    /// @param helpContent the HTML help body to show in the popup
    public void setHelpContent(final SafeHtml helpContent) {
        FloorMapEditorHelp.addHelpButton(view.getHelpToolbar(), "Timeline help", "Timeline", helpContent);
        view.setHelpVisible(true);
    }


    // --------------------------------------------------------------------------------


    /// Indicates whether the selected time falls outside the visible timeline range.
    /// Used to show a directional warning indicator so the user knows they need to
    /// extend the range to see the selected object.
    private enum OutOfRange {
        /// The selected time is within the visible range.
        NONE,
        /// The selected time is before the timeline start.
        BEFORE,
        /// The selected time is after the timeline end.
        AFTER
    }
}
