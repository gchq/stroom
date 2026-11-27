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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.draft.LocalCheck.QualifiedIds;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.ItemSource;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.EventSchema.Child;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/// A user's Id whose values carry a domain (`domain.com\sam.lee`, `sam.lee@domain.com`), split as the user agrees:
/// the account name in the Id (`strip_domain`), the domain in the Domain beside it (`domain`). Not in the Stroom
/// MCP server, which left the Ids whole; asked for by the user (seen: SecretServer's Ids kept the domain).
final class DomainSplits {

    /// The option that splits the domain off.
    static final String SPLIT = "Split";
    /// The option that keeps the Ids as they are.
    static final String KEEP = "Keep as is";

    private DomainSplits() {
        // Static only.
    }

    /// @param found The fields whose values carry a domain, at least one.
    /// @return The one question put to the user for them all.
    static String question(final List<QualifiedIds> found) {
        final String example = found.getFirst().examples().getFirst();
        // Short, as a form's message is: the first few fields, each with its first few paths, and how many more.
        final String fields = found.stream()
                .limit(Consent.LINES_SHOWN)
                .map(each -> "'" + each.field() + "' (" + firstFew(each.paths()) + ")")
                .collect(Collectors.joining(", ")) + (found.size() > Consent.LINES_SHOWN
                ? " and " + (found.size() - Consent.LINES_SHOWN) + " more field(s)"
                : "");
        return "Split the domain off the users' Ids from " + fields + "? E.g. " + example + ": the Id "
               + account(example) + ", the Domain " + domain(example) + ".";
    }

    // A field's paths for a question: the first few, and how many more.
    private static String firstFew(final List<String> paths) {
        return paths.size() <= Consent.LINES_SHOWN
                ? String.join(", ", paths)
                : String.join(", ", paths.subList(0, Consent.LINES_SHOWN)) + " and "
                  + (paths.size() - Consent.LINES_SHOWN) + " more";
    }

    /// The user's answer as a call gives it (`domain_split`, what they said in the chat): the latest word, over any
    /// answer kept with the XSLT.
    ///
    /// @param given The argument, or null.
    /// @return [#SPLIT] or [#KEEP]; empty when not given.
    /// @throws ToolRefusal if it is neither.
    static Optional<String> answerOf(final String given) {
        if (given == null || given.isBlank()) {
            return Optional.empty();
        }
        final String said = given.strip().toLowerCase(Locale.ROOT);
        if (said.startsWith("split")) {
            return Optional.of(SPLIT);
        }
        if (said.startsWith("keep")) {
            return Optional.of(KEEP);
        }
        throw new ToolRefusal("domain_split is '" + SPLIT + "' or '" + KEEP + "', as the user said, not '" + given
                              + "'");
    }

    /// Whether a call's changes undo a split of a field's Ids: they remove the Domain written beside one of its Ids
    /// (seen in an eval: the user said in the chat to keep the Ids whole, the agent's changes took the split out,
    /// and the answer kept with the XSLT split them again on every save).
    ///
    /// @param changes The call's changes, or null.
    /// @param found   A field whose values carry a domain.
    /// @return True if they do.
    static boolean undoneIn(final Object changes, final QualifiedIds found) {
        if (!(changes instanceof final Map<?, ?> map)) {
            return false;
        }
        final List<Map<String, Object>> entries = new ArrayList<>(entries(map.get("common")));
        entries(map.get("events")).forEach(rule -> entries.addAll(entries(rule.get("fields"))));
        final List<String> domains = found.paths().stream()
                .filter(path -> path.endsWith("Id"))
                .map(DomainSplits::domainPath)
                .toList();
        return entries.stream().anyMatch(entry -> Boolean.TRUE.equals(entry.get("remove"))
                                                  && domains.contains(String.valueOf(entry.get("path"))));
    }

