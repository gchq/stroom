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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.sample.RecordSplitter;
import stroom.ai.impl.tools.sample.RecordStream;
import stroom.ai.impl.tools.sample.Shapes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// How far survey_feed reads into each part of a stream it is to read whole (a sample's streams, or a feed's last):
/// a part bigger than a call's budget for it is read to the budget, then the user says whether to read deeper and
/// how far (asked for by the user: seen in testing, a 25,000-record stream surveyed to its 10,000th record, its two
/// rarest kinds of event, near its end, never seen nor classified). Reading deeper goes on from where the first read
/// stopped, in order, skipping the records its windows read further in, so no record is counted twice.
final class SurveyDepth {

    /// The survey state's key for each part's depth, by `stream:part`.
    static final String DEPTH = "depth";
    /// What the user chose for a part read in part: to stop there.
    static final String STOP = "stop";
    /// The records a part is read further by when the user asks for more without saying how many.
    static final int MORE = 10_000;
    /// The most records left in the parts read in part for which reading them to the end is offered.
    static final long AFFORDABLE = 500_000;
    /// The answer suggested for stopping.
    static final String STOP_HERE = "Stop here";
    /// The question's title.
    static final String QUESTION = "Read deeper into them, and how far?";

    // Records read from the store at a time when reading deeper.
    private static final int BATCH = 10_000;
    // The most characters of record data a batch holds.
    private static final int BATCH_CHARS = 20_000_000;
    // A number of records the user typed, with commas or a k: 5,000, 5000, 5k.
    private static final Pattern NUMBER = Pattern.compile("(\\d[\\d,]*)\\s*(k)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TO_THE_END = Pattern.compile("(?i)\\b(end|all|whole|rest|everything)\\b");
    private static final Pattern STOPPING = Pattern.compile("(?i)^(stop|no|none|enough|that'?s enough)\\b");
    private static final Pattern ABSOLUTE = Pattern.compile("(?i)\\b(to|up to|first)\\s+\\d");

    private SurveyDepth() {
        // Static only.
    }

    /// @param answer What the user answered: a suggestion, or their own words ("another 5,000", "to the end").
    /// @return What they chose; reading [#MORE] more when their words name no number nor the end.
    static Decision decide(final String answer) {
        final String text = answer.strip().toLowerCase(Locale.ROOT);
        if (STOPPING.matcher(text).find()) {
            return new Decision(true, 0, false, -1);
        }
        if (TO_THE_END.matcher(text).find()) {
            return new Decision(false, 0, true, -1);
        }
        final Matcher number = NUMBER.matcher(text);
        if (number.find()) {
            long count = Long.parseLong(number.group(1).replace(",", ""));
            if (number.group(2) != null) {
                count *= 1000;
            }
            return ABSOLUTE.matcher(text).find()
                    ? new Decision(false, 0, false, count)
                    : new Decision(false, count, false, -1);
        }
        return new Decision(false, MORE, false, -1);
    }

    /// @param parts The parts read in part.
    /// @return The answers to suggest: stop, read [#MORE] more of each, and, when the parts have few enough left,
    /// read them to the end.
    static List<String> suggestions(final List<Part> parts) {
        final List<String> out = new ArrayList<>(List.of(STOP_HERE, "Read " + StreamReader.thousands(MORE) + " more"
                                                                    + (parts.size() == 1
                ? ""
                : " of each")));
        final long left = parts.stream().mapToLong(Part::left).sum();
        if (left <= AFFORDABLE) {
            final long bytes = parts.stream()
                    .mapToLong(part -> part.estimate() == 0
                            ? 0
                            : part.size() * part.left() / part.estimate())
                    .sum();
            out.add("Read to the end (about " + StreamReader.thousands(left) + " records, " + megabytes(bytes) + ")");
        }
        return out;
    }

    /// @param feed  The feed.
    /// @param parts The parts read in part.
    /// @return The form's message: plain text, a line a part (the first few), how far each was read.
    static String intro(final String feed, final List<Part> parts) {
        final List<String> lines = new ArrayList<>();
        lines.add("Survey of feed '" + feed + "': " + parts.size() + " part(s) read only in part.");
        for (final Part part : parts.subList(0, Math.min(5, parts.size()))) {
            lines.add("Stream " + part.stream() + (part.part() == 0
                    ? ""
                    : " part " + (part.part() + 1)) + ": " + StreamReader.thousands(part.readTo()) + " of about "
                      + StreamReader.thousands(part.estimate()) + " records read.");
        }
        if (parts.size() > 5) {
            lines.add("And " + (parts.size() - 5) + " more.");
        }
        lines.add("Kinds of event further in are only found by reading deeper.");
        return String.join("\n", lines);
    }

    /// Reads a part further, in order, from where it was read to, up to how far the user asked, skipping the records
    /// its windows read: their shapes added, each where it is.
    ///
    /// @param reader   Reads the stream.
    /// @param shapes   The shapes, added to.
    /// @param part     The part.
    /// @param timeIsUp Whether the call's time is nearly up.
    /// @return The part as read now, the records read and the shapes new among them.
    static Deeper readDeeper(final StreamReader reader,
                             final Shapes shapes,
                             final Part part,
                             final BooleanSupplier timeIsUp) {
        final long target = part.target();
        long readTo = part.readTo();
        boolean whole = part.whole();
        long records = 0;
        int newShapes = 0;
        final Character quote = RecordSplitter.DELIMITED.equals(part.format())
                ? '"'
                : null;
        while (!whole && readTo < target && !timeIsUp.getAsBoolean()) {
            final int count = (int) Math.min(BATCH, target - readTo);
            final RecordStream.Read read = reader.rawRecords(part.stream(), part.part(), quote,
                    part.header() != null, readTo, count, BATCH_CHARS);
            if (read.records().isEmpty()) {
                whole = read.ended();
                break;
            }
            final RecordSplitter.Chunk chunk = RecordSplitter.splitAs(part.format(), (part.header() == null
                    ? ""
                    : part.header() + "\n") + String.join("\n", read.records()), read.records().size() + 1, false);
            if (!chunk.format().equals(part.format())) {
                // Read otherwise than the part's start: not counted as its kinds.
                break;
            }
            for (int index = 0; index < chunk.records().size(); index++) {
                if (inWindows(part.windows(), readTo + index)) {
                    continue;
                }
                if (shapes.add(chunk, index, part.stream(), part.part(), readTo)) {
                    newShapes++;
                }
                records++;
            }
            readTo += read.records().size();
            whole = read.ended();
            if (read.timedOut()) {
                break;
            }
        }
        return new Deeper(part.read(readTo, whole), records, newShapes);
    }

    // Whether the record (from 0) is one a window read.
    private static boolean inWindows(final List<List<Long>> windows, final long record) {
        for (final List<Long> window : windows) {
            if (record >= window.get(0) && record < window.get(0) + window.get(1)) {
                return true;
            }
        }
        return false;
    }

    private static String megabytes(final long bytes) {
        return bytes < 1_000_000
                ? Math.max(1, bytes / 1000) + " KB"
                : String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0);
    }


