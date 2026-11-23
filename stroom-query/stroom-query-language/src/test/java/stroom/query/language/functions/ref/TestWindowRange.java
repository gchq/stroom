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

package stroom.query.language.functions.ref;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestWindowRange {

    @Test
    void everyWindowIsInRangeUntilTheRangeIsSet() {
        final WindowRange windowRange = new WindowRange();

        assertThat(windowRange.contains(Long.MIN_VALUE)).isTrue();
        assertThat(windowRange.contains(0)).isTrue();
        assertThat(windowRange.contains(Long.MAX_VALUE)).isTrue();
    }

    @Test
    void firstAndLastWindowsAreInRange() {
        final WindowRange windowRange = new WindowRange();
        windowRange.set(10, 20);

        assertThat(windowRange.contains(9)).isFalse();
        assertThat(windowRange.contains(10)).isTrue();
        assertThat(windowRange.contains(15)).isTrue();
        assertThat(windowRange.contains(20)).isTrue();
        assertThat(windowRange.contains(21)).isFalse();
    }

    @Test
    void rangeOfOneWindow() {
        final WindowRange windowRange = new WindowRange();
        windowRange.set(-5, -5);

        assertThat(windowRange.contains(-6)).isFalse();
        assertThat(windowRange.contains(-5)).isTrue();
        assertThat(windowRange.contains(-4)).isFalse();
    }

    @Test
    void clearingPutsEveryWindowBackInRange() {
        final WindowRange windowRange = new WindowRange();
        windowRange.set(10, 20);
        windowRange.clear();

        assertThat(windowRange.contains(0)).isTrue();
        assertThat(windowRange.contains(100)).isTrue();
    }
}
