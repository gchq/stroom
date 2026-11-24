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

package stroom.ai.impl.tools.timeline;

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A timeline skill's `## Event chains` section: what was learned of each source, so the next run applies it
/// rather than infers it again (before the schema's pairs and inference). A line an entry:
///
/// ```markdown
/// ## Event chains
/// - Proxy: pair Tunnel-Up -> Tunnel-Down by Session/Id
/// - Endpoint: pair Process/Execute -> Process/Terminate by ProcessId label Command
/// - Windows: Lock-Workstation is idle
/// ```
///
/// A pair's `by` is the key path the two are matched by (none: the host alone), its `label` the key path that labels
/// its spans; a type's role is `active` (active from here), `idle` (idle from here), `open`, `close`, `action` or
/// `ignore` (left out of the timeline). The source is an index's name (or `any`).
public final class EventChains {

    /// The section's heading, as a skill's sections are keyed (lower case).
    public static final String SECTION = "event chains";
    /// The roles a line may give a type.
    public static final List<String> ROLES = List.of("active", "idle", "open", "close", "action", "ignore");
    /// The source of a line for every source.
    public static final String ANY = "any";
    private static final Pattern PAIR = Pattern.compile(
            "^\\s*[-*]\\s*(.+?):\\s*pair\\s+(.+?)\\s*->\\s*(.+?)(?:\\s+by\\s+(\\S+))?(?:\\s+label\\s+(\\S+))?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ROLE = Pattern.compile(
            "^\\s*[-*]\\s*(.+?):\\s*(.+?)\\s+is\\s+(active|idle|open|close|action|ignore)\\b.*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*]\\s+\\S.*$");
    private static final Pattern SECTION_HEADING = Pattern.compile("^##\\s+event chains\\s*#*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_SECTION = Pattern.compile("^##?\\s+\\S");

    private EventChains() {
        // Static only.
    }

    /// @param section The section's text (below its heading), or null.
    /// @return Its entries, and its bullet lines that aren't one.
    public static Parsed parse(final String section) {
        final List<Entry> entries = new ArrayList<>();
        final List<String> unread = new ArrayList<>();
        for (final String line : NullSafe.string(section).split("\\R")) {
            final String plain = line.replace("`", "");
            final Matcher pair = PAIR.matcher(plain);
            final Matcher role = ROLE.matcher(plain);
            if (pair.matches()) {
                entries.add(Entry.pair(pair.group(1).strip(), pair.group(2).strip(), pair.group(3).strip(),
                        pair.group(4), pair.group(5)));
            } else if (role.matches()) {
                entries.add(Entry.role(role.group(1).strip(), role.group(2).strip(),
                        role.group(3).toLowerCase(Locale.ROOT)));
            } else if (BULLET.matcher(line).matches()) {
                unread.add(line.strip());
            }
        }
        return new Parsed(entries, unread);
    }

    /// The skill with entries added to its `## Event chains` section (made, before its `## Output`, or at its end,
    /// if it has none); a line it has already is not added again.
    ///
    /// @param skillMarkdown One skill's text, from its heading.
    /// @param entries       The entries to add.
    /// @return Its text with them.
    public static String withEntries(final String skillMarkdown, final List<Entry> entries) {
        final List<String> lines = new ArrayList<>(List.of(NullSafe.string(skillMarkdown).split("\\R", -1)));
        int heading = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (SECTION_HEADING.matcher(lines.get(i)).matches()) {
                heading = i;
                break;
            }
        }
        final List<String> wanted = entries.stream().map(Entry::line).distinct().toList();
        if (heading < 0) {
            int at = lines.size();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).strip().toLowerCase(Locale.ROOT).matches("^##\\s+output\\s*$")) {
                    at = i;
                    break;
                }
            }
            while (at > 0 && at == lines.size() && lines.get(at - 1).isBlank()) {
                at--;
            }
            final List<String> section = new ArrayList<>();
            if (at > 0 && !lines.get(at - 1).isBlank()) {
                section.add("");
            }
            section.add("## Event chains");
            section.add("");
            section.addAll(wanted);
            section.add("");
            lines.addAll(at, section);
            return String.join("\n", lines).stripTrailing() + "\n";
        }
        int end = heading + 1;
        while (end < lines.size() && !NEXT_SECTION.matcher(lines.get(end)).find()) {
            end++;
        }
        final List<String> existing = lines.subList(heading + 1, end).stream().map(String::strip).toList();
        int insertAt = end;
        while (insertAt > heading + 1 && lines.get(insertAt - 1).isBlank()) {
            insertAt--;
        }
        final List<String> added = wanted.stream().filter(line -> !existing.contains(line)).toList();
        lines.addAll(insertAt, added);
        return String.join("\n", lines).stripTrailing() + "\n";
    }


    // --------------------------------------------------------------------------------


    /// One entry.
    ///
    /// @param source The index (by name), or [#ANY].
    /// @param start  A pair's start type, else null.
    /// @param end    A pair's end type, else null.
    /// @param by     A pair's key path, or null for the host alone.
    /// @param label  A pair's label path, or null.
    /// @param type   A role line's type, else null.
    /// @param role   A role line's role (one of [#ROLES]), else null.
    public record Entry(String source, String start, String end, String by, String label, String type, String role) {

        /// @param source The index.
        /// @param start  The start type.
        /// @param end    The end type.
        /// @param by     The key path, or null.
        /// @param label  The label path, or null.
        /// @return A pair's entry.
        public static Entry pair(final String source,
                                 final String start,
                                 final String end,
                                 final String by,
                                 final String label) {
            return new Entry(source, start, end, blank(by), blank(label), null, null);
        }

        /// @param source The index.
        /// @param type   The type.
        /// @param role   Its role.
        /// @return A role's entry.
        public static Entry role(final String source, final String type, final String role) {
            return new Entry(source, null, null, null, null, type, role);
        }

        /// @return True if it is a pair's.
        public boolean isPair() {
            return start != null;
        }

        /// @return It as a line of the section.
        public String line() {
            if (isPair()) {
                return "- " + source + ": pair " + start + " -> " + end + (by == null
                        ? ""
                        : " by " + by) + (label == null
                        ? ""
                        : " label " + label);
            }
            return "- " + source + ": " + type + " is " + role;
        }

        /// @param sourceName An index's name.
        /// @return True if it applies to that source.
        public boolean appliesTo(final String sourceName) {
            return ANY.equalsIgnoreCase(source) || source.equalsIgnoreCase(sourceName);
        }

        private static String blank(final String text) {
            return text == null || text.isBlank()
                    ? null
                    : text.strip();
        }
    }


    // --------------------------------------------------------------------------------


    /// A section read.
    ///
    /// @param entries Its entries.
    /// @param unread  Its bullet lines that aren't an entry.
    public record Parsed(List<Entry> entries, List<String> unread) {

        /// Copies the lists.
        public Parsed {
            entries = List.copyOf(entries);
            unread = List.copyOf(unread);
        }

        /// @return The entries by source, in order.
        public Map<String, List<Entry>> bySource() {
            final Map<String, List<Entry>> out = new LinkedHashMap<>();
            entries.forEach(entry -> out.computeIfAbsent(entry.source(), s -> new ArrayList<>()).add(entry));
            return out;
        }
    }
}
