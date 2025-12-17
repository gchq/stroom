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

package stroom.util.shared;


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestLocation {

    @Test
    void testCompare() {
        doIsAfterTest("2:2", "2:2", false);
        doIsAfterTest("2:2", "2:3", true);
        doIsAfterTest("2:2", "3:2", true);
        doIsAfterTest("2:2", "3", true);
        doIsAfterTest("2", "3", true);
        doIsAfterTest("2", "2", false);
    }

    private void doIsAfterTest(final String fromStr, final String toStr, final boolean expectedResult) {
        final Location from = DefaultLocation.parse(fromStr).get();
        final Location to = DefaultLocation.parse(toStr).get();

        final boolean result = to.isAfter(from);

        Assertions.assertThat(result)
                .isEqualTo(expectedResult);

        if (!from.equals(to)) {
            final boolean result2 = to.isBefore(from);
            Assertions.assertThat(result2)
                    .isEqualTo(!expectedResult);
        }
    }
}
