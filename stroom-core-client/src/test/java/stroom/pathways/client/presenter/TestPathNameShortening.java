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

package stroom.pathways.client.presenter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a node is called in the Path column, where the room runs out long before the path does.
 */
class TestPathNameShortening {

    @Test
    void aDatabaseSpanKeepsItsOperationAndLosesTheSchema() {
        assertThat(PathwayPathListPresenter.shorten("SELECT stroom.doc")).isEqualTo("SELECT doc");
        assertThat(PathwayPathListPresenter.shorten("UPDATE stroom.cluster_lock"))
                .isEqualTo("UPDATE cluster_lock");
        assertThat(PathwayPathListPresenter.shorten("INSERT stroom.processor_task"))
                .isEqualTo("INSERT processor_task");
    }

    @Test
    void aCodeSpanKeepsItsMethodAndLosesTheClass() {
        assertThat(PathwayPathListPresenter.shorten("HoldingAreaMergeStrategy.mergeShard"))
                .isEqualTo("mergeShard");
        assertThat(PathwayPathListPresenter.shorten("QueueItemWriter.publish")).isEqualTo("publish");
    }

    @Test
    void aNameWithNothingToDropIsLeftAlone() {
        assertThat(PathwayPathListPresenter.shorten("Commit")).isEqualTo("Commit");
        assertThat(PathwayPathListPresenter.shorten("SELECT stroom")).isEqualTo("SELECT stroom");
        assertThat(PathwayPathListPresenter.shorten("Batch execute prepared statement"))
                .isEqualTo("Batch execute prepared statement");
    }

    @Test
    void aDotInsideAQuotedValueIsNotAName() {
        // The agent names a span for the variable it read, and the dot there belongs to the value.
        // Cutting at it would leave a fragment of the quoted text rather than a shorter name.
        assertThat(PathwayPathListPresenter.shorten("Get variable '@@session.transaction_read_only'"))
                .isEqualTo("Get variable '@@session.transaction_read_only'");
        assertThat(PathwayPathListPresenter.shorten("Set variable 'character_set_results'"))
                .isEqualTo("Set variable 'character_set_results'");
    }

    @Test
    void aNameEndingInADotIsLeftAlone() {
        assertThat(PathwayPathListPresenter.shorten("trailing.")).isEqualTo("trailing.");
    }
}
