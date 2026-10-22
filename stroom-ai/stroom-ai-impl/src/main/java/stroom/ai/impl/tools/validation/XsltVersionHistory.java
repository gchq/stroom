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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.KeptMapping;
import stroom.util.shared.NullSafe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The version history of each XSLT the tools save, kept in its description (the XSLT's Documentation tab in
/// Stroom), not in the code. Ported from the Stroom MCP server's `utils/xsltversion.py`.
///
/// One row per version: its number, date, author, whether the agent made it (through the tools) or someone changed
/// the XSLT by hand (in Stroom's editor), what changed, and for an agent change who made it (the tools' version,
/// the client and the model the client names), and the digest of the code it describes.
///
/// Within a build an XSLT is saved many times; the user asked for one row for the lot, written when the build is
/// done. So each save adds its change to the pending changes kept in the description, and promotion turns them
/// into one row. Until then the table previews it: the row (or rows, an edit made by hand before the build too)
/// promotion will write, marked Unreleased instead of numbered, rewritten each save. The user asked for that, to
/// see what a build has done before it is released, and for the history to stay out of the code (a comment there
/// changed the code, and every check comparing code had to look past it); an XSLT saved with such a comment has it
/// moved here when next saved.
///
/// ```
/// ## Version history
///
/// | Version | Date | Author | How | Change | By | Digest |
/// | --- | --- | --- | --- | --- | --- | --- |
/// | 1 | 2026-10-10 | peter.kimberley | agent | Created from its mapping | stroom-mcp 0.16.42 | #3f2a9c1e |
/// | 2 | 2026-10-12 | jane.doe | by hand | Changed outside the agent (Stroom's editor) |  | #77ab01c2 |
/// | Unreleased | 2026-10-14 | peter.kimberley | agent | Rule for VPN events | stroom-mcp 0.16.45 | #9b10d4e3 |
/// ```
public final class XsltVersionHistory {

    /// The heading of the history.
    public static final String HEADING = "## Version history";
    /// The version a previewed row has.
    public static final String UNRELEASED = "Unreleased";
    /// The name of the block of a build's pending changes.
    public static final String PENDING = KeptMapping.PENDING;

    private static final String INTRO = "Each version of this XSLT: changes made by the agent through stroom-mcp, "
                                        + "and changes made by hand in Stroom's editor. Unreleased rows are a "
                                        + "build's changes so far, recorded as a version when the build is "
                                        + "promoted.";
    // The table ends where the tools' part of the description begins (its note, its hidden blocks), or at the end.
    private static final Pattern SECTION = Pattern.compile(
            "\\n*" + Pattern.quote(HEADING) + "\\n.*?(?=\\n\\*Below, hidden, is what stroom-mcp|"
            + "\\n<!-- stroom-mcp [a-z ]+: kept by the server|\\n--- stroom-mcp |\\z)", Pattern.DOTALL);
    private static final Pattern ROW = Pattern.compile(
            "^\\|\\s*(\\d+)\\s*\\|([^|]*)\\|([^|]*)\\|([^|]*)\\|([^|]*)\\|([^|]*)\\|\\s*#?([0-9a-f]*)\\s*\\|\\s*$");
    // The comment block XSLTs carried before (0.16.43 to 0.16.45): read, to move it here, and left out of the code.
    private static final Pattern BLOCK = Pattern.compile(
            "[ \\t]*<!-- stroom-mcp version history\\n(.*?)\\nend of version history -->[ \\t]*\\n?", Pattern.DOTALL);
    private static final Pattern DECL = Pattern.compile("^\\s*<\\?xml[^>]*\\?>[ \\t]*\\n?");
    private static final Pattern BETWEEN_TAGS = Pattern.compile(">\\s+<");
    private static final Pattern COMMENT_VERSION = Pattern.compile("v\\d+");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private XsltVersionHistory() {
        // Static only.
    }

    /// @param code An XSLT's code, or null.
    /// @return It without the version history comment an earlier version put in it.
    public static String strip(final String code) {
        return BLOCK.matcher(NullSafe.string(code)).replaceFirst("");
    }

    /// @param code An XSLT's code, or null.
    /// @return The digest of what it does: its first 8 hex digits of SHA-256, without its XML declaration, a
    /// history comment or the space between tags.
    public static String digest(final String code) {
        final String withoutDecl = DECL.matcher(strip(code)).replaceFirst("");
        final String body = BETWEEN_TAGS.matcher(withoutDecl).replaceAll("><").strip();
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 8);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /// @param description An XSLT's description, or null.
    /// @return Its released versions, oldest first (not the Unreleased preview).
    public static List<Row> history(final String description) {
        final Matcher section = SECTION.matcher(NullSafe.string(description));
        final List<Row> rows = new ArrayList<>();
        if (!section.find()) {
            return rows;
        }
        for (final String line : section.group().split("\n")) {
            final Matcher row = ROW.matcher(line.strip());
            if (row.matches()) {
                rows.add(new Row(row.group(1), row.group(2).strip(), row.group(3).strip(), row.group(4).strip(),
                        row.group(5).strip(), row.group(6).strip(), row.group(7)));
            }
        }
        return rows;
    }

    /// @param description   An XSLT's description, or null.
    /// @param previousCode The XSLT's code before this save, or null.
    /// @return The description with the history an earlier version kept in the code moved into it, when it has
    /// none.
    public static String adopt(final String description, final String previousCode) {
        final List<Row> fromCode = commentRows(previousCode);
        if (!history(description).isEmpty() || fromCode.isEmpty()) {
            return NullSafe.string(description);
        }
        return withSection(description, fromCode);
    }

    /// @param description An XSLT's description, or null.
    /// @return The build's pending changes it keeps (`base` and `entries`), if any.
    public static Optional<Map<String, Object>> pendingOf(final String description) {
        return KeptMapping.pendingOf(description);
    }

    /// What the tools recorded of their last save of this code: the newest pending entry with a digest, else the
    /// newest version.
    ///
    /// @param description An XSLT's description, or null.
    /// @param code        Its code, or null.
    /// @return The `digest` and `by` of the last save; empty when nothing was recorded (saved before digests were).
    public static Optional<Saved> lastSaved(final String description, final String code) {
        final List<Map<String, Object>> entries = entries(pendingOf(description).orElse(Map.of())).stream()
                .filter(entry -> NullSafe.isNonEmptyString(stringOf(entry.get("digest"))))
                .toList();
        if (!entries.isEmpty()) {
            final Map<String, Object> last = entries.getLast();
            return Optional.of(new Saved(stringOf(last.get("digest")), stringOf(last.get("by"))));
        }
        List<Row> rows = history(description);
        if (rows.isEmpty()) {
            rows = commentRows(code);
        }
        return rows.isEmpty()
                ? Optional.empty()
                : Optional.of(new Saved(rows.getLast().digest(), rows.getLast().by()));
    }

    /// @param description An XSLT's description, or null.
    /// @param code        Its code, or null.
    /// @return Whether the code is exactly what the tools last saved (true), changed since (false), or unknown
    /// (empty).
    public static Optional<Boolean> untouched(final String description, final String code) {
        return lastSaved(description, code).map(saved -> saved.digest().equals(digest(code)));
    }

    /// @param description An XSLT's description, or null.
    /// @return It without the pending changes.
    public static String withoutPending(final String description) {
        return KeptMapping.withoutBlock(description, PENDING);
    }

    /// The description with this change added to the build's pending changes. The first also keeps what the code
    /// was before the build touched it, so an edit made by hand before then is recorded too.
    ///
    /// @param description An XSLT's description, or null.
    /// @param previous    The XSLT as it was before this save (its `data`, `updateUser` and `updateTimeMs`), or
    ///                    null for a new one.
    /// @param author      Who made the change.
    /// @param change      What changed.
    /// @param by          What made it, e.g. the tools' version, the client and the model.
    /// @param now         When.
    /// @param code        The code saved, or null if it isn't known.
    /// @return The description with the change pending.
    public static String withPending(final String description,
                                     final Previous previous,
                                     final String author,
                                     final String change,
                                     final String by,
                                     final Instant now,
                                     final String code) {
        final Map<String, Object> pending = new LinkedHashMap<>(pendingOf(description).orElse(Map.of()));
        final List<Map<String, Object>> entries = new ArrayList<>(entries(pending));
        if (!pending.containsKey("base")) {
            final String old = previous == null
                    ? ""
                    : NullSafe.string(previous.data());
            if (old.isEmpty()) {
                // No code before the build: false rather than null, which the JSON writer leaves out (and the
                // next save would then take its code for the code before the build).
                pending.put("base", false);
            } else {
                final Map<String, Object> base = new LinkedHashMap<>();
                base.put("digest", digest(old));
                base.put("user", previous.updateUser());
                base.put("time_ms", previous.updateTimeMs());
                pending.put("base", base);
            }
        }
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("date", DATE.format(now));
        entry.put("author", NullSafe.isNonEmptyString(author)
                ? author
                : "-");
        entry.put("change", change);
        entry.put("by", by);
        if (code != null) {
            entry.put("digest", digest(code));
        }
        entries.add(entry);
        pending.put("entries", entries);
        return KeptMapping.withBlock(withoutPending(description), PENDING, pending);
    }

    /// @param description An XSLT's description, or null.
    /// @param code        Its code.
    /// @param now         When.
    /// @return The description with its released versions and, while a build's changes are pending, the rows
    /// promotion will write for them, each marked Unreleased.
    public static String preview(final String description, final String code, final Instant now) {
        final List<Row> released = history(description);
        final Optional<Map<String, Object>> pending = pendingOf(description);
        if (pending.isEmpty() || entries(pending.get()).isEmpty()) {
            return withSection(description, released);
        }
        final List<Row> rows = new ArrayList<>(released);
        coming(released, pending.get(), code, now).forEach(row -> rows.add(row.withVersion(UNRELEASED)));
        return withSection(description, rows);
    }

    /// @param description An XSLT's description, or null.
    /// @param code        Its code.
    /// @param now         When.
    /// @return The description with the build's pending changes as released versions (one row, and an edit made
    /// by hand before them as its own), and nothing pending.
    public static String consolidate(final String description, final String code, final Instant now) {
        final List<Row> released = new ArrayList<>(history(description));
        final Optional<Map<String, Object>> pending = pendingOf(description);
        if (pending.isPresent() && !entries(pending.get()).isEmpty()) {
            released.addAll(coming(released, pending.get(), code, now));
        }
        return withSection(withoutPending(description), released);
    }

    // The rows of a version history comment in the code (as 0.16.43 to 0.16.45 wrote it).
    private static List<Row> commentRows(final String code) {
        final Matcher found = BLOCK.matcher(NullSafe.string(code));
        final List<Row> rows = new ArrayList<>();
        if (!found.find()) {
            return rows;
        }
        for (final String line : found.group(1).split("\n")) {
            final String[] parts = line.strip().split("\\|", -1);
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].strip();
            }
            if (parts.length >= 7 && COMMENT_VERSION.matcher(parts[0]).matches()) {
                rows.add(new Row(parts[0].substring(1), parts[1], parts[2], parts[3], parts[4], parts[5],
                        parts[6].replaceFirst("^#+", "")));
            }
        }
        return rows;
    }

    // Fit for a table cell: one line, no column separators.
    private static String clean(final String text) {
        final String cleaned = SPACE.matcher(NullSafe.string(text)).replaceAll(" ").replace('|', '/').strip();
        return cleaned.substring(0, Math.min(600, cleaned.length()));
    }

    private static String section(final List<Row> rows) {
        final List<String> lines = new ArrayList<>(List.of(HEADING, "", INTRO, "",
                "| Version | Date | Author | How | Change | By | Digest |",
                "| --- | --- | --- | --- | --- | --- | --- |"));
        for (final Row row : rows) {
            lines.add("| " + row.version() + " | " + row.date() + " | " + orDash(clean(row.author())) + " | "
                      + row.how() + " | " + orDash(clean(row.change())) + " | " + clean(row.by()) + " | #"
                      + row.digest() + " |");
        }
        return String.join("\n", lines);
    }

    // The description with the history table (after its own text, before the tools' blocks), or without one.
    private static String withSection(final String description, final List<Row> rows) {
        final String text = SECTION.matcher(NullSafe.string(description)).replaceAll("").strip();
        if (rows.isEmpty()) {
            return text;
        }
        final Matcher at = KeptMapping.SERVER_PART.matcher(text);
        final String head;
        final String tail;
        if (at.find()) {
            head = text.substring(0, at.start());
            tail = text.substring(at.start());
        } else {
            head = text;
            tail = "";
        }
        final List<String> parts = new ArrayList<>();
        for (final String part : List.of(head, section(rows), tail)) {
            if (!part.strip().isEmpty()) {
                parts.add(part.strip());
            }
        }
        return String.join("\n\n", parts);
    }

    // The rows promotion writes for the pending changes: an edit made by hand before them, then the build's.
    private static List<Row> coming(final List<Row> released,
                                    final Map<String, Object> pending,
                                    final String code,
                                    final Instant now) {
        final List<Row> out = new ArrayList<>();
        if (pending.get("base") instanceof final Map<?, ?> base) {
            final String baseDigest = stringOf(base.get("digest"));
            if (released.isEmpty() || !released.getLast().digest().equals(baseDigest)) {
                final String date = base.get("time_ms") instanceof final Number when && when.longValue() != 0
                        ? DATE.format(Instant.ofEpochMilli(when.longValue()))
                        : "?";
                final String user = stringOf(base.get("user"));
                out.add(new Row(String.valueOf(released.size() + 1), date, user.isEmpty()
                        ? "?"
                        : user, "by hand", released.isEmpty()
                        ? "Written before the agent kept a history"
                        : "Changed outside the agent (Stroom's editor)", "", baseDigest));
            }
        }
        final List<Map<String, Object>> entries = entries(pending);
        out.add(new Row(String.valueOf(released.size() + out.size() + 1), DATE.format(now),
                orDash(String.join(", ", distinct(entries, "author"))), "agent",
                orDash(String.join("; ", distinct(entries, "change"))), String.join("; ", distinct(entries, "by")),
                digest(code)));
        return out;
    }

    private static Set<String> distinct(final List<Map<String, Object>> entries, final String key) {
        final Set<String> values = new LinkedHashSet<>();
        for (final Map<String, Object> entry : entries) {
            final String value = stringOf(entry.get(key));
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries(final Map<String, Object> pending) {
        final List<Map<String, Object>> entries = new ArrayList<>();
        if (pending.get("entries") instanceof final List<?> list) {
            for (final Object item : list) {
                if (item instanceof Map) {
                    entries.add((Map<String, Object>) item);
                }
            }
        }
        return entries;
    }

    private static String stringOf(final Object value) {
        return value == null
                ? ""
                : Objects.toString(value);
    }

    private static String orDash(final String text) {
        return text.isEmpty()
                ? "-"
                : text;
    }


    // --------------------------------------------------------------------------------


    /// A version of an XSLT.
    ///
    /// @param version Its number, or [#UNRELEASED].
    /// @param date    Its date, e.g. `2026-10-10`.
    /// @param author  Who made it.
    /// @param how     `agent` or `by hand`.
    /// @param change  What changed.
    /// @param by      What made an agent change (the tools' version, the client, the model); "" by hand.
    /// @param digest  The digest of the code it describes.
    public record Row(String version, String date, String author, String how, String change, String by,
                      String digest) {

        /// @param newVersion Another version.
        /// @return The row with that version.
        public Row withVersion(final String newVersion) {
            return new Row(newVersion, date, author, how, change, by, digest);
        }
    }

    /// What the tools recorded of a save.
    ///
    /// @param digest The digest of the code saved.
    /// @param by     What saved it.
    public record Saved(String digest, String by) {

    }

    /// An XSLT as it was before a save.
    ///
    /// @param data         Its code.
    /// @param updateUser   Who last changed it.
    /// @param updateTimeMs When it was last changed, in milliseconds since the epoch, or null.
    public record Previous(String data, String updateUser, Long updateTimeMs) {

    }
}
