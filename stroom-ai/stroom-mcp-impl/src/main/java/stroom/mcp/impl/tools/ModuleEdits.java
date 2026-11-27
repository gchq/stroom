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

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.CodeDiff;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.HelpersXslt;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Edits made by hand in Stroom to an aggregated feed's shared modules (its common module, its helpers) since the
/// tools saved them, told before a build writes one (decided by the user, 2026-11-24): a build that needs to add to a
/// module edited by hand writes nothing until the user says whether to keep the edit (read into the module's kept
/// mapping, the build's additions beside it), discard it (the module goes back to the generated code), or stop.
///
/// An edit is told from an older generator's code by the version history ([XsltVersionHistory]), as [HandEditGate]
/// and [GeneratedDrift] tell them: code the tools last saved is out of date, never an edit. A common module's values
/// read back from its code (each constant of its kept mapping, read where the generator writes it) are an edit
/// whatever the history says, as no generator changes a mapping's values; the values read are proven by generating
/// the module from them, which must give its code exactly. A helpers module of the generator's own version that
/// isn't the generator's code is an edit too: that version's helpers are the same to the byte.
final class ModuleEdits {

    /// The common module's key, as the feed's registry names its modules.
    static final String COMMON = "common";
    /// The helpers' key.
    static final String HELPERS = "helpers";
    /// Keep the edit: read into the module's kept mapping, what the build needs added beside it.
    static final String KEEP = "Keep the edit (recommended)";
    /// Discard the edit: the module goes back to the generated code.
    static final String DISCARD = "Discard the edit";
    /// Stop: nothing changed.
    static final String STOP = "Stop";
    /// The answer `hand_edit_choices` gives to keep a module's edit.
    static final String KEEP_CHOICE = "keep";
    /// The answer `hand_edit_choices` gives to discard it.
    static final String DISCARD_CHOICE = "overwrite";

