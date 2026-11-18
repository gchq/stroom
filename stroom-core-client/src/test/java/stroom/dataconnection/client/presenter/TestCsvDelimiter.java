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

package stroom.dataconnection.client.presenter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestCsvDelimiter {

    @ParameterizedTest
    @EnumSource(CsvDelimiter.class)
    void storedValueReadsBackAsTheSameDelimiter(final CsvDelimiter delimiter) {
        assertThat(CsvDelimiter.fromValue(delimiter.getValue())).isEqualTo(delimiter);
    }

    @Test
    void noDelimiterIsAComma() {
        assertThat(CsvDelimiter.fromValue(null)).isEqualTo(CsvDelimiter.COMMA);
    }

    @Test
    void delimiterNotOfferedIsShownAsAComma() {
        assertThat(CsvDelimiter.fromValue("#")).isEqualTo(CsvDelimiter.COMMA);
    }

    @Test
    void tabIsTheTabCharacter() {
        assertThat(CsvDelimiter.TAB.getValue()).isEqualTo("\t");
    }
}
