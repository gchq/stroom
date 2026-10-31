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

import stroom.ai.impl.tools.validation.XPathChecks.Extraction;
import stroom.ai.impl.tools.validation.XPathChecks.Findings;
import stroom.ai.impl.workspace.SafePattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A mapping's xpath inputs and extraction regexes are tried on the sample records as the translation will see
/// them, with the records given as documents (the mapping model is the translation build's).
class TestXPathChecks {

    private static final String JSON_NS = XsltChecks.JSON_NS;
    private static final String EMBEDDED = "[{\"host\": \"app01\", \"event\": \"{\\\"timestamp\\\": "
                                           + "\\\"2026-10-01T10:00:00Z\\\", \\\"user\\\": \\\"alice\\\"}\"},"
                                           + " {\"host\": \"app02\", \"event\": \"{\\\"timestamp\\\": "
                                           + "\\\"2026-10-01T10:05:00Z\\\"}\"}]";
    // As seen in a test environment: an en dash between the parts, a domain user with a backslash, [[AppServer]].
    private static final String MESSAGE = "Sep 27 2026 23:48:10 – User: domain.com\\Bloggs, Joe – "
                                          + "[[AppServer]] Event: [User] Action: [Login] By User: "
                                          + "domain.com\\joe.bloggs (Item Id: 219)";
    // Written from memory with '-', and the brackets escaped once, as XPath reads them.
    private static final String DASHED = "^(\\S+ \\d+ \\d+ \\S+) - User: (.+?) - \\[\\[(\\w+)\\]\\] Event: "
                                         + "\\[(\\w+)\\] Action: \\[(\\w+)\\] By User: (\\S+) \\(Item Id: (\\d+)\\)$";
    private static final List<String> NAMES = List.of("when", "subject", "server", "kind", "action", "user", "item");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void testJsonValuesAreWrittenAsTheParserDoes() {
        final Map<String, Object> value = new LinkedHashMap<>();
        value.put("a", "x<y");
        value.put("n", 2);
        value.put("ok", true);
        value.put("z", null);
        value.put("l", List.of(1));
        assertThat(XPathChecks.jsonXml(value, null)).isEqualTo(
                "<map><string key=\"a\">x&lt;y</string><number key=\"n\">2</number><boolean key=\"ok\">true"
                + "</boolean><null key=\"z\"/><array key=\"l\"><number>1</number></array></map>");
    }

    @Test
    void testAnXPathSelectingNothingInAnyRecordIsReported() {
        final List<String> documents = XPathChecks.jsonRecordDocuments(EMBEDDED, JSON_NS);
        assertThat(documents).hasSize(2);
        assertThat(check("json-to-xml(*[@key='event'])/*/*[@key='timestamp']", documents)).isEmpty();
        assertThat(check("json-to-xml(event)/timestamp", documents)).singleElement().asString()
                .startsWith("xpath `json-to-xml(event)/timestamp` (used for EventTime/TimeCreated) selects "
                            + "nothing in any of the 2 sample records")
                .contains("json-to-xml(*[@key='field'])/*/*[@key='name']");
    }

    @Test
    void testAValueInOnlySomeRecordsIsFineAndConditionsAreChecked() {
        final List<String> documents = XPathChecks.jsonRecordDocuments(EMBEDDED, JSON_NS);
        final Map<String, List<String>> inputs = new LinkedHashMap<>();
        inputs.put("json-to-xml(*[@key='event'])/*/*[@key='timestamp']", List.of("EventTime/TimeCreated"));
        // User is in one record: fine; there is no kind key at all.
        inputs.put("json-to-xml(*[@key='event'])/*/*[@key='user']", List.of("EventSource/User/Id"));
        inputs.put("*[@key='kind']", List.of("[any] when"));
        assertThat(XPathChecks.checkXPaths(inputs, Map.of(), documents, JSON_NS)).singleElement().asString()
                .startsWith("xpath `*[@key='kind']` (used for [any] when)");
    }

