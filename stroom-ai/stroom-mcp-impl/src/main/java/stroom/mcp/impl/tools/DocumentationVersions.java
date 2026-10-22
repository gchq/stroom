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

import stroom.ai.impl.tools.sample.PyJson;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The version control block at the end of a pipeline's documentation (the Stroom MCP server's
/// `utils/versionlog.py`): one row per released version, numbered, dated, with who made it (and whether through the
/// agent), what changed and why, and the code it describes.
///
/// Asked for by the user: changes such as event types found only once a whole feed was processed, and the rules
/// added for them, are recorded with the documentation they changed; and one row for a build, written when it is
/// done, not a row each time the documentation is rewritten while it is worked on. So write_documentation keeps the
/// rows as they are and adds its change to the pending changes (a comment at the end of the doc), and promote_build
/// turns them into one row. Until then an Unreleased row previews it (asked for by the user, to see what a build has
/// done). A doc written before this carries a bullet change log (`## Change log`, `- date: change`): its lines
/// become the first rows.
///
/// A row is a map, as the Python's dicts: `version`, `date`, `by`, `change` and `code`; a pending change has the
/// same keys but `version`.
final class DocumentationVersions {

    /// The block's heading.
    static final String HEADING = "## Version control";
    /// The heading of an older doc's bullet change log.
    static final String OLD_HEADING = "## Change log";
    /// Said under a block with no row yet and nothing pending.
    static final String NONE_YET = "No version released yet: this build's changes are recorded here when it is "
                                   + "promoted.";
    /// Said under a block with no row yet and changes pending.
    static final String UNRELEASED = "No version released yet: the Unreleased row is this build's changes so far, "
                                     + "recorded as a version when it is promoted.";

    private static final Pattern ROW = Pattern.compile(
            "^\\|\\s*(\\d+)\\s*\\|([^|]*)\\|([^|]*)\\|(.*)\\|([^|]*)\\|\\s*$");
    private static final Pattern BULLET = Pattern.compile("^-\\s*(\\d{4}-\\d{2}-\\d{2}):\\s*(.*)$");
    private static final Pattern PENDING = Pattern.compile("\\n*<!-- stroom-mcp pending changes (.*?) -->\\n*",
            Pattern.DOTALL);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final List<String> KEYS = List.of("version", "date", "by", "change", "code");

    private DocumentationVersions() {
        // Static only.
    }

    /// @param markdown A documentation doc's text, or null.
    /// @return It without its version control block (or older change log) and pending changes.
    static String bodyOf(final String markdown) {
        String text = PENDING.matcher(NullSafe.string(markdown)).replaceAll("\n");
        for (final String heading : List.of(HEADING, OLD_HEADING)) {
            final int at = text.indexOf(heading);
            if (at >= 0) {
                text = text.substring(0, at);
            }
        }
        return text.stripTrailing();
    }

    /// @param markdown A documentation doc's text, or null.
    /// @return The block's rows, oldest first; an older bullet change log read as rows.
    static List<Map<String, String>> rowsOf(final String markdown) {
        final String text = PENDING.matcher(NullSafe.string(markdown)).replaceAll("\n");
        final List<Map<String, String>> rows = new ArrayList<>();
        if (text.contains(HEADING)) {
            for (final String line : after(text, HEADING).split("\\R", -1)) {
                final Matcher m = ROW.matcher(line.strip());
                if (m.matches()) {
                    rows.add(row(m.group(1), m.group(2).strip(), m.group(3).strip(),
                            m.group(4).strip().replace("\\|", "|"), m.group(5).strip()));
                }
            }
        } else if (text.contains(OLD_HEADING)) {
            for (final String line : after(text, OLD_HEADING).split("\\R", -1)) {
                final Matcher m = BULLET.matcher(line.strip());
                if (m.matches()) {
                    rows.add(row(String.valueOf(rows.size() + 1), m.group(1), "", m.group(2).strip(), ""));
                }
            }
        }
        return rows;
    }

    /// @param markdown A documentation doc's text, or null.
    /// @return The changes pending, oldest first; none when there are none, or they can't be read.
    static List<Map<String, String>> pendingOf(final String markdown) {
        final Matcher found = PENDING.matcher(NullSafe.string(markdown));
        if (!found.find()) {
            return List.of();
        }
        try {
            final Object read = PyJson.loads(found.group(1).replace("- -", "--"));
            final List<Map<String, String>> entries = new ArrayList<>();
            if (read instanceof final List<?> list) {
                for (final Object item : list) {
                    if (item instanceof final Map<?, ?> map) {
                        final Map<String, String> entry = new LinkedHashMap<>();
                        map.forEach((key, value) -> entry.put(String.valueOf(key), value == null
                                ? null
                                : String.valueOf(value)));
                        entries.add(entry);
                    }
                }
            }
            return entries;
        } catch (final RuntimeException e) {
            return List.of();
        }
    }

