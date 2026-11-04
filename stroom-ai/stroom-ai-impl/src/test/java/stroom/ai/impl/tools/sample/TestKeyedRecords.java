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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Lines tied into records by a key each holds. Asked for by the user: auditd, whose events are several lines sharing
/// a `msg=audit(...)` envelope; lines of events written interleaved are not put back together (the user's decision),
/// only warned of.
class TestKeyedRecords {

    // auditd-lines-generator.py: 529 lines, 151 events, one pair interleaved, so 152 records.
    private static final String AUDITD = resource("auditd-lines.log");
    private static final String KEY = "(?<!\\S)msg=(\"[^\"]*\"|[^\\s\"]+)";

    @Test
    void auditdsEnvelopeTiesItsLinesAndItsTypeNamesEachLine() {
        final Map<String, Object> profile = SampleProfiler.profile(AUDITD);

        assertThat(profile).containsEntry("format", SampleProfiler.KEY_VALUE_FORMAT)
                .containsEntry("record_key", KEY)
                .containsEntry("key_field", "msg")
                .containsEntry("lead_field", "type")
                .containsEntry("records", 152)
                .containsEntry("lines", 529)
                .containsEntry("interleaved", 1);
        assertThat(String.valueOf(profile.get("interleaved_warning"))).contains("not supported");
        assertThat(String.valueOf(profile.get("suggested_parser"))).contains("SYSCALL/exe");
    }

    @Test
    void eachLinesFieldsAreReadUnderItsName() {
        final SplitterSpec spec = SplitterDryRun.inferSpec(AUDITD).spec();
        assertThat(spec.recordKey()).isEqualTo(KEY);
        final SplitterDryRun.DryRun dryRun = SplitterDryRun.dryRun(spec, AUDITD, 1_000);

        assertThat(dryRun.unmatchedLines()).isEmpty();
        assertThat(dryRun.records()).hasSize(152);
        final Map<String, String> syscall = dryRun.records().stream()
                .filter(record -> "SYSCALL".equals(record.get("type")))
                .findFirst()
                .orElseThrow();
        // The key and the lead line's name, then each line's fields under its name: a PATH given twice keeps its
        // first, as a field reads the first of several data elements.
        assertThat(syscall).containsEntry("msg", "audit(1791709234.390:4506):")
                .containsEntry("SYSCALL/exe", "/usr/bin/sudo")
                .containsEntry("EXECVE/a1", "systemctl")
                .containsEntry("CWD/cwd", "/home/carol")
                .containsEntry("PATH/name", "/usr/bin/sudo")
                .containsKey("PROCTITLE/proctitle")
                .containsKey("EOE/msg");
        // A one-line event's own message of single-quoted pairs (msg='op=login ... res=success'): its pairs the
        // line's own, without the quotes.
        assertThat(spec.lineSpec().singleQuotes()).isTrue();
        final Map<String, String> login = dryRun.records().getFirst();
        assertThat(login).containsEntry("type", "USER_LOGIN")
                .containsEntry("USER_LOGIN/op", "login")
                .containsEntry("USER_LOGIN/acct", "alice")
                .containsEntry("USER_LOGIN/res", "success");
    }

    @Test
    void anInterleavedEventIsReadAsTwoRecords() {
        final SplitterSpec spec = SplitterDryRun.inferSpec(AUDITD).spec();
        final List<Map<String, String>> records = SplitterDryRun.dryRun(spec, AUDITD, 1_000).records();

        assertThat(records.stream().filter(record -> "audit(1791709615.665:4576):".equals(record.get("msg"))))
                .extracting(record -> record.get("type"))
                .containsExactly("SYSCALL", "CWD");
    }

    @Test
    void linesOfOneKindSharingAValueAreNotTied() {
        // Lines written in the same second share their time, and a session runs over several lines: neither is
        // one event.
        final StringBuilder lines = new StringBuilder();
        for (int n = 0; n < 30; n++) {
            lines.append("time=2026-10-11T10:00:").append(String.format("%02d", n / 3)).append(" session=s")
                    .append(n / 4).append(" user=u").append(n % 5).append(" action=login\n");
        }
        assertThat(SampleProfiler.profile(lines.toString())).doesNotContainKey("record_key");
    }

