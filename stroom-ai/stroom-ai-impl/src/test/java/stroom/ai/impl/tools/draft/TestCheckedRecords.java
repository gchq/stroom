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
import stroom.ai.impl.tools.sample.Placeholders;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A sample's records with each record's extracted values, rule and ways' verdicts worked out once for a check: the
/// same as the one evaluator gives them record by record, and shared with a changed mapping only where it reads and
/// tests them alike.
class TestCheckedRecords extends DraftFixtures {

    // JSON lines holding JSON (event) and XML (payload) in strings, and key/value pairs in a message read by
    // extractions, one of them reading a value the held JSON gives.
    private static final String MAPPING = """
            {"input": "json", "json_layout": "lines", "json_fields": ["event"], "xml_fields": ["payload"],
             "nil_values": %s,
             "extract": [{"field": "msg", "regex": "user=(\\\\S+)", "names": ["user"]},
                         {"field": "msg", "regex": "act=(\\\\S+)", "names": ["act"]},
                         {"field": "event.detail", "regex": "^(\\\\w+)", "names": ["detail_word"]}],
             "common": [{"path": "EventTime/TimeCreated", "field": "time"},
                        {"path": "EventSource/User/Id", "field": "user"%s},
                        {"path": "EventDetail/Description", "field": "note", "transform": "unescape_quotes"}],
             "events": [{"name": "quoted", "when": [{"field": "note", "equals": "say \\"hi\\""}],
                         "fields": [{"path": "EventDetail/TypeId", "value": "Quoted"}]},
                        {"name": "login", "when": [{"field": "act", "equals": "login"},
                                                   {"field": "user", "present": true}],
                         "fields": [{"path": "EventDetail/TypeId", "value": "Login"}]},
                        {"name": "held", "when": [{"field": "event.kind", "equals": "audit"}],
                         "fields": [{"path": "EventDetail/TypeId", "value": "Held"}]},
                        {"name": "soap", "when": [{"field": "payload//User", "present": true}],
                         "fields": [{"path": "EventDetail/TypeId", "value": "Soap"}]},
                        {"name": "detail", "when": [{"field": "detail_word", "equals": "Disk"}],
                         "fields": [{"path": "EventDetail/TypeId", "value": "Detail"}]},
                        {"name": "rest", "allow_unknown": "the rest",
                         "fields": [{"path": "EventDetail/TypeId", "value": "Rest"}]}]}""";

    private static final String SAMPLE = String.join("\n",
            "{\"time\": \"t1\", \"msg\": \"user=alice act=login\"}",
            "{\"time\": \"t2\", \"msg\": \"user=- act=login\"}",
            "{\"time\": \"t3\", \"msg\": \"act=logout\", \"event\": " + ToolJson.dumps("{\"kind\": \"audit\"}") + "}",
            "{\"time\": \"t4\", \"msg\": \"\", \"payload\": "
            + ToolJson.dumps("<Envelope><Body><User>bob</User></Body></Envelope>") + "}",
            "{\"time\": \"t5\", \"msg\": \"x\", \"event\": " + ToolJson.dumps("{\"detail\": \"Disk full\"}") + "}",
            "{\"time\": \"t6\", \"note\": \"say \\\"\\\"hi\\\"\\\"\"}",
            "{\"time\": \"t7\"}") + "\n";

    private static TranslationMapping mapping(final String nilValues, final String userTransform) {
        return mapping(json(MAPPING.formatted(nilValues, userTransform)));
    }

    private static List<Object> records(final TranslationMapping mapping) {
        return LocalCheck.sampleRecords(mapping, SAMPLE, null).records();
    }

    @Test
    void eachRecordsExtractedValuesRuleAndVerdictsAreTheEvaluatorsOwn() {
        final TranslationMapping m = mapping("[]", "");
        final List<Object> records = records(m);
        final CheckedRecords checked = CheckedRecords.of(m, records);
        final List<Condition> loginWay = m.events().get(1).when();

        final List<String> rules = new ArrayList<>();
        for (int n = 0; n < records.size(); n++) {
            final Map<String, String> extracted = LocalCheck.extractedValues(m, records.get(n));
            assertThat(checked.extracted(n)).as("record %s", n).isEqualTo(extracted);
            assertThat(checked.ruleOf(n)).as("record %s", n)
                    .isEqualTo(LocalCheck.ruleOf(m, records.get(n), extracted));
            assertThat(checked.holds(loginWay, n)).as("record %s", n)
                    .isEqualTo(holdsOneByOne(m, loginWay, records.get(n), extracted));
            rules.add(checked.ruleOf(n));
        }
        // The extracted fields, the held JSON and XML, an extraction of a held value and an unescaped field each
        // tell a record's rule.
        assertThat(rules).containsExactly("login", "login", "held", "soap", "detail", "quoted", "rest");
    }

    @Test
    void aRecordScopeConditionIsNotToldHere() {
        final TranslationMapping m = mapping(json("""
                {"input": "json", "json_layout": "lines", "for_each": "items",
                 "common": [{"path": "EventTime/TimeCreated", "field": "time", "scope": "record"}],
                 "events": [{"name": "one", "when": [{"field": "kind", "equals": "a", "scope": "record"}],
                             "fields": [{"path": "EventDetail/TypeId", "value": "One"}]}]}"""));
        final List<Object> records = LocalCheck.sampleRecords(m, "{\"time\": \"t\", \"kind\": \"a\", \"items\": "
                                                                 + "[{\"x\": 1}]}\n", null).records();
        final CheckedRecords checked = CheckedRecords.of(m, records);

        assertThat(checked.holds(m.events().getFirst().when(), 0)).isNull();
        assertThat(checked.ruleOf(0)).isNull();
    }

