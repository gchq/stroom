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

package stroom.widget.util.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestListSelection {

    @Test
    void removingAMiddleRowSelectsTheNextRow() {
        assertThat(ListSelection.nextAfterRemove(
                List.of("a", "b", "c"), List.of("b"), List.of("a", "c")))
                .isEqualTo("c");
    }

    @Test
    void removingTheLastRowSelectsTheNewLastRow() {
        assertThat(ListSelection.nextAfterRemove(
                List.of("a", "b", "c"), List.of("c"), List.of("a", "b")))
                .isEqualTo("b");
    }

    @Test
    void removingSeveralRowsSelectsTheRowAfterTheFirstOfThem() {
        assertThat(ListSelection.nextAfterRemove(
                List.of("a", "b", "c", "d", "e"), List.of("d", "b"), List.of("a", "c", "e")))
                .isEqualTo("c");
    }

    @Test
    void removingEveryRowSelectsNothing() {
        assertThat(ListSelection.nextAfterRemove(
                List.of("a", "b"), List.of("a", "b"), List.of()))
                .isNull();
    }

    @Test
    void removingARowThatWasNotListedSelectsNothing() {
        assertThat(ListSelection.nextAfterRemove(
                List.of("a", "b"), List.of("x"), List.of("a", "b")))
                .isNull();
    }

    @Test
    void theRowNowAtAnIndexIsClampedToTheList() {
        assertThat(ListSelection.rowAt(List.of("a", "b"), 0)).isEqualTo("a");
        assertThat(ListSelection.rowAt(List.of("a", "b"), 5)).isEqualTo("b");
        assertThat(ListSelection.rowAt(List.of("a", "b"), -1)).isNull();
        assertThat(ListSelection.<String>rowAt(List.of(), 0)).isNull();
    }
}
