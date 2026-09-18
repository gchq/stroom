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

package stroom.widget.dropdowntree.client.view;

import java.util.function.Consumer;

/**
 * Decides when a quick filter's text has been "used" and should go into history.
 * <p>
 * A use is a <em>commit</em> (Enter, or leaving the box) of text the server has <em>accepted</em>.
 * Neither alone is enough: the debounced as-you-type queries are accepted but not committed, so
 * recording on acceptance would store {@code a}, {@code ab}, {@code abc}; and a commit of text the
 * server then rejects must not be stored either. The two events arrive in either order, so this
 * remembers whichever came first and records when the other one matches it.
 * <p>
 * Plain Java with no GWT types so that it can be unit tested; {@link QuickFilter} owns one and
 * feeds it events.
 */
final class QuickFilterHistoryTracker {

    private final Consumer<String> recorder;

    /** The text the server most recently accepted, or null after a rejection. */
    private String acceptedText;
    /** Committed, but the verdict on it has not arrived yet. */
    private String pendingRecord;
    /** The last text handed to the recorder, so a repeated commit is not a repeated record. */
    private String lastRecorded;

    QuickFilterHistoryTracker(final Consumer<String> recorder) {
        this.recorder = recorder;
    }

    /**
     * The user committed the box's current text.
     */
    void commit(final String currentText) {
        final String text = trim(currentText);
        if (text.isEmpty() || text.equals(lastRecorded)) {
            pendingRecord = null;
        } else if (text.equals(acceptedText)) {
            record(text);
        } else {
            pendingRecord = text;
        }
    }

    /**
     * The server answered the query that was sent with {@code sentText}.
     */
    void verdict(final String sentText, final boolean accepted) {
        if (accepted) {
            acceptedText = trim(sentText);
            if (pendingRecord != null && pendingRecord.equals(acceptedText)) {
                record(pendingRecord);
            }
        } else {
            acceptedText = null;
            pendingRecord = null;
        }
    }

    /**
     * The user chose {@code text} from the history itself. It was accepted once already, so it is
     * recorded straight away rather than waiting to be told so again.
     */
    void chosen(final String text) {
        record(trim(text));
    }

    /**
     * The box was cleared. Forgets what was accepted: clearing is not a verdict on anything, and
     * the old text must not be treated as pre-approved if it is typed again.
     */
    void cleared() {
        acceptedText = null;
        pendingRecord = null;
    }

    private void record(final String text) {
        pendingRecord = null;
        lastRecorded = text;
        recorder.accept(text);
    }

    private static String trim(final String text) {
        return text == null
                ? ""
                : text.trim();
    }
}