    /// The answer a mapping kept with an XSLT gives the question for these fields: [#SPLIT] where an Id it writes
    /// from one of them is given `strip_domain`, [#KEEP] where one is written whole.
    ///
    /// @param mapping A kept translation mapping (`common`, `events` with their `fields`).
    /// @param fields  The fields whose values carry a domain.
    /// @return The answers it gives (none, one, or both where its Ids differ).
    static Set<String> answersIn(final Object mapping, final Set<String> fields) {
        final Set<String> answers = new TreeSet<>();
        if (!(mapping instanceof final Map<?, ?> map)) {
            return answers;
        }
        final List<Map<String, Object>> entries = new ArrayList<>(entries(map.get("common")));
        entries(map.get("events")).forEach(rule -> entries.addAll(entries(rule.get("fields"))));
        for (final Map<String, Object> entry : entries) {
            final String path = String.valueOf(entry.get("path"));
            final String grouped = splitGroupField(entry);
            if (grouped != null && fields.contains(grouped)) {
                // A repeated Id split in a group over the field's values.
                answers.add(SPLIT);
                continue;
            }
            if (!path.endsWith("User/Id") || !fields.contains(String.valueOf(entry.get("field")))) {
                continue;
            }
            final Object transform = entry.get("transform");
            if ("strip_domain".equals(transform)) {
                answers.add(SPLIT);
            } else if (transform == null) {
                answers.add(KEEP);
            }
        }
        return answers;
    }

    /// @param found A field whose values carry a domain.
    /// @return What the user's answer is kept against: the field and the Ids it goes into, so a field going into
    /// another Id is asked about again.
    static String key(final QualifiedIds found) {
        return found.field() + " -> " + String.join(", ", found.paths());
    }

    /// @param mapping The mapping.
    /// @param found   A field whose values carry a domain.
    /// @param schema  The event-logging schema.
    /// @return The mapping with the field's Ids given `strip_domain`, and a Domain beside each, from the field with
    /// `domain`, where the schema has one and the rule (or the common entries) maps none; with what was done.
    @SuppressWarnings("unchecked")
    static Split split(final TranslationMapping mapping, final QualifiedIds found, final EventSchema schema) {
        final Map<String, Object> json = new LinkedHashMap<>(mapping.toJson(DumpMode.COMPACT));
        final List<String> done = new ArrayList<>();
        final List<Map<String, Object>> common = entries(json.get("common"));
        json.put("common", splitIn(common, List.of(), found, schema, done));
        final List<Map<String, Object>> rules = new ArrayList<>();
        for (final Map<String, Object> rule : entries(json.get("events"))) {
            final Map<String, Object> copy = new LinkedHashMap<>(rule);
            copy.put("fields", splitIn(entries(rule.get("fields")), common, found, schema, done));
            rules.add(copy);
        }
        json.put("events", rules);
        return new Split(TranslationMapping.fromJson(json), List.copyOf(done));
    }

    /// The split undone, as the user said in the chat (`domain_split` Keep): each user's Id given `strip_domain`
    /// written whole again, and the Domain written beside it from the same field with `domain` taken out.
    ///
    /// @param mapping The mapping.
    /// @return The mapping with its users' Ids whole, with what was done (none when nothing was split).
    @SuppressWarnings("unchecked")
    static Split unsplit(final TranslationMapping mapping) {
        final Map<String, Object> json = new LinkedHashMap<>(mapping.toJson(DumpMode.COMPACT));
        final List<String> done = new ArrayList<>();
        json.put("common", unsplitIn(entries(json.get("common")), done));
        final List<Map<String, Object>> rules = new ArrayList<>();
        for (final Map<String, Object> rule : entries(json.get("events"))) {
            final Map<String, Object> copy = new LinkedHashMap<>(rule);
            copy.put("fields", unsplitIn(entries(rule.get("fields")), done));
            rules.add(copy);
        }
        json.put("events", rules);
        return done.isEmpty()
                ? new Split(mapping, List.of())
                : new Split(TranslationMapping.fromJson(json), List.copyOf(done));
    }