    /// @param rows    The released rows, oldest first.
    /// @param pending The changes pending, previewed as an Unreleased row, or none.
    /// @return The version control block.
    static String block(final List<Map<String, String>> rows, final List<Map<String, String>> pending) {
        final List<String> lines = new ArrayList<>(List.of(HEADING, "", "| Version | Date | By | Change | Code |",
                "| --- | --- | --- | --- | --- |"));
        for (final Map<String, String> r : rows) {
            lines.add("| " + r.get("version") + " | " + r.get("date") + " | " + cell(r.get("by")) + " | "
                      + cell(r.get("change")) + " | " + cell(r.get("code")) + " |");
        }
        if (!NullSafe.isEmptyCollection(pending)) {
            // What promotion will write, previewed: not a number, so it is never read back as a released row.
            final List<String> codes = distinct(pending, "code");
            lines.add("| Unreleased | " + NullSafe.string(pending.getLast().get("date")) + " | "
                      + cell(String.join("; ", distinct(pending, "by"))) + " | "
                      + cell(String.join("; ", distinct(pending, "change"))) + " | "
                      + cell(codes.isEmpty()
                    ? ""
                    : codes.getLast()) + " |");
        }
        if (rows.isEmpty()) {
            lines.add("");
            lines.add(NullSafe.isEmptyCollection(pending)
                    ? NONE_YET
                    : UNRELEASED);
        }
        return String.join("\n", lines) + "\n";
    }

    /// @param body   The documentation's new text.
    /// @param old    Its text before, or "" for a new doc.
    /// @param change What changed and why.
    /// @param by     Who made the change.
    /// @param code   The code it describes ([#codeOf]), or "".
    /// @param now    The time now.
    /// @return The documentation: the body, the version control rows as they were, and this change pending.
    static String withPending(final String body,
                              final String old,
                              final String change,
                              final String by,
                              final String code,
                              final Instant now) {
        final List<Map<String, String>> entries = new ArrayList<>(pendingOf(old));
        final Map<String, String> entry = new LinkedHashMap<>();
        entry.put("date", DAY.format(now));
        entry.put("change", change);
        entry.put("by", by);
        entry.put("code", NullSafe.string(code));
        entries.add(entry);
        return bodyOf(body) + "\n\n" + block(rowsOf(old), entries) + "\n" + pendingComment(entries);
    }

    /// @param markdown A documentation doc's text.
    /// @param now      The time now.
    /// @return The documentation with its pending changes as one new row; empty when nothing is pending.
    static Optional<String> consolidate(final String markdown, final Instant now) {
        final List<Map<String, String>> entries = pendingOf(markdown);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        final List<Map<String, String>> rows = new ArrayList<>(rowsOf(markdown));
        final List<String> codes = distinct(entries, "code");
        rows.add(row(String.valueOf(rows.isEmpty()
                        ? 1
                        : Integer.parseInt(rows.getLast().get("version")) + 1), DAY.format(now),
                String.join("; ", distinct(entries, "by")), String.join("; ", distinct(entries, "change")),
                codes.isEmpty()
                        ? ""
                        : codes.getLast()));
        return Optional.of(bodyOf(markdown) + "\n\n" + block(rows, List.of()));
    }

    /// @param kept The mapping kept with a pipeline's XSLT, or null.
    /// @return The code a row describes: the XSLT and its Stroom version, and the mapping's digest; "" for none.
    static String codeOf(final KeptMappings.KeptXslt kept) {
        if (kept == null) {
            return "";
        }
        final String version = NullSafe.string(kept.xslt().getVersion());
        final String shortVersion = version.substring(0, Math.min(8, version.length()));
        return "XSLT " + kept.xslt().getName() + (shortVersion.isEmpty()
                ? ""
                : " @" + shortVersion) + ", " + kept.kept().kind() + " mapping "
               + BuildChecks.mappingDigest(kept).substring(0, 8);
    }

    private static String after(final String text, final String heading) {
        return text.substring(text.indexOf(heading) + heading.length());
    }

    private static Map<String, String> row(final String version,
                                           final String date,
                                           final String by,
                                           final String change,
                                           final String code) {
        final Map<String, String> row = new LinkedHashMap<>();
        row.put(KEYS.get(0), version);
        row.put(KEYS.get(1), date);
        row.put(KEYS.get(2), by);
        row.put(KEYS.get(3), change);
        row.put(KEYS.get(4), code);
        return row;
    }

    private static String cell(final String text) {
        return NullSafe.string(text).replace("|", "\\|").replace("\n", " ").strip();
    }

    private static List<String> distinct(final List<Map<String, String>> entries, final String key) {
        final Set<String> found = new LinkedHashSet<>();
        for (final Map<String, String> entry : entries) {
            final String value = entry.get(key);
            if (NullSafe.isNonEmptyString(value)) {
                found.add(value);
            }
        }
        return List.copyOf(found);
    }

    // An HTML comment may not hold '--': JSON with it spaced out, put back when read.
    private static String pendingComment(final List<Map<String, String>> entries) {
        return entries.isEmpty()
                ? ""
                : "<!-- stroom-mcp pending changes " + PyJson.dumps(entries).replace("--", "- -") + " -->\n";
    }
}
