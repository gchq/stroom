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

package stroom.ai.impl.tools.compare;

import stroom.ai.impl.tools.compare.ActivityStatistics.Basis;
import stroom.ai.impl.tools.compare.ActivityStatistics.Score;
import stroom.ai.impl.tools.compare.ActivityStatistics.Shift;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TestActivityStatistics {

    private static final double TOLERANCE = 1e-9;

    @Test
    void medianOfOddAndEvenCounts() {
        assertThat(ActivityStatistics.median(new double[]{3, 1, 2})).isEqualTo(2.0);
        assertThat(ActivityStatistics.median(new double[]{4, 1, 3, 2})).isEqualTo(2.5);
        assertThat(ActivityStatistics.median(new double[]{7})).isEqualTo(7.0);
        assertThat(ActivityStatistics.median(new double[0])).isNaN();
    }

    @Test
    void medianLeavesItsArgumentUnsorted() {
        final double[] values = {3, 1, 2};
        ActivityStatistics.median(values);
        assertThat(values).containsExactly(3, 1, 2);
    }

    @Test
    void madIgnoresTheOutlier() {
        // Median 3; deviations 2, 1, 0, 1, 97; their median 1.
        assertThat(ActivityStatistics.mad(new double[]{1, 2, 3, 4, 100})).isEqualTo(1.0);
        assertThat(ActivityStatistics.mad(new double[0])).isNaN();
    }

    @Test
    void robustZAgainstTheMad() {
        // (10 - 3) / (1.4826 * 1)
        final Score score = ActivityStatistics.robustZ(10, new double[]{1, 2, 3, 4, 100}).orElseThrow();
        assertThat(score.z()).isCloseTo(7 / 1.4826, within(TOLERANCE));
        assertThat(score.median()).isEqualTo(3.0);
        assertThat(score.mad()).isEqualTo(1.0);
        assertThat(score.n()).isEqualTo(5);
        assertThat(score.basis()).isEqualTo(Basis.MAD);
    }

    @Test
    void robustZRefusesAShortBaseline() {
        assertThat(ActivityStatistics.robustZ(10, new double[]{1, 2, 3, 4})).isEmpty();
        assertThat(ActivityStatistics.robustZ(10, new double[0])).isEmpty();
    }

    @Test
    void robustZFallsBackToTheMeanAbsoluteDeviationWhenTheMadIsZero() {
        // Median 5, MAD 0 (four of five equal); mean absolute deviation 4 / 5 = 0.8; (7 - 5) / (1.2533 * 0.8).
        final Score score = ActivityStatistics.robustZ(7, new double[]{5, 5, 5, 5, 9}).orElseThrow();
        assertThat(score.z()).isCloseTo(2 / (1.2533 * 0.8), within(TOLERANCE));
        assertThat(score.basis()).isEqualTo(Basis.MEAN_AD);
    }

    @Test
    void robustZAgainstAConstantBaseline() {
        final double[] constant = {3, 3, 3, 3, 3};
        assertThat(ActivityStatistics.robustZ(3, constant).map(Score::z)).contains(0.0);
        assertThat(ActivityStatistics.robustZ(4, constant).map(Score::z))
                .contains(ActivityStatistics.CONSTANT_BASELINE_Z);
        assertThat(ActivityStatistics.robustZ(2, constant).map(Score::z))
                .contains(-ActivityStatistics.CONSTANT_BASELINE_Z);
        assertThat(ActivityStatistics.robustZ(2, constant).map(Score::basis)).contains(Basis.CONSTANT);
    }

    @Test
    void theilSenIsRobustToAnOutlier() {
        // Pairwise slopes: six of 2, then 25, 32.33, 47.5 and 93: the median of ten is 2.
        assertThat(ActivityStatistics.theilSen(new double[]{0, 1, 2, 3, 4}, new double[]{1, 3, 5, 7, 100}))
                .isEqualTo(2.0);
    }

    @Test
    void theilSenOfAFlatOrDecliningSeries() {
        assertThat(ActivityStatistics.theilSen(new double[]{0, 1, 2}, new double[]{4, 4, 4})).isEqualTo(0.0);
        assertThat(ActivityStatistics.theilSen(new double[]{0, 1, 2}, new double[]{6, 4, 2})).isEqualTo(-2.0);
    }

    @Test
    void theilSenNeedsTwoDistinctXValues() {
        assertThat(ActivityStatistics.theilSen(new double[]{1}, new double[]{1})).isNaN();
        assertThat(ActivityStatistics.theilSen(new double[]{1, 1}, new double[]{1, 5})).isNaN();
        assertThatThrownBy(() -> ActivityStatistics.theilSen(new double[]{1, 2}, new double[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changePointOfACleanStep() {
        // At split 4 both sides are constant (score 10); split 3 or 5 leaves one odd value on a side, which then has
        // a mean absolute deviation of 0.8, so scores about 5.
        final Shift shift = ActivityStatistics.changePoint(new double[]{1, 1, 1, 1, 5, 5, 5, 5}, 2).orElseThrow();
        assertThat(shift.index()).isEqualTo(4);
        assertThat(shift.before()).isEqualTo(1.0);
        assertThat(shift.after()).isEqualTo(5.0);
        assertThat(shift.score()).isEqualTo(ActivityStatistics.CONSTANT_BASELINE_Z);
    }

    @Test
    void changePointOfANoisyStep() {
        // Split 5: medians 10 and 30, each side's MAD 1, so 20 / 1.4826.
        final Shift shift = ActivityStatistics.changePoint(
                new double[]{10, 12, 11, 9, 10, 30, 31, 29, 32, 30}, 3).orElseThrow();
        assertThat(shift.index()).isEqualTo(5);
        assertThat(shift.before()).isEqualTo(10.0);
        assertThat(shift.after()).isEqualTo(30.0);
        assertThat(shift.score()).isCloseTo(20 / 1.4826, within(TOLERANCE));
    }

    @Test
    void changePointOfAFlatOrShortSeries() {
        assertThat(ActivityStatistics.changePoint(new double[]{2, 2, 2, 2, 2, 2}, 2)).isEmpty();
        assertThat(ActivityStatistics.changePoint(new double[]{1, 5, 9}, 2)).isEmpty();
        assertThat(ActivityStatistics.changePoint(new double[0], 1)).isEmpty();
        assertThat(ActivityStatistics.changePoint(new double[]{1, 2}, 0)).isEmpty();
    }

    @Test
    void profileDistance() {
        assertThat(ActivityStatistics.profileDistance(new double[]{1, 1, 0, 0}, new double[]{0, 0, 1, 1}))
                .isEqualTo(1.0);
        assertThat(ActivityStatistics.profileDistance(new double[]{2, 2}, new double[]{1, 1})).isEqualTo(0.0);
        // Shares 0.25, 0.75 against 0.75, 0.25: (0.5 + 0.5) / 2.
        assertThat(ActivityStatistics.profileDistance(new double[]{1, 3}, new double[]{3, 1})).isEqualTo(0.5);
        assertThat(ActivityStatistics.profileDistance(new double[]{0, 0}, new double[]{1, 1})).isNaN();
        assertThatThrownBy(() -> ActivityStatistics.profileDistance(new double[]{1}, new double[]{1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quietRuns() {
        final List<int[]> runs = ActivityStatistics.quietRuns(
                new boolean[]{true, false, false, false, true, false, true, false, false}, 2);
        assertThat(runs).hasSize(2);
        assertThat(runs.get(0)).containsExactly(1, 3);
        assertThat(runs.get(1)).containsExactly(7, 2);
        assertThat(ActivityStatistics.quietRuns(new boolean[0], 1)).isEmpty();
        assertThat(ActivityStatistics.quietRuns(new boolean[]{false, false}, 3)).isEmpty();
    }

    @Test
    void robustZOfAnEmptyOptionalIsEmpty() {
        final Optional<Score> score = ActivityStatistics.robustZ(Double.NaN, new double[]{1, 2, 3, 4, 5});
        assertThat(score.map(Score::z).filter(z -> !z.isNaN())).isEmpty();
    }
}
