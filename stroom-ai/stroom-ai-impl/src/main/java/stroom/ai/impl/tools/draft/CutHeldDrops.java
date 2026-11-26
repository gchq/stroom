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

import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.DropRule;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.JsonFieldNames;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.ToolJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// A mapping's drop rules (`drop_when`, and events rules with `drop: true`) whose conditions read a key of a held
/// JSON field (`json_fields`) that processing's string cut leaves unreadable on some sample records: Stroom's
/// JSONParser cuts a string longer than its `stringTruncateLength` (10,000 characters by default), the cut text is no
/// longer JSON, and the XSLT's `mcp:json-to-xml` of it gives nothing, so none of its keys are there. A condition on
/// one then never holds and a negated one always does: the rule drops records it wouldn't read whole, or keeps those
/// it would drop (seen in testing: a drop rule on `event.appender_ref` of an aggregated feed). The mapping's check
/// reads the records whole, so this compares the two.
public final class CutHeldDrops {

    private CutHeldDrops() {
        // Static only.
    }

    /// The warnings for a mapping's drop rules whose verdict on some sample record differs once a held JSON field
    /// they read is cut at the length given.
    ///
    /// @param checked The mapping's records, read whole (their held JSON fields' keys added).
    /// @param cut     Where processing cuts JSON strings (the pipeline's `stringTruncateLength`).
    /// @return One warning a rule, naming the held field, how many records and what happens to them; none when no
    /// rule's verdict differs (or the mapping reads no JSON).
    public static List<String> warnings(final CheckedRecords checked, final int cut) {
        final TranslationMapping mapping = checked.mapping();
        final List<String> out = new ArrayList<>();
        if (cut <= 0 || !"json".equals(mapping.input()) || mapping.jsonFields().isEmpty()) {
            return out;
        }
        final Map<String, List<Condition>> drops = new LinkedHashMap<>();
        for (final DropRule drop : mapping.dropWhen()) {
            drops.put("drop_when '" + drop.reason() + "'", drop.when());
        }
        for (final EventRule rule : mapping.events()) {
            if (rule.drop()) {
                drops.put("rule '" + rule.name() + "'", rule.when());
            }
        }
        drops.forEach((name, when) -> warning(checked, cut, name, when).ifPresent(out::add));
        return out;
    }

    // The warning for one drop rule, or none when its verdict is the same on every record read as processing reads
    // it.
    private static Optional<String> warning(final CheckedRecords checked,
                                                      final int cut,
                                                      final String name,
                                                      final List<Condition> when) {
        final Set<String> held = new LinkedHashSet<>();
        for (final Condition c : when) {
            final String json = MappingFacts.heldJsonField(checked.mapping().jsonFields(), c.field());
            if (json != null && !json.equals(c.field())) {
                held.add(json);
            }
        }
        if (held.isEmpty()) {
            return Optional.empty();
        }
        int changed = 0;
        String cutField = null;
        boolean droppedWhole = false;
        for (int n = 0; n < checked.size(); n++) {
            if (!(checked.record(n) instanceof final Map<?, ?> record)) {
                continue;
            }
            final List<String> unreadable = held.stream()
                    .filter(json -> unreadable(record.get(json), cut))
                    .toList();
            if (unreadable.isEmpty()) {
                continue;
            }
            final Boolean whole = checked.holds(when, n);
            final Boolean asCut = holds(checked, when, cutRecord(record, unreadable, cut), n);
            if (whole != null && asCut != null && !whole.equals(asCut)) {
                changed++;
                if (cutField == null) {
                    cutField = unreadable.getFirst();
                    droppedWhole = whole;
                }
            }
        }
        if (changed == 0) {
            return Optional.empty();
        }
        return Optional.of("drop rule " + name + ": '" + cutField + "' is longer than stringTruncateLength ("
                                     + String.format("%,d", cut) + " characters) on " + changed + " sample "
                                     + "record(s), so in processing none of its keys can be read there (the cut text "
                                     + "isn't JSON): " + (droppedWhole
                ? "the rule drops them read whole, but in processing doesn't"
                : "the rule keeps them read whole, but in processing drops them") + ". Raise the pipeline's "
                                     + "stringTruncateLength (long_values=whole), or test a field outside '"
                                     + cutField + "'");
    }

    // Whether a held value is a string processing cuts into text that isn't JSON.
    private static boolean unreadable(final Object value, final int cut) {
        if (!(value instanceof final String text) || text.length() <= cut) {
            return false;
        }
        try {
            SampleProfiler.loadsDecoded(text.substring(0, cut));
            return false;
        } catch (final ToolJson.JsonDecodeException e) {
            return true;
        }
    }

    // The record as processing reads it: each unreadable held field cut, none of its keys there.
    private static Map<String, Object> cutRecord(final Map<?, ?> record, final List<String> unreadable, final int cut) {
        final Map<String, Object> out = new LinkedHashMap<>();
        record.forEach((key, value) -> {
            final String name = String.valueOf(key);
            if (unreadable.stream().noneMatch(json -> JsonFieldNames.below(json, name) != null)) {
                out.put(name, value);
            }
        });
        unreadable.forEach(json -> out.put(json, ((String) record.get(json)).substring(0, cut)));
        return out;
    }

    // Whether every condition holds on the record; null when one can't be told and none fails.
    private static Boolean holds(final CheckedRecords checked,
                                 final List<Condition> when,
                                 final Map<String, Object> record,
                                 final int n) {
        boolean unknown = false;
        for (final Condition c : when) {
            final Boolean verdict = checked.conditions().holds(c, record, checked.extracted(n));
            if (verdict == null) {
                unknown = true;
            } else if (!verdict) {
                return false;
            }
        }
        return unknown
                ? null
                : Boolean.TRUE;
    }
}
