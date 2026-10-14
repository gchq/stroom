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

package stroom.sqlstore.api;

import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestTemporalEntryValidator {

    @Test
    void testAKeyOfExactlyTheMaximumLengthIsValid() {
        assertThat(TemporalEntryValidator.keyProblem("k".repeat(UpdatableTemporalStore.MAX_KEY_LENGTH)))
                .isNull();
    }

    @Test
    void testAKeyOverTheMaximumLengthIsInvalidAndTheProblemSaysSo() {
        final String key = "k".repeat(UpdatableTemporalStore.MAX_KEY_LENGTH + 1);

        assertThat(TemporalEntryValidator.keyProblem(key))
                .contains(String.valueOf(UpdatableTemporalStore.MAX_KEY_LENGTH))
                .contains(String.valueOf(key.length()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void testABlankKeyIsInvalid(final String key) {
        assertThat(TemporalEntryValidator.keyProblem(key))
                .isEqualTo("the key must not be blank.");
    }

    @Test
    void testAValidEntry() {
        assertThat(TemporalEntryValidator.entryProblem(new TemporalEntry("map", "key", 1L, "value")))
                .isNull();
    }

    @Test
    void testAnEntryWithAValueOfNullIsValid() {
        // The value column is nullable; only the key fields are required.
        assertThat(TemporalEntryValidator.entryProblem(new TemporalEntry("map", "key", 1L, null)))
                .isNull();
    }

    @Test
    void testAnInvalidEntry() {
        assertThat(TemporalEntryValidator.entryProblem(null))
                .isEqualTo("there is no entry.");
        assertThat(TemporalEntryValidator.entryProblem(new TemporalEntry(" ", "key", 1L, "value")))
                .isEqualTo("the map name must not be blank.");
        assertThat(TemporalEntryValidator.entryProblem(new TemporalEntry("map", "", 1L, "value")))
                .isEqualTo("the key must not be blank.");
        assertThat(TemporalEntryValidator.entryProblem(new TemporalEntry(
                "map", "k".repeat(UpdatableTemporalStore.MAX_KEY_LENGTH + 1), 1L, "value")))
                .startsWith("the key is ");
        assertThat(TemporalEntryValidator.entryProblem(new TemporalEntry("map", "key", null, "value")))
                .isEqualTo("the effective time must be set.");
    }

    @Test
    void testAValidId() {
        assertThat(TemporalEntryValidator.idProblem(new TemporalEntryId("map", "key", 1L)))
                .isNull();
    }

    @Test
    void testAnIdIsNotRequiredToNameAMap() {
        assertThat(TemporalEntryValidator.idProblem(new TemporalEntryId(null, "key", 1L)))
                .isNull();
    }

    @Test
    void testAnInvalidId() {
        assertThat(TemporalEntryValidator.idProblem(null))
                .isEqualTo("there is no entry id.");
        assertThat(TemporalEntryValidator.idProblem(new TemporalEntryId("map", null, 1L)))
                .isEqualTo("the key must not be blank.");
        assertThat(TemporalEntryValidator.idProblem(new TemporalEntryId(
                "map", "k".repeat(UpdatableTemporalStore.MAX_KEY_LENGTH + 1), 1L)))
                .startsWith("the key is ");
        assertThat(TemporalEntryValidator.idProblem(new TemporalEntryId("map", "key", null)))
                .isEqualTo("the effective time must be set.");
    }
}
