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
import stroom.ai.impl.tools.generation.TranslationMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// A sample's records as one check of a mapping reads them, with what the one evaluator works out of each record
/// worked out once and shared by every part of the check: the values the mapping's extractions give it
/// ([LocalCheck#extractedValues(TranslationMapping, Object)]), the rule that takes it
/// ([LocalCheck#ruleOf(TranslationMapping, Object, Map)]), and whether a list of conditions (an event type's way of
/// recognising it) all hold for it. Seen in eval case 36 on Haiku: 60 extract entries run over the same 1,000 records
/// some 55 times in one check, and once more for each way of each confirmed event type, a minute of a two-minute
/// build.
///
/// Everything here is of the one mapping it was made for: [#forMapping] makes another mapping's, sharing only what
/// that mapping reads and tests alike, so a changed mapping never reads a value worked out for another.
public final class CheckedRecords {

    // A record's rule not worked out yet (null is a rule that can't be told).
    private static final Object UNSET = new Object();
    // Whether a list of conditions holds for a record, as kept: not worked out yet, false, true, can't be told.
    private static final byte NOT_TESTED = 0;
    private static final byte FALSE = 1;
    private static final byte TRUE = 2;
    private static final byte UNTOLD = 3;

    private final MappingConditions conditions;
    private final List<Object> records;
    private final Extracted extracted;
    private final Object[] rules;
    private final Map<List<Condition>, byte[]> held;

    private CheckedRecords(final MappingConditions conditions,
                           final List<Object> records,
                           final Extracted extracted,
                           final Object[] rules,
                           final Map<List<Condition>, byte[]> held) {
        this.conditions = conditions;
        this.records = records;
        this.extracted = extracted;
        this.rules = rules;
        this.held = held;
    }

    /// @param mapping The mapping.
    /// @param records The sample's records, as [LocalCheck#sampleRecords] reads them for it.
    /// @return The records, nothing worked out of them yet.
    public static CheckedRecords of(final TranslationMapping mapping, final List<Object> records) {
        return new CheckedRecords(MappingConditions.of(mapping), records, new Extracted(), unsetRules(records.size()),
                new IdentityHashMap<>());
    }

    /// The same records as another mapping reads them, sharing what it reads and tests alike: the records and
    /// their extracted values when it reads records as this one does (input, held fields, namespace, record, root,
    /// pairs, extract and for_each the same); whether a list of conditions holds when it also tests them alike (nil
    /// values and unescape transforms the same); each record's rule when its rules are the same too.
    ///
    /// @param changed The other mapping.
    /// @return Its records, or null when it reads records otherwise (they are to be read again).
    public CheckedRecords forMapping(final TranslationMapping changed) {
        if (!readsAlike(mapping(), changed)) {
            return null;
        }
        final MappingConditions other = MappingConditions.of(changed);
        final boolean testsAlike = conditions.testsAlike(other);
        return new CheckedRecords(other, records, extracted, testsAlike && conditions.rulesAlike(other)
                ? rules
                : unsetRules(records.size()), testsAlike
                ? held
                : new IdentityHashMap<>());
    }

    /// @return The mapping.
    public TranslationMapping mapping() {
        return conditions.mapping();
    }

    /// @return The mapping's conditions, ready to test.
    public MappingConditions conditions() {
        return conditions;
    }

    /// @return The records.
    public List<Object> records() {
        return records;
    }

    /// @return How many records there are.
    public int size() {
        return records.size();
    }

    /// @param n A record's index.
    /// @return The record.
    public Object record(final int n) {
        return records.get(n);
    }

    /// @return Each record's extracted values ([LocalCheck#extractedValues(TranslationMapping, Object)], not to be
    /// changed; null for a record whose extractions can't be run here), or null when the mapping has no extract
    /// entries.
    public List<Map<String, String>> extracted() {
        if (mapping().extract().isEmpty()) {
            return null;
        }
        return extracted.of(mapping(), records);
    }

    /// @param n A record's index.
    /// @return Its extracted values (not to be changed), or null when the mapping has no extract entries or they
    /// can't be run here.
    public Map<String, String> extracted(final int n) {
        final List<Map<String, String>> all = extracted();
        return all == null
                ? null
                : all.get(n);
    }

    /// @param n A record's index.
    /// @return The rule taking it, as [LocalCheck#ruleOf(TranslationMapping, Object, Map)] names it.
    public String ruleOf(final int n) {
        if (rules[n] == UNSET) {
            rules[n] = conditions.ruleOf(records.get(n), extracted(n));
        }
        return (String) rules[n];
    }

    /// Whether every condition of a list holds for a record, kept by the list (the same list, not an equal one).
    ///
    /// @param when The conditions.
    /// @param n    A record's index.
    /// @return Whether they all hold; null when one can't be told and none fails.
    public Boolean holds(final List<Condition> when, final int n) {
        final byte[] kept = held.computeIfAbsent(when, list -> new byte[records.size()]);
        if (kept[n] == NOT_TESTED) {
            kept[n] = tested(when, n);
        }
        return switch (kept[n]) {
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            default -> null;
        };
    }

    // The conditions tested on a record: false at the first that fails, else untold if one can't be told.
    private byte tested(final List<Condition> when, final int n) {
        boolean untold = false;
        for (final Condition condition : when) {
            final Boolean one = conditions.holds(condition, records.get(n), extracted(n));
            if (Boolean.FALSE.equals(one)) {
                return FALSE;
            }
            untold |= one == null;
        }
        return untold
                ? UNTOLD
                : TRUE;
    }

    private static Object[] unsetRules(final int size) {
        final Object[] out = new Object[size];
        Arrays.fill(out, UNSET);
        return out;
    }

    // Whether two mappings read the sample into the same records with the same extracted values.
    private static boolean readsAlike(final TranslationMapping one, final TranslationMapping other) {
        return Objects.equals(one.input(), other.input()) && Objects.equals(one.root(), other.root())
               && Objects.equals(one.record(), other.record())
               && Objects.equals(one.xmlNamespace(), other.xmlNamespace())
               && Objects.equals(one.jsonLayout(), other.jsonLayout())
               && one.jsonFields().equals(other.jsonFields()) && one.xmlFields().equals(other.xmlFields())
               && one.pairs().equals(other.pairs()) && one.extract().equals(other.extract())
               && Objects.equals(one.forEach(), other.forEach());
    }


    // --------------------------------------------------------------------------------


    // Each record's extracted values, worked out the first time they are wanted.
    private static final class Extracted {

        private List<Map<String, String>> values;

        private List<Map<String, String>> of(final TranslationMapping mapping, final List<Object> records) {
            if (values == null) {
                final List<Map<String, String>> out = new ArrayList<>(records.size());
                for (final Object record : records) {
                    final Map<String, String> found = LocalCheck.extractedValues(mapping, record);
                    out.add(found == null
                            ? null
                            : Collections.unmodifiableMap(found));
                }
                values = Collections.unmodifiableList(out);
            }
            return values;
        }
    }
}