    // --------------------------------------------------------------------------------


    /// One part read in part: how far it has been read, the windows read further in, and what the user chose.
    ///
    /// @param stream   The stream.
    /// @param part     The part, from 0.
    /// @param readTo   The records read in order from its start (the next to read is this one, from 0).
    /// @param windows  The records the windows read further in, each `[first, count]`.
    /// @param header   The header line its records follow, or null.
    /// @param format   The format its records were read as.
    /// @param size     Its size in bytes.
    /// @param estimate About how many records it holds.
    /// @param whole    Whether it has been read to its end.
    /// @param choice   What the user chose: [#STOP], or how far to read (a record count, -1 to the end), or null.
    record Part(long stream,
                int part,
                long readTo,
                List<List<Long>> windows,
                String header,
                String format,
                long size,
                long estimate,
                boolean whole,
                String choice) {

        /// @return The part's key in the survey state.
        String key() {
            return stream + ":" + part;
        }

        /// @return Whether the user chose to stop reading it.
        boolean stopped() {
            return STOP.equals(choice);
        }

        /// @return How far the user asked for it to be read: a record count, Long.MAX_VALUE to its end, or -1 when
        /// they haven't (or chose to stop).
        long target() {
            if (choice == null || stopped()) {
                return -1;
            }
            final long target = Long.parseLong(choice);
            return target < 0
                    ? Long.MAX_VALUE
                    : target;
        }

        /// @return Whether it is settled: read whole, or the user chose to stop.
        boolean settled() {
            return whole || stopped();
        }

        /// @return About how many records it has left to read.
        long left() {
            return Math.max(0, estimate - readTo);
        }

        Part withChoice(final String chosen) {
            return new Part(stream, part, readTo, windows, header, format, size, estimate, whole, chosen);
        }

        Part read(final long readToNow, final boolean wholeNow) {
            return new Part(stream, part, readToNow, windows, header, format, size, Math.max(estimate, readToNow),
                    wholeNow, choice);
        }

        /// @return It as kept in the survey state, and shown in the result.
        Map<String, Object> toMap() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("stream", stream);
            out.put("part", part);
            out.put("records_read", readTo);
            out.put("about", estimate);
            out.put("whole", whole);
            out.put("windows", windows);
            out.put("header", header);
            out.put("format", format);
            out.put("bytes", size);
            if (choice != null) {
                out.put("choice", choice);
            }
            return out;
        }

        /// @param map A part as [#toMap()] wrote it.
        /// @return The part.
        static Part fromMap(final Map<String, Object> map) {
            final List<List<Long>> windows = new ArrayList<>();
            JsonValues.listOrEmpty(map.get("windows")).forEach(window -> {
                final List<?> pair = (List<?>) window;
                windows.add(List.of(((Number) pair.get(0)).longValue(), ((Number) pair.get(1)).longValue()));
            });
            return new Part(((Number) map.get("stream")).longValue(), ((Number) map.get("part")).intValue(),
                    ((Number) map.get("records_read")).longValue(), windows, (String) map.get("header"),
                    (String) map.get("format"), ((Number) map.getOrDefault("bytes", 0)).longValue(),
                    ((Number) map.getOrDefault("about", 0)).longValue(), Boolean.TRUE.equals(map.get("whole")),
                    (String) map.get("choice"));
        }
    }

    /// What the user chose: to stop, or to read further (by a number of records, or to the end).
    ///
    /// @param stop  True to stop.
    /// @param more  Records more to read in each part (when not stopping nor reading to the end).
    /// @param toEnd True to read to the end.
    /// @param upTo  A record count to read each part up to, given as such ("up to 30,000"), or -1.
    record Decision(boolean stop, long more, boolean toEnd, long upTo) {

        /// @param part A part read in part.
        /// @return How far to read it, as [Part#choice()] keeps it.
        String choiceFor(final Part part) {
            if (stop) {
                return STOP;
            }
            if (toEnd) {
                return "-1";
            }
            return String.valueOf(upTo >= 0
                    ? Math.max(part.readTo(), upTo)
                    : part.readTo() + more);
        }
    }

    /// A part read deeper.
    ///
    /// @param part      The part as read now.
    /// @param records   The records read.
    /// @param newShapes The shapes new among them.
    record Deeper(Part part, long records, int newShapes) {

    }
}
