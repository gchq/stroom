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
import stroom.ai.impl.tools.generation.XPathText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// A mapping's conditions as the one evaluator ([LocalCheck#holds]) tests them, with what the tests need of the
/// mapping worked out once rather than once a test: the fields its extractions produce, the unescape transform each
/// field is compared through ([MappingFacts#unescapeOf]), its nil values as XPath literals give them, and its rules
/// in the order a record is tried against them. Seen in eval case 36 on Haiku: a mapping of 34 rules over 1,000
/// records, each test looking up the field's transform among every entry, a fifth of a build's time.
///
/// The mapping is immutable, so one of these stands for it for as long as it is held.
public final class MappingConditions {

    private final TranslationMapping mapping;
    private final Set<String> derived;
    private final Map<String, String> unescapes;
    private final List<String> nilTexts;
    private final List<Map.Entry<String, List<Condition>>> candidates;

    private MappingConditions(final TranslationMapping mapping) {
        this.mapping = mapping;
        final Set<String> names = new HashSet<>();
        mapping.extract().forEach(ex -> ex.names().stream()
                .filter(name -> name != null && !name.isEmpty())
                .forEach(names::add));
        this.derived = Set.copyOf(names);
        this.unescapes = MappingFacts.unescapes(mapping);
        this.nilTexts = mapping.nilValues().stream().map(XPathText::literalText).toList();
        final List<Map.Entry<String, List<Condition>>> rules = new ArrayList<>();
        for (final DropRule drop : mapping.dropWhen()) {
            rules.add(Map.entry(LocalCheck.DROP + drop.reason(), drop.when()));
        }
        for (final EventRule rule : mapping.events()) {
            rules.add(Map.entry(rule.name(), rule.when()));
        }
        this.candidates = List.copyOf(rules);
    }

    /// @param mapping A mapping.
    /// @return Its conditions, ready to test.
    public static MappingConditions of(final TranslationMapping mapping) {
        return new MappingConditions(mapping);
    }

    /// @return The mapping.
    public TranslationMapping mapping() {
        return mapping;
    }

    /// @param field A field's name.
    /// @return True if one of the mapping's extractions produces it.
    public boolean derives(final String field) {
        return derived.contains(field);
    }

    /// As [LocalCheck#holds(TranslationMapping, Condition, Object, Map)].
    ///
    /// @param c         The condition.
    /// @param record    A record, as [LocalCheck#sampleRecords] reads them.
    /// @param extracted The values its extractions give it, or null.
    /// @return Whether it holds; null when it can't be told here.
    public Boolean holds(final Condition c, final Object record, final Map<String, String> extracted) {
        if (!LocalCheck.evaluable(c)) {
            return null;
        }
        final List<String> values = conditionValues(c.field(), record, extracted);
        return values == null
                ? null
                : ConditionValues.holds(unescapes.get(c.field()), nilTexts, c, values);
    }

    /// As [LocalCheck#conditionValues(TranslationMapping, String, Object, Map)].
    ///
    /// @param field     A condition's field.
    /// @param record    A record, as [LocalCheck#sampleRecords] reads them.
    /// @param extracted The values its extractions give it, or null.
    /// @return The field's values in the record; null when they can't be told here.
    public List<String> conditionValues(final String field, final Object record, final Map<String, String> extracted) {
        if (derived.contains(field)) {
            return extracted == null || !extracted.containsKey(field)
                    ? null
                    : List.of(extracted.get(field));
        }
        return ConditionValues.values(mapping, field, record);
    }

    /// As [LocalCheck#holdsOver(TranslationMapping, Condition, List)].
    ///
    /// @param c      The condition.
    /// @param values Its field's values in a record.
    /// @return Whether it holds; null when it can't be told here.
    public Boolean holdsOver(final Condition c, final List<String> values) {
        return LocalCheck.evaluable(c)
                ? ConditionValues.holds(unescapes.get(c.field()), nilTexts, c, values)
                : null;
    }

    /// As [LocalCheck#ruleOf(TranslationMapping, Object, Map)].
    ///
    /// @param record    A record.
    /// @param extracted The values its extractions give it, or null.
    /// @return A drop rule (`drop: <reason>`), an event rule's name, "" when none matches, or null when a
    /// condition on the way cannot be told here.
    public String ruleOf(final Object record, final Map<String, String> extracted) {
        for (final Map.Entry<String, List<Condition>> candidate : candidates) {
            final List<Boolean> tests = new ArrayList<>();
            candidate.getValue().forEach(c -> tests.add(holds(c, record, extracted)));
            if (tests.contains(null)) {
                return null;
            }
            if (tests.stream().allMatch(Boolean::booleanValue)) {
                return candidate.getKey();
            }
        }
        return "";
    }

    /// @param other Another mapping's conditions.
    /// @return True if a condition tested by either gives the same answer on the same record and extracted values:
    /// the same nil values, unescape transforms and reading of the records (input, held fields, namespace,
    /// extraction names).
    boolean testsAlike(final MappingConditions other) {
        return nilTexts.equals(other.nilTexts) && unescapes.equals(other.unescapes) && derived.equals(other.derived)
               && mapping.input().equals(other.mapping.input())
               && mapping.xmlNamespace().equals(other.mapping.xmlNamespace())
               && mapping.jsonFields().equals(other.mapping.jsonFields())
               && mapping.xmlFields().equals(other.mapping.xmlFields());
    }

    /// @param other Another mapping's conditions.
    /// @return True if [#ruleOf] gives the same answer for either on the same record and extracted values: the
    /// conditions tested alike ([#testsAlike]), and the same rules, by name and conditions, in the same order.
    boolean rulesAlike(final MappingConditions other) {
        return testsAlike(other) && candidates.equals(other.candidates);
    }
}
