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

package stroom.ai.impl.tools.workspace;

import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.shared.MermaidGantt;
import stroom.ai.shared.MermaidGanttParser;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/// Writes rows of times as a Mermaid `gantt` block of the subset [MermaidGanttParser] reads (what the chat draws),
/// the same layout every time: a bar from each row's start to its end, a marker where it has none, a section per
/// lane (or per day), and every value from the data stripped of what Mermaid reads as syntax. Never `click`,
/// callbacks or `%%{init}%%`. Large inputs are folded to stay readable, saying how: consecutive bars of one label
/// in a lane merged, bars under a minimum length left out, the top labels kept and the rest shown as "Other", and
/// at most [#MAX_BARS] bars and markers. Every block written is read back by the parser (a round trip).
final class GanttCharts {

    /// The most labels kept, each with its own colour (the chat's chart has eight); the rest are "Other".
    static final int MAX_TOP = 8;
    /// The most bars and markers in a chart.
    static final int MAX_BARS = 200;
    /// The most sections (lanes) in a chart; the rest are "Other".
    static final int MAX_SECTIONS = 20;
    /// The longest a label or section name is written.
    static final int MAX_LABEL_CHARS = 40;
    /// What the labels, and lanes, past the top ones are shown as.
    static final String OTHER = "Other";
    /// What a row with no label, or no lane, is shown as.
    static final String NONE = "(none)";
    /// The date format the block's times are written in.
    static final String DATE_FORMAT = "YYYY-MM-DD HH:mm:ss";
    // What goes before a label Mermaid would read as a statement or a date, so it is read as a task's name.
    private static final String NEUTRAL_PREFIX = "\u00b7 ";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE", Locale.ROOT);
    private static final String INDENT = "    ";
    private static final int MAX_NAMED = 5;

    private GanttCharts() {
        // Static only.
    }

    /// @param rows    The rows to draw.
    /// @param options How to draw and fold them.
    /// @return The block, with what was folded.
    /// @throws IllegalStateException if the block written doesn't read back (a bug, never the data's doing).
    static Chart write(final List<Row> rows, final Options options) {
        final List<String> folded = new ArrayList<>();
        final List<Item> items = labelled(rows, options, folded);
        final Map<String, List<Item>> bySection = sections(items, options, folded);
        final Map<String, List<Item>> merged = new LinkedHashMap<>();
        final int[] merges = {0};
        final int[] shortOnes = {0};
        bySection.forEach((section, sectionItems) -> merged.put(section,
                merge(sectionItems, options, merges, shortOnes)));
        if (merges[0] > 0) {
            folded.add("Merged " + merges[0] + " bar(s) into the bar before of the same label in the same lane "
                       + "(gaps of up to " + human(options.mergeSeconds()) + ").");
        }
        if (shortOnes[0] > 0) {
            folded.add("Left out " + shortOnes[0] + " bar(s) shorter than " + human(options.minSeconds()) + ".");
        }
        capped(merged, folded);

        final String text = text(merged, options);
        check(text, merged);
        int bars = 0;
        int markers = 0;
        for (final List<Item> sectionItems : merged.values()) {
            for (final Item item : sectionItems) {
                if (item.isMarker()) {
                    markers++;
                } else {
                    bars++;
                }
            }
        }
        return new Chart(text, folded, bars, markers, merged.size());
    }

