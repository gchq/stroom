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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.JsonFieldNames;
import stroom.ai.impl.tools.sample.SourceTypeFields;
import stroom.ai.impl.tools.sample.SourceTypeScope;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/// Records of an aggregated feed whose source type processing can't read once it cuts their strings: Stroom's
/// JSONParser cuts a string longer than its `stringTruncateLength` (10,000 characters by default), and where the
/// source type field is read inside that string (`event>appender_ref`, a key of the JSON text `event` holds), the cut
/// text is no longer what the reading reads, so the record has no source type. A source type group's pipeline then
/// passes it over untranslated, whatever it holds, and no other group's takes it (seen in eval case 112 on Haiku,
/// 2026-11-26: the kiosk accounts' failed logons, each with a long stack trace, passed over as "(no source type)",
/// while the catalogue and the mapping's check, reading them whole, counted them as logons).
public final class CutSourceTypes {

    private CutSourceTypes() {
        // Static only.
    }

    /// Whether a record's source type is lost to the cut: read whole it has a value, and read as processing reads it
    /// the value differs or is gone.
    ///
    /// @param field   The source type field, as the registry keeps it (`event>appender_ref`).
    /// @param whole   The record read whole (a map of its fields), or null.
    /// @param asCut   The same record as processing reads it, its strings cut, or null.
    /// @return True if its source type is lost.
    public static boolean lost(final String field, final Object whole, final Object asCut) {
        if (field == null || !(whole instanceof final Map<?, ?> wholeRecord)) {
            return false;
        }
        final String read = SourceTypeFields.valueOf(fields(wholeRecord), field);
        if (read == null) {
            return false;
        }
        final String cut = asCut instanceof final Map<?, ?> cutRecord
                ? SourceTypeFields.valueOf(fields(cutRecord), field)
                : null;
        return !Objects.equals(read, cut);
    }

    /// A record read whole, as processing reads it: each string of the input longer than the cut cut there, and the
    /// keys read out of a held field (json_fields) that is cut left out, as the XSLT reads none of them.
    ///
    /// @param mapping The mapping the record was read by (its held JSON fields).
    /// @param record  The record read whole.
    /// @param cut     Where processing cuts strings (the pipeline's `stringTruncateLength`).
    /// @return The record as processing reads it.
    public static Map<String, Object> asCut(final TranslationMapping mapping, final Map<?, ?> record, final int cut) {
        final Set<String> cutFields = record.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof final String text && text.length() > cut)
                .map(entry -> String.valueOf(entry.getKey()))
                .filter(name -> MappingFacts.heldJsonFields(mapping.jsonFields(), name).isEmpty())
                .collect(Collectors.toSet());
        final Map<String, Object> out = new LinkedHashMap<>();
        record.forEach((key, value) -> {
            final String name = String.valueOf(key);
            if (cutFields.contains(name)) {
                out.put(name, ((String) value).substring(0, cut));
            } else if (cutFields.stream().noneMatch(held -> JsonFieldNames.below(held, name) != null)) {
                out.put(name, value);
            }
        });
        return out;
    }

    /// The problem with a mapping of a source type group whose sample records include some of the group's source
    /// types that processing can't read once it cuts their strings, and that no drop rule drops: the group's pipeline
    /// passes them over untranslated.
    ///
    /// @param checked The mapping's records, read whole.
    /// @param scope   The group's (or the build's) source type scope.
    /// @param cut     Where processing cuts strings (the pipeline's `stringTruncateLength`).
    /// @return The problem, naming the field, how many records and the rules they are of; empty when there is none.
    public static Optional<String> problem(final CheckedRecords checked, final SourceTypeScope scope, final int cut) {
        if (checked == null || scope == null || cut <= 0 || !"json".equals(checked.mapping().input())) {
            return Optional.empty();
        }
        final Set<String> dropRules = checked.mapping().events().stream()
                .filter(EventRule::drop)
                .map(EventRule::name)
                .collect(Collectors.toSet());
        final Map<String, Integer> byRule = new TreeMap<>();
        int lost = 0;
        for (int n = 0; n < checked.size(); n++) {
            if (!(checked.record(n) instanceof final Map<?, ?> record) || !scope.inScope(fields(record))) {
                continue;
            }
            final Map<String, Object> asCut = asCut(checked.mapping(), record, cut);
            if (scope.inScope(asCut) || !lost(scope.field(), record, asCut)) {
                continue;
            }
            final String rule = checked.ruleOf(n);
            if (rule != null && (rule.startsWith(LocalCheck.DROP) || dropRules.contains(rule))) {
                // Dropped anyway: passed over, it is untranslated all the same.
                continue;
            }
            lost++;
            byRule.merge(rule == null || rule.isEmpty()
                    ? "uncatalogued"
                    : rule, 1, Integer::sum);
        }
        if (lost == 0) {
            return Optional.empty();
        }
        return Optional.of(said(scope.field(), lost, cut) + " (" + byRule.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(", ")) + ")");
    }

    /// What is said of records whose source type is lost to the cut: why, and the two fixes.
    ///
    /// @param field   The source type field, as the registry keeps it.
    /// @param records How many records.
    /// @param cut     Where processing cuts strings.
    /// @return The text.
    public static String said(final String field, final int records, final int cut) {
        return "the source type field " + field + " is read from a value longer than stringTruncateLength ("
               + String.format("%,d", cut) + " characters) on " + records + " record(s): processing cuts the value "
               + "there, the source type can't be read from the cut text, and the group's pipeline passes those "
               + "records over untranslated, whatever they hold; keep long values whole (long_values=whole, the "
               + "pipeline's stringTruncateLength raised), or read the source type from a field outside the long "
               + "value";
    }

    // A record's fields by name.
    private static Map<String, Object> fields(final Map<?, ?> record) {
        final Map<String, Object> out = new LinkedHashMap<>();
        record.forEach((key, value) -> out.put(String.valueOf(key), value));
        return out;
    }
}
