/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.widget.progress.client.presenter;

import java.util.Optional;

/**
 * Defines the state of a progress bar that has a sliding window i.e.
 * <p>
 * -----------------------------------------------------
 * ===================
 * -----------------------------------------------------
 * ^          ^                 ^                      ^
 * a          b                 c                      d
 * <p>
 * a lowerBound
 * b rangeFromInc
 * c rangeToInc
 * d upperBound
 * <p>
 * All values are in the domain units, e.g. if the progress is representing
 * the display of a range of characters in a file then all values would be
 * char offsets.
 * <p>
 * It can be user to represent a traditional progress bar if rangeFromInc is equal
 * to lowerBound.
 * <p>
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
        return new Progress(0, upperBound, 0, progress);
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

    public boolean hasKnownUpperBound() {
        return upperBound != null;
    }

    public double getRangeFromInc() {
        return rangeFromInc;
    }

    public double getRangeToInc() {
        return rangeToInc;
    }

    public boolean isComplete() {
        return upperBound != null
                && rangeFromInc == lowerBound
                && rangeToInc == upperBound;
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
