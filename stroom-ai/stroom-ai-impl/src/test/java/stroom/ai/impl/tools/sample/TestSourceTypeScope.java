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

package stroom.ai.impl.tools.sample;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A build's scope over an aggregated feed's source types: records judged one by one on the source type field's
/// value and the registry's rows, the first match winning.
class TestSourceTypeScope {

    private static final List<SourceTypeRows.Row> ROWS = List.of(
            new SourceTypeRows.Row("mail audit", SourceTypeRows.PATTERN, "^/var/log/mail/audit/"),
            new SourceTypeRows.Row("mail", SourceTypeRows.PATTERN, "^/var/log/mail/"),
            new SourceTypeRows.Row("secure", SourceTypeRows.VALUE, "/var/log/secure"));
    private static final SourceTypeScope MAIL = new SourceTypeScope("b1", "path", ROWS, List.of("mail"));

    @Test
    void aRecordIsInTheScopeByItsValuesFirstMatchingRow() {
        assertThat(MAIL.inScope(Map.of("path", "/var/log/mail/maillog"))).isTrue();
        // The narrower row is tried first: an audit log is mail audit, not mail.
        assertThat(MAIL.inScope(Map.of("path", "/var/log/mail/audit/a.log"))).isFalse();
        assertThat(MAIL.sourceType(Map.of("path", "/var/log/mail/audit/a.log"))).hasValue("mail audit");
        assertThat(MAIL.inScope(Map.of("path", "/var/log/secure"))).isFalse();
        // No value, or a value no row matches (a new source type): outside the scope.
        assertThat(MAIL.inScope(Map.of("other", "x"))).isFalse();
        assertThat(MAIL.inScope(Map.of("path", "/var/log/messages"))).isFalse();
        assertThat(MAIL.inScope(null)).isFalse();
        // No scope chosen: every record.
        assertThat(new SourceTypeScope(null, "path", ROWS, null).inScope(Map.of("path", "/x"))).isTrue();
    }

    @Test
    void theScopeIsTheBuildsThatChoseIt() {
        final Map<String, Object> sources = sources(scope("b1", List.of("mail"), SourceTypeScope.kept(ROWS)));

        assertThat(SourceTypeScope.of(sources, "b1", List.of())).hasValueSatisfying(found -> {
            assertThat(found.names()).containsExactly("mail");
            assertThat(found.rows()).isEqualTo(ROWS);
            assertThat(found.field()).isEqualTo("path");
        });
        // A survey carried on from one promoted beside the feed keeps the scope of the build that promoted it.
        assertThat(SourceTypeScope.of(sources, "b2", List.of())).isEmpty();
        // Any build's, when none is named.
        assertThat(SourceTypeScope.of(sources, null, List.of())).isPresent();
        assertThat(SourceTypeScope.of(Map.of("field", "path"), "b1", List.of())).isEmpty();
        assertThat(SourceTypeScope.of(null, "b1", List.of())).isEmpty();
    }

    @Test
    void aScopeKeptWithoutRowsMatchesByTheRegistrysElseByName() {
        final Map<String, Object> sources = sources(scope("b1", List.of("PegaDB"), null));

        assertThat(SourceTypeScope.of(sources, "b1", ROWS).orElseThrow().rows()).isEqualTo(ROWS);
        final SourceTypeScope byName = SourceTypeScope.of(sources, "b1", List.of()).orElseThrow();
        assertThat(byName.inScope(Map.of("path", "PegaDB"))).isTrue();
        assertThat(byName.inScope(Map.of("path", "PegaRULES"))).isFalse();
    }

    @Test
    void aStreamReadForSomeSourceTypesIsReadAgainForTheOthersOnly() {
        final SourceTypeScope both = new SourceTypeScope("b1", "path", ROWS, List.of("mail", "secure"));

        assertThat(both.covers(List.of(SourceTypeScope.ALL))).isTrue();
        assertThat(both.covers(List.of("mail", "secure", "mail audit"))).isTrue();
        assertThat(both.covers(List.of("mail"))).isFalse();
        assertThat(both.notReadFor(List.of("mail")).test(Map.of("path", "/var/log/secure"))).isTrue();
        assertThat(both.notReadFor(List.of("mail")).test(Map.of("path", "/var/log/mail/maillog"))).isFalse();
        assertThat(both.notReadFor(List.of(SourceTypeScope.ALL)).test(Map.of("path", "/var/log/secure"))).isFalse();
        // With no scope, a stream a scope read is read again for every other source type, new ones too.
        final SourceTypeScope every = new SourceTypeScope(null, "path", ROWS, null);
        assertThat(every.covers(List.of("mail"))).isFalse();
        assertThat(every.notReadFor(List.of("mail")).test(Map.of("path", "/var/log/messages"))).isTrue();
        assertThat(every.signature()).containsExactly(SourceTypeScope.ALL);
        assertThat(both.signature()).containsExactly("mail", "secure");
    }

    @Test
    void jsonLinesOutsideTheScopeAreLeftOutAsTheyAreRead() {
        final String text = line("/var/log/mail/maillog", "sent")
                            + line("/var/log/secure", "Accepted password for /var/log/mail/x")
                            + line("/var/log/mail/audit/a.log", "audit")
                            + line("/var/log/mail/maillog", "deferred");

        final SourceTypeScope.Filtered filtered = MAIL.filter(text);

        assertThat(filtered.filtered()).isTrue();
        assertThat(filtered.kept()).isEqualTo(2);
        // A record outside the scope is left out whatever its message says: judged by its field, never by a
        // pattern over the text.
        assertThat(filtered.text()).isEqualTo(line("/var/log/mail/maillog", "sent")
                                              + line("/var/log/mail/maillog", "deferred"));
        assertThat(filtered.passedOver()).containsExactly(Map.entry("mail audit", 1), Map.entry("secure", 1));
        assertThat(filtered.left()).isEqualTo(2);
    }

