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

/// The rows of a source type registry: exact values or anchored patterns, tried in order, the first match winning.
class TestSourceTypeRows {

    private static final SourceTypeRows.Row MAIL = new SourceTypeRows.Row("mail", SourceTypeRows.PATTERN,
            "^/var/log/mail/");
    private static final SourceTypeRows.Row AUDIT = new SourceTypeRows.Row("mail audit", SourceTypeRows.PATTERN,
            "^/var/log/mail/audit/");

    @Test
    void fewValuesAreARowEach() {
        final SourceTypeRows.Proposal proposal = SourceTypeRows.propose(counts("PegaDB", 300L, "PegaRULES", 100L,
                "PegaALERT", 5L));

        assertThat(proposal.patterns()).isFalse();
        assertThat(proposal.rows()).extracting(row -> row.row().said())
                .containsExactly("= PegaDB", "= PegaRULES", "= PegaALERT");
    }

    @Test
    void pathsSharingDirectoriesArePatternsTheNarrowestFirst() {
        final SourceTypeRows.Proposal proposal = SourceTypeRows.propose(counts(
                "/var/log/mail/maillog", 700L,
                "/var/log/httpd/access_log", 600L,
                "/var/log/httpd/error_log", 70L,
                "/var/log/secure", 300L,
                "/opt/app/logs/app-2026-11-10.log", 180L,
                "/opt/app/logs/app-2026-11-11.log", 180L,
                "/var/log/mail/audit/mailaudit.log", 90L));

        assertThat(proposal.patterns()).isTrue();
        final List<String> said = proposal.rows().stream().map(row -> row.row().said()).toList();
        // /var/log/ holds several source directories: its own file keeps a row, so a new file there is new.
        assertThat(said).contains("= /var/log/secure", "~ ^/var/log/httpd/", "~ ^/opt/app/logs/");
        assertThat(said).doesNotContain("~ ^/var/log/");
        assertThat(said.indexOf("~ ^/var/log/mail/audit/")).isLessThan(said.indexOf("~ ^/var/log/mail/"));
        assertThat(proposal.rows().stream().filter(row -> row.row().value().equals("^/var/log/httpd/"))
                .findFirst().orElseThrow()).satisfies(row -> {
                    assertThat(row.records()).isEqualTo(670L);
                    assertThat(row.values()).containsExactlyInAnyOrder("/var/log/httpd/access_log",
                            "/var/log/httpd/error_log");
                });
        final List<SourceTypeRows.Row> rows = proposal.rows().stream().map(SourceTypeRows.Proposed::row).toList();
        assertThat(SourceTypeRows.problems(rows, List.of("/var/log/mail/maillog"))).isEmpty();
        assertThat(SourceTypeRows.first(rows, "/var/log/mail/audit/x.log")).hasValueSatisfying(row ->
                assertThat(row.name()).isEqualTo("/var/log/mail/audit"));
        assertThat(SourceTypeRows.first(rows, "/var/log/messages")).isEmpty();
    }

    @Test
    void dottedNamesArePatternsByPackage() {
        final Map<String, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < 14; i++) {
            counts.put("com.acme." + (i % 2 == 0
                    ? "db"
                    : "web") + ".Class" + i, 10L);
        }

        final SourceTypeRows.Proposal proposal = SourceTypeRows.propose(counts);

