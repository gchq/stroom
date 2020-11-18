package stroom.widget.progress.client.presenter;

import stroom.widget.progress.client.presenter.ProgressPresenter.ProgressView;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ProgressPresenter extends MyPresenterWidget<ProgressView> {

    private static final String TITLE_PREFIX = "Visible section of the source data";
    private static final double UNCERTAINTY_FACTOR_MAX = 3;
    private static final double UNCERTAINTY_FACTOR_MIN = 1.1;
    private static final double UNCERTAINTY_FACTOR_DELTA = 0.1;

    private Progress progress;
    private double computedUpperBound = 0;
    private double uncertaintyFactor = UNCERTAINTY_FACTOR_MAX;

    @Inject
    public ProgressPresenter(final EventBus eventBus, final ProgressView view) {
        super(eventBus, view);
    }

    /**
     * The progress bar maintains state of the highest seen upper bound
     * so that it can show the latest information about the source. This
     * resets that state.
     */
    public void reset() {
        this.computedUpperBound = 0;
        this.uncertaintyFactor = UNCERTAINTY_FACTOR_MAX;
    }

    public void setProgress(final Progress progress) {
        this.progress = progress;
        if (progress != null) {
            update();
        }
    }

    public void setVisible(final boolean isVisible) {
        getView().setVisible(isVisible);
    }

    private double getWindowSize() {
        return progress.getRangeToInc() - progress.getRangeFromInc();
    }

    private double getTotalSize() {
        return getUpperBound() - progress.getLowerBound();
    }

    private double getUpperBound() {
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
        } else if (position == getUpperBound()) {
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

//        GWT.log("lowerBound: " + progress.getLowerBound()
//                + " upperBound: " + progress.getUpperBound().orElse((double) -1)
//                + " rangeFrom: " + progress.getRangeFromInc()
//                + " rangeTo: " + progress.getRangeToInc()
//                + " rangePct: " + rangeFromPct
//                + " winddowPct: " + windowSizePct);

        getView().setRangeFromPct(rangeFromPct);
        getView().setProgressPct(windowSizePct);

        final String barColour;
        final String title;
        // TODO set colour via a style name
        if (progress.getUpperBound().isPresent()) {
            // blue
            barColour = "#1e88e5";
            title = TITLE_PREFIX;

        } else {
            // orange, consistent with the orange svg icons, e.g. favorites.svg
//            barColour = "#ff8f00"; // Material Design Amber 800
//            barColour = "#FFD54F"; // Material Design Amber 300
            barColour = "#FFCA28"; // Material Design Amber 301
//            barColour = "#FDD835"; // Material Design Yellow 600
            title = TITLE_PREFIX + " (total size unknown)";
        }
        getView().setProgressBarColour(barColour);
        getView().setTitle(title);
    }

    public interface ProgressView extends View {

        void setRangeFromPct(final double rangeFrom);

        /**
         * The progress % if working from the lower bound or the size of the sliding
         * window as a % of the whole.
         */
        void setProgressPct(final double progress);

        void setVisible(final boolean isVisible);

        void setProgressBarColour(final String colour);

        void setTitle(final String title);
    }

}