    @Test
    void forEachItemsGiveTheirOwnExtractedValues() {
        // An entry for_each repeats reads its item's extracted values, not the record's.
        final TranslationMapping m = mapping(json("""
                {"input": "json", "json_layout": "lines", "for_each": "items",
                 "extract": [{"field": "msg", "regex": "user=(\\\\S+)", "names": ["user"]}],
                 "common": [{"path": "EventTime/TimeCreated", "field": "time", "scope": "record"},
                            {"path": "EventSource/User/Id", "field": "user"}],
                 "events": [{"name": "one", "fields": [{"path": "EventDetail/TypeId", "value": "One"}]}]}"""));
        final List<Object> records = LocalCheck.sampleRecords(m, String.join("\n",
                "{\"time\": \"t\", \"msg\": \"user=rec\", \"items\": [{\"msg\": \"user=-\"}, {\"msg\": \"user=-\"}]}",
                "{\"time\": \"t\", \"msg\": \"user=rec\", \"items\": [{\"msg\": \"user=-\"}, {\"msg\": \"user=amy\"}]}")
                                                                 + "\n", null).records();

        assertThat(LocalCheck.placeholders(CheckedRecords.of(m, records), SCHEMA))
                .extracting(Placeholders.Found::value)
                .containsExactly("-");
    }

    @Test
    void aMappingReadingRecordsAlikeSharesTheirExtractedValuesAndTheirRulesWhenItsRulesAreAlike() {
        final TranslationMapping m = mapping("[]", "");
        final CheckedRecords checked = CheckedRecords.of(m, records(m));
        for (int n = 0; n < checked.size(); n++) {
            checked.ruleOf(n);
        }
        // A domain split: a transform on an entry, the conditions as they were.
        final TranslationMapping split = mapping("[]", ", \"transform\": \"strip_domain\"");

        final CheckedRecords again = checked.forMapping(split);

        assertThat(again).isNotNull();
        assertThat(again.mapping()).isSameAs(split);
        assertThat(again.records()).isSameAs(checked.records());
        assertThat(again.extracted()).isSameAs(checked.extracted());
        assertThat(rulesOf(again)).isEqualTo(rulesOf(CheckedRecords.of(split, records(split))));
    }

    @Test
    void nilValuesChangedTellRulesAgainOverTheSameExtractedValues() {
        final TranslationMapping m = mapping("[]", "");
        final CheckedRecords checked = CheckedRecords.of(m, records(m));
        final List<Condition> loginWay = m.events().get(1).when();
        assertThat(checked.ruleOf(1)).isEqualTo("login");
        assertThat(checked.holds(loginWay, 1)).isTrue();
        // '-' left out (nil_values): the login rule's 'user present' no longer holds for the record with user=-.
        final TranslationMapping leftOut = mapping("[\"-\"]", "");

        final CheckedRecords again = checked.forMapping(leftOut);

        assertThat(again).isNotNull();
        assertThat(again.extracted()).isSameAs(checked.extracted());
        assertThat(again.ruleOf(1)).isEqualTo("rest");
        assertThat(again.holds(leftOut.events().get(1).when(), 1)).isFalse();
        assertThat(rulesOf(again)).isEqualTo(rulesOf(CheckedRecords.of(leftOut, records(leftOut))));
    }

    @Test
    void aMappingReadingRecordsOtherwiseReadsThemAgain() {
        final TranslationMapping m = mapping("[]", "");
        final CheckedRecords checked = CheckedRecords.of(m, records(m));
        final TranslationMapping otherExtract = mapping(json(MAPPING.formatted("[]", "")
                .replace("user=(", "usr=(")));

        assertThat(checked.forMapping(otherExtract)).isNull();
    }

    @Test
    void rulesChangedAreToldAgain() {
        final TranslationMapping m = mapping("[]", "");
        final CheckedRecords checked = CheckedRecords.of(m, records(m));
        assertThat(checked.ruleOf(0)).isEqualTo("login");
        final TranslationMapping renamed = mapping(json(MAPPING.formatted("[]", "")
                .replace("\"name\": \"login\"", "\"name\": \"logon\"")));

        assertThat(checked.forMapping(renamed).ruleOf(0)).isEqualTo("logon");
    }

    private static List<String> rulesOf(final CheckedRecords checked) {
        final List<String> rules = new ArrayList<>();
        for (int n = 0; n < checked.size(); n++) {
            rules.add(checked.ruleOf(n));
        }
        return rules;
    }

    // The conditions tested one by one, as CatalogueKept.holds tests them.
    private static Boolean holdsOneByOne(final TranslationMapping m,
                                         final List<Condition> when,
                                         final Object record,
                                         final Map<String, String> extracted) {
        boolean untold = false;
        for (final Condition condition : when) {
            final Boolean one = LocalCheck.holds(m, condition, record, extracted);
            if (Boolean.FALSE.equals(one)) {
                return false;
            }
            untold |= one == null;
        }
        return untold
                ? null
                : true;
    }
}
