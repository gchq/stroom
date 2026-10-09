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

package stroom.floormap.client.view;

import stroom.floormap.client.presenter.FloorMapTimelinePresenter.FloorMapTimelineView;
import stroom.floormap.client.presenter.FloorMapTimelinePresenter.OutOfRange;
import stroom.svg.client.Preset;
import stroom.widget.button.client.SvgButton;
import stroom.widget.histogram.client.HistogramLayout;
import stroom.widget.histogram.client.HistogramWidget;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
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
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.function.Consumer;

/// View implementation for the floor map timeline control.
/// Includes step-back, play/pause and step-forward buttons, a progress scrubber with date labels,
/// a clickable speed badge that opens the playback-speed menu, a settings button, and a histogram
/// above the scrubber.
/// Features:
///
/// - Scrub tooltip — datetime pill above the handle while dragging.
/// - Histogram click-to-seek — clicking the histogram jumps the timeline head.
/// - Histogram hover tooltip — shows event count for the hovered bin.
/// - ARIA slider attributes for accessibility.
public class FloorMapTimelineViewImpl extends ViewImpl implements FloorMapTimelineView {

    private final Widget widget;
    /// Called on every mouse-move during a drag — updates visuals only, no data queries.
    private Consumer<Double> scrubHandler;
    /// Called on mouse-up (release) — commits the time and triggers data queries.
    private Consumer<Double> commitHandler;
    /// Called when the play/pause button is clicked.
    private Runnable playPauseHandler;
    /// Called when the step-back button is clicked.
    private Runnable stepBackHandler;
    /// Called when the step-forward button is clicked.
    private Runnable stepForwardHandler;
    /// Called when the settings button is clicked.
    private Runnable settingsHandler;
    /// Called when the speed badge is clicked (or activated from the keyboard).
    private Runnable speedBadgeHandler;
    /// Called with a signed number of histogram bins when the focused bar is
    /// scrubbed from the keyboard.
    private Consumer<Integer> nudgeHandler;
    private boolean dragging;
    /// Set when the user dismisses the datetime pill with Escape, so it stays
    /// dismissed until the bar is focused again.
    private boolean scrubTooltipSuppressed;
    private final HistogramWidget histogramWidget;

    /// Class that makes the datetime pill visible.
    private static final String SCRUB_TOOLTIP_VISIBLE =
            "stroom-floormap-timeline-scrub-tooltip--visible";

    @UiField
    SimplePanel histogramContainer;
    @UiField
    FlowPanel outerBar;
    @UiField
    FlowPanel innerBar;
    @UiField
    FlowPanel handle;
    @UiField
    Label scrubTooltip;
    @UiField
    SvgButton stepBackButton;
    @UiField
    SvgButton playPauseButton;
    @UiField
    SvgButton stepForwardButton;
    @UiField
    SvgButton settingsButton;
    @UiField
    FlowPanel rightControls;
    @UiField
    Label speedBadge;
    @UiField
    Label startDateLabel;
    @UiField
    Label endDateLabel;
    @UiField
    Label outOfRangeLeftLabel;
    @UiField
    Label outOfRangeRightLabel;
    /// Visually-hidden live region announcing the out-of-range state.
    @UiField
    Label timelineStatus;

    @Inject
    public FloorMapTimelineViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        // Mouse handlers for dragging the timeline handle.
        outerBar.addDomHandler(this::onBarMouseDown, MouseDownEvent.getType());
        outerBar.addDomHandler(this::onBarMouseMove, MouseMoveEvent.getType());
        outerBar.addDomHandler(this::onBarMouseUp, MouseUpEvent.getType());

        // Button handlers are registered ONCE here and dispatch through a
        // mutable field, so a second setXxxHandler() call (e.g. a rebind of the
        // presenter) replaces the handler rather than stacking another click
        // registration — two registrations on play/pause would toggle `playing`
        // twice per click and silently cancel playback.
        //noinspection unused e
        playPauseButton.addClickHandler(e -> {
            if (playPauseHandler != null) {
                playPauseHandler.run();
            }
        });

