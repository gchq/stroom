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

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;

/**
 * What the reader needs to know about an answer that does not cover everything it was asked about.
 * A partial answer is worth having, but only if it is not read as a whole one.
 * <p>
 * The notes are gathered as sentences and rendered as a single block, because an answer that trails
 * off into three separate notes reads like an apology.
 * </p>
 */
class AnswerNotes {

    private final List<String> notes = new ArrayList<>();

    /**
     * @param note A sentence, capitalised, with no full stop - one is added when the notes are joined.
     */
    AnswerNotes add(final String note) {
        if (NullSafe.isNonBlankString(note)) {
            notes.add(note.strip());
        }
        return this;
    }

    /**
     * Says how much of the data the answer covers, when it does not cover all of it.
     *
     * @param contributed           Batches that produced something.
     * @param totalBatches          Batches the readable data was split into.
     * @param failedBatches         Batches that failed or answered with nothing.
     * @param unreadableAttachments Attachments that could not be read, so are not in the batch count.
     * @param cancelled             Whether the user stopped the analysis part way through.
     */
    AnswerNotes coverage(final int contributed,
                         final int totalBatches,
                         final int failedBatches,
                         final int unreadableAttachments,
                         final boolean cancelled) {
        if (contributed < totalBatches) {
            final StringBuilder sb = new StringBuilder("This answer covers ")
                    .append(contributed)
                    .append(" of ")
                    .append(totalBatches)
                    .append(" batches of the data");
            if (cancelled) {
                sb.append(", as the analysis was cancelled before the rest were processed");
            } else if (failedBatches > 0) {
                sb.append(", as ")
                        .append(failedBatches)
                        .append(failedBatches == 1
                                ? " batch failed"
                                : " batches failed");
            }
            add(sb.toString());
        }

        if (unreadableAttachments > 0) {
            add(unreadableAttachments == 1
                    ? "1 attachment could not be read and is not included"
                    : unreadableAttachments + " attachments could not be read and are not included");
        }

        return this;
    }

    /**
     * @return The answer with a single note block appended, or the answer as it stands if there is
     * nothing the reader needs to know.
     */
    String appendTo(final String answer) {
        return notes.isEmpty()
                ? answer
                : answer + "\n\n---\n*Note: " + String.join(". ", notes) + ".*";
    }

    @Override
    public String toString() {
        return notes.toString();
    }
}
