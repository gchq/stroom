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

package stroom.ai.impl.tools;

import stroom.ai.impl.workspace.SessionWorkspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// A fetch_events read of a stream too large for a client outside Stroom ([LargeResults]): seen in eval case 36 on
/// Haiku over Claude Code, reads of 50 to 100 Events records of 60,000 to 127,000 characters, which the client saved
/// to a file of its own that the agent couldn't read through Stroom's tools. Outlined: the first records whole while
/// they fit, then each of the rest by the keys that find it again (its stream, part, record and, of an Events stream,
/// event id), with how to read them a few at a time. The rest of the result (counts, hint, where to read on) is kept
/// whole.
final class FetchEventsOutline {

    static final String RECORDS = "records";
    static final String NOT_GIVEN = "records_not_given";
    static final String READ_THE_REST = "read_the_rest";

    // Room kept for the note on reading the rest.
    private static final int READ_ROOM = 900;

    private FetchEventsOutline() {
        // Static only.
    }

    /// Gives a read of a stream in outline when it is too large for the caller ([LargeResults#apply]).
    ///
    /// @param caller    Who called the tool.
    /// @param workspace The session's workspace, if it has one.
    /// @param read      What was read: the stream, its part and child type, and whether it is an Events stream.
    /// @param result    The result, changed in place.
    /// @param limit     The most characters it is given whole in.
    /// @return True if it was given in outline.
    static boolean outlineIfLarge(final ToolCaller caller,
                                  final Optional<SessionWorkspace> workspace,
                                  final Read read,
                                  final Map<String, Object> result,
                                  final int limit) {
        return LargeResults.of(FetchEventsTool.NAME, read.key(intOf(result.get("first_record"))))
                .wholeTexts()
                .keep("stream_id", "feed", "type", "first_record", "total_records", "available_child_types",
                        "parts", "part", "header", "records_are", "truncated", "next_first_record",
                        "record_count_capped", "undecodable_bytes", NOT_GIVEN, READ_THE_REST)
                .step(RECORDS, (said, room) -> outline(said, room, read))
                .apply(caller, workspace, result, limit);
    }

    // The first records whole while they fit the room, the rest each by its keys, and how to read them.
    private static boolean outline(final Map<String, Object> result,
                                   final LargeResults.Room room,
                                   final Read read) {
        final List<Object> records = JsonValues.listOrEmpty(result.get(RECORDS));
        if (records.isEmpty()) {
            return false;
        }
        final int first = intOf(result.get("first_record"));
        final List<Map<String, Object>> keys = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            keys.add(keys(read, result, first + i));
        }
        // The room for the records and their keys: the result's, less the rest of it and the note on reading them.
        final int recordsRoom = room.target() - (LargeResults.length(result) - LargeResults.length(records))
                                 - READ_ROOM;
        // Each part's length once, kept as running totals: the records given, the keys of the rest.
        int recordsChars = 0;
        int keysChars = 0;
        final int[] keyChars = new int[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            keyChars[i] = LargeResults.length(keys.get(i));
            keysChars += keyChars[i];
        }
        int given = 0;
        while (given < records.size()) {
            final int withNext = recordsChars + LargeResults.length(records.get(given));
            final int keysLeft = keysChars - keyChars[given];
            if (listLength(withNext, given + 1) + listLength(keysLeft, keys.size() - given - 1) > recordsRoom) {
                break;
            }
            recordsChars = withNext;
            keysChars = keysLeft;
            given++;
        }
        if (given == records.size()) {
            // The records all fit: what is too large is the rest of the result, cut after this.
            return false;
        }
        result.put(RECORDS, new ArrayList<>(records.subList(0, given)));
        result.put(NOT_GIVEN, new ArrayList<>(keys.subList(given, keys.size())));
        result.put(READ_THE_REST, readTheRest(read, room.saved(), first, given, records.size(), records.get(given)));
        return true;
    }

    // A JSON list's length, as LargeResults.length gives it, from its entries' total and count: its brackets and
    // the commas between them.
    private static int listLength(final int entriesChars, final int count) {
        return 2 + entriesChars + Math.max(0, count - 1);
    }

    // The keys that find a record again: its stream, its part (of a stream of several), its record and, of an Events
    // stream, its event id (record n is event n + 1, as the table form and the index number it); of a child read a
    // record a part, its part.
    private static Map<String, Object> keys(final Read read, final Map<String, Object> result, final int record) {
        final Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("stream", read.streamId());
        if (read.childType() != null && result.containsKey("records_are")) {
            keys.put("part", record);
            return keys;
        }
        if (result.containsKey("parts")) {
            keys.put("part", read.part());
        }
        keys.put("record", record);
        if (read.events()) {
            keys.put("event_id", record + 1);
        }
        return keys;
    }

    // How to read the records not given: again from the stream a few at a time, or from the saved file.
    private static String readTheRest(final Read read,
                                      final String saved,
                                      final int first,
                                      final int given,
                                      final int count,
                                      final Object next) {
        final int from = first + given;
        final int last = first + count - 1;
        final int perCall = Math.max(1, given);
        final String again = FetchEventsTool.NAME + " streamId=" + read.streamId()
                             + (read.part() > 0
                ? " part=" + read.part()
                : "")
                             + (read.childType() != null
                ? " childType='" + read.childType() + "'"
                : "")
                             + " firstRecord=" + from + " recordCount=" + perCall;
        final String inFile = "jq (path=" + saved + ", query='.records[" + given + ":" + (given + perCall)
                              + "][]')";
        final String rest = "Records " + from + " to " + last + " (" + (count - given) + ", listed in " + NOT_GIVEN
                            + ") are not shown here: this read is too large to show whole. It is saved whole at "
                            + saved + ", a record a line.";
        if (given == 0) {
            final int characters = LargeResults.length(next);
            return rest + " Record " + from + " alone is too large to show here (" + characters + " characters): "
                   + "read it from the saved file a slice at a time, with jq (path=" + saved + ", query='.records[0]"
                   + " | .[0:1500]', raw=true), or find what you need in it with grep (path=" + saved + ").";
        }
        return rest + " Read them a few at a time: " + again + " (as many as fit here), or " + inFile + " from the "
               + "saved file, the next slice from " + (given + perCall) + ". Read only what you need.";
    }

    private static int intOf(final Object value) {
        return value instanceof final Number number
                ? number.intValue()
                : 0;
    }


    // --------------------------------------------------------------------------------


    /// What a read of a stream was of.
    ///
    /// @param streamId  The stream.
    /// @param part      The part asked for (0 if none).
    /// @param childType The child type read, or null.
    /// @param events    True if the records are an Events stream's own, each an event.
    record Read(long streamId, int part, String childType, boolean events) {

        /// @param firstRecord The first record read.
        /// @return The key the read's result is saved by.
        String key(final int firstRecord) {
            return "stream-" + streamId
                   + (part > 0
                    ? "-part-" + part
                    : "")
                   + (childType != null
                    ? "-" + childType
                    : "")
                   + "-from-" + firstRecord;
        }
    }
}
