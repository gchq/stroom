/*
 * Copyright 2020 Crown Copyright
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

package stroom.widget.progress.client;

import stroom.widget.component.client.Component;
import stroom.widget.util.client.CssProperties;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A bar showing which section of some data is shown (e.g. the part of a stream in the editor) of
/// how much, as a window on a track; a click on the track asks for the section there. The BEM block
/// `source-progress-bar` (`source-progress-bar.css`), its markup `SourceProgressBar.ui.xml`.
///
/// The window is blue while it can be moved, grey while it can't (`--disabled`), green when it
/// shows everything (`--complete`) and amber when the size isn't known (`--unbounded`), its size then
/// a guess a bit bigger than what is known, which shrinks as more is seen.
public class SourceProgressBar extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String TITLE_PREFIX = "Section of the source data visible in the editor";
    private static final String TITLE_SUFFIX = "\nClick elsewhere in the bar to move the visible section.";
    private static final String TITLE_SUFFIX_DISABLED = "\nUse the Source View to see all of the data.";
    private static final double UNCERTAINTY_FACTOR_MAX = 3;
    private static final double UNCERTAINTY_FACTOR_MIN = 1.1;
    private static final double UNCERTAINTY_FACTOR_DELTA = 0.1;
    private static final String COMPLETE = "source-progress-bar__window--complete";
    private static final String DISABLED = "source-progress-bar__window--disabled";
    private static final String UNBOUNDED = "source-progress-bar__window--unbounded";
    private static final String CLICKABLE = "source-progress-bar__track--clickable";
    private static final NumberFormat DEFAULT_PERCENT_FORMAT = NumberFormat.getFormat("0.0");

    @UiField
    FlowPanel track;
    @UiField
    FlowPanel window;

    private Progress progress;
    private double computedUpperBound = 0;
    private double uncertaintyFactor = UNCERTAINTY_FACTOR_MAX;
    private Consumer<Double> onClick;

    public SourceProgressBar() {
        initWidget(BINDER.createAndBindUi(this));
        track.addDomHandler(this::onTrackClick, ClickEvent.getType());
    }

    /// The bar keeps the highest upper bound it has seen, so that it shows the latest it knows of the
    /// data; this forgets it.
    public void reset() {
        this.computedUpperBound = 0;
        this.uncertaintyFactor = UNCERTAINTY_FACTOR_MAX;
    }

    /// @param progress Which section of the data is shown, of how much.
    public void setProgress(final Progress progress) {
        this.progress = progress;
        if (progress != null) {
            update();
        }
    }

    /// @param onClick Told where on the track the user clicked, in the units of [Progress], to show
    ///                the section there; not told while the data has no upper bound. Null for
    ///                nothing, and a window that can't be moved.
    public void setOnClick(final Consumer<Double> onClick) {
        this.onClick = onClick;
        track.setStyleName(CLICKABLE, onClick != null);
        if (progress != null) {
            update();
        }
    }

    private void onTrackClick(final ClickEvent event) {
        if (onClick != null && progress != null) {
            // Zero based, as a percentage of the track, from 0 to 100
            final double x = event.getX() - 1;
            final double width = event.getRelativeElement().getOffsetWidth();
            final double percentage = Math.max(0, Math.min(100, (x / width * 100)));
            progress.getUpperBound()
                    .map(upperBound -> upperBound * percentage / 100)
                    .ifPresent(onClick);
        }
    }

    private double getWindowSize() {
        return progress.getRangeToInc() - progress.getRangeFromInc();
    }

    private double getTotalSize() {
        return getComputedUpperBound() - progress.getLowerBound();
    }

    private double getComputedUpperBound() {
        // If there is no upperbound then make one that is a bit bigger than what is known
        if (progress.getUpperBound().isPresent()) {
            computedUpperBound = progress.getUpperBound().get();
        } else {
            computedUpperBound = Math.max(
                    computedUpperBound,
                    (progress.getRangeToInc() - progress.getLowerBound()) * uncertaintyFactor);
            uncertaintyFactor = Math.max(
                    UNCERTAINTY_FACTOR_MIN,
                    uncertaintyFactor - UNCERTAINTY_FACTOR_DELTA);
        }
        return computedUpperBound;
    }

    private double getRangeFromPct() {
        return getPositionAsPercentage(progress.getRangeFromInc());
    }

    private double getRangeToPct() {
        return getPositionAsPercentage(progress.getRangeToInc());
    }

    private double getPositionAsPercentage(final double position) {
        if (position == progress.getLowerBound()) {
            return 0;
        } else if (position == getComputedUpperBound()) {
            return 100;
        } else {
            return (position - progress.getLowerBound()) / getTotalSize() * 100;
        }
    }

    private double getWindowAsPercentage() {
        return getWindowSize() / getTotalSize() * 100;
    }

    private void update() {
        final double rangeFromPct = getRangeFromPct();
        final double windowSizePct = getWindowAsPercentage();

        CssProperties.set(window.getElement(), "--source-progress-bar__from", rangeFromPct + "%");
        CssProperties.set(window.getElement(), "--source-progress-bar__width", windowSizePct + "%");

        final String state;
        final String title;
        if (progress.isComplete()) {
            state = COMPLETE;
            title = "All data visible in the editor";
        } else if (progress.hasKnownUpperBound()) {
            state = onClick == null
                    ? DISABLED
                    : null;
            title = TITLE_PREFIX
                    + " ("
                    + formatPercentage(getWindowAsPercentage())
                    + "%). " + titleSuffix();
        } else {
            state = UNBOUNDED;
            title = TITLE_PREFIX
                    + " (total size unknown). "
                    + titleSuffix();
        }
        for (final String modifier : new String[]{COMPLETE, DISABLED, UNBOUNDED}) {
            window.setStyleName(modifier, modifier.equals(state));
        }
        track.setTitle(title);
        window.setTitle(title);
    }

    // What the title says the user can do about the section shown
    private String titleSuffix() {
        return onClick == null
                ? TITLE_SUFFIX_DISABLED
                : TITLE_SUFFIX;
    }

    private String formatPercentage(final double percentage) {
        // I'm sure there is a neater way of doing this
        if (percentage > 0.1) {
            return DEFAULT_PERCENT_FORMAT.format(percentage);
        } else if (percentage > 0.01) {
            return NumberFormat.getFormat("0.00").format(percentage);
        } else if (percentage > 0.001) {
            return NumberFormat.getFormat("0.000").format(percentage);
        } else if (percentage > 0.0001) {
            return NumberFormat.getFormat("0.0000").format(percentage);
        } else {
            return NumberFormat.getFormat("0.00000").format(percentage);
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SourceProgressBar> {

    }
}
