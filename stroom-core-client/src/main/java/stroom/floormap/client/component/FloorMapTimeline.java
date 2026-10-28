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

import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.AriaPanel;
import stroom.widget.component.client.Component;
import stroom.widget.histogram.client.Histogram;
import stroom.widget.histogram.client.HistogramLayout;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.CssProperties;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The floor map's timeline: step-back, play/pause and step-forward buttons, a scrubber bar with
/// its start and end dates, a speed badge that opens the playback-speed menu, a settings button,
/// an optional help button, and a histogram of the events above the bar.
///
/// - Scrub tooltip: the datetime pill above the handle while dragging, or while the bar has the
///   keyboard focus.
/// - Histogram click-to-seek: clicking the histogram moves the time there.
/// - The bar is an ARIA slider, scrubbed from the keyboard by histogram bins.
public class FloorMapTimeline extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    /// Class that makes the datetime pill visible.
    private static final String SCRUB_TOOLTIP_VISIBLE =
            "stroom-floormap-timeline-scrub-tooltip--visible";

    /// The bar's position, in %, read by its inner bar (width), handle and datetime pill (left).
    private static final String PROGRESS = "--stroom-floormap-timeline__progress";

    /// Number of histogram bins a Page Up / Page Down moves — the ARIA slider
    /// pattern's "larger step". Ten bins crosses a visible fraction of the bar
    /// without skipping so far that the user loses their place.
    private static final int PAGE_BINS = 10;

    @UiField
    Histogram histogram;
    @UiField
    ToolbarButton stepBack;
    @UiField
    ToolbarButton playPause;
    @UiField
    ToolbarButton stepForward;
    @UiField
    AriaPanel outerBar;
    @UiField
    Label scrubTooltip;
    @UiField
    AriaPanel speedBadge;
    @UiField
    ToolbarButton settings;
    @UiField
    Toolbar helpToolbar;
    @UiField
    DivElement outOfRangeLeft;
    @UiField
    Label startDateLabel;
    @UiField
    Label endDateLabel;
    @UiField
    DivElement outOfRangeRight;
    /// Visually-hidden live region announcing the out-of-range state.
    @UiField
    DivElement timelineStatus;

    /// Called on every mouse-move during a drag — updates visuals only, no data queries.
    private Consumer<Double> onScrub;
    /// Called on mouse-up (release) — commits the time and triggers data queries.
    private Consumer<Double> onCommit;
    /// Called with a signed number of histogram bins when the focused bar is
    /// scrubbed from the keyboard.
    private Consumer<Integer> onNudge;
    /// Called when the speed badge is clicked (or activated from the keyboard).
    private Runnable onSpeedBadge;
    private boolean dragging;
    /// Set when the user dismisses the datetime pill with Escape, so it stays
    /// dismissed until the bar is focused again.
    private boolean scrubTooltipSuppressed;

    /// Makes a timeline at its start, with no histogram.
    public FloorMapTimeline() {
        initWidget(BINDER.createAndBindUi(this));
        UIObject.setVisible(outOfRangeLeft, false);
        UIObject.setVisible(outOfRangeRight, false);

        // Mouse handlers for dragging the timeline handle.
        outerBar.addDomHandler(this::onBarMouseDown, MouseDownEvent.getType());
        outerBar.addDomHandler(this::onBarMouseMove, MouseMoveEvent.getType());
        outerBar.addDomHandler(this::onBarMouseUp, MouseUpEvent.getType());

        // Speed badge behaves as a button: click or keyboard activation opens the speed menu.
        // Its title is a mouse tooltip only. An element's accessible name comes from its
        // content when it has any, so this badge would announce as bare "×1, button" — which
        // does not say what the ×1 is — and the title would never be read. setSpeedBadge()
        // therefore keeps an explicit aria-label carrying both the purpose and the value.
        applySpeedBadgeLabel();
        //noinspection unused e
        speedBadge.addDomHandler(e -> {
            if (onSpeedBadge != null) {
                onSpeedBadge.run();
            }
        }, ClickEvent.getType());
        speedBadge.addDomHandler(e -> {
            if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER || e.getNativeKeyCode() == KeyCodes.KEY_SPACE) {
                e.preventDefault();
                if (onSpeedBadge != null) {
                    onSpeedBadge.run();
                }
            }
        }, KeyDownEvent.getType());

        // Keyboard scrubbing, in the key bindings the ARIA slider pattern
        // specifies. Without these the bar is worse than an unfocusable div: it
        // takes a tab stop and announces itself as a slider, then does nothing.
        //
        // Arrow and Page keys nudge by whole histogram bins rather than by a
        // percentage, so a keypress lands on the same times the step buttons
        // reach — a percentage step would drift off the bin grid and make the
        // step buttons and the keyboard disagree about "one step". Bin width is
        // the controller's business, hence the signed-bin callback.
        outerBar.addDomHandler(this::onBarKeyDown, KeyDownEvent.getType());

        // WCAG 1.4.13: the datetime pill was reachable only by holding the mouse
        // down on the bar, so the one piece of information that says *when* the
        // map is showing was unavailable to anyone not using a pointer. Showing it
        // while the bar holds focus makes it available on the keyboard, and gives
        // a sighted keyboard user the same readout a dragger gets.
        //
        // Escape dismisses it without moving focus — also 1.4.13 — and the
        // suppressed flag makes that stick, so it does not immediately return on
        // the next arrow key.
        //noinspection unused e
        outerBar.addDomHandler(e -> {
            scrubTooltipSuppressed = false;
            showScrubTooltip(true);
        }, FocusEvent.getType());
        //noinspection unused e
        outerBar.addDomHandler(e -> showScrubTooltip(false), BlurEvent.getType());

        // Histogram click-to-seek — treat it like releasing the scrubber.
        histogram.setOnClick(fraction -> {
            if (onCommit != null) {
                onCommit.accept(fraction * 100.0);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Progress bar
    // -----------------------------------------------------------------------

    /// Moves the bar's handle.
    ///
    /// @param pct Where it is, from 0 to 100.
    public void setProgressPct(final double pct) {
        CssProperties.set(outerBar.getElement(), PROGRESS, pct + "%");
        // Keep ARIA in sync so screen-readers announce the current position.
        outerBar.setAriaValueNow((int) Math.round(pct));
    }

    /// Updates the datetime pill shown above the handle, which is also the bar's value as screen
    /// readers announce it.
    ///
    /// @param text The time, as shown.
    public void setScrubTooltip(final String text) {
        scrubTooltip.setText(text);
        outerBar.getElement().setAttribute("aria-valuetext", text);
    }

    /// @param onScrub What to run, with where the handle is (0 to 100), on every move while the
    ///                user drags it. It should move the time shown only, not query data.
    public void setOnScrub(final Consumer<Double> onScrub) {
        this.onScrub = onScrub;
    }

    /// @param onCommit What to run, with where the handle is (0 to 100), when the user releases
    ///                 it, clicks the histogram, or presses Home or End: the time to query data
    ///                 for.
    public void setOnCommit(final Consumer<Double> onCommit) {
        this.onCommit = onCommit;
    }

    /// Stated in bins rather than as a percentage so a keypress lands on the same instants the
    /// step buttons reach; the timeline does not know the bin width, and a percentage step would
    /// drift off the bin grid.
    ///
    /// @param onNudge What to run, with a signed number of histogram bins, when the user scrubs
    ///                the bar from the keyboard.
    public void setOnNudge(final Consumer<Integer> onNudge) {
        this.onNudge = onNudge;
    }

    // -----------------------------------------------------------------------
    // Date labels
    // -----------------------------------------------------------------------

    /// @param text The time at the left end of the bar.
    public void setStartDateLabel(final String text) {
        startDateLabel.setText(text);
        outerBar.setAriaLabel("Timeline from " + text + " to " + endDateLabel.getText());
    }

    /// @param text The time at the right end of the bar.
    public void setEndDateLabel(final String text) {
        endDateLabel.setText(text);
        outerBar.setAriaLabel("Timeline from " + startDateLabel.getText() + " to " + text);
    }

    // -----------------------------------------------------------------------
    // Buttons
    // -----------------------------------------------------------------------

    /// Sets play/pause's icon and name, e.g. Pause while playing.
    ///
    /// @param icon  The icon.
    /// @param title The name.
    public void setPlayPauseIcon(final SvgImage icon, final String title) {
        playPause.setSvg(icon);
        playPause.setTitle(title);
    }

    /// @param onPlayPause What to run when the user presses play/pause.
    public void setOnPlayPause(final Runnable onPlayPause) {
        playPause.setOnClick(onPlayPause);
    }

    /// @param onStepBack What to run when the user presses Step Back.
    public void setOnStepBack(final Runnable onStepBack) {
        stepBack.setOnClick(onStepBack);
    }

    /// @param onStepForward What to run when the user presses Step Forward.
    public void setOnStepForward(final Runnable onStepForward) {
        stepForward.setOnClick(onStepForward);
    }

    /// @param onSettings What to run when the user presses Playback Settings.
    public void setOnSettings(final Runnable onSettings) {
        settings.setOnClick(onSettings);
    }

    /// @return The settings button, for its popup to be shown by.
    public Widget getSettingsButton() {
        return settings;
    }

    /// @return The help toolbar, hidden until [#setHelpVisible(boolean)] shows it, for its owner
    ///         to add the help button to.
    public Toolbar getHelpToolbar() {
        return helpToolbar;
    }

    /// @param visible Whether the help toolbar is shown.
    public void setHelpVisible(final boolean visible) {
        helpToolbar.setVisible(visible);
    }

    // -----------------------------------------------------------------------
    // Speed badge
    // -----------------------------------------------------------------------

    /// @param text The playback speed, e.g. `x1`.
    public void setSpeedBadge(final String text) {
        speedBadge.getElement().setInnerText(text);
        applySpeedBadgeLabel();
    }

    /// @param onSpeedBadge What to run when the user presses the speed badge.
    public void setOnSpeedBadge(final Runnable onSpeedBadge) {
        this.onSpeedBadge = onSpeedBadge;
    }

    /// @return The speed badge, for the speed menu to be shown by.
    public Widget getSpeedBadge() {
        return speedBadge;
    }

    /// Names the speed badge for assistive technology as purpose plus current
    /// value, e.g. `"Playback speed ×1, opens the speed menu"`.
    ///
    /// Kept in step with the visible text, which is the whole accessible name
    /// otherwise: `×1` on its own is unintelligible out of visual context,
    /// and the `title` attribute that explains it is not used for the name
    /// of an element that has content.
    private void applySpeedBadgeLabel() {
        speedBadge.setAriaLabel("Playback speed " + speedBadge.getElement().getInnerText()
                                + ", opens the speed menu");
    }

    // -----------------------------------------------------------------------
    // Histogram and out of range
    // -----------------------------------------------------------------------

    /// Shows the event counts above the bar. An empty or null array clears the histogram.
    ///
    /// @param binCounts The event count in each bin.
    /// @param layout    Where each bin belongs across the visible range, or `null` for an even
    ///                  division of it.
    public void setHistogramData(final int[] binCounts, final HistogramLayout layout) {
        histogram.setData(binCounts, layout);
    }

    /// Repaints the histogram with the data it already has.
    ///
    /// Needed on a theme change: the bars are canvas pixels, so unlike the CSS-styled parts of
    /// the strip they keep the colour they were painted with until something repaints them.
    public void redrawHistogram() {
        histogram.redraw();
    }

    /// Shows or hides the out-of-range chevron at either end of the bar, and says what it means
    /// through the live region, so it is heard when it happens.
    ///
    /// @param before True if the tracked object is before the start of the range.
    /// @param after  True if it is after the end.
    public void setOutOfRange(final boolean before, final boolean after) {
        UIObject.setVisible(outOfRangeLeft, before);
        UIObject.setVisible(outOfRangeRight, after);

        // The chevrons are «/» glyphs — read aloud they are punctuation, or
        // nothing at all. Say what the state actually means, and say it through
        // the live region so it is heard when it happens rather than only if the
        // user thinks to go looking.
        final String message;
        if (before) {
            message = "Tracked object is before the start of the timeline range."
                      + " Extend the range to see it.";
        } else if (after) {
            message = "Tracked object is after the end of the timeline range."
                      + " Extend the range to see it.";
        } else {
            message = "";
        }
        timelineStatus.setInnerText(message);
    }

    // -----------------------------------------------------------------------
    // Mouse / drag handling on the scrubber bar
    // -----------------------------------------------------------------------

    /// Handles keyboard scrubbing on the focused bar.
    ///
    /// Every handled key calls `preventDefault`: left unhandled, the
    /// arrows and Page keys scroll the surrounding panel instead, which moves the
    /// timeline out from under the user rather than moving the time.
    private void onBarKeyDown(final KeyDownEvent event) {
        switch (event.getNativeKeyCode()) {
            // Right/Up increase, Left/Down decrease — the ARIA slider convention,
            // and the reason Down is grouped with Left rather than with Up.
            case KeyCodes.KEY_RIGHT:
            case KeyCodes.KEY_UP:
                nudge(event, 1);
                break;
            case KeyCodes.KEY_LEFT:
            case KeyCodes.KEY_DOWN:
                nudge(event, -1);
                break;
            case KeyCodes.KEY_PAGEUP:
                nudge(event, PAGE_BINS);
                break;
            case KeyCodes.KEY_PAGEDOWN:
                nudge(event, -PAGE_BINS);
                break;
            case KeyCodes.KEY_HOME:
                seekTo(event, 0.0);
                break;
            case KeyCodes.KEY_END:
                seekTo(event, 100.0);
                break;
            case KeyCodes.KEY_ESCAPE:
                // Dismiss the pill without moving focus (WCAG 1.4.13). Used up only
                // when the pill is showing: otherwise Escape may also mean "close
                // the dialog I am in", and swallowing it here would trap the user.
                if (scrubTooltip.getStyleName().contains(SCRUB_TOOLTIP_VISIBLE)) {
                    event.preventDefault();
                }
                scrubTooltipSuppressed = true;
                showScrubTooltip(false);
                break;
            default:
                // Anything else belongs to the browser (Tab, shortcuts, …).
                break;
        }
    }

    /// Shows or hides the datetime pill, honouring an Escape dismissal.
    ///
    /// A dismissal that un-did itself on the next keystroke would not be a
    /// dismissal, so `scrubTooltipSuppressed` outranks a request to show;
    /// it is cleared when the bar is focused afresh.
    private void showScrubTooltip(final boolean visible) {
        if (visible && !scrubTooltipSuppressed) {
            scrubTooltip.addStyleName(SCRUB_TOOLTIP_VISIBLE);
        } else {
            scrubTooltip.removeStyleName(SCRUB_TOOLTIP_VISIBLE);
        }
    }

    /// Moves the time by `bins` histogram bins and swallows the keystroke.
    private void nudge(final KeyDownEvent event, final int bins) {
        event.preventDefault();
        event.stopPropagation();
        showScrubTooltip(true);
        if (onNudge != null) {
            onNudge.accept(bins);
        }
    }

    /// Jumps to an absolute position on the bar and swallows the keystroke. Goes
    /// through the commit callback — the same path as releasing a drag — because
    /// Home/End are a finished movement, not an in-progress one, and so should
    /// fire the data query rather than only move the handle.
    private void seekTo(final KeyDownEvent event, final double pct) {
        event.preventDefault();
        event.stopPropagation();
        showScrubTooltip(true);
        if (onCommit != null) {
            onCommit.accept(pct);
        }
    }

    private void onBarMouseDown(final MouseDownEvent event) {
        if (event.getNativeButton() == NativeEvent.BUTTON_LEFT) {
            dragging = true;
            // A fresh pointer interaction overrides an earlier Escape dismissal.
            scrubTooltipSuppressed = false;
            showScrubTooltip(true);
            // Move the handle immediately on click but do not yet fire a data query.
            notifyScrub(event.getClientX());
            DOM.setCapture(outerBar.getElement());
            event.preventDefault();
        }
    }

    private void onBarMouseMove(final MouseMoveEvent event) {
        if (dragging) {
            // Keep updating the visual position while dragging — still no data query.
            notifyScrub(event.getClientX());
        }
    }

    private void onBarMouseUp(final MouseUpEvent event) {
        if (dragging) {
            dragging = false;
            // Unchanged pointer behaviour: the pill goes away on release. Keyboard
            // scrubbing brings it back (see nudge/seekTo), so a click followed by
            // arrow keys still shows the readout.
            showScrubTooltip(false);
            // Commit the final position: this is the single point at which we fire a data query.
            notifyCommit(event.getClientX());
            DOM.releaseCapture(outerBar.getElement());
        }
    }

    /// Tells the scrub callback the percentage position for the given client X coordinate.
    /// Updates visuals immediately but intentionally does NOT trigger a data query.
    private void notifyScrub(final int clientX) {
        if (onScrub != null) {
            onScrub.accept(computeBarPct(clientX));
        }
    }

    /// Tells the commit callback the percentage position for the given client X coordinate.
    /// This is the signal that the user has finished scrubbing and a data query should be fired.
    private void notifyCommit(final int clientX) {
        if (onCommit != null) {
            onCommit.accept(computeBarPct(clientX));
        }
    }

    /// Converts a client-X pixel position to a `[0, 100]` percentage along the scrubber bar.
    private double computeBarPct(final int clientX) {
        final Element element = outerBar.getElement();
        final int absoluteLeft = element.getAbsoluteLeft();
        final int width = element.getOffsetWidth();
        final double relativeX = clientX - absoluteLeft;
        return Math.max(0, Math.min(100, (relativeX / width) * 100));
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapTimeline> {

    }
}
