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