    @Test
    void testSeveralValuesWithoutRepeatAreWarnedOf() {
        // Seen: mentions without repeat came out joined in one Data ("pataz1 Lancegooden"), and the agent took
        // that for something the generator can't do.
        final Map<String, String> record = new LinkedHashMap<>();
        record.put("text", "@pataz1 @Lancegooden Facts much");
        record.put("id", "1");
        final List<String> documents = List.of(XPathChecks.splitterRecordDocument(Map.of("id", "0"), "records:2"),
                XPathChecks.splitterRecordDocument(record, "records:2"));
        final Map<String, List<String>> inputs = new LinkedHashMap<>();
        inputs.put("for $m in tokenize(data[@name='text']/@value, '\\s+')[starts-with(., '@')] return "
                   + "substring($m, 2)", List.of("[tweet] EventDetail/Create/Document/Data mentions"));
        inputs.put("data[@name='id']/@value", List.of("[tweet] EventDetail/Create/Document/Id"));

        assertThat(XPathChecks.severalValues(inputs, Map.of(), documents, "records:2")).singleElement().asString()
                .contains("(used for [tweet] EventDetail/Create/Document/Data mentions) gives 2 values in sample "
                          + "record 2 (['pataz1', 'Lancegooden'])")
                .contains("without repeat they are written joined in one element")
                .contains("repeat: true writes one element per value");
        assertThat(XPathChecks.severalValues(Map.of(), Map.of(), documents, "records:2")).isEmpty();
        // The loop's own variable is the expression's; one the XSLT binds is not.
        assertThat(XPathChecks.readsUnboundVariable("for $m in tokenize(x, ' ') return $m")).isFalse();
        assertThat(XPathChecks.readsUnboundVariable("let $a := x, $b := y return concat($a, $b)")).isFalse();
        assertThat(XPathChecks.readsUnboundVariable("for $m in $words return $m")).isTrue();
        assertThat(XPathChecks.readsUnboundVariable("replace(x, '(a)', '$1')")).isFalse();
    }

    @Test
    void testDataSplitterRecordsAndWhatIsNotEvaluated() {
        final Map<String, String> record = new LinkedHashMap<>();
        record.put("time", "2026-10-01T10:00:00Z");
        record.put("user", "alice");
        final List<String> documents = List.of(XPathChecks.splitterRecordDocument(record, "records:2"));
        assertThat(check("data[@name='time']/@value", documents, "records:2")).isEmpty();
        assertThat(check("data[@name='kind']/@value", documents, "records:2")).singleElement().asString()
                .contains("data[@name='kind']/@value");
        // Stroom functions and variables need Stroom: they are not evaluated.
        assertThat(XPathChecks.evaluable("stroom:meta('Feed')", List.of())).isFalse();
        assertThat(XPathChecks.evaluable("geo:lookup(.)", List.of("geo"))).isFalse();
        assertThat(XPathChecks.evaluable("concat($prefix, *[@key='mac'])", List.of())).isFalse();
    }

    @Test
    void testARegexReplacementIsCheckedThoughItHasDollars() {
        // Seen: replace(..., '$1:$2') skipped as if $1 were a variable, so an xpath selecting nothing (a JSON
        // field named as if it were an element) passed: the MAC address it should have written was empty in every
        // event.
        final String mac = "replace(upper-case(replace(%s, '[.:-]', '')), '^(..)(..)(..)(..)(..)(..)$', "
                           + "'$1:$2:$3:$4:$5:$6')";
        final List<String> documents = XPathChecks.jsonRecordDocuments(
                "[{\"host\": \"sw1\", \"time\": \"2026-10-01T10:00:00Z\", \"mac\": \"001a.2b3c.4d5e\"}]", JSON_NS);
        assertThat(XPathChecks.evaluable(mac.formatted("mac"), List.of())).isTrue();
        assertThat(check(mac.formatted("mac"), documents)).singleElement().asString()
                .contains("selects nothing");
        assertThat(check(mac.formatted("*[@key='mac']"), documents)).isEmpty();
    }