    // The most changed values, or lines of code, a question names.
    private static final int MAX_NAMED = 3;
    private static final Pattern HELPERS_VERSION = Pattern.compile("<mcp:module\\b[^>]*\\bversion=\"(\\d+)\"");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|lt|gt|quot|apos|amp);");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private ModuleEdits() {
        // Static only.
    }

    /// A common module's edit made by hand, if it has one.
    ///
    /// @param uuid     The module's UUID.
    /// @param doc      The module, as it is.
    /// @param kept     The mapping kept with it.
    /// @param generate What the generator writes for a common module's mapping (null where it doesn't generate).
    /// @return The edit, with the mapping read back from the code where its values can be; empty when the code is
    /// what the kept mapping generates, or what the tools last saved (an older generator's).
    static Optional<Edit> common(final String uuid,
                                 final XsltDoc doc,
                                 final TranslationMapping kept,
                                 final Function<TranslationMapping, String> generate) {
        final String code = NullSafe.string(doc.getData());
        final String keptCode = generated(generate, kept);
        if (keptCode != null && XsltCode.normalise(keptCode).equals(XsltCode.normalise(code))) {
            return Optional.empty();
        }
        final Optional<TranslationMapping> read = keptCode == null
                ? Optional.empty()
                : readValues(kept, code, generate);
        if (read.isPresent()) {
            return Optional.of(new Edit(COMMON, uuid, doc.getName(), by(doc), changedValues(kept, read.get()),
                    read.get(), null, XsltVersionHistory.digest(code)));
        }
        if (!Optional.of(false).equals(XsltVersionHistory.asGenerated(doc.getDescription(), code))) {
            // As the tools last saved it: an older generator's code, out of date, not an edit.
            return Optional.empty();
        }
        return Optional.of(new Edit(COMMON, uuid, doc.getName(), by(doc), changedLines(keptCode, code), null,
                "the edit changes more than the values its mapping holds (" + changedLines(keptCode, code)
                + "), so it can't be read into the mapping beside what this build adds",
                XsltVersionHistory.digest(code)));
    }

    /// The helpers' edit made by hand, if they have one.
    ///
    /// @param uuid The helpers' UUID.
    /// @param doc  The helpers, as they are.
    /// @return The edit; empty when the code is the generator's, or an older generator's as the tools last saved it.
    static Optional<Edit> helpers(final String uuid, final XsltDoc doc) {
        final String code = NullSafe.string(doc.getData());
        final String current = HelpersXslt.generate();
        if (XsltCode.normalise(current).equals(XsltCode.normalise(code)) || HelpersXslt.isCurrent(code)) {
            return Optional.empty();
        }
        final Integer version = helpersVersion(code);
        final boolean thisVersion = Objects.equals(version, HelpersXslt.VERSION);
        // The generator's own version is the same to the byte: anything else of it is an edit. An older version's
        // code is an edit only where the tools last saved other code.
        if (!thisVersion && !Optional.of(false).equals(XsltVersionHistory.asGenerated(doc.getDescription(), code))) {
            return Optional.empty();
        }
        final String unkeepable = thisVersion
                ? null
                : "they were written by the generator's helpers version " + Objects.requireNonNullElse(version, 0)
                  + ", and this build needs version " + HelpersXslt.VERSION + "'s functions, which can't be "
                  + "added beside the edit (the helpers are the generator's code, with no mapping to read the edit "
                  + "into)";
        return Optional.of(new Edit(HELPERS, uuid, doc.getName(), by(doc), changedLines(current, code), null,
                unkeepable, XsltVersionHistory.digest(code)));
    }

    /// A common module's values read back from its code: each constant of the kept mapping read where the generator
    /// writes it, proven by generating the module from what was read, which must give the code exactly.
    ///
    /// @param kept     The mapping kept with the module.
    /// @param code     The module's code.
    /// @param generate What the generator writes for a mapping (null where it doesn't generate).
    /// @return The kept mapping with the values the code holds; empty where the code differs in more than its
    /// values (a structure the mapping can't hold, a field read otherwise), or none can be found.
    static Optional<TranslationMapping> readValues(final TranslationMapping kept,
                                                   final String code,
                                                   final Function<TranslationMapping, String> generate) {
        final List<FieldMapping> entries = kept.common();
        final List<FieldMapping> read = new ArrayList<>(entries);
        final List<String> codeLines = code.lines().toList();
        for (int i = 0; i < entries.size(); i++) {
            final FieldMapping entry = entries.get(i);
            if (entry.value() == null) {
                continue;
            }
            // A marker no value holds, generated where the value goes: the code around it says where to read.
            final String marker = "mcpModuleValue" + i + "Read";
            final String marked = generated(generate, withValue(kept, i, marker));
            if (marked == null) {
                return Optional.empty();
            }
            final Optional<String> value = valueAt(marked.lines().toList(), marker, codeLines);
            if (value.isEmpty()) {
                return Optional.empty();
            }
            read.set(i, withValue(entry, value.get()));
        }
        final TranslationMapping candidate = kept.withCommonAndShared(read, kept.shared());
        final String regenerated = generated(generate, candidate);
        return regenerated != null && XsltCode.normalise(regenerated).equals(XsltCode.normalise(code))
                ? Optional.of(candidate)
                : Optional.empty();
    }

    /// The question asked before a build writes a module edited by hand.
    ///
    /// @param edit  The edit.
    /// @param needs What the build needs to write to the module.
    /// @return The question, plain text.
    static String question(final Edit edit, final String needs) {
        final String what = COMMON.equals(edit.key())
                ? "The common module"
                : "The feed's helpers";
        return what + " " + (COMMON.equals(edit.key())
                ? "was"
                : "were") + " edited by hand: '" + edit.name() + "' was changed in Stroom since the tools saved it"
               + edit.by() + ": " + edit.changed() + ". This build needs to " + needs + "." + (edit.keepable()
                ? " Keep the edit (" + (edit.read() != null
                        ? "it is read into the module's mapping, and what the build needs is added beside it"
                        : "nothing is added to it") + "), discard it (the module goes back to the generated code), "
                  + "or stop (nothing changed)?"
                : " The edit can't be kept: " + edit.unkeepable() + ". Discard it (the module goes back to the "
                  + "generated code), or stop (nothing changed)?");
    }

    /// @param edit An edit.
    /// @return The options the user is offered: keep (where it can be kept), discard, stop.
    static List<String> options(final Edit edit) {
        return edit.keepable()
                ? List.of(KEEP, DISCARD, STOP)
                : List.of(DISCARD, STOP);
    }

    /// What the agent is told where no form can be shown: the question, and how to pass the user's answer.
    ///
    /// @param edit     The edit.
    /// @param question The question.
    /// @return The refusal's text.
    static String refusal(final Edit edit, final String question) {
        return "Nothing was saved: " + question + " Ask the user exactly, offering "
               + (edit.keepable()
                ? "all three"
                : "both") + ", then call again with the same arguments and hand_edit_choices={'" + edit.name()
               + "': " + (edit.keepable()
                ? "'" + KEEP_CHOICE + "' to keep the edit, or "
                : "") + "'" + DISCARD_CHOICE + "' to discard it}; to stop, don't call again. Don't choose for them.";
    }

    // Who changed the module, and when, as a question says it: " (by <user>, <date>)", or "".
    private static String by(final XsltDoc doc) {
        final List<String> parts = new ArrayList<>();
        if (NullSafe.isNonBlankString(doc.getUpdateUser())) {
            parts.add("by " + doc.getUpdateUser());
        }
        if (doc.getUpdateTimeMs() != null && doc.getUpdateTimeMs() > 0) {
            parts.add(DATE.format(Instant.ofEpochMilli(doc.getUpdateTimeMs())));
        }
        return parts.isEmpty()
                ? ""
                : " (" + String.join(", ", parts) + ")";
    }

    // Each value the edit changed: `<path> is now "…" (was "…")`.
    private static String changedValues(final TranslationMapping kept, final TranslationMapping read) {
        final List<String> changed = new ArrayList<>();
        for (int i = 0; i < kept.common().size(); i++) {
            final FieldMapping before = kept.common().get(i);
            final FieldMapping after = read.common().get(i);
            if (!Objects.equals(before.value(), after.value())) {
                changed.add(before.strippedPath() + " is now \"" + HandEditGate.quoted(after.value()) + "\" (was \""
                            + HandEditGate.quoted(before.value()) + "\")");
            }
        }
        return named(changed);
    }

    // The lines the edit changed, a few, on one line.
    private static String changedLines(final String expected, final String code) {
        if (expected == null) {
            return "its code changed";
        }
        final List<String> lines = CodeDiff.diff(expected, code, MAX_NAMED).stream()
                .map(HandEditGate::quoted)
                .toList();
        return lines.isEmpty()
                ? "its layout changed"
                : "it changed " + String.join(" | ", lines);
    }

    private static String named(final List<String> items) {
        final String first = String.join("; ", items.subList(0, Math.min(MAX_NAMED, items.size())));
        final int others = items.size() - MAX_NAMED;
        return others > 0
                ? first + " and " + others + " other value" + (others > 1
                ? "s"
                : "")
                : first;
    }

    // The value at the marker's place in the code: the line holding the marker in the marked code, as a pattern, read
    // from the code's line of that pattern (the same occurrence of it). Every line holding the marker must read the
    // same value.
    private static Optional<String> valueAt(final List<String> marked, final String marker, final List<String> code) {
        String value = null;
        for (int at = 0; at < marked.size(); at++) {
            final String line = marked.get(at);
            final int found = line.indexOf(marker);
            if (found < 0) {
                continue;
            }
            final String before = line.substring(0, found).strip();
            final String after = line.substring(found + marker.length()).strip();
            if (after.contains(marker)) {
                return Optional.empty();
            }
            final Pattern pattern = Pattern.compile("^\\s*" + Pattern.quote(before) + "(.*?)" + Pattern.quote(after)
                                                    + "\\s*$");
            int occurrence = 0;
            for (int earlier = 0; earlier < at; earlier++) {
                if (pattern.matcher(marked.get(earlier)).matches()) {
                    occurrence++;
                }
            }
            final Optional<String> raw = nthMatch(code, pattern, occurrence);
            if (raw.isEmpty()) {
                return Optional.empty();
            }
            final String one = unescaped(raw.get(), before.endsWith("'"));
            if (value != null && !value.equals(one)) {
                return Optional.empty();
            }
            value = one;
        }
        return Optional.ofNullable(value);
    }

    private static Optional<String> nthMatch(final List<String> lines, final Pattern pattern, final int occurrence) {
        int seen = 0;
        for (final String line : lines) {
            final Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                if (seen == occurrence) {
                    return Optional.of(matcher.group(1));
                }
                seen++;
            }
        }
        return Optional.empty();
    }

    // XML's escapes undone; in an XPath string literal (after a quote), its doubled quotes too.
    private static String unescaped(final String raw, final boolean literal) {
        final Matcher matcher = ENTITY.matcher(raw);
        final StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            final String entity = matcher.group(1);
            final String text = switch (entity) {
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "amp" -> "&";
                default -> Character.toString(entity.startsWith("#x")
                        ? Integer.parseInt(entity.substring(2), 16)
                        : Integer.parseInt(entity.substring(1)));
            };
            matcher.appendReplacement(out, Matcher.quoteReplacement(text));
        }
        matcher.appendTail(out);
        return literal
                ? out.toString().replace("''", "'")
                : out.toString();
    }

    private static TranslationMapping withValue(final TranslationMapping mapping, final int index, final String value) {
        final List<FieldMapping> entries = new ArrayList<>(mapping.common());
        entries.set(index, withValue(entries.get(index), value));
        return mapping.withCommonAndShared(entries, mapping.shared());
    }

    private static FieldMapping withValue(final FieldMapping entry, final String value) {
        final Map<String, Object> json = new LinkedHashMap<>(entry.toJson(DumpMode.COMPACT));
        json.put("value", value);
        return FieldMapping.fromJson(json);
    }

    private static String generated(final Function<TranslationMapping, String> generate,
                                    final TranslationMapping mapping) {
        try {
            return generate.apply(mapping);
        } catch (final RuntimeException e) {
            // A mapping that doesn't generate reads nothing back.
            return null;
        }
    }

    // The helpers' version, from their mark; null for none.
    private static Integer helpersVersion(final String code) {
        final Matcher matcher = HELPERS_VERSION.matcher(code);
        return matcher.find()
                ? Integer.valueOf(matcher.group(1))
                : null;
    }


    // --------------------------------------------------------------------------------


    /// A module's edit made by hand.
    ///
    /// @param key        [#COMMON] or [#HELPERS].
    /// @param uuid       The module's UUID.
    /// @param name       Its name.
    /// @param by         Who changed it and when, as a question says it (` (by …, …)`), or "".
    /// @param changed    What the edit changed, said briefly.
    /// @param read       The common module's mapping read back from its code, or null (helpers, or values that
    ///                   can't be read).
    /// @param unkeepable Why the edit can't be kept beside what the build adds, or null where it can.
    /// @param digest     The digest of the module's code as edited: answers are kept for it.
    record Edit(String key,
                String uuid,
                String name,
                String by,
                String changed,
                TranslationMapping read,
                String unkeepable,
                String digest) {

        /// @return True if the user may keep the edit.
        boolean keepable() {
            return unkeepable == null;
        }

        /// @return The key an answer for this edit is kept under: its module and code as edited.
        String answerKey() {
            return uuid + " " + digest;
        }

        /// @return The edit's mapping as the module's kept mapping says it, for a note: its values.
        String readSaid() {
            return read == null
                    ? ""
                    : ToolText.list(read.common().stream()
                            .filter(entry -> entry.value() != null)
                            .map(entry -> entry.strippedPath() + " \"" + HandEditGate.quoted(entry.value()) + "\"")
                            .toList());
        }
    }

    /// The edits found in the feed's modules a group's build reads, and the user's answers about them.
    ///
    /// @param found   Each module's edit, by the module's UUID.
    /// @param asked   The edits of modules the build writes that the user hasn't answered about yet: asked before
    ///                anything is written.
    /// @param decided The user's answers, by the module's UUID: [#KEEP_CHOICE] or [#DISCARD_CHOICE].
    record Edits(Map<String, Edit> found, List<Ask> asked, Map<String, String> decided) {

        /// No edits.
        static final Edits NONE = new Edits(Map.of(), List.of(), Map.of());

        /// @param uuid A module's UUID.
        /// @return True if the user said to keep its edit.
        boolean kept(final String uuid) {
            return found.containsKey(uuid) && KEEP_CHOICE.equals(decided.get(uuid));
        }

        /// @param uuid A module's UUID.
        /// @return True if the module has an edit the user hasn't answered about.
        boolean undecided(final String uuid) {
            return found.containsKey(uuid) && !decided.containsKey(uuid);
        }
    }

    /// An edit the user is asked about, as a module the build writes has it.
    ///
    /// @param edit  The edit.
    /// @param needs What the build needs to write to the module, as the question says it.
    record Ask(Edit edit, String needs) {

        /// @return The question.
        String question() {
            return ModuleEdits.question(edit, needs);
        }
    }
}
