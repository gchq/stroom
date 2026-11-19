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

package stroom.cell.valuespinner.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestValueSpinnerCell {

    private static final HasSpinnerConstraints ONE_TO_HUNDRED = new HasSpinnerConstraints() {
        @Override
        public int getStep() {
            return 1;
        }

        @Override
        public int getMaxStep() {
            return 99;
        }

        @Override
        public long getMin() {
            return 1;
        }

        @Override
        public long getMax() {
            return 100;
        }
    };

    @Test
    void parseWithinBounds_wholeNumberWithinBounds_isAccepted() {
        assertThat(ValueSpinnerCell.parseWithinBounds(ONE_TO_HUNDRED, "1")).isEqualTo(1L);
        assertThat(ValueSpinnerCell.parseWithinBounds(ONE_TO_HUNDRED, " 100 ")).isEqualTo(100L);
        assertThat(ValueSpinnerCell.parseWithinBounds(ONE_TO_HUNDRED, "010")).isEqualTo(10L);
    }

    @Test
    void parseWithinBounds_outOfBounds_isRejected() {
        // Once kept within the bounds without a word: 500 became 100
        assertThat(ValueSpinnerCell.parseWithinBounds(ONE_TO_HUNDRED, "500")).isNull();
        assertThat(ValueSpinnerCell.parseWithinBounds(ONE_TO_HUNDRED, "0")).isNull();
        assertThat(ValueSpinnerCell.parseWithinBounds(ONE_TO_HUNDRED, "-5")).isNull();
    }

    @Test
    void parseWithinBounds_notAWholeNumber_isRejected() {
        // Once had its non-digits dropped: "7x" became 7
        assertThat(ValueSpinnerCell.parseWithinBounds(ONE_TO_HUNDRED, "7x")).isNull();
        assertThat(ValueSpinnerCell.parseWithinBounds(ONE_TO_HUNDRED, "2.5")).isNull();
        assertThat(ValueSpinnerCell.parseWithinBounds(ONE_TO_HUNDRED, "")).isNull();
        assertThat(ValueSpinnerCell.parseWithinBounds(ONE_TO_HUNDRED, "99999999999999999999")).isNull();
    }

    @Test
    void rejectionMessage_saysWhatIsAllowed() {
        assertThat(ValueSpinnerCell.rejectionMessage(ONE_TO_HUNDRED, " 500 "))
                .isEqualTo("\"500\" isn't allowed. Enter a whole number from 1 to 100.");
    }

    @Test
    void rejectionMessage_blank_saysAValueIsNeeded() {
        assertThat(ValueSpinnerCell.rejectionMessage(ONE_TO_HUNDRED, "  "))
                .isEqualTo("A value is needed. Enter a whole number from 1 to 100.");
    }
}
