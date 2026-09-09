/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.ai.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAnswerNotes {

    @Test
    void nothingToSay_leavesTheAnswerAlone() {
        assertThat(new AnswerNotes().appendTo("answer")).isEqualTo("answer");
        assertThat(new AnswerNotes().coverage(4, 4, 0, 0, false).appendTo("answer")).isEqualTo("answer");
    }

    @Test
    void partialCoverage_saysHowMuchAndWhy() {
        assertThat(new AnswerNotes().coverage(3, 4, 1, 0, false).appendTo("answer"))
                .contains("3 of 4 batches")
                .contains("1 batch failed");
        assertThat(new AnswerNotes().coverage(2, 4, 2, 0, false).appendTo("answer"))
                .contains("2 batches failed");
        assertThat(new AnswerNotes().coverage(1, 4, 0, 0, true).appendTo("answer"))
                .contains("cancelled");
    }

    @Test
    void unreadableAttachments_areNotedEvenWhenEveryBatchRan() {
        // Batches are only built from the attachments that could be read, so the batch count alone
        // would say the answer was complete.
        assertThat(new AnswerNotes().coverage(4, 4, 0, 1, false).appendTo("answer"))
                .contains("1 attachment could not be read");
        assertThat(new AnswerNotes().coverage(3, 4, 1, 2, false).appendTo("answer"))
                .contains("3 of 4 batches")
                .contains("2 attachments could not be read");
    }

    @Test
    void severalNotes_readAsOneBlock() {
        final String result = new AnswerNotes()
                .add("The data was processed in batches")
                .coverage(3, 4, 1, 1, false)
                .appendTo("answer");

        assertThat(result).startsWith("answer\n\n---\n*Note: ");
        assertThat(result).endsWith(".*");
        assertThat(count(result, "---")).isEqualTo(1);
        assertThat(count(result, "*Note:")).isEqualTo(1);
        assertThat(result)
                .contains("The data was processed in batches. ")
                .contains("1 attachment could not be read and is not included.*");
    }

    @Test
    void blankNotes_areIgnored() {
        assertThat(new AnswerNotes().add(null).add(" ").appendTo("answer")).isEqualTo("answer");
    }

    private int count(final String text, final String token) {
        int count = 0;
        int idx = text.indexOf(token);
        while (idx >= 0) {
            count++;
            idx = text.indexOf(token, idx + token.length());
        }
        return count;
    }
}
