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

package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.AnyTypeValue;
import stroom.pathways.shared.pathway.ConstraintValue;
import stroom.pathways.shared.pathway.NanoTimeRange;
import stroom.pathways.shared.pathway.NanoTimeValue;
import stroom.pathways.shared.pathway.StringSet;
import stroom.pathways.shared.pathway.StringValue;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a constraint was given, summarised as it arrives.
 *
 * <p>Kept as an outer bound rather than whole, so the narrowing can ask what the last few days
 * actually held without the model having stored every value it ever saw.
 */
class TestObservedValues {

    @Test
    void oneObservationIsTheDaySoFar() {
        assertThat(ObservedValues.add(null, time(5))).isEqualTo(time(5));
    }

    @Test
    void twoObservationsBecomeTheSpanBetweenThem() {
        final ConstraintValue seen = ObservedValues.add(ObservedValues.add(null, time(5)), time(2));

        assertThat(seen).isEqualTo(new NanoTimeRange(millis(2), millis(5)));
    }

    @Test
    void anObservationInsideTheSpanLeavesItAlone() {
        ConstraintValue seen = ObservedValues.add(ObservedValues.add(null, time(2)), time(40));
        seen = ObservedValues.add(seen, time(9));

        assertThat(seen)
                .as("what is kept is the outer bound, so a value it already admits changes nothing")
                .isEqualTo(new NanoTimeRange(millis(2), millis(40)));
    }

    @Test
    void valuesThatDoNotOrderAreListed() {
        ConstraintValue seen = ObservedValues.add(null, new StringValue("GET"));
        seen = ObservedValues.add(seen, new StringValue("POST"));
        seen = ObservedValues.add(seen, new StringValue("GET"));

        assertThat(seen).isEqualTo(new StringSet(Set.of("GET", "POST")));
    }

    @Test
    void tooManyValuesToListSaysSoRatherThanGrowing() {
        ConstraintValue seen = null;
        for (int i = 0; i <= ObservedValues.MAX_VALUES; i++) {
            seen = ObservedValues.add(seen, new StringValue("value-" + i));
        }

        assertThat(seen)
                .as("one that admits everything is as much as needs storing: nothing narrower can"
                    + " be learnt from it")
                .isInstanceOf(AnyTypeValue.class);
    }

    @Test
    void aValueOfAnotherTypeGivesUpRatherThanGuessing() {
        final ConstraintValue seen = ObservedValues.add(ObservedValues.add(null, time(5)), new StringValue("GET"));

        assertThat(seen).isInstanceOf(AnyTypeValue.class);
    }

    @Test
    void nothingObservedLeavesTheDayAsItWas() {
        final ConstraintValue seen = ObservedValues.add(null, time(5));

        assertThat(ObservedValues.add(seen, null)).isEqualTo(seen);
    }

    @Test
    void twoAccountsCombineIntoOneCoveringBoth() {
        final ConstraintValue monday = ObservedValues.add(ObservedValues.add(null, time(5)), time(9));
        final ConstraintValue tuesday = ObservedValues.add(ObservedValues.add(null, time(2)), time(7));

        assertThat(ObservedValues.add(monday, tuesday))
                .as("the narrowing folds whole days together, not values into a day")
                .isEqualTo(new NanoTimeRange(millis(2), millis(9)));
    }

    @Test
    void combiningIsTheSameWhicheverWayRound() {
        final ConstraintValue monday = ObservedValues.add(ObservedValues.add(null, time(5)), time(9));
        final ConstraintValue tuesday = ObservedValues.add(ObservedValues.add(null, time(2)), time(7));

        assertThat(ObservedValues.add(monday, tuesday)).isEqualTo(ObservedValues.add(tuesday, monday));
    }

    @Test
    void twoListsOfValuesCombineIntoOne() {
        final ConstraintValue monday = ObservedValues.add(null, new StringValue("GET"));
        final ConstraintValue tuesday = ObservedValues.add(
                ObservedValues.add(null, new StringValue("POST")), new StringValue("HEAD"));

        assertThat(ObservedValues.add(monday, tuesday))
                .isEqualTo(new StringSet(Set.of("GET", "POST", "HEAD")));
    }

    private static NanoTimeValue time(final int millis) {
        return new NanoTimeValue(millis(millis));
    }

    private static NanoTime millis(final int millis) {
        return NanoTime.ofMillis(millis);
    }
}
