package stroom.widget.progress.client.presenter;

import java.util.Optional;

/**
 * Defines the state of a progress bar that has a sliding window i.e.
 *
 * -----------------------------------------------------
 *            ===================
 * -----------------------------------------------------
 * ^          ^                 ^                      ^
 * a          b                 c                      d
 *
 * a lowerBound
 * b rangeFromInc
 * c rangeToInc
 * d upperBound
 *
 * All values are in the domain units, e.g. if the progress is representing
 * the display of a range of characters in a file then all values would be
 * char offsets.
 *
 * It can be user to represent a traditional progress bar if rangeFromInc is equal
 * to lowerBound.
 *
 * -----------------------------------------------------
 * ==============================
 * -----------------------------------------------------
 * ^                            ^                      ^
 * ab                           c                      d
 */
public class Progress {

    private final double lowerBound;
    private final Double upperBound;
    private final double rangeFromInc;
    private final double rangeToInc;


    public Progress(final double lowerBound,
                    final Double upperBound,
                    final double rangeFromInc,
                    final double rangeToInc) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.rangeFromInc = rangeFromInc;
        this.rangeToInc = rangeToInc;
    }

    public static Progress simplePercentage(final double progressPct) {
        return new Progress(0, (double) 100, 0, progressPct);
    }

    public static Progress simpleProgress(final double progress,
                                          final double upperBound) {
        return new Progress(0,  upperBound, 0, progress);
    }

    public static Progress boundedRange(final double upperBound,
                                        final double rangeFromInc,
                                        final double rangeToInc) {
        return new Progress(0, upperBound, rangeFromInc, rangeToInc);
    }

    public static Progress boundedRange(final double lowerBound,
                                        final double upperBound,
                                        final double rangeFromInc,
                                        final double rangeToInc) {
        return new Progress(lowerBound, upperBound, rangeFromInc, rangeToInc);
    }

    public static Progress unboundedRange(final double rangeFromInc,
                                          final double rangeToInc) {
        return new Progress(0, null, rangeFromInc, rangeToInc);
    }

    public static Progress unboundedRange(final double lowerBound,
                                          final double rangeFromInc,
                                          final double rangeToInc) {
        return new Progress(lowerBound, null, rangeFromInc, rangeToInc);
    }

    public double getLowerBound() {
        return lowerBound;
    }

    public Optional<Double> getUpperBound() {
        return Optional.ofNullable(upperBound);
    }

    public double getRangeFromInc() {
        return rangeFromInc;
    }

    public double getRangeToInc() {
        return rangeToInc;
    }

    @Override
    public String toString() {
        return "Progress{" +
                "lowerBound=" + lowerBound +
                ", upperBound=" + upperBound +
                ", rangeFromInc=" + rangeFromInc +
                ", rangeToInc=" + rangeToInc +
                '}';
    }
}