    /// @param text A value from the data, or null.
    /// @return It as a Mermaid gantt chart may hold it in a task's name, a section's name or the title: Mermaid's
    /// syntax characters ([MermaidGanttParser#SYNTAX_CHARACTERS], `:`, `,`) and line breaks taken out, its spaces
    /// collapsed, and cut to [#MAX_LABEL_CHARS]. Empty if nothing is left.
    static String clean(final String text) {
        if (text == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c) || c == '\u2028' || c == '\u2029'
                || c == '\u0085') {
                space = !sb.isEmpty();
            } else if (MermaidGanttParser.SYNTAX_CHARACTERS.indexOf(c) < 0 && c != ':' && c != ',') {
                if (space) {
                    sb.append(' ');
                    space = false;
                }
                sb.append(c);
            }
        }
        final String cleaned = sb.toString();
        return cleaned.length() > MAX_LABEL_CHARS
                ? cleaned.substring(0, MAX_LABEL_CHARS - 1).strip() + "\u2026"
                : cleaned;
    }

    /// @param text A value from the data, or null.
    /// @return It as a task's name: [#clean(String)], [#NONE] if nothing is left, and prefixed if Mermaid would
    /// read it as a statement or a date (e.g. one starting "click" or "2026-11-23"), so it is read as a name.
    static String taskName(final String text) {
        final String cleaned = clean(text);
        if (cleaned.isEmpty()) {
            return NONE;
        }
        return MermaidGanttParser.startsLikeAStatement(cleaned)
                ? NEUTRAL_PREFIX + cleaned
                : cleaned;
    }

    /// @param seconds A length of time in seconds.
    /// @return It for people, e.g. "90 s", "5 min", "2 h".
    static String human(final long seconds) {
        if (seconds != 0 && seconds % 3_600 == 0) {
            return seconds / 3_600 + " h";
        } else if (seconds != 0 && seconds % 60 == 0) {
            return seconds / 60 + " min";
        }
        return seconds + " s";
    }

    // The rows as items, each label past the top ones as Other. Labels of bars are kept by the most time (bars'
    // lengths), then the most rows, then by name, so the same rows always keep the same labels; labels only of
    // markers (e.g. a file copied), which have no time, by the most rows, then by name, up to half the labels kept
    // where there are bars too, so a chart of long sessions keeps its markers' labels.
    private static List<Item> labelled(final List<Row> rows, final Options options, final List<String> folded) {
        final Map<String, long[]> totals = new HashMap<>();
        for (final Row row : rows) {
            final long[] total = totals.computeIfAbsent(taskName(row.label()), label -> new long[2]);
            total[0] += row.end() == null
                    ? 0
                    : row.end() - row.start();
            total[1]++;
        }
        final List<String> ranked = ranked(totals);
        final List<String> barLabels = ranked.stream().filter(label -> totals.get(label)[0] > 0).toList();
        final List<String> markerLabels = ranked.stream().filter(label -> totals.get(label)[0] == 0).toList();
        final int top = Math.max(1, Math.min(options.top(), MAX_TOP));
        final int markerShare = barLabels.isEmpty()
                ? top
                : Math.min(markerLabels.size(), top / 2);
        final int barSlots = Math.min(barLabels.size(), top - markerShare);
        final int markerSlots = Math.min(markerLabels.size(), top - barSlots);
        final List<String> kept = new ArrayList<>(barLabels.subList(0, barSlots));
        kept.addAll(markerLabels.subList(0, markerSlots));
        if (ranked.size() > kept.size()) {
            final List<String> others = new ArrayList<>(barLabels.subList(barSlots, barLabels.size()));
            others.addAll(markerLabels.subList(markerSlots, markerLabels.size()));
            folded.add("Kept " + kept.size() + " label(s) (those with the most time, and of markers the most "
                       + "rows); the other " + others.size() + " (" + named(others) + ") are shown as " + OTHER
                       + ".");
        }
        final List<Item> items = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            final String label = taskName(row.label());
            items.add(new Item(kept.contains(label)
                    ? label
                    : OTHER, lane(row, options), row.start(), row.end() == null
                    ? row.start()
                    : row.end()));
        }
        return items;
    }

    // The items by section, in the order the sections start (then by name), the lanes past the most a chart has
    // (those with the least time) as Other.
    private static Map<String, List<Item>> sections(final List<Item> items,
                                                    final Options options,
                                                    final List<String> folded) {
        final Map<String, long[]> totals = new HashMap<>();
        for (final Item item : items) {
            final long[] total = totals.computeIfAbsent(item.section(), section -> new long[2]);
            total[0] += item.end() - item.start();
            total[1]++;
        }
        final List<String> ranked = ranked(totals);
        final List<String> kept = ranked.subList(0, Math.min(MAX_SECTIONS, ranked.size()));
        if (ranked.size() > MAX_SECTIONS) {
            final List<String> others = ranked.subList(MAX_SECTIONS, ranked.size());
            folded.add("Kept the " + MAX_SECTIONS + " " + (options.byLane()
                    ? "lanes"
                    : "days") + " with the most time; the other " + others.size() + " (" + named(others)
                       + ") are in the section " + OTHER + ".");
        }
        final List<Item> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingLong(Item::start).thenComparingLong(Item::end)
                .thenComparing(Item::label));
        final Map<String, List<Item>> bySection = new LinkedHashMap<>();
        for (final Item item : sorted) {
            final String section = kept.contains(item.section())
                    ? item.section()
                    : OTHER;
            bySection.computeIfAbsent(section, name -> new ArrayList<>()).add(item.in(section));
        }
        return bySection;
    }

    // A section's items in order with each bar that follows a bar of its label (no other bar between them) within
    // the gap merged into it, the same marker at the same second once, then the bars under the minimum left out.
    private static List<Item> merge(final List<Item> items,
                                    final Options options,
                                    final int[] merges,
                                    final int[] shortOnes) {
        final List<Item> kept = new ArrayList<>();
        int lastBar = -1;
        Item lastMarker = null;
        final long gap = options.mergeSeconds() * 1_000;
        for (final Item item : items) {
            if (item.isMarker()) {
                if (lastMarker != null && lastMarker.label().equals(item.label())
                    && lastMarker.start() / 1_000 == item.start() / 1_000) {
                    continue;
                }
                kept.add(item);
                lastMarker = item;
            } else if (lastBar >= 0 && kept.get(lastBar).label().equals(item.label())
                       && item.start() - kept.get(lastBar).end() <= gap) {
                final Item before = kept.get(lastBar);
                kept.set(lastBar, before.endingAt(Math.max(before.end(), item.end())));
                merges[0]++;
            } else {
                kept.add(item);
                lastBar = kept.size() - 1;
            }
        }
        final long shortest = options.minSeconds() * 1_000;
        final List<Item> result = new ArrayList<>();
        for (final Item item : kept) {
            if (!item.isMarker() && item.end() - item.start() < shortest) {
                shortOnes[0]++;
            } else {
                result.add(item);
            }
        }
        return result;
    }

    // At most MAX_BARS: the shortest bars left out first, then the latest markers.
    private static void capped(final Map<String, List<Item>> bySection, final List<String> folded) {
        final List<Item> all = bySection.values().stream().flatMap(List::stream).toList();
        int over = all.size() - MAX_BARS;
        if (over <= 0) {
            return;
        }
        final List<Item> bars = all.stream()
                .filter(item -> !item.isMarker())
                .sorted(Comparator.comparingLong((Item item) -> item.end() - item.start())
                        .thenComparingLong(Item::start))
                .toList();
        final List<Item> leftOut = new ArrayList<>(bars.subList(0, Math.min(over, bars.size())));
        if (!leftOut.isEmpty()) {
            final long longest = leftOut.getLast().end() - leftOut.getLast().start();
            folded.add("Left out the " + leftOut.size() + " shortest bar(s) (each " + human(longest / 1_000)
                       + " or shorter) to keep to " + MAX_BARS + ".");
            over -= leftOut.size();
        }
        if (over > 0) {
            final List<Item> markers = all.stream()
                    .filter(Item::isMarker)
                    .sorted(Comparator.comparingLong(Item::start).reversed())
                    .limit(over)
                    .toList();
            folded.add("Left out the " + markers.size() + " latest marker(s) to keep to " + MAX_BARS + ".");
            leftOut.addAll(markers);
        }
        bySection.values().forEach(items -> items.removeIf(item -> leftOut.stream()
                .anyMatch(out -> out == item)));
        bySection.values().removeIf(List::isEmpty);
    }

    private static String text(final Map<String, List<Item>> bySection, final Options options) {
        final StringBuilder sb = new StringBuilder("gantt\n");
        final String title = clean(Objects.requireNonNullElse(options.title(), "Timeline"));
        sb.append(INDENT).append("title ").append(title.isEmpty()
                ? "Timeline"
                : title).append(" (times in ").append(clean(options.zone().id())).append(")\n");
        sb.append(INDENT).append("dateFormat ").append(DATE_FORMAT).append('\n');
        sb.append(INDENT).append("axisFormat ").append(axisFormat(bySection)).append('\n');
        bySection.forEach((section, items) -> {
            sb.append(INDENT).append("section ").append(section).append('\n');
            for (final Item item : items) {
                final long start = item.start() / 1_000;
                sb.append(INDENT).append(item.label()).append(" :");
                if (item.isMarker()) {
                    sb.append("milestone, ").append(time(start, options)).append(", 0d\n");
                } else {
                    // A bar shorter than a second is drawn a second long, not as a marker.
                    final long end = Math.max(start + 1, item.end() / 1_000);
                    sb.append(time(start, options)).append(", ").append(time(end, options)).append('\n');
                }
            }
        });
        return sb.toString();
    }

    // The ticks' format: times of day over a day or less, with the date over a week or less, else dates.
    private static String axisFormat(final Map<String, List<Item>> bySection) {
        final long from = bySection.values().stream().flatMap(List::stream).mapToLong(Item::start).min().orElse(0);
        final long to = bySection.values().stream().flatMap(List::stream).mapToLong(Item::end).max().orElse(0);
        final Duration span = Duration.ofMillis(to - from);
        if (span.compareTo(Duration.ofDays(1)) <= 0) {
            return "%H:%M";
        } else if (span.compareTo(Duration.ofDays(7)) <= 0) {
            return "%d %b %H:%M";
        }
        return "%d %b";
    }

    private static String time(final long epochSecond, final Options options) {
        return TIME.format(Instant.ofEpochSecond(epochSecond).atZone(options.zone().zone()));
    }

    // Reads the block back: it must be of the subset, with every section and task written.
    private static void check(final String text, final Map<String, List<Item>> bySection) {
        final MermaidGantt gantt;
        try {
            gantt = MermaidGanttParser.parse(text);
        } catch (final IllegalArgumentException e) {
            throw new IllegalStateException("The gantt chart written doesn't read back: " + e.getMessage(), e);
        }
        final int written = bySection.values().stream().mapToInt(List::size).sum();
        if (gantt.taskCount() != written || gantt.getSections().size() != bySection.size()) {
            throw new IllegalStateException("The gantt chart written reads back as " + gantt.taskCount()
                                            + " tasks in " + gantt.getSections().size() + " sections, not "
                                            + written + " in " + bySection.size());
        }
    }

    private static String lane(final Row row, final Options options) {
        if (options.byLane()) {
            final String cleaned = clean(row.lane());
            return cleaned.isEmpty()
                    ? NONE
                    : cleaned;
        }
        final ZonedDateTime start = Instant.ofEpochMilli(row.start()).atZone(options.zone().zone());
        return DAY.format(start);
    }

    // Names with the most time first, then the most rows, then by name.
    private static List<String> ranked(final Map<String, long[]> totals) {
        return totals.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> entry) -> -entry.getValue()[0])
                        .thenComparingLong(entry -> -entry.getValue()[1])
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static String named(final List<String> names) {
        final String listed = names.stream().limit(MAX_NAMED).collect(Collectors.joining(", "));
        return names.size() > MAX_NAMED
                ? listed + " and " + (names.size() - MAX_NAMED) + " more"
                : listed;
    }


    // --------------------------------------------------------------------------------


    /// A row to draw.
    ///
    /// @param start When it starts, in epoch milliseconds.
    /// @param end   When it ends, in epoch milliseconds, not before its start; null (or its start) for a marker.
    /// @param label What it is (e.g. an application), its bar's name and colour.
    /// @param lane  The lane it is in (e.g. a host), or null.
    record Row(long start, Long end, String label, String lane) {

    }


    // --------------------------------------------------------------------------------


    /// How to draw and fold the rows.
    ///
    /// @param title        What the chart shows, or null; the time zone is named after it.
    /// @param zone         The time zone its times are shown in.
    /// @param byLane       True for a section per lane, false for one per day.
    /// @param top          The most labels kept (at most [#MAX_TOP]); the rest are "Other".
    /// @param minSeconds   Bars shorter than this, once merged, are left out.
    /// @param mergeSeconds A bar that follows one of its label in its lane, no other bar between, within this many
    ///                     seconds of its end is merged into it.
    record Options(String title, ToolTimeZone zone, boolean byLane, int top, long minSeconds, long mergeSeconds) {

    }


    // --------------------------------------------------------------------------------


    /// A chart written.
    ///
    /// @param mermaid  The block's content (without its fences).
    /// @param folded   What was folded to keep it readable, a sentence each.
    /// @param bars     How many bars it has.
    /// @param markers  How many markers it has.
    /// @param sections How many sections it has.
    record Chart(String mermaid, List<String> folded, int bars, int markers, int sections) {

    }


    // --------------------------------------------------------------------------------


    // A bar or marker as it is drawn: its label (or Other) and section.
    private record Item(String label, String section, long start, long end) {

        private boolean isMarker() {
            return end == start;
        }

        private Item in(final String otherSection) {
            return new Item(label, otherSection, start, end);
        }

        private Item endingAt(final long otherEnd) {
            return new Item(label, section, start, otherEnd);
        }
    }
}