        assertThat(proposal.rows()).extracting(row -> row.row().said())
                .containsExactlyInAnyOrder("~ ^com\\.acme\\.db\\.", "~ ^com\\.acme\\.web\\.");
    }

    @Test
    void aNarrowerPatternAfterAWiderOneCanNeverMatch() {
        assertThat(SourceTypeRows.problems(List.of(MAIL, AUDIT), List.of()))
                .singleElement().asString().contains("'mail audit' can never match").contains("'mail'");
        assertThat(SourceTypeRows.problems(List.of(AUDIT, MAIL), List.of())).isEmpty();
    }

    @Test
    void aRowWhoseEveryValueAnEarlierRowTakesCanNeverMatch() {
        final SourceTypeRows.Row exact = new SourceTypeRows.Row("maillog", SourceTypeRows.VALUE,
                "/var/log/mail/maillog");
        assertThat(SourceTypeRows.problems(List.of(MAIL, exact), List.of())).singleElement().asString()
                .contains("'maillog' can never match");
        final SourceTypeRows.Row logs = new SourceTypeRows.Row("logs", SourceTypeRows.PATTERN, "^/var/log/(mail|x)/");
        assertThat(SourceTypeRows.problems(List.of(MAIL, logs), List.of("/var/log/mail/maillog")))
                .singleElement().asString().contains("'logs' can never match");
        assertThat(SourceTypeRows.problems(List.of(MAIL, logs), List.of("/var/log/x/y"))).isEmpty();
    }

    @Test
    void patternsMustBeAnchoredShortAndSafe() {
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("a", SourceTypeRows.PATTERN,
                "/var/log/")), List.of())).singleElement().asString().contains("anchored");
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("a", SourceTypeRows.PATTERN,
                "^" + "a".repeat(SourceTypeRows.MAX_PATTERN))), List.of())).singleElement().asString()
                .contains("over " + SourceTypeRows.MAX_PATTERN);
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("a", SourceTypeRows.PATTERN,
                "^(a+)+b")), List.of())).singleElement().asString().contains("repeats a group");
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("a", SourceTypeRows.PATTERN,
                "^(")), List.of())).singleElement().asString().contains("not a regular expression");
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("a", SourceTypeRows.VALUE, "x"),
                new SourceTypeRows.Row("a", SourceTypeRows.VALUE, "y")), List.of())).singleElement().asString()
                .contains("two rows are named 'a'");
    }

    @Test
    void aPatternMatchesOnlyFromTheValuesStart() {
        assertThat(MAIL.matches("/var/log/mail/maillog")).isTrue();
        assertThat(MAIL.matches("/backup/var/log/mail/maillog")).isFalse();
        assertThat(new SourceTypeRows.Row("x", SourceTypeRows.VALUE, "PegaDB").matches("PegaDBX")).isFalse();
        assertThat(SourceTypeRows.escape("a.b(c)")).isEqualTo("a\\.b\\(c\\)");
    }

    @Test
    void aListMemberRowMatchesAnItemExactlyOnceStripped() {
        final SourceTypeRows.Row directory = new SourceTypeRows.Row("Directory", SourceTypeRows.MEMBER,
                "Directory Services", ",");

        assertThat(directory.matches("DNS Server, Directory Services, File Server")).isTrue();
        assertThat(directory.matches(" Directory Services ")).isTrue();
        assertThat(directory.matches("DNS Server,Directory Services")).isTrue();
        // An exact item: not part of one, not another case, not another separator's list.
        assertThat(directory.matches("DNS Server, Directory Services Lite")).isFalse();
        assertThat(directory.matches("directory services")).isFalse();
        assertThat(directory.matches("DNS Server; Directory Services")).isFalse();
        assertThat(directory.matches("")).isFalse();
        assertThat(new SourceTypeRows.Row("Directory", SourceTypeRows.MEMBER, "Directory Services", ";")
                .matches("DNS Server; Directory Services")).isTrue();
        assertThat(directory.said()).isEqualTo("∈ Directory Services");
        assertThat(SourceTypeRows.items("a |b||  c ", "|")).containsExactly("a", "b", "c");
    }

    @Test
    void aRowIsWrittenWithEqualsTildeOrAnItemSignOrItsAsciiWord() {
        assertThat(SourceTypeRows.parse("Dir ∈ Directory Services")).contains(new SourceTypeRows.Row("Dir",
                SourceTypeRows.MEMBER, "Directory Services"));
        assertThat(SourceTypeRows.parse("Dir in Directory Services")).contains(new SourceTypeRows.Row("Dir",
                SourceTypeRows.MEMBER, "Directory Services"));
        // The signs come before the word: a name holding ' in ' takes one of them.
        assertThat(SourceTypeRows.parse("Sign in = SignIn")).contains(new SourceTypeRows.Row("Sign in",
                SourceTypeRows.VALUE, "SignIn"));
        assertThat(SourceTypeRows.parse("Logged in ∈ Logon")).contains(new SourceTypeRows.Row("Logged in",
                SourceTypeRows.MEMBER, "Logon"));
        assertThat(SourceTypeRows.parse("mail ~ ^/var/log/mail/")).contains(MAIL);
        assertThat(SourceTypeRows.parse("just a name")).isEmpty();
        // The separator is read from the values seen.
        assertThat(SourceTypeRows.withSeparator(List.of(SourceTypeRows.parse("Dir in Directory Services")
                .orElseThrow()), SourceTypeRows.separator(List.of("a; b", "c")))).singleElement()
                .extracting(SourceTypeRows.Row::separator).isEqualTo(";");
        assertThat(SourceTypeRows.separator(List.of("a", "b"))).isEqualTo(SourceTypeRows.DEFAULT_SEPARATOR);
    }

    @Test
    void listValuesAreProposedARowAnItemTheRarestFirst() {
        final SourceTypeRows.Proposal proposal = SourceTypeRows.propose(counts(
                "DNS Server, File Server", 500L,
                "File Server", 300L,
                "DNS Server, Directory Services, File Server", 40L,
                "Directory Services, DNS Server", 10L));

        assertThat(proposal.lists()).isTrue();
        assertThat(proposal.patterns()).isFalse();
        // Directory Services is rarest, so a controller's list (with DNS and File Server too) is its; DNS Server
        // takes the rest of its lists; File Server those holding only it.
        assertThat(proposal.rows()).extracting(row -> row.row().name() + " " + row.row().said())
                .containsExactly("Directory Services ∈ Directory Services", "DNS Server ∈ DNS Server",
                        "File Server ∈ File Server");
        assertThat(proposal.rows()).extracting(SourceTypeRows.Proposed::records).containsExactly(50L, 500L, 300L);
        assertThat(proposal.rows()).allSatisfy(row -> assertThat(row.row().separator()).isEqualTo(","));
        final List<SourceTypeRows.Row> rows = proposal.rows().stream().map(SourceTypeRows.Proposed::row).toList();
        assertThat(SourceTypeRows.problems(rows, counts("DNS Server, File Server", 1L, "File Server", 1L,
                "DNS Server, Directory Services, File Server", 1L).keySet())).isEmpty();
    }

    @Test
    void valuesThatArentListsAreNotProposedItems() {
        // Too few lists, or items that never repeat (free text with commas).
        assertThat(SourceTypeRows.propose(counts("PegaDB", 300L, "PegaRULES", 100L, "Pega, Batch", 5L)).lists())
                .isFalse();
        assertThat(SourceTypeRows.propose(counts("a, b", 3L, "c, d", 2L)).lists()).isFalse();
    }

    @Test
    void aListRowEveryValueOfWhichAnEarlierRowTakesCanNeverMatch() {
        final SourceTypeRows.Row dns = new SourceTypeRows.Row("DNS", SourceTypeRows.MEMBER, "DNS Server", ",");
        final SourceTypeRows.Row directory = new SourceTypeRows.Row("Directory", SourceTypeRows.MEMBER,
                "Directory Services", ",");
        final List<String> seen = List.of("DNS Server, Directory Services", "DNS Server");

        // Every list holding Directory Services holds DNS Server: after DNS, Directory is never reached.
        assertThat(SourceTypeRows.problems(List.of(dns, directory), seen)).singleElement().asString()
                .contains("'Directory' can never match", "'DNS'");
        assertThat(SourceTypeRows.problems(List.of(directory, dns), seen)).isEmpty();
        // The same item twice, whatever the values.
        assertThat(SourceTypeRows.problems(List.of(dns, new SourceTypeRows.Row("DNS again", SourceTypeRows.MEMBER,
                "DNS Server", ",")), List.of())).singleElement().asString().contains("'DNS again' can never match");
        // A whole value, after an item it holds.
        assertThat(SourceTypeRows.problems(List.of(dns, new SourceTypeRows.Row("pair", SourceTypeRows.VALUE,
                "DNS Server, Directory Services")), List.of())).singleElement().asString()
                .contains("'pair' can never match");
        // An item holding the separator, or none.
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("x", SourceTypeRows.MEMBER, "a, b",
                ",")), List.of())).singleElement().asString().contains("holds the list's separator");
        assertThat(SourceTypeRows.problems(List.of(new SourceTypeRows.Row("x", SourceTypeRows.MEMBER, "",
                ",")), List.of())).singleElement().asString().contains("needs an item");
    }

    @Test
    void aListRowIsKeptWithItsSeparator() {
        final SourceTypeRows.Row row = new SourceTypeRows.Row("Dir", SourceTypeRows.MEMBER, "Directory Services",
                ";");

        assertThat(row.toMap()).containsEntry("separator", ";").containsEntry("match", "member");
        assertThat(SourceTypeRows.Row.of(row.toMap())).isEqualTo(row);
        assertThat(SourceTypeRows.Row.of(MAIL.toMap())).isEqualTo(MAIL);
        assertThat(MAIL.toMap()).doesNotContainKey("separator");
    }

    @Test
    void theNoValueRowMatchesOnlyRecordsWithNoValue() {
        final SourceTypeRows.Row none = SourceTypeRows.noValue();
        final List<SourceTypeRows.Row> rows = List.of(new SourceTypeRows.Row("a", SourceTypeRows.VALUE, "a"), none);

        assertThat(none.matches(null)).isTrue();
        assertThat(none.matches("")).isTrue();
        assertThat(none.matches("a")).isFalse();
        assertThat(SourceTypeRows.first(rows, null)).hasValue(none);
        assertThat(SourceTypeRows.first(rows, "a")).hasValueSatisfying(row -> assertThat(row.name()).isEqualTo("a"));
        assertThat(SourceTypeRows.first(rows, "b")).isEmpty();
        // Without it, a record with no value is no row's.
        assertThat(SourceTypeRows.first(rows.subList(0, 1), null)).isEmpty();
        assertThat(none.said()).isEqualTo("no value");
        assertThat(SourceTypeRows.Row.of(none.toMap())).isEqualTo(none);
        assertThat(SourceTypeRows.parse(" (No Value) ")).hasValue(none);
        assertThat(SourceTypeRows.problems(rows, List.of("a"))).isEmpty();
        assertThat(SourceTypeRows.problems(List.of(none, new SourceTypeRows.Row("other", SourceTypeRows.NONE, "")),
                List.of())).singleElement().satisfies(problem -> assertThat(problem).contains("keep one"));
    }

    private static Map<String, Long> counts(final Object... valuesAndCounts) {
        final Map<String, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < valuesAndCounts.length; i += 2) {
            out.put((String) valuesAndCounts[i], (Long) valuesAndCounts[i + 1]);
        }
        return out;
    }
}
