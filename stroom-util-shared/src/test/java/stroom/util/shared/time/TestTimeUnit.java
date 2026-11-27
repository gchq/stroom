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

package stroom.util.shared.time;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestTimeUnit {

    @ParameterizedTest
    @EnumSource
    void testFromShortForm(final TimeUnit timeUnit) {
        assertThat(TimeUnit.fromShortForm(timeUnit.getShortForm())).isEqualTo(timeUnit);
    }

    @Test
    void testFromShortFormIsCaseSensitive() {
        assertThat(TimeUnit.fromShortForm("m")).isEqualTo(TimeUnit.MINUTES);
        assertThat(TimeUnit.fromShortForm("M")).isEqualTo(TimeUnit.MONTHS);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"H", "D", "W", "Y", "MS", "1d", " d", "us", "x"})
    void testFromShortFormUnknown(final String shortForm) {
        assertThat(TimeUnit.fromShortForm(shortForm)).isNull();
    }
}