    @Test
    void delimitedRecordsKeepTheirHeaderAndJsonArraysStayArrays() {
        final String csv = "time,source,action,user\n"
                           + "10:00,db,select,alice\n10:01,web,get,bob\n10:02,db,update,carol\n"
                           + "10:03,web,post,dave\n10:04,db,delete,erin\n";
        final SourceTypeScope byName = SourceTypeScope.of(sources("source", scope("b1", List.of("db"), null)),
                "b1", List.of()).orElseThrow();

        final SourceTypeScope.Filtered rows = byName.filter(csv);
        assertThat(rows.text()).isEqualTo("time,source,action,user\n10:00,db,select,alice\n10:02,db,update,carol\n"
                                          + "10:04,db,delete,erin\n");
        assertThat(rows.passedOver()).containsExactly(Map.entry(SourceTypeScope.UNMATCHED, 2));

        final SourceTypeScope.Filtered array = byName.filter("[{\"source\": \"db\", \"n\": 1}, "
                                                             + "{\"source\": \"web\", \"n\": 2}]");
        assertThat(array.kept()).isEqualTo(1);
        assertThat(SampleProfiler.loadsDecoded(array.text())).isInstanceOf(List.class);
        assertThat(array.text()).contains("\"n\": 1").doesNotContain("web");
        assertThat(byName.filter("{\"source\": \"db\"}\n").passedOver()).isEmpty();
    }

    @Test
    void aTextOfOneJsonLineIsReadAsARecord() {
        // One JSON line alone reads as a delimited header: still a record of a source type.
        assertThat(MAIL.filter(line("/var/log/secure", "x")).passedOver()).containsExactly(Map.entry("secure", 1));
        assertThat(MAIL.filter(line("/var/log/mail/maillog", "x")).kept()).isEqualTo(1);
    }

    @Test
    void textThatCantBeReadRecordByRecordIsKeptWhole() {
        final String syslog = "<13>Nov 12 10:00:00 host sshd[1]: Accepted password for alice\n";

        final SourceTypeScope.Filtered filtered = MAIL.filter(syslog);

        assertThat(filtered.filtered()).isFalse();
        assertThat(filtered.text()).isEqualTo(syslog);
    }

    @Test
    void theScopesShareIsOfTheRecordsTheSurveyCounted() {
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("/var/log/mail/maillog", Map.of("records", 30));
        values.put("/var/log/mail/other", Map.of("records", 10));
        values.put("/var/log/mail/audit/a.log", Map.of("records", 20));
        values.put("/var/log/secure", Map.of("records", 40));
        final Map<String, Object> sources = new LinkedHashMap<>(sources(scope("b1", List.of("mail"),
                SourceTypeScope.kept(ROWS))));
        sources.put("records", 100);
        sources.put(SourceTypeProfiles.VALUES, values);

        final Map<String, Object> share = MAIL.share(sources);

        assertThat(share).containsEntry("records_read", 40L).containsEntry("of_records_read", 100L)
                .containsEntry("share_percent", 40.0);
        assertThat(MAIL.shareSaid(sources)).isEqualTo("build 'b1' covers mail (about 40.0% of the feed's records "
                                                      + "read)");
        assertThat(MAIL.share(Map.of())).containsEntry("share_percent", null);
    }

    @Test
    void aShapeIsTheScopesByTheValuesItsRecordsHadElseByItsExamples() {
        assertThat(MAIL.shapeInScope(Map.of(Shapes.SOURCE_VALUES, List.of("/var/log/secure",
                "/var/log/mail/maillog")))).isTrue();
        assertThat(MAIL.shapeInScope(Map.of(Shapes.SOURCE_VALUES, List.of("/var/log/secure")))).isFalse();
        // Kept before the values were: told by its examples' records, where they parse.
        assertThat(MAIL.shapeInScope(Map.of("examples", List.of(Map.of("text",
                "{\"path\": \"/var/log/secure\"}"))))).isFalse();
        assertThat(MAIL.shapeInScope(Map.of("examples", List.of(Map.of("text",
                "{\"path\": \"/var/log/mail/x\"}"))))).isTrue();
        // Its source can't be told: kept.
        assertThat(MAIL.shapeInScope(Map.of("examples", List.of(Map.of("text", "plain text"))))).isTrue();
    }

    @Test
    void aShapeKeepsAtMostTwentyValues() {
        List<String> values = null;
        for (int i = 0; i < 30; i++) {
            values = SourceTypeScope.withValue(values, "v" + (i % 25));
        }
        assertThat(values).hasSize(20).startsWith("v0", "v1");
        assertThat(SourceTypeScope.withValue(null, null)).isEmpty();
    }

    private static String line(final String path, final String message) {
        return "{\"path\": \"" + path + "\", \"message\": \"" + message + "\"}\n";
    }

    private static Map<String, Object> scope(final String build,
                                             final List<String> names,
                                             final List<Map<String, Object>> rows) {
        final Map<String, Object> scope = new LinkedHashMap<>();
        scope.put(SourceTypeScope.BUILD, build);
        scope.put(SourceTypeScope.SOURCE_TYPES, names);
        if (rows != null) {
            scope.put(SourceTypeScope.ROWS, rows);
        }
        return scope;
    }

    private static Map<String, Object> sources(final Map<String, Object> scope) {
        return sources("path", scope);
    }

    private static Map<String, Object> sources(final String field, final Map<String, Object> scope) {
        final Map<String, Object> sources = new LinkedHashMap<>();
        sources.put(SourceTypeProfiles.FIELD, field);
        sources.put(SourceTypeProfiles.SCOPE, scope);
        return sources;
    }
}