    @Test
    void aLineWithNoKeyIsARecordOfItsOwn() {
        final SplitterSpec spec = SplitterSpec.builder("key_value").delimiter(" ").quote("\"").recordKey(KEY)
                .keyField("msg").lineName("\\s*type=\"?([A-Za-z_][\\w.-]*)\"?\\s*").leadField("type").build();
        final String sample = "type=A msg=audit(1:1): x=1\ntype=B msg=audit(1:1): y=2\nnote=restarted\n"
                              + "type=A msg=audit(2:2): x=3\n";

        assertThat(SplitterDryRun.dryRun(spec, sample).records()).containsExactly(
                Map.of("msg", "audit(1:1):", "type", "A", "A/type", "A", "A/msg", "audit(1:1):", "A/x", "1",
                        "B/type", "B", "B/msg", "audit(1:1):", "B/y", "2"),
                Map.of("note", "restarted"),
                Map.of("msg", "audit(2:2):", "type", "A", "A/type", "A", "A/msg", "audit(2:2):", "A/x", "3"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            // The same events: forwarded by audispd (node= before each line's type), and as ausearch prints them
            // (a ---- row and a time-> line before each, complete).
            "auditd-node-lines.log, msg, type, 152, false",
            "auditd-ausearch-lines.log, msg, type, 151, true",
            // Not auditd: an application's transactions, tied by txn=, each line named by step= (not its first
            // field).
            "txn-lines.log, txn, step, 80, false"})
    void otherKeyedLogsAreTiedAndRead(final String fixture,
                                      final String keyField,
                                      final String leadField,
                                      final int records,
                                      final boolean separated) {
        final String sample = resource(fixture);
        final Map<String, Object> profile = SampleProfiler.profile(sample);
        assertThat(profile).containsEntry("key_field", keyField).containsEntry("lead_field", leadField)
                .containsEntry("records", records);
        assertThat(profile.get("skip_lines") != null).isEqualTo(separated);

        final SplitterSpec spec = SplitterDryRun.inferSpec(sample).spec();
        final SplitterDryRun.DryRun dryRun = SplitterDryRun.dryRun(spec, sample, 1_000);
        assertThat(dryRun.unmatchedLines()).isEmpty();
        assertThat(dryRun.records()).hasSize(records)
                .allSatisfy(record -> assertThat(record).containsKeys(keyField, leadField));
    }

    @Test
    void whatEachVariantAddsIsKept() {
        // The forwarder's host, before the line's name.
        assertThat(firstOf("auditd-node-lines.log", "SYSCALL")).containsEntry("SYSCALL/exe", "/usr/bin/sudo")
                .containsKey("SYSCALL/node");

        // ausearch's separators are no records and no fields.
        assertThat(firstOf("auditd-ausearch-lines.log", "SYSCALL")).containsEntry("SYSCALL/exe", "/usr/bin/sudo")
                .doesNotContainKey("unparsed");
        // A transaction: each step's fields under its name, the first step its kind.
        assertThat(firstOf("txn-lines.log", "begin")).containsEntry("txn", "8f2c00")
                .containsEntry("begin/user", "alice")
                .containsEntry("query/table", "orders")
                .containsEntry("commit/status", "ok");
    }

    @Test
    void noOtherSampleIsTiedIntoRecords() {
        // Every format and evaluation sample, and the other generated logs: a key ties lines only where there is
        // one.
        final Map<String, String> samples = new LinkedHashMap<>();
        SampleFixtures.formatNames().forEach(name -> samples.put(name, SampleFixtures.format(name)));
        SampleFixtures.evalNames().forEach(name -> {
            final List<String> texts = SampleFixtures.eval(name);
            for (int n = 0; n < texts.size(); n++) {
                samples.put(name + "#" + n, texts.get(n));
            }
        });
        samples.put("mixed-lines.log", resource("mixed-lines.log"));
        samples.put("dateless-lines.log", resource("dateless-lines.log"));

        samples.forEach((name, text) -> assertThat(SampleProfiler.profile(text)).as(name)
                .doesNotContainKey("record_key")
                .doesNotContainKey("skip_lines"));
    }

    @Test
    void theKeysGroupIsFoundPastWhatDoesntCapture() {
        assertThat(KeyedRecords.groupSpan("msg=audit\\(([^)]+)\\)")).containsExactly(11, 18);
        assertThat(KeyedRecords.groupSpan("(?:a|b)=(\\d+)")).containsExactly(8, 13);
        assertThat(KeyedRecords.groupSpan("[(]x(y)")).containsExactly(4, 7);
        assertThat(KeyedRecords.groupSpan("\\Q(\\E(z)")).containsExactly(5, 8);
        assertThat(KeyedRecords.groupSpan("(?<=\\s)(?<key>\\w+)")).containsExactly(7, 18);
        // The later lines' key, a backreference to the first's.
        final SplitterSpec spec = SplitterSpec.builder("key_value").recordKey("id=(\\d+)").build();
        assertThat(KeyedRecords.recordRegex(spec)).isEqualTo(
                "^([^\\n]*?id=(\\d+)[^\\n]*(?:\\n[^\\n]*?id=\\2[^\\n]*)*)\\n?");
    }

    @Test
    void aKeyAndALineNameAreChecked() {
        assertThatThrownBy(() -> SplitterSpec.builder("key_value").lineName("type=(\\S+)").build())
                .hasMessageContaining("give record_key");
        assertThatThrownBy(() -> SplitterSpec.builder("key_value").recordKey("(a)=(b)").build())
                .hasMessageContaining("one capturing group");
        assertThatThrownBy(() -> SplitterSpec.builder("key_value").recordKey("a=(b)").recordStart("x").build())
                .hasMessageContaining("not with record_start");
        assertThatThrownBy(() -> SplitterSpec.builder("delimited").header(true).recordKey("a=(b)").build())
                .hasMessageContaining("not delimited");
        assertThatThrownBy(() -> SplitterSpec.builder("regex").pattern("(a)").names(List.of("a")).singleQuotes(true)
                .build()).hasMessageContaining("single_quotes is for key_value");
        // Kept as given, and read back.
        final SplitterSpec spec = SplitterSpec.builder("key_value").recordKey(KEY).keyField("msg")
                .lineName("type=(\\S+)\\s*").leadField("type").build();
        assertThat(SplitterSpec.fromMap(spec.toMap())).isEqualTo(spec);
    }

    @Test
    void charactersStroomTakesOutAreNamedAndASpaceInTheirPlaceReadsThem() {
        // auditd's ENRICHED format: its translated fields after a group separator (\x1d), which Stroom's parser
        // takes out, running a value into the next key (nametype=NORMALOUID="root").
        final String raw = resource("auditd-enriched-lines.log");
        assertThat(String.valueOf(SampleProfiler.profile(raw).get("removed_characters")))
                .contains("\\x1d x", "FindReplaceFilter");
        assertThat(StroomInput.asParsed(raw)).doesNotContain("\u001d").contains("NORMALOUID=");

        // As a FindReplaceFilter before the parser makes it: a space in its place.
        final String spaced = enriched();
        assertThat(SampleProfiler.profile(spaced)).containsEntry("records", 152).doesNotContainKey(
                "removed_characters");
        assertThat(firstOf(spaced, "SYSCALL")).containsEntry("SYSCALL/AUID", "carol")
                .containsEntry("SYSCALL/key", "exec")
                .containsEntry("PATH/OUID", "root");
        assertThat(firstOf(spaced, "USER_LOGIN")).containsEntry("USER_LOGIN/res", "success")
                .containsEntry("USER_LOGIN/AUID", "alice");
    }

    /// @return The ENRICHED auditd fixture as a FindReplaceFilter turning its group separators into spaces gives it.
    static String enriched() {
        return resource("auditd-enriched-lines.log").replace('\u001d', ' ');
    }

    // The first record whose first line is named `lead`.
    private static Map<String, String> firstOf(final String fixture, final String lead) {
        final String sample = fixture.endsWith(".log")
                ? resource(fixture)
                : fixture;
        final SplitterSpec spec = SplitterDryRun.inferSpec(sample).spec();
        return SplitterDryRun.dryRun(spec, sample, 1_000).records().stream()
                .filter(record -> lead.equals(record.get(spec.leadField())))
                .findFirst()
                .orElseThrow();
    }

    private static String resource(final String name) {
        try (InputStream in = Objects.requireNonNull(TestKeyedRecords.class.getResourceAsStream(name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