        // Step button handlers.
        //noinspection unused e
        stepBackButton.addDomHandler(e -> {
            if (stepBackHandler != null) {
                stepBackHandler.run();
            }
        }, ClickEvent.getType());
        //noinspection unused e
        stepForwardButton.addDomHandler(e -> {
            if (stepForwardHandler != null) {
                stepForwardHandler.run();
            }
        }, ClickEvent.getType());

        //noinspection unused e
        settingsButton.addClickHandler(e -> {
            if (settingsHandler != null) {
                settingsHandler.run();
            }
        });

        // Speed badge behaves as a button: click or keyboard activation opens the speed menu.
        speedBadge.setTitle("Playback Speed");
        final Element badgeEl = speedBadge.getElement();
        badgeEl.setAttribute("role", "button");
        badgeEl.setAttribute("tabindex", "0");
        // The title above is a mouse tooltip only. An element's accessible name
        // comes from its content when it has any, so this badge would announce as
        // bare "×1, button" — which does not say what the ×1 is — and the title
        // would never be read. setSpeedBadge() therefore maintains an explicit
        // aria-label carrying both the purpose and the value.
        applySpeedBadgeLabel();
        //noinspection unused e
        speedBadge.addClickHandler(e -> {
            if (speedBadgeHandler != null) {
                speedBadgeHandler.run();
            }
        });
        speedBadge.addDomHandler(e -> {
            if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER || e.getNativeKeyCode() == KeyCodes.KEY_SPACE) {
                e.preventDefault();
                if (speedBadgeHandler != null) {
                    speedBadgeHandler.run();
                }
            }
        }, KeyDownEvent.getType());

        // ARIA: mark the outer bar as a slider.
        final Element barEl = outerBar.getElement();
        barEl.setAttribute("role", "slider");
        barEl.setAttribute("aria-valuemin", "0");
        barEl.setAttribute("aria-valuemax", "100");
        barEl.setAttribute("aria-valuenow", "0");
        barEl.setAttribute("tabindex", "0");

        // Keyboard scrubbing, in the key bindings the ARIA slider pattern
        // specifies. Without these the bar is worse than an unfocusable div: it
        // takes a tab stop and announces itself as a slider, then does nothing.
        //
        // Arrow and Page keys nudge by whole histogram bins rather than by a
        // percentage, so a keypress lands on the same times the step buttons
        // reach — a percentage step would drift off the bin grid and make the
        // step buttons and the keyboard disagree about "one step". Bin width is
        // the presenter's business, hence the signed-bin handler.
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

        // Build the histogram widget and place it inside the container.
        histogramWidget = new HistogramWidget();
        // Histogram click-to-seek — treat it like releasing the scrubber.
        histogramWidget.setClickHandler(fraction -> {
            if (commitHandler != null) {
                commitHandler.accept(fraction * 100.0);
            }
        });
        histogramContainer.setWidget(histogramWidget);

        // Set tooltip text for out-of-range indicators (labels are declared in UiBinder).
        //noinspection UnnecessaryUnicodeEscape
        outOfRangeLeftLabel.setText("\u00AB"); // «
        outOfRangeLeftLabel.setTitle("Object out of range. Extend timeline range to view object.");
        //noinspection UnnecessaryUnicodeEscape
        outOfRangeRightLabel.setText("\u00BB"); // »
        outOfRangeRightLabel.setTitle("Object out of range. Extend timeline range to view object.");
        // The chevrons are decoration: they are guillemets, which a screen reader
        // renders as punctuation or skips entirely, and setOutOfRangeIndicator()
        // states the same thing in words through the live region below. Hiding them
        // keeps the meaning from being offered twice, once uselessly.
        outOfRangeLeftLabel.getElement().setAttribute("aria-hidden", "true");
        outOfRangeRightLabel.getElement().setAttribute("aria-hidden", "true");

        // role="status" implies aria-live="polite"; both are set because some
        // screen readers honour only one.
        final Element statusEl = timelineStatus.getElement();
        statusEl.setAttribute("role", "status");
        statusEl.setAttribute("aria-live", "polite");
        statusEl.setAttribute("aria-atomic", "true");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    // -----------------------------------------------------------------------
    // Progress bar
    // -----------------------------------------------------------------------

    @Override
    public void setProgressPct(final double pct) {
        innerBar.getElement().getStyle().setWidth(pct, Unit.PCT);
        handle.getElement().getStyle().setLeft(pct, Unit.PCT);
        scrubTooltip.getElement().getStyle().setLeft(pct, Unit.PCT);

        // Keep ARIA in sync so screen-readers announce the current position.
        outerBar.getElement().setAttribute("aria-valuenow", String.valueOf((int) Math.round(pct)));
    }

    @Override
    public void setScrubTooltip(final String text) {
        scrubTooltip.setText(text);
        // Also push to ARIA so screen-readers announce the formatted datetime.
        outerBar.getElement().setAttribute("aria-valuetext", text);
    }

    @Override
    public void setScrubHandler(final Consumer<Double> scrubHandler) {
        this.scrubHandler = scrubHandler;
    }

    @Override
    public void setCommitHandler(final Consumer<Double> commitHandler) {
        this.commitHandler = commitHandler;
    }

    @Override
    public void setNudgeHandler(final Consumer<Integer> nudgeHandler) {
        this.nudgeHandler = nudgeHandler;
    }

    // -----------------------------------------------------------------------
    // Date labels
    // -----------------------------------------------------------------------

    @Override
    public void setStartDateLabel(final String text) {
        startDateLabel.setText(text);
        outerBar.getElement().setAttribute("aria-label",
                "Timeline from " + text + " to " + endDateLabel.getText());
    }

    @Override
    public void setEndDateLabel(final String text) {
        endDateLabel.setText(text);
        outerBar.getElement().setAttribute("aria-label",
                "Timeline from " + startDateLabel.getText() + " to " + text);
    }

    // -----------------------------------------------------------------------
    // Play/Pause button
    // -----------------------------------------------------------------------

    @Override
    public void setPlayPausePreset(final Preset preset) {
        playPauseButton.setSvg(preset.getSvgImage());
        playPauseButton.setTitle(preset.getTitle());
    }

    @Override
    public void setPlayPauseHandler(final Runnable handler) {
        this.playPauseHandler = handler;
    }

    // -----------------------------------------------------------------------
    // Step buttons
    // -----------------------------------------------------------------------

    @Override
    public void setStepBackPreset(final Preset preset) {
        stepBackButton.setSvg(preset.getSvgImage());
        stepBackButton.setTitle(preset.getTitle());
    }

    @Override
    public void setStepForwardPreset(final Preset preset) {
        stepForwardButton.setSvg(preset.getSvgImage());
        stepForwardButton.setTitle(preset.getTitle());
    }

    @Override
    public void setStepBackHandler(final Runnable handler) {
        this.stepBackHandler = handler;
    }

    @Override
    public void setStepForwardHandler(final Runnable handler) {
        this.stepForwardHandler = handler;
    }

    // -----------------------------------------------------------------------
    // Settings button
    // -----------------------------------------------------------------------

    @Override
    public void setSettingsPreset(final Preset preset) {
        settingsButton.setSvg(preset.getSvgImage());
        settingsButton.setTitle(preset.getTitle());
    }

    @Override
    public void setSettingsHandler(final Runnable handler) {
        this.settingsHandler = handler;
    }

    @Override
    public Widget getSettingsButtonWidget() {
        return settingsButton;
    }

    @Override
    public void addRightControl(final Widget widget) {
        rightControls.add(widget);
    }

    // -----------------------------------------------------------------------
    // Speed badge
    // -----------------------------------------------------------------------

    @Override
    public void setSpeedBadge(final String text) {
        speedBadge.setText(text);
        applySpeedBadgeLabel();
    }

    /// Names the speed badge for assistive technology as purpose plus current
    /// value, e.g. `"Playback speed ×1, opens the speed menu"`.
    ///
    /// Kept in step with the visible text, which is the whole accessible name
    /// otherwise: `×1` on its own is unintelligible out of visual context,
    /// and the `title` attribute that explains it is not used for the name
    /// of an element that has content.
    private void applySpeedBadgeLabel() {
        speedBadge.getElement().setAttribute("aria-label",
                "Playback speed " + speedBadge.getText() + ", opens the speed menu");
    }

    @Override
    public void setSpeedBadgeHandler(final Runnable handler) {
        this.speedBadgeHandler = handler;
    }

    @Override
    public Widget getSpeedBadgeWidget() {
        return speedBadge;
    }

    @Override
    public void setHistogramData(final int[] binCounts, final HistogramLayout layout) {
        if (histogramWidget != null) {
            histogramWidget.setData(binCounts, layout);
        }
    }

    @Override
    public void redrawHistogram() {
        if (histogramWidget != null) {
            histogramWidget.redraw();
        }
    }

    @Override
    public void setOutOfRangeIndicator(final OutOfRange direction) {
        outOfRangeLeftLabel.setVisible(direction == OutOfRange.BEFORE);
        outOfRangeRightLabel.setVisible(direction == OutOfRange.AFTER);

        // The chevrons are «/» glyphs — read aloud they are punctuation, or
        // nothing at all. Say what the state actually means, and say it through
        // the live region so it is heard when it happens rather than only if the
        // user thinks to go looking.
        final String message;
        //noinspection EnhancedSwitchMigration
        switch (direction) {
            case BEFORE:
                message = "Tracked object is before the start of the timeline range."
                        + " Extend the range to see it.";
                break;
            case AFTER:
                message = "Tracked object is after the end of the timeline range."
                        + " Extend the range to see it.";
                break;
            default:
                message = "";
                break;
        }
        timelineStatus.setText(message);
    }

    // -----------------------------------------------------------------------
    // Mouse / drag handling on the scrubber bar
    // -----------------------------------------------------------------------

    /// Number of histogram bins a Page Up / Page Down moves — the ARIA slider
    /// pattern's "larger step". Ten bins crosses a visible fraction of the bar
    /// without skipping so far that the user loses their place.
    private static final int PAGE_BINS = 10;

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
                // Dismiss the pill without moving focus (WCAG 1.4.13). Not
                // preventDefault-ed: Escape may also mean "close the dialog I am
                // in", and swallowing it here would trap the user.
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
        if (nudgeHandler != null) {
            nudgeHandler.accept(bins);
        }
    }

    /// Jumps to an absolute position on the bar and swallows the keystroke. Goes
    /// through the commit handler — the same path as releasing a drag — because
    /// Home/End are a finished movement, not an in-progress one, and so should
    /// fire the data query rather than only move the handle.
    private void seekTo(final KeyDownEvent event, final double pct) {
        event.preventDefault();
        event.stopPropagation();
        showScrubTooltip(true);
        if (commitHandler != null) {
            commitHandler.accept(pct);
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

    /// Notifies the scrub handler with the percentage position for the given client X coordinate.
    /// Updates visuals immediately but intentionally does NOT trigger a data query.
    private void notifyScrub(final int clientX) {
        if (scrubHandler != null) {
            scrubHandler.accept(computeBarPct(clientX));
        }
    }

    /// Notifies the commit handler with the percentage position for the given client X coordinate.
    /// This is the signal that the user has finished scrubbing and a data query should be fired.
    private void notifyCommit(final int clientX) {
        if (commitHandler != null) {
            commitHandler.accept(computeBarPct(clientX));
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

    public interface Binder extends UiBinder<Widget, FloorMapTimelineViewImpl> {

    }
}
