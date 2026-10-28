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

package stroom.widget.form.client;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TestInputChecks {

    @Test
    void aNumberInRangeIsKept() {
        final Function<String, String> check = InputChecks.numberFrom(0, 1);

        assertThat(check.apply("0.8")).isNull();
        assertThat(check.apply(" 0 ")).isNull();
        assertThat(check.apply("1")).isNull();
    }

    @Test
    void textThatIsNotANumberInRangeIsRefused() {
        // Before, such text in a rerank score became the default, 0.8, or threw
        final Function<String, String> check = InputChecks.numberFrom(0, 1);

        assertThat(check.apply("abc")).isEqualTo("Must be a number from 0 to 1");
        assertThat(check.apply("")).isEqualTo("Must be a number from 0 to 1");
        assertThat(check.apply("1.5")).isEqualTo("Must be a number from 0 to 1");
        assertThat(check.apply("-0.1")).isEqualTo("Must be a number from 0 to 1");
        assertThat(check.apply("NaN")).isEqualTo("Must be a number from 0 to 1");
    }

    @Test
    void aListOfWholeNumbersOrNothingIsKept() {
        final Function<String, String> check = InputChecks.wholeNumberList("1000000,100,10");

        assertThat(check.apply("1000000,100,10")).isNull();
        assertThat(check.apply(" 5 , 6 ")).isNull();
        assertThat(check.apply("")).isNull();
        assertThat(check.apply(null)).isNull();
    }

    @Test
    void aListWithAPartThatIsNotAWholeNumberIsRefused() {
        // Before, the parts that weren't numbers were dropped
        final Function<String, String> check = InputChecks.wholeNumberList("1000000,100,10");
        final String reason = "Must be whole numbers separated by commas, e.g. 1000000,100,10";

        assertThat(check.apply("100,abc")).isEqualTo(reason);
        assertThat(check.apply("100,,10")).isEqualTo(reason);
        assertThat(check.apply("1.5")).isEqualTo(reason);
        assertThat(check.apply("0")).isEqualTo(reason);
    }
}