    @Test
    void testAMappingsUnsafeXPathIsNotEvaluated() {
        final List<String> documents = List.of("<Event xmlns=\"records:2\"><User>alice</User></Event>");
        assertThat(check("environment-variable('PATH')", documents, "records:2")).singleElement().asString()
                .contains("was not evaluated").contains("environment-variable()");
        final Findings findings = XPathChecks.checkExtractions(List.of(new Extraction(null,
                        "unparsed-text('file:///etc/passwd')", "(.*)", null, List.of("all"))), false, List.of(),
                () -> documents, "records:2", Map.of(), null, CLOCK);
        assertThat(findings.problems()).anyMatch(problem -> problem.contains("unparsed-text()"));
    }

    @Test
    void testARegexThatMatchesNothingSaysWhereItStopsAndNamesTheCharacter() {
        final Findings findings = extract(DASHED);
        assertThat(findings.problems()).singleElement().asString()
                .startsWith("extract[0] (EventData/Data): the regex matches none of the 3 sample texts, so "
                            + "['when', 'subject', 'server', 'kind', 'action', 'user', 'item'] would be empty")
                .contains("It matches as far as `Sep 27 2026 23:48:10 ` and stops there")
                .contains("EN DASH (U+2013)");
    }

    @Test
    void testTheRightRegexPassesAndOneMatchingSomeIsAWarning() {
        final String right = DASHED.replace(" - ", " – ");
        final Findings passed = extract(right);
        assertThat(passed.problems()).isEmpty();
        assertThat(passed.warnings()).isEmpty();
        final Findings some = extract(right.replace("\\[(\\w+)\\] By", "\\[(Login|Logout)\\] By"));
        assertThat(some.problems()).isEmpty();
        assertThat(some.warnings().getFirst())
                .startsWith("extract[0] (EventData/Data): the regex matches 2 of the 3");
    }

    @Test
    void testOverEscapingAndInvalidRegexesAreExplained() {
        // Escaped twice: \\[ in the regex is a backslash, then a character class.
        final Findings twice = extract(DASHED.replace(" - ", " – ").replace("\\[\\[", "\\\\[\\\\["));
        assertThat(twice.problems()).isNotEmpty();
        assertThat(twice.problems().getFirst()).contains("in a regex matches a backslash character");
        assertThat(extract("^(unclosed").problems().getFirst()).contains("not a valid XPath regular expression");
    }

    @Test
    void testWhereItStopsOnARegexThatMatchesButTheTextGoesOn() {
        assertThat(XPathChecks.whereItStops("^Sep (\\d+)$", "", List.of("Sep 27 2026")))
                .startsWith("All of it matches, but the text goes on after: ` 2026`");
        assertThat(XPathChecks.whereItStops("^Sep (\\d+) x", "", List.of("Sep 27 2026")))
                .startsWith("It matches as far as `Sep 27 ` and stops");
        assertThat(XPathChecks.whereItStops("^Sep$", "", List.of("Sep 27"))).contains("drop the $");
    }

    @Test
    void testEverySampleTextIsTriedNotTheFirst200() {
        // Seen in production: a VPN rule's keys first appeared at the 557th of 3,933 records; reading only the
        // first 200, the check said they matched nothing, and a sound mapping was refused.
        final List<Map<String, ?>> records = new ArrayList<>();
        for (int n = 0; n < 600; n++) {
            records.add(Map.of("host", "fw1", "body", "type=\"traffic\" action=\"deny\" n=\"" + n + "\""));
        }
        records.set(557, Map.of("host", "fw1", "body",
                "type=\"event\" action=\"negotiate\" remip=\"10.0.0.1\" vpntunnel=\"hq\""));
        final List<Extraction> extractions = List.of(kv("action"), kv("vpntunnel"), kv("nothere"));
        final Findings findings = XPathChecks.checkExtractions(extractions, true, records, List::of, JSON_NS,
                Map.of(), null, CLOCK);
        assertThat(findings.problems()).noneMatch(problem -> problem.contains("vpntunnel"));
        assertThat(findings.warnings()).anyMatch(warning -> warning.contains(
                "matches 1 of the 600 sample texts; the rest leave ['vpntunnel'] empty"));
        // A key no record has is still refused, and the reason says so, not "matches as far as ``".
        assertThat(findings.problems()).singleElement().asString()
                .contains("nothere")
                .contains("none of the 600 sample texts")
                .contains("None of these texts has `nothere=\"`");
    }