    private static List<Map<String, Object>> unsplitIn(final List<Map<String, Object>> entries,
                                                       final List<String> done) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final Map<String, Object> entry : entries) {
            final String path = String.valueOf(entry.get("path"));
            final String grouped = splitGroupField(entry);
            if (grouped != null) {
                out.add(repeatOf(entry, grouped));
                record(done, path + "/Id: written whole");
                record(done, path + "/Domain: taken out");
            } else if (path.endsWith("User/Id") && "strip_domain".equals(entry.get("transform"))) {
                final Map<String, Object> whole = new LinkedHashMap<>(entry);
                whole.remove("transform");
                out.add(whole);
                record(done, path + ": written whole");
            } else if (path.endsWith("User/Domain") && "domain".equals(entry.get("transform"))
                       && entries.stream().anyMatch(id -> idPathOf(path).equals(id.get("path"))
                                                          && "strip_domain".equals(id.get("transform"))
                                                          && Objects.equals(id.get("field"), entry.get("field")))) {
                record(done, path + ": taken out");
            } else {
                out.add(entry);
            }
        }
        return out;
    }

    // The Id beside a Domain.
    private static String idPathOf(final String domainPath) {
        return domainPath.substring(0, domainPath.length() - "Domain".length()) + "Id";
    }

    // One list of entries (the common ones, or a rule's), split; `also` are entries that are written as well.
    private static List<Map<String, Object>> splitIn(final List<Map<String, Object>> entries,
                                                     final List<Map<String, Object>> also,
                                                     final QualifiedIds found,
                                                     final EventSchema schema,
                                                     final List<String> done) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final Map<String, Object> entry : entries) {
            final Object path = entry.get("path");
            if (!found.field().equals(entry.get("field")) || !found.paths().contains(path)
                || entry.get("transform") != null) {
                out.add(entry);
                continue;
            }
            final String domainPath = domainPath((String) path);
            final boolean domainFree = !mapped(entries, domainPath) && !mapped(also, domainPath)
                                       && inSchema(schema, domainPath);
            final String holder = repeatedHolder(entry, schema);
            if (holder != null && domainFree) {
                // A repeat writes its User once per value, so nothing else may be mapped below it: a group over the
                // field's values writes each User with both (seen, chat 183: a Domain beside a repeated recipient's
                // Id, which the build's own check refused).
                out.add(group(entry, holder, found.field()));
                record(done, path + ": a for_each group over '" + found.field() + "' at " + holder + ", each "
                             + lastName(holder) + " with its Id (strip_domain) and Domain (domain), in place of the "
                             + "repeat");
                continue;
            }
            final Map<String, Object> id = new LinkedHashMap<>(entry);
            id.put("transform", "strip_domain");
            out.add(id);
            if (!domainFree) {
                record(done, path + ": strip_domain");
                continue;
            }
            final Map<String, Object> domain = new LinkedHashMap<>();
            domain.put("path", domainPath);
            domain.put("field", found.field());
            domain.put("transform", "domain");
            out.add(domain);
            record(done, path + ": strip_domain, and " + domainPath + " from '" + found.field() + "' (domain)");
        }
        return out;
    }

    // The element a repeat entry writes once per value, where it holds the Id (a User, which may occur more than
    // once in its parent, the Id itself not): null for an entry that isn't a repeat, or whose Id itself repeats.
    private static String repeatedHolder(final Map<String, Object> entry, final EventSchema schema) {
        if (!Boolean.TRUE.equals(entry.get("repeat"))) {
            return null;
        }
        final String path = ToolText.strip(String.valueOf(entry.get("path")), "/");
        final List<Child> chain;
        try {
            chain = schema.resolve(path);
        } catch (final IllegalArgumentException e) {
            return null;
        }
        if (chain.size() < 2 || chain.getLast().repeatable() || !chain.get(chain.size() - 2).repeatable()) {
            return null;
        }
        return path.substring(0, path.lastIndexOf('/'));
    }

    // A group over the field's values at the element holding the Id: the Id from each value with strip_domain (the
    // entry's other settings kept), the Domain from it with domain.
    private static Map<String, Object> group(final Map<String, Object> entry,
                                             final String holder,
                                             final String field) {
        final Map<String, Object> id = new LinkedHashMap<>(entry);
        id.remove("field");
        id.remove("repeat");
        id.put("path", "Id");
        id.put("item", true);
        id.put("transform", "strip_domain");
        final Map<String, Object> domain = new LinkedHashMap<>();
        domain.put("path", "Domain");
        domain.put("item", true);
        domain.put("transform", "domain");
        final Map<String, Object> group = new LinkedHashMap<>();
        group.put("path", holder);
        group.put("for_each", Map.of(ItemSource.ARRAY, field));
        group.put("fields", List.of(id, domain));
        return group;
    }

    // The field a group made by a split is over ([#group]), or null for any other entry.
    private static String splitGroupField(final Map<String, Object> entry) {
        if (!(entry.get("for_each") instanceof final Map<?, ?> forEach) || forEach.size() != 1
            || !(forEach.get(ItemSource.ARRAY) instanceof final String field)) {
            return null;
        }
        final List<Map<String, Object>> fields = entries(entry.get("fields"));
        if (fields.size() != 2 || !String.valueOf(entry.get("path")).endsWith("User")) {
            return null;
        }
        final boolean split = fields.stream().anyMatch(each -> isItem(each, "Id", "strip_domain"))
                              && fields.stream().anyMatch(each -> isItem(each, "Domain", "domain"));
        return split
                ? field
                : null;
    }

    private static boolean isItem(final Map<String, Object> entry, final String path, final String transform) {
        return path.equals(entry.get("path")) && Boolean.TRUE.equals(entry.get("item"))
               && transform.equals(entry.get("transform"));
    }

    // A split group as the repeat it was made from: the Id written whole from each value of the field.
    private static Map<String, Object> repeatOf(final Map<String, Object> group, final String field) {
        final Map<String, Object> id = new LinkedHashMap<>(entries(group.get("fields")).stream()
                .filter(each -> "Id".equals(each.get("path")))
                .findFirst()
                .orElseThrow());
        id.remove("path");
        id.remove("item");
        id.remove("transform");
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("path", group.get("path") + "/Id");
        out.put("field", field);
        out.putAll(id);
        out.put("repeat", true);
        return out;
    }

    private static String lastName(final String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    // The Domain beside an Id.
    private static String domainPath(final String idPath) {
        return idPath.substring(0, idPath.length() - "Id".length()) + "Domain";
    }

    private static void record(final List<String> done, final String said) {
        if (!done.contains(said)) {
            done.add(said);
        }
    }

    private static boolean mapped(final List<Map<String, Object>> entries, final String path) {
        return entries.stream().anyMatch(entry -> path.equals(entry.get("path")));
    }

    private static boolean inSchema(final EventSchema schema, final String path) {
        try {
            schema.resolve(path);
            return true;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> entries(final Object given) {
        final List<Map<String, Object>> out = new ArrayList<>();
        if (given instanceof final List<?> list) {
            list.stream().filter(Map.class::isInstance).forEach(item -> out.add((Map<String, Object>) item));
        }
        return out;
    }

    // As strip_domain writes it: no DOMAIN\ before it, no @domain after it.
    private static String account(final String value) {
        return value.replaceFirst("^[^\\\\]*\\\\", "").replaceFirst("@.*$", "");
    }

    // As domain writes it: what is before the \, else after the @.
    private static String domain(final String value) {
        final int slash = value.indexOf('\\');
        if (slash >= 0) {
            return value.substring(0, slash);
        }
        final int at = value.indexOf('@');
        return at >= 0
                ? value.substring(at + 1)
                : "";
    }

    /// A mapping split, and what was done to it.
    ///
    /// @param mapping The mapping.
    /// @param done    What changed, an entry a line.
    record Split(TranslationMapping mapping, List<String> done) {

    }
}
