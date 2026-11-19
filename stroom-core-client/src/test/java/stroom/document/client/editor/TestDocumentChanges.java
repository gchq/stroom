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

package stroom.document.client.editor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentChanges {

    @Test
    void aDocumentWrittenAsReadHasNoChanges() {
        assertThat(DocumentChanges.hasChanges("doc", () -> "doc", () -> false)).isFalse();
    }

    @Test
    void aDocumentWrittenDifferentlyHasChanges() {
        assertThat(DocumentChanges.hasChanges("doc", () -> "edited", () -> false)).isTrue();
    }

    @Test
    void changesUndoneLeaveNoChanges() {
        // Compared by value, so an edit typed and then removed leaves the document clean
        assertThat(DocumentChanges.hasChanges(new StringBuilder("doc").toString(),
                () -> new StringBuilder("do").append("c").toString(), () -> false)).isFalse();
    }

    @Test
    void whatIsSavedWithItCountsWithoutWritingItOut() {
        final AtomicInteger writes = new AtomicInteger();
        assertThat(DocumentChanges.hasChanges("doc", () -> {
            writes.incrementAndGet();
            return "doc";
        }, () -> true)).isTrue();
        // Writing the document out, on every key press, may cost more, so it isn't
        assertThat(writes).hasValue(0);
    }
}
