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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/// The formats shortlist (formats-shortlist-generator.py), each read as its source writes it. Asked for by the user,
/// each fix in the general case: what is learned from one format is a rule for any.
class TestFormatsShortlist {

    @Test
    void textAroundPairsIsKeptAsWords() {
        // netfilter: a prefix before the pairs, empty values, bare flags between them.
        final SplitterSpec iptables = inferred("iptables-lines.log");
        assertThat(iptables.body().words()).isTrue();
        final Map<String, String> dropped = firstRecord("iptables-lines.log");
        assertThat(dropped.get(SplitterSpec.WORDS)).matches("\\[\\d+\\.\\d+] IPTABLES-(DROP|ACCEPT):");
        assertThat(dropped).containsEntry("OUT", "").containsEntry("PROTO", "TCP").containsKey("URGP");
        // OpenLDAP: verbs between the pairs.
        assertThat(firstRecord("slapd-lines.log")).containsEntry(SplitterSpec.WORDS, "ACCEPT from")
                .containsEntry("conn", "1000");
        // The same few words before every record's pairs, and nothing else: fields of their own, the time named.
        final String prefixed = PREFIXED;
        final SplitterSpec prefix = SplitterDryRun.inferSpec(prefixed).spec();

        assertThat(prefix.names()).containsExactly("time", "prefix2", "message");
        assertThat(SplitterDryRun.dryRun(prefix, prefixed).records().getFirst())
                .containsEntry("time", "2026-09-28T15:00:00Z").containsEntry("prefix2", "fw01")
                .containsEntry("action", "allow");
        // Pairs and nothing else need none.
        final String pairs = "a=1 b=2 c=3\na=4 b=5 c=6\n";
        assertThat(SplitterDryRun.inferSpec(pairs).spec().words()).isFalse();
    }

    @Test
    void pairsSeparatedByACommaMayHaveKeysHoldingSpaces() {
        // Cisco ISE: ", " between pairs, keys holding spaces after it, text before the pairs and a trailing comma.
        assertThat(inferred("ise-lines.log").body().delimiter()).isEqualTo(", ");
        final Map<String, String> ise = records("ise-lines.log").stream()
                .filter(record -> record.containsKey("Device IP Address"))
                .findFirst()
                .orElseThrow();
        assertThat(ise.get(SplitterSpec.WORDS)).startsWith("CISE_").endsWith("Authentication failed");
        assertThat(ise).containsEntry("UserName", "bob").containsEntry("Device Type",
                "Device Type#All Device Types#Switch");
        // A later segment: words before its first key, which holds no space.
        assertThat(records("ise-lines.log")).anySatisfy(record -> assertThat(record)
                .containsEntry(SplitterSpec.WORDS, "CISE_Passed_Authentications 0001234501 3 2")
                .containsEntry("AuthenticationMethod", "MSCHAPV2"));
        // Postfix: a queue id before the pairs, values running to the next comma.
        final List<Map<String, String>> postfix = records("postfix-lines.log");
        assertThat(postfix).anySatisfy(record -> assertThat(record).containsKey("from")
                .containsKey("size")
                .containsEntry("nrcpt", "1 (queue active)")
                .hasEntrySatisfying(SplitterSpec.WORDS, words -> assertThat(words).matches("[0-9A-F]+:")));
        assertThat(postfix).anySatisfy(record -> assertThat(record.get("status")).startsWith("sent (250 2.0.0 Ok"));
    }

    @Test
    void quotedKeyColonValuePairsAreRead() {
        // Check Point's Log Exporter: the pairs in the syslog record's structured data, its message empty.
        final SplitterSpec checkPoint = inferred("checkpoint-lines.log");
        assertThat(checkPoint.bodyField()).isEqualTo("structured");
        assertThat(checkPoint.body().body().pairSeparator()).isEqualTo(":");
        assertThat(checkPoint.body().body().delimiter()).isEqualTo("; ");
        final Map<String, String> rejected = records("checkpoint-lines.log").stream()
                .filter(record -> record.containsKey("rule_name"))
                .findFirst()
                .orElseThrow();
        // The header's time first, as Stroom keeps it, its own time:"..." the second of that name.
        assertThat(rejected.get("time")).startsWith("2026-10-11T");
        assertThat(rejected).containsEntry("ifname", "eth1").containsEntry("origin", "10.0.0.1")
                .hasEntrySatisfying("rule_name", rule -> assertThat(rule).startsWith("Block "))
                .hasEntrySatisfying("loguid", uid -> assertThat(uid).startsWith("{0x").endsWith("}"));
        // The same pairs as lines of their own.
        final String lines = "action:\"Accept\"; src:\"10.1.2.3\"; dst:\"10.0.0.5\"; service:\"443\"\n"
                             + "action:\"Drop\"; src:\"203.0.113.9\"; dst:\"10.0.0.6\"; service:\"22\"\n";
        final SplitterSpec spec = SplitterDryRun.inferSpec(lines).spec();
        assertThat(spec.pairSeparator()).isEqualTo(":");
        assertThat(SplitterDryRun.dryRun(spec, lines).records().getLast()).containsEntry("action", "Drop")
                .containsEntry("service", "22");
        assertThat(SampleProfiler.profile(lines)).doesNotContainKey("malformed_lines");
    }

    private static List<Map<String, String>> records(final String fixture) {
        final SplitterDryRun.DryRun dryRun = SplitterDryRun.dryRun(inferred(fixture), resource(fixture), 1_000);
        assertThat(dryRun.unmatchedLines()).as(fixture).isEmpty();
        return dryRun.records();
    }

    /// Records of a time and a host before their pairs.
    static final String PREFIXED = "2026-09-28T15:00:00Z fw01 action=allow src=10.0.0.5 dst=10.1.2.3 dport=443\n"
                                   + "2026-09-28T15:00:03Z fw02 action=deny src=203.0.113.77 dst=10.0.0.10 dport=22\n"
                                   + "2026-09-28T15:01:12Z fw01 action=allow src=10.0.1.8 dst=8.8.8.8 dport=53\n";

    private static SplitterSpec inferred(final String fixture) {
        return SplitterDryRun.inferSpec(resource(fixture)).spec();
    }

    private static Map<String, String> firstRecord(final String fixture) {
        final String sample = resource(fixture);
        final SplitterDryRun.DryRun dryRun = SplitterDryRun.dryRun(inferred(fixture), sample, 1_000);
        assertThat(dryRun.unmatchedLines()).as(fixture).isEmpty();
        return dryRun.records().getFirst();
    }

    /// @param name A fixture.
    /// @return Its text.
    static String resource(final String name) {
        try (InputStream in = Objects.requireNonNull(TestFormatsShortlist.class.getResourceAsStream(name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /// @return The shortlist's fixtures.
    static List<String> fixtures() {
        return List.of("checkpoint", "iptables", "leef1", "leef2", "postfix", "slapd", "ise", "postgres", "modsec");
    }
}
