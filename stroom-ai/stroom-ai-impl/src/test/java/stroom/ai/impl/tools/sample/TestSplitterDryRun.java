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

import stroom.ai.impl.workspace.PatternTimeoutException;
import stroom.ai.impl.workspace.SafePattern;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSplitterDryRun {

    static final String BLUECOAT = """
            #Software: SGOS 6.7.5.12
            #Version: 1.0
            #Start-Date: 2026-10-11 08:00:00
            #Date: 2026-10-11 08:00:00
            #Fields: date time time-taken c-ip cs-username cs-auth-group x-exception-id sc-filter-result \
            cs-categories cs(Referer) sc-status s-action cs-method rs(Content-Type) cs-uri-scheme cs-host \
            cs-uri-port cs-uri-path cs-uri-query cs(User-Agent) s-ip sc-bytes cs-bytes
            #Remark: 4111140007 "proxy01" "192.168.1.10" "main"

            2026-10-11 08:00:01 125 192.168.1.100 jsmith Employees - OBSERVED "Business/Economy" - 200 TCP_NC_MISS \
            GET text/html https www.example.com 443 /index.html - "Mozilla/5.0" 192.168.1.10 12450 820
            2026-10-11 08:00:03 2 192.168.1.102 guest Guests policy_denied DENIED "Malicious Sources" - 403 \
            TCP_DENIED GET text/html http malware.example.net 80 /payload.exe - "Mozilla/5.0" 192.168.1.10 1250 420
            2026-10-11 08:00:04 210 192.168.1.103 jbloggs Employees - OBSERVED "Business/Economy" - 200 TCP_NC_MISS \
            POST application/json https portal.example.org 443 /api/login - "Mozilla/5.0" 192.168.1.10 850 1450
            #Software: SGOS 7.3.1
            #Fields: date time c-ip cs-username sc-status
            2026-10-11 09:00:00 192.168.1.200 later 404
            """;
    static final String ZEEK = "#separator \\x09\n#path\tconn\n#open\t2026-10-11-00-00-00\n"
                               + "#fields\tts\tuid\tid.orig_h\tid.resp_h\tid.resp_p\tproto\n"
                               + "#types\ttime\tstring\taddr\taddr\tport\tenum\n"
                               + "1791676800.1\tC1\t10.0.0.1\t93.184.216.34\t443\ttcp\n"
                               + "1791676801.2\tC2\t10.0.0.2\t8.8.8.8\t53\tudp\n"
                               + "1791676802.3\tC3\t10.0.0.3\t10.0.0.9\t22\ttcp\n#close\t2026-10-11\n";
    static final String CRI_TEXT = "2026-10-11T08:00:01.1Z stdout F user=alice action=login\n"
                                   + "2026-10-11T08:00:02.1Z stderr P user=bo\n"
                                   + "2026-10-11T08:00:02.2Z stderr F b action=export\n"
                                   + "2026-10-11T08:00:03.1Z stdout F user=carol action=logout\n";
    private static final String FORTIOS = "date=2026-10-01 time=10:00:00 devname=\"fw01\" srcip=10.0.0.1 "
                                          + "dstip=203.0.113.5 dstport=443 action=\"accept\"\n"
                                          + "date=2026-10-01 time=10:00:05 devname=\"fw01\" srcip=10.0.0.2 "
                                          + "dstip=203.0.113.6 dstport=80 action=\"deny\"\n";

    private static List<Map<String, String>> records(final String name) {
        final String sample = SampleFixtures.format(name);
        final SplitterDryRun.DryRun run = SplitterDryRun.dryRun(SplitterDryRun.inferSpec(sample).spec(), sample);
        assertThat(run.unmatchedLines()).isEmpty();
        return run.records();
    }

    private static Map<String, String> pick(final Map<String, String> record, final String... keys) {
        final Map<String, String> picked = new LinkedHashMap<>();
        for (final String key : keys) {
            picked.put(key, record.get(key));
        }
        return picked;
    }

    @Test
    void cefAloneAndAfterSyslogIsItsHeaderThenPairsWhoseValuesHoldSpaces() {
        // Seen: CEF read as key=value, its header fields taken for keys and its values cut at the first space.
        for (final String name : List.of("cef", "syslog_cef")) {
            assertThat(SampleProfiler.profile(SampleFixtures.format(name))).containsEntry("format", "cef");
            final Map<String, String> first = records(name).getFirst();
            assertThat(pick(first, "cef_vendor", "cef_product", "cef_signature_id", "cef_name", "cef_severity"))
                    .isEqualTo(Map.of("cef_vendor", "Acme", "cef_product", "Gateway", "cef_signature_id", "100",
                            "cef_name", "User logged in", "cef_severity", "3"));
            assertThat(pick(first, "suser", "src", "msg")).isEqualTo(Map.of("suser", "alice", "src", "10.0.0.5",
                    "msg", "Logged in from the VPN"));
        }
        assertThat(records("syslog_cef").getFirst()).containsEntry("host", "gw01");
        assertThat(records("cef").getFirst()).containsEntry("rt", "Oct 01 2026 08:00:00 UTC");
    }

    @Test
    void keyValuePairsAreOneRegexWithOneValueGroupQuotedOrNot() {
        // Seen in Stroom: a split on '=' with $2 (a split has none), then $2$3 (a group that took no part can't be
        // named).
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("date", "2026-10-01");
        expected.put("time", "08:05:00");
        expected.put("devname", "fw01");
        expected.put("type", "traffic");
        expected.put("action", "deny");
        expected.put("srcip", "203.0.113.9");
        expected.put("dstip", "10.0.0.10");
        expected.put("user", "bob smith");
        assertThat(records("kv_quoted").get(1)).isEqualTo(expected);
        assertThat(SplitterDryRun.kvPatterns(SplitterDryRun.inferSpec(SampleFixtures.format("kv_quoted")).spec()))
                .hasSize(1);
        final SplitterSpec unquoted = SplitterSpec.builder("key_value").build();
        assertThat(SplitterDryRun.dryRun(unquoted, "a=1 b=two c=").records())
                .containsExactly(Map.of("a", "1", "b", "two", "c", ""));
    }

    @Test
    void aDoubledQuoteStaysDoubledAsInStroom() {
        assertThat(records("csv_quoted").get(1)).containsEntry("message", "Said \"\"bye\"\" and left");
    }

    @Test
    void headerlessQuotedCsvIsOneGroupPerColumn() {
        // $1$2 per column (a quoted group or a bare one) is refused by Stroom when one took no part.
        final SplitterSpec spec = SplitterSpec.builder("delimited").header(List.of("time", "user", "message"))
                .quote("\"").build();

        final SplitterDryRun.DryRun run = SplitterDryRun.dryRun(spec, "2026-10-01T08:00:00Z,\"o'brien, pat\","
                                                                      + "\"Said \"\"bye\"\" and left\"\n"
                                                                      + "2026-10-01T08:05:00Z,bob,\n");

        assertThat(run.records()).containsExactly(
                Map.of("time", "2026-10-01T08:00:00Z", "user", "o'brien, pat", "message", "Said \"\"bye\"\" and left"),
                Map.of("time", "2026-10-01T08:05:00Z", "user", "bob", "message", ""));
        assertThat(SplitterDryRun.namedColumnsPattern(spec)).doesNotContain("$1$2");
    }

    @Test
    void splitterSpecsRunOnTheSample() {
        final SplitterSpec syslog = SplitterSpec.builder("syslog").rfc("rfc3164")
                .body(SplitterSpec.builder("key_value").quote("\"").build()).build();

        final SplitterDryRun.DryRun run = SplitterDryRun.dryRun(syslog, "<34>Sep 28 10:00:00 host sshd[1]: "
                                                                        + "user=alice action=\"log in\" src=10.0.0.1\n"
                                                                        + "noise line\n");

        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("pri", "34");
        expected.put("time", "Sep 28 10:00:00");
        expected.put("host", "host");
        expected.put("app", "sshd");
        expected.put("pid", "1");
        expected.put("message", "user=alice action=\"log in\" src=10.0.0.1");
        expected.put("user", "alice");
        expected.put("action", "log in");
        expected.put("src", "10.0.0.1");
        assertThat(run.records()).containsExactly(expected);
        assertThat(run.unmatchedLines()).containsExactly("noise line");
        assertThat(run.fields()).contains("action");
        final SplitterDryRun.DryRun csv = SplitterDryRun.dryRun(SplitterSpec.builder("delimited").header(true)
                .quote("\"").build(), "time,user,msg\n2026-01-01T00:00:00Z,alice,\"a, b\"\n");
        assertThat(csv.records()).containsExactly(Map.of("time", "2026-01-01T00:00:00Z", "user", "alice", "msg",
                "a, b"));
        final SplitterSpec named = SplitterSpec.builder("delimited").header(List.of("time", "user")).delimiter("|")
                .build();
        assertThat(SplitterDryRun.namedColumnsPattern(named)).isEqualTo("^([^\\|]*)\\|([^\\|]*)$");
        assertThat(SplitterDryRun.dryRun(named, "a|b\nc|d|e\n").records())
                .containsExactly(Map.of("time", "a", "user", "b"));
        final SplitterSpec regex = SplitterSpec.builder("regex").pattern("^(\\S+) (.*)$").names(List.of("t",
                "message")).body(SplitterSpec.builder("regex").pattern("^user=(\\S+)$").names(List.of("user"))
                .build()).build();
        assertThat(SplitterDryRun.dryRun(regex, "x user=bob").records())
                .containsExactly(Map.of("t", "x", "message", "user=bob", "user", "bob"));
        assertThatThrownBy(() -> SplitterSpec.builder("regex").build())
                .hasMessage("regex needs pattern and names");
    }

    @Test
    void aRepeatedKeyIsReadByItsFirst() {
        // Seen in eval case 50: normalize-space() over every rcpt failed each record with two recipients.
        final SplitterSpec spec = SplitterSpec.builder("key_value").delimiter(" ").build();

        assertThat(SplitterDryRun.dryRun(spec, "rcpt=bob rcpt=carol from=alice\n").records().getFirst())
                .containsEntry("rcpt", "bob");
    }

    @Test
    void aRecordThatEndsAtAMarkedLineKubernetesCri() {
        final SplitterDryRun.Inferred inferred = SplitterDryRun.inferSpec(CRI_TEXT);

        assertThat(inferred.profile()).containsEntry("format", "cri");
        assertThat(inferred.spec().recordEnd()).isEqualTo(SplitterDryRun.CRI_END);
        final List<String> messages = new ArrayList<>();
        SplitterDryRun.dryRun(inferred.spec(), CRI_TEXT).records().forEach(r -> messages.add(r.get("message")));
        assertThat(messages).hasSize(3);
        assertThat(messages.get(1)).isEqualTo("user=bo\n2026-10-11T08:00:02.2Z stderr F b action=export");
    }

    @Test
    void theColumnsComeFromTheDirectiveInEffectAndDirectivesRideAlong() {
        final SplitterDryRun.DryRun run = SplitterDryRun.dryRun(SplitterDryRun.inferSpec(BLUECOAT).spec(), BLUECOAT);

        assertThat(run.records()).hasSize(4);
        assertThat(run.unmatchedLines()).isEmpty();
        final Map<String, String> first = run.records().getFirst();
        assertThat(first).containsEntry("cs-categories", "Business/Economy")
                .containsEntry("cs(User-Agent)", "Mozilla/5.0").containsEntry("#Software", "SGOS 6.7.5.12");
        assertThat(first.get("#Remark")).startsWith("4111140007 \"proxy01\"");
        // The log restarted: new columns and a new software version for the lines after it (as Stroom runs it:
        // checked on the local stack, step for step).
        final Map<String, String> last = new LinkedHashMap<>(run.records().getLast());
        last.keySet().removeIf(key -> key.startsWith("#"));
        assertThat(last).isEqualTo(Map.of("date", "2026-10-11", "time", "09:00:00", "c-ip", "192.168.1.200",
                "cs-username", "later", "sc-status", "404"));
        assertThat(run.records().getLast()).containsEntry("#Software", "SGOS 7.3.1");
    }

    @Test
    void zeekNamesItsColumnsWithATabSeparatedFieldsDirective() {
        final SplitterDryRun.DryRun run = SplitterDryRun.dryRun(SplitterDryRun.inferSpec(ZEEK).spec(), ZEEK);

        assertThat(run.records()).extracting(r -> r.get("proto")).containsExactly("tcp", "udp", "tcp");
        assertThat(run.records().getFirst()).containsEntry("#path", "conn");
    }

    @Test
    void aHeaderOverAColumnOfTimesIsAHeader() {
        // Seen in e2e: time,user,host,action over ISO times and words was read as a record (only the time column
        // differed from its name), and the columns were called col1 to col4.
        assertThat(SplitterDryRun.inferSpec("time,user,host,action\n2026-10-01T08:00:00Z,alice,ws01,start\n"
                                            + "2026-10-01T08:05:00Z,bob,ws02,stop\n").spec().header())
                .isEqualTo(true);
        assertThat(SplitterDryRun.inferSpec("alice,ws01,start\nbob,ws02,stop\ncarol,ws03,start\n").spec().header())
                .isEqualTo(List.of("col1", "col2", "col3"));
        assertThat(SplitterDryRun.inferSpec("2026-10-01T08:00:00Z,alice\n2026-10-01T08:05:00Z,bob\n").spec()
                .header()).isEqualTo(List.of("col1", "col2"));
    }

    @Test
    void theSplitterSpecIsInferredFromTheSample() {
        assertThat(SplitterDryRun.inferSpec(FORTIOS).spec().toMap())
                .isEqualTo(SampleProfiler.map("kind", "key_value", "delimiter", " ", "quote", "\""));
        assertThat(SplitterDryRun.inferSpec("time,user,action\n2026-10-01T10:00:00Z,alice,login\n").spec().toMap())
                .isEqualTo(SampleProfiler.map("kind", "delimited", "header", true));
        final String syslog = "<34>Sep 28 10:00:00 fw01 fortigate: user=alice action=login src=10.0.0.1\n".repeat(3);
        final SplitterSpec spec = SplitterDryRun.inferSpec(syslog).spec();
        assertThat(spec.kind()).isEqualTo("syslog");
        assertThat(spec.body().toMap()).isEqualTo(SampleProfiler.map("kind", "key_value", "delimiter", " "));
        assertThat(SplitterDryRun.dryRun(spec, syslog).records().getFirst()).containsEntry("user", "alice");
        assertThat(SplitterDryRun.inferSpec("{\"a\": 1}\n{\"a\": 2}\n").spec()).isNull();
        assertThat(SplitterDryRun.inferSpec("something odd happened here\nand again there\n").spec()).isNull();
        final SplitterSpec piped = SplitterDryRun.inferSpec("a|b\nc|d\n").spec();
        assertThat(piped.kind()).isEqualTo("delimited");
        assertThat(piped.delimiter()).isEqualTo("|");
    }

    @Test
    void aSyslogHeaderOverCsvIsReadByPosition() {
        // Palo Alto: a syslog header, then their CSV, named by position for the model to rename.
        final String paloAlto = "<14>Oct  1 08:00:00 pa01 1,2026/10/01 08:00:00,0011,TRAFFIC,end,2561,10.0.0.5\n"
                                + "<14>Oct  1 08:00:01 pa01 1,2026/10/01 08:00:01,0011,TRAFFIC,deny,2561,10.0.0.6\n"
                                + "<14>Oct  1 08:00:02 pa01 1,2026/10/01 08:00:02,0011,THREAT,url,2561,10.0.0.7\n";

        final SplitterSpec spec = SplitterDryRun.inferSpec(paloAlto).spec();

        assertThat(spec.body().kind()).isEqualTo("delimited");
        assertThat(spec.body().columns()).hasSize(7).startsWith("col1");
        assertThat(SplitterDryRun.dryRun(spec, paloAlto).records().get(2)).containsEntry("col4", "THREAT");
    }

    @Test
    void aBodyItsExpressionsDoNotMatchIsAnUnmatchedRecord() {
        // Seen: a Palo Alto body short of a column, fine here and refused in Stroom.
        final SplitterSpec spec = SplitterSpec.builder("syslog")
                .body(SplitterSpec.builder("delimited").header(List.of("a", "b", "c")).build()).build();

        final SplitterDryRun.DryRun run = SplitterDryRun.dryRun(spec, "<14>Oct  1 08:00:00 pa01 1,2,3\n"
                                                                      + "<14>Oct  1 08:00:01 pa01 1,2\n");

        assertThat(run.records()).hasSize(1);
        assertThat(run.unmatchedLines()).containsExactly("<14>Oct  1 08:00:01 pa01 1,2");
    }

    @Test
    void aLongSampleIsReadAtLinesSpreadOverIt() {
        // Seen in an eval case (a day's CEF file): the first 200 lines held only two kinds of event.
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            text.append("n=").append(i).append('\n');
        }

        final SplitterDryRun.DryRun run = SplitterDryRun.dryRun(SplitterSpec.builder("key_value").build(),
                text.toString(), 100);

        assertThat(run.records()).hasSize(100);
        assertThat(run.records().getFirst()).containsEntry("n", "0");
        assertThat(run.records().getLast()).containsEntry("n", "4999");
    }

    @Test
    @Timeout(120)
    void aPatternThatBacktracksBadlyIsStoppedAndSaysSo() {
        // Before, the agent's pattern ran on texts of up to a million characters with no time limit.
        final SplitterSpec spec = SplitterSpec.builder("regex").pattern("(.*?,){25}x").names(List.of("a")).build();

        assertThatThrownBy(() -> SplitterDryRun.dryRun(spec, "a,".repeat(40)))
                .isInstanceOf(PatternTimeoutException.class)
                .hasMessageStartingWith("The pattern '(.*?,){25}x' took too long to match");
    }

    @Test
    @Timeout(60)
    void aRecordStartThatBacktracksBadlyStopsAtTheCallsTimeLimit() {
        // The first line starts a record whatever it is: the second is matched.
        final List<String> lines = List.of("first", "a,".repeat(40));

        assertThatThrownBy(() -> ToolDeadline.run(Duration.ofMillis(300),
                () -> SplitterDryRun.groupLines(lines, "(.*?,){25}x"))).isInstanceOf(ToolTimeoutException.class);
        assertThatThrownBy(() -> SplitterDryRun.groupLinesToEnd(lines, "a".repeat(SafePattern.MAX_REGEX_LENGTH + 1)))
                .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void anInconsistentSpecIsRefusedAsThePythonModelRefusesIt() {
        assertThatThrownBy(() -> SplitterSpec.builder("delimited").build())
                .hasMessage("delimited needs header: True (a header line) or the column names");
        assertThatThrownBy(() -> SplitterSpec.builder("delimited").header(true).headerDirective("#Fields:").build())
                .hasMessageStartingWith("header_directive and directives need comment_prefix");
        assertThatThrownBy(() -> SplitterSpec.builder("delimited").header(true).commentPrefix("#")
                .directives(List.of("!Software")).build())
                .hasMessage("directives start with the comment_prefix '#'");
        assertThatThrownBy(() -> SplitterSpec.builder("delimited").header(List.of("a")).commentPrefix("#")
                .headerDirective("#Fields:").build())
                .hasMessage("header_directive names the columns: give header: true with it");
        assertThat(SplitterSpec.builder("key_value").build().delimiter()).isEqualTo(" ");
    }
}
