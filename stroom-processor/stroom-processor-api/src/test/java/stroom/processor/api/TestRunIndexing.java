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

package stroom.processor.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests what an index can say of a run.
class TestRunIndexing {

    @Test
    void nothingIsSaidAtFirst() {
        final RunIndexing indexing = new RunIndexing();

        assertThat(indexing.isHeld()).isFalse();
        assertThat(indexing.getCouldNotIndexReason()).isEmpty();
        assertThat(indexing.isCouldNotIndexCounted()).isFalse();
        assertThat(indexing.isDocumentsLost()).isFalse();
    }

    @Test
    void firstReasonIsKeptWhileNoneIsCounted() {
        final RunIndexing indexing = new RunIndexing();

        indexing.couldNotIndex("First", false);
        indexing.couldNotIndex("Second", false);

        assertThat(indexing.getCouldNotIndexReason()).contains("First");
        assertThat(indexing.isCouldNotIndexCounted()).isFalse();
    }

    @Test
    void reasonOfTheFirstCountedOneIsKept() {
        final RunIndexing indexing = new RunIndexing();

        indexing.couldNotIndex("Not writable", false);
        indexing.couldNotIndex("Bad document", true);
        indexing.couldNotIndex("Another bad document", true);
        indexing.couldNotIndex("Not writable again", false);

        assertThat(indexing.getCouldNotIndexReason()).contains("Bad document");
        assertThat(indexing.isCouldNotIndexCounted()).isTrue();
    }

    @Test
    void reasonIsGivenIfThereIsNone() {
        final RunIndexing indexing = new RunIndexing();

        indexing.couldNotIndex(null, false);

        assertThat(indexing.getCouldNotIndexReason()).contains("Could not index the stream");
    }

    @Test
    void everythingSaidIsKept() {
        final RunIndexing indexing = new RunIndexing();

        indexing.hold();
        indexing.documentsLost();
        indexing.hold();

        assertThat(indexing.isHeld()).isTrue();
        assertThat(indexing.isDocumentsLost()).isTrue();
    }
}
