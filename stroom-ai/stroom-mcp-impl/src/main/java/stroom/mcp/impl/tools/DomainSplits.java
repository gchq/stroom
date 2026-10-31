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

import stroom.ai.impl.tools.draft.LocalCheck.QualifiedIds;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.validation.EventSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        final String fields = found.stream()
                .map(each -> "'" + each.field() + "' (" + String.join(", ", each.paths()) + ")")
                .collect(Collectors.joining(", "));
        return "Split the domain off the users' Ids from " + fields + "? E.g. " + example + ": the Id "
               + account(example) + ", the Domain " + domain(example) + ".";
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
            final Map<String, Object> id = new LinkedHashMap<>(entry);
            id.put("transform", "strip_domain");
            out.add(id);
            final String domainPath = ((String) path).substring(0, ((String) path).length() - "Id".length())
                                      + "Domain";
            if (mapped(entries, domainPath) || mapped(also, domainPath) || !inSchema(schema, domainPath)) {
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
    private static List<Map<String, Object>> entries(final Object given) {
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
