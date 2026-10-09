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

package stroom.floormap.client.editor;

import stroom.floormap.client.editor.FloorMapEditorModel.FactEndCheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Builds the messages shown when deleting facts from a point in time (see
/// [FloorMapEditorModel#stageFactEnd]).
///
/// Pure string logic, kept out of the GWT presenter so it is unit-testable on the JVM.
public final class FloorMapFactEndMessages {

    /// The most objects named in one sentence, so a large selection does not produce an
    /// unreadable dialog.
    static final int MAX_NAMED = 5;

    private FloorMapFactEndMessages() {
        // Static helpers only.
    }

    /// The warning shown when none of the requested facts can be deleted from this time.
    ///
    /// @param skipped the keys that cannot be ended, with why, in display order
    /// @return the warning message; never `null`
    public static String nothingToEnd(final Map<String, FactEndCheck> skipped) {
        return "Nothing can be deleted from this time. " + describeSkipped(skipped);
    }

    /// The confirmation asked before ending `endable`.
    ///
    /// It says what is deleted and that earlier versions are kept, warns when later versions
    /// will bring facts back (or that they could not be checked), says how to undo, and lists
    /// any facts that will be left as they are.
    ///
    /// @param endable     the keys that can be ended; must not be empty
    /// @param skipped     the keys that cannot, with why, in display order
    /// @param laterCounts the number of versions after the timeline time for each key that has
    ///         any, or `null` if that could not be found out
    /// @return the confirmation message; never `null`
    public static String confirmation(final List<String> endable,
                                      final Map<String, FactEndCheck> skipped,
                                      final Map<String, Integer> laterCounts) {
        final boolean single = endable.size() == 1;
        final String subject = single
                ? "'" + endable.get(0) + "'"
                : endable.size() + " objects";
        final boolean noLaterVersions = laterCounts != null && laterCounts.isEmpty();
        final StringBuilder message = new StringBuilder()
                .append("Delete ").append(subject)
                .append(noLaterVersions
                        ? " from the current timeline time onwards? "
                        : " from the current timeline time? ")
                .append(single
                        ? "Earlier versions are kept, so it still shows before this time."
                        : "Earlier versions are kept, so they still show before this time.");
        if (laterCounts == null) {
            message.append(single
                    ? " Later versions could not be checked: if it has any, it will come back "
                      + "at the first of them."
                    : " Later versions could not be checked: any object that has them will come "
                      + "back at the first of them.");
        } else if (!laterCounts.isEmpty()) {
            message.append(describeLaterVersions(laterCounts, single));
        }
        message.append(" To undo, delete the 'Deleted' version from the Time List.");
        if (!skipped.isEmpty()) {
            message.append(" ").append(skipped.size()).append(" will be left as they are. ")
                    .append(describeSkipped(skipped));
        }
        return message.toString();
    }

    /// Warns that later versions will bring facts back, and says how to stop that.
    private static String describeLaterVersions(final Map<String, Integer> laterCounts,
                                                final boolean single) {
        final List<String> phrases = new ArrayList<>();
        for (final Map.Entry<String, Integer> entry : laterCounts.entrySet()) {
            final int count = entry.getValue();
            phrases.add("'" + entry.getKey() + "' has " + count
                        + (count == 1 ? " later version" : " later versions"));
        }
        if (single) {
            return " However, " + phrases.get(0) + ", which will bring it back at the first of "
                   + "them. To keep it deleted, delete or edit those versions in the Time List.";
        }
        return " However, " + laterCounts.size() + " of them have later versions, which will "
               + "bring them back: " + joinNamed(phrases) + " To keep them deleted, delete or "
               + "edit those versions in the Time List.";
    }

    /// Says why each of `skipped` cannot be deleted from this time.
    private static String describeSkipped(final Map<String, FactEndCheck> skipped) {
        final List<String> phrases = new ArrayList<>();
        for (final Map.Entry<String, FactEndCheck> entry : skipped.entrySet()) {
            phrases.add("'" + entry.getKey() + "' " + entry.getValue().getReason());
        }
        return joinNamed(phrases);
    }

    /// Joins per-object phrases into one sentence, naming at most [#MAX_NAMED].
    private static String joinNamed(final List<String> phrases) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < phrases.size() && i < MAX_NAMED; i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(phrases.get(i));
        }
        if (phrases.size() > MAX_NAMED) {
            sb.append(" (and ").append(phrases.size() - MAX_NAMED).append(" more)");
        }
        return sb.append(".").toString();
    }
}
