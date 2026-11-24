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

package stroom.query.language.functions;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestValPeriod {

    @Test
    void fieldsAreKeptAsTheyAreGiven() {
        final ValPeriod period = ValPeriod.create(0, 18, 40);

        assertThat(period.toPeriod()).isEqualTo(Period.of(0, 18, 40));
        assertThat(period).hasToString("P18M40D");
        assertThat(period).isEqualTo(Val.create(Period.of(0, 18, 40)));
        assertThat(period).isNotEqualTo(ValPeriod.create(1, 6, 40));
        assertThat(period).hasSameHashCodeAs(ValPeriod.create(0, 18, 40));
    }

    @Test
    void onlyDaysHaveANumericValue() {
        final ValPeriod days = ValPeriod.create(0, 0, 2);
        assertThat(days.hasNumericValue()).isTrue();
        assertThat(days.toLong()).isEqualTo(172_800_000L);
        assertThat(days.toDouble()).isEqualTo(172_800_000D);
        assertThat(days.toNumber()).isEqualTo(172_800_000L);

        for (final ValPeriod other : List.of(ValPeriod.create(1, 0, 2), ValPeriod.create(0, 1, 2))) {
            assertThat(other.hasNumericValue()).isFalse();
            assertThat(other.toLong()).isNull();
            assertThat(other.toInteger()).isNull();
            assertThat(other.toDouble()).isNull();
            assertThat(other.toFloat()).isNull();
            assertThat(other.toNumber()).isNull();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0,false",
            "0,0,1,true",
            "0,1,0,true",
            "1,0,0,true"})
    void asBoolean(final int years, final int months, final int days, final boolean expected) {
        assertThat(ValPeriod.create(years, months, days).toBoolean()).isEqualTo(expected);
    }

    @Test
    void sortsByEstimatedLength() {
        final List<Val> sorted = List.of(
                ValPeriod.create(-1, 0, 0),
                ValPeriod.create(0, 0, 0),
                ValPeriod.create(0, 0, 30),
                ValPeriod.create(0, 1, 0),
                ValPeriod.create(0, 0, 31),
                ValPeriod.create(0, 12, 0),
                ValPeriod.create(1, 0, 0),
                ValPeriod.create(0, 0, 366));
        final List<Val> reversed = new ArrayList<>(sorted.reversed());

        reversed.sort(ValComparators.getSortComparator(Type.PERIOD).orElseThrow());
        assertThat(reversed).isEqualTo(sorted);

        final List<Val> generic = new ArrayList<>(sorted.reversed());
        generic.sort(ValComparators.getComparator(false));
        assertThat(generic).isEqualTo(sorted);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,7,7d",
            "0,3,0,3M",
            "2,0,0,2y",
            "0,0,0,0d",
            "1,2,3,parsePeriod('P1Y2M3D')"})
    void expressionText(final int years, final int months, final int days, final String expected) {
        final StringBuilder sb = new StringBuilder();
        ValPeriod.create(years, months, days).appendString(sb);

        assertThat(sb).hasToString(expected);
    }

    @Test
    void json() {
        final Val period = ValPeriod.create(1, -2, 3);

        final String json = JsonUtil.writeValueAsString(period);
        assertThat(JsonUtil.readValue(json, Val.class)).isEqualTo(period);
    }
}
