package stroom.widget.progress.client.presenter;

import stroom.widget.progress.client.presenter.ProgressPresenter.ProgressView;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ProgressPresenter extends MyPresenterWidget<ProgressView> {

    private static final String TITLE_PREFIX = "Visible section of the source data";
    private Progress progress;
    private double computedUpperBound = 0;

    @Inject
    public ProgressPresenter(final EventBus eventBus, final ProgressView view) {
        super(eventBus, view);
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
                        (progress.getRangeToInc() - progress.getLowerBound()) * 1.1);
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

        GWT.log("lowerBound: " + progress.getLowerBound()
                + " upperBound: " + progress.getUpperBound().orElse((double) -1)
                + " rangeFrom: " + progress.getRangeFromInc()
                + " rangeTo: " + progress.getRangeToInc()
                + " rangePct: " + rangeFromPct
                + " + winddowPct: " + windowSizePct);

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
            barColour = "#FFD54F"; // Material Design Amber 300
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