    @Test
    void testAKeyThatIsThereSaysWhereItsValueStopsMatching() {
        assertThat(XPathChecks.whereItStops("(?:^|\\s)level=([0-9]+)", "",
                List.of("eventtime=1790 level=\"notice\" logid=\"0001\"")))
                .isEqualTo("It finds `level=`, then the regex goes on with `([0-9]+)`, the text with "
                           + "`\"notice\" logid=\"0001\"`.");
    }

    @Test
    void testEntriesPastTheDeadlineAreNamedNotChecked() {
        final List<Map<String, ?>> records = List.of(Map.of("body", "action=\"deny\""));
        final Findings findings = XPathChecks.checkExtractions(List.of(kv("action")), true, records, List::of,
                JSON_NS, Map.of(), CLOCK.instant().minusSeconds(1), CLOCK);
        assertThat(findings.problems()).isEmpty();
        assertThat(findings.warnings()).singleElement().asString()
                .startsWith("extract[0] (body) not checked against the sample: the check's time ran out");
    }

    @Test
    @Timeout(120)
    void testARegexThatBacktracksBadlyIsAProblemNotAHang() {
        // Before, an extraction's regex ran in Saxon with no time limit (Saxon copies the text and checks none).
        final List<Map<String, ?>> records = List.of(Map.of("body", "a,".repeat(40)));
        final Findings findings = XPathChecks.checkExtractions(List.of(new Extraction("body", null, "(.*?,){25}x",
                null, List.of("x"))), true, records, List::of, JSON_NS, Map.of(), null, CLOCK);
        assertThat(findings.problems()).singleElement().asString()
                .startsWith("extract[0] (body): The pattern '(.*?,){25}x' took too long to match");
    }

    @Test
    void testARegexTooLongIsAProblem() {
        final List<Map<String, ?>> records = List.of(Map.of("body", "a"));
        final Findings findings = XPathChecks.checkExtractions(List.of(new Extraction("body", null,
                "a".repeat(SafePattern.MAX_REGEX_LENGTH + 1), null, List.of("x"))), true, records, List::of, JSON_NS,
                Map.of(), null, CLOCK);
        assertThat(findings.problems()).singleElement().asString()
                .isEqualTo("extract[0] (body): the regex is " + (SafePattern.MAX_REGEX_LENGTH + 1) + " characters "
                           + "long, and may be at most " + SafePattern.MAX_REGEX_LENGTH + ".");
    }

    @Test
    void testControlCharactersMakeARecordXml11() {
        assertThat(XPathChecks.xml11("<a>\u001b[31m</a>")).isEqualTo("<?xml version=\"1.1\"?><a>&#x1B;[31m</a>");
        assertThat(XPathChecks.xml11("<a/>")).isEqualTo("<a/>");
    }

    private static Extraction kv(final String key) {
        return new Extraction("body", null, "(?:^|\\s)" + key + "=\"([^\"]*)\"", null, List.of(key));
    }

    private static Findings extract(final String regex) {
        final List<String> documents = new ArrayList<>();
        for (final String action : List.of("Login", "Logout", "Update")) {
            documents.add("<Event xmlns=\"records:2\"><System><Computer>app01</Computer></System><EventData><Data>"
                          + MESSAGE.replace("Login", action) + "</Data></EventData></Event>");
        }
        return XPathChecks.checkExtractions(List.of(new Extraction(null, "EventData/Data", regex, null, NAMES)),
                false, List.of(), () -> documents, "records:2", Map.of(), null, CLOCK);
    }

    private static List<String> check(final String xpath, final List<String> documents) {
        return check(xpath, documents, JSON_NS);
    }

    private static List<String> check(final String xpath, final List<String> documents, final String namespace) {
        return XPathChecks.checkXPaths(Map.of(xpath, List.of("EventTime/TimeCreated")), Map.of(), documents,
                namespace);
    }
}
