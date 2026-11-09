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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    // The keys a record has values for, of those given.
    private static Map<String, String> present(final Map<String, String> record, final String... keys) {
        final Map<String, String> found = new LinkedHashMap<>();
        for (final String key : keys) {
            if (record.get(key) != null && !record.get(key).isEmpty()) {
                found.put(key, record.get(key));
            }
        }
        return found;
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
    void cefAfterRsyslogsIsoHeaderHasItsTimeAndHost() {
        // Seen in testing: a relay's lines, rsyslog's RFC 3339 header before CEF, were read with the whole header as
        // one field, so the draft found no time (and asked the user what time to give events that had one) and no
        // host.
        final String sample = """
                2026-11-16T00:00:02+00:00 ns-gw01 ns: CEF:0|Citrix|NetScaler|NS13.1|APPFW|APPFW_SQL|6|src=203.0.113.2 \
                act=blocked
                2026-11-16T00:00:09.512004Z ns-gw02 ns[812]: CEF:0|Citrix|NetScaler|NS13.1|APPFW|APPFW_XSS|6|\
                src=203.0.113.3 act=transformed
                2026-11-16T00:00:12-05:00 ns-gw01 CEF:0|Citrix|NetScaler|NS13.1|APPFW|APPFW_CSRF|6|src=203.0.113.4 \
                act=blocked
                """;
        final SplitterDryRun.DryRun run = SplitterDryRun.dryRun(SplitterDryRun.inferSpec(sample).spec(), sample);

        assertThat(run.unmatchedLines()).isEmpty();
        assertThat(run.records()).extracting(record -> present(record, "time", "host", "app", "pid"))
                .containsExactly(Map.of("time", "2026-11-16T00:00:02+00:00", "host", "ns-gw01", "app", "ns"),
                        Map.of("time", "2026-11-16T00:00:09.512004Z", "host", "ns-gw02", "app", "ns", "pid", "812"),
                        Map.of("time", "2026-11-16T00:00:12-05:00", "host", "ns-gw01"));
        assertThat(run.records()).allSatisfy(record -> assertThat(record).doesNotContainKey("prefix"));
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
    void longValuesAreReadWithoutRecursingOnceACharacter() throws InterruptedException {
        // Seen in testing: a mail gateway's quoted list of 168 recipients (about 5,000 characters) overflowed the
        // stack of the key=value regex, (?:\\.|[^"\\])* recursing once a character, in the tools' checks and in
        // Stroom's Data Splitter. Run on a stack far smaller than a thread's default.
        final String recipients = IntStream.range(0, 400)
                .mapToObj(n -> "user." + n + "@domain" + (n % 37) + ".example.co.uk")
                .collect(Collectors.joining(","));
        final String line = "id=\"q1\" recipients=\"" + recipients + "\" note=\"said \\\"hi\\\"\" size=5";
        final SplitterSpec spec = SplitterSpec.builder("key_value").delimiter(" ").quote("\"").build();
        final String cef = "CEF:0|Acme|Gate|1.0|100|" + recipients.replace(",", " ") + "|5|msg=" + recipients
                           + " more " + recipients + " src=10.0.0.1";
        final Map<String, Object> found = new LinkedHashMap<>();
        final Thread small = new Thread(null, () -> {
            found.put("pairs", SplitterDryRun.dryRun(spec, line).records());
            final Matcher header = Pattern.compile(SplitterDryRun.CEF_PATTERN).matcher(cef);
            found.put("header", header.matches()
                    ? header.group(7)
                    : null);
            final Matcher pair = Pattern.compile(SplitterDryRun.CEF_PAIR).matcher(header.group(9));
            final Map<String, String> pairs = new LinkedHashMap<>();
            while (pair.find()) {
                pairs.put(pair.group(1), pair.group(2));
            }
            found.put("extension", pairs);
        }, "small-stack", 256 * 1024);
        small.setUncaughtExceptionHandler((thread, e) -> found.put("failed", e));
        small.start();
        small.join();

        assertThat(found.get("failed")).as("no stack overflow").isNull();
        assertThat(found.get("pairs")).isEqualTo(List.of(Map.of("id", "q1", "recipients", recipients,
                "note", "said \\\"hi\\\"", "size", "5")));
        assertThat(found.get("header")).isEqualTo(recipients.replace(",", " "));
        assertThat(found.get("extension")).isEqualTo(Map.of("msg", recipients + " more " + recipients,
                "src", "10.0.0.1"));
        // An escaped = and | read as part of a value.
        final Matcher escaped = Pattern.compile(SplitterDryRun.CEF_PAIR).matcher("msg=a\\=b c\\ d src=1");
        assertThat(escaped.find()).isTrue();
        assertThat(escaped.group(2)).isEqualTo("a\\=b c\\ d");
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
    void anInconsistentSpecIsRefused() {
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

    // A relay's RFC 3164 header before each source's own line, as a relay forwards what it received.
    static final String RELAYED = """
            <13>Nov  9 10:00:01 relay01 <38>Nov  9 09:59:58 bastion01 sshd[2211]: user=alice action=login src=192.0.2.5
            <13>Nov  9 10:00:02 relay01 <38>Nov  9 09:59:59 bastion01 sshd[2215]: user=bob action=failed src=192.0.2.9
            <13>Nov  9 10:00:03 relay01 <38>Nov  9 10:00:00 bastion02 sshd[2219]: user=carol action=login src=192.0.2.7
            """;

    @Test
    void aRelaysHeaderBeforeTheSourcesIsReadAsItsOwnFields() {
        final SplitterSpec spec = SplitterDryRun.inferSpec(RELAYED).spec();

        assertThat(spec.kind()).isEqualTo("syslog");
        assertThat(spec.namePrefix()).isEqualTo("relay_");
        assertThat(spec.bodyField()).isEqualTo("relay_message");
        assertThat(spec.body().kind()).isEqualTo("syslog");
        assertThat(spec.body().namePrefix()).isNull();
        assertThat(spec.body().body().kind()).isEqualTo("key_value");
        final Map<String, String> first = SplitterDryRun.dryRun(spec, RELAYED).records().getFirst();
        assertThat(first).containsEntry("relay_time", "Nov  9 10:00:01").containsEntry("relay_host", "relay01")
                .containsEntry("time", "Nov  9 09:59:58").containsEntry("host", "bastion01")
                .containsEntry("app", "sshd").containsEntry("pid", "2211").containsEntry("user", "alice");
        // The relay's message is the source's whole line.
        assertThat(first.get("relay_message")).startsWith("<38>Nov  9 09:59:58 bastion01 sshd[2211]: ");
    }

    @Test
    void anRfc5424RelayAroundAnRfc3164SourceIsReadToo() {
        final String sample = RELAYED.lines()
                .map(line -> "<13>1 2026-11-09T10:00:01.123Z relay01 rsyslogd - - - " + line.substring(28))
                .collect(Collectors.joining("\n"));
        final SplitterSpec spec = SplitterDryRun.inferSpec(sample).spec();

        assertThat(spec.rfc()).isEqualTo("rfc5424");
        assertThat(spec.body().rfc()).isEqualTo("rfc3164");
        assertThat(SplitterDryRun.dryRun(spec, sample).records().getFirst())
                .containsEntry("relay_time", "2026-11-09T10:00:01.123Z").containsEntry("host", "bastion01");
    }

    @Test
    void aCollectorsWordsBeforeEachHeaderAreTheRelaysFields() {
        final String sample = RELAYED.lines()
                .map(line -> "2026-11-09T10:00:01Z collector01 " + line.substring(28))
                .collect(Collectors.joining("\n"));
        final Map<String, Object> profile = SampleProfiler.profile(sample);
        assertThat(profile).containsEntry("format", SampleProfiler.SYSLOG_3164_FORMAT)
                .containsEntry(SampleProfiler.PREFIX_WORDS, 2);

        final SplitterSpec spec = SplitterDryRun.inferSpec(sample).spec();

        assertThat(spec.kind()).isEqualTo("regex");
        assertThat(spec.names()).containsExactly("relay_time", "relay_word2", "relay_line");
        assertThat(spec.body().kind()).isEqualTo("syslog");
        final SplitterDryRun.DryRun run = SplitterDryRun.dryRun(spec, sample);
        assertThat(run.records()).hasSize(3);
        assertThat(run.records().getFirst()).containsEntry("relay_time", "2026-11-09T10:00:01Z")
                .containsEntry("relay_word2", "collector01").containsEntry("time", "Nov  9 09:59:58")
                .containsEntry("user", "alice");
    }

    @Test
    void aPlainSyslogStreamIsNotTakenForARelayedOne() {
        final String sample = RELAYED.lines().map(line -> line.substring(28)).collect(Collectors.joining("\n"));
        final SplitterSpec spec = SplitterDryRun.inferSpec(sample).spec();

        assertThat(spec.namePrefix()).isNull();
        assertThat(spec.body().kind()).isEqualTo("key_value");
    }

    @Test
    void structuredDataIsReadToItsOwnClosingBracket() {
        // A quoted value holding escaped brackets, then a second element.
        final String line = "<14>1 2026-11-09T21:32:22+11:00 nas01 Connection - - [synolog@6574 event_id=\"0x0004\" "
                            + "event=\"User [EXAMPLE\\\\alice\\] from [192.0.2.10\\] accessed [Projects\\].\"]"
                            + "[meta sequenceId=\"12\"] User [EXAMPLE\\alice] accessed [Projects].";
        final Map<String, String> fields = SplitterDryRun.dryRun(SplitterSpec.builder("syslog").rfc("rfc5424").build(),
                line).records().getFirst();

        assertThat(fields.get("structured")).startsWith("[synolog@6574 ").endsWith("[meta sequenceId=\"12\"]");
        assertThat(fields).containsEntry("message", "User [EXAMPLE\\alice] accessed [Projects].");
    }

    @Test
    void aNamePrefixIsForSyslogAndRoundTrips() {
        final SplitterSpec spec = SplitterSpec.builder("syslog").namePrefix("relay_")
                .body(SplitterSpec.builder("syslog").build()).build();

        assertThat(spec.syslogNames()).containsExactly("relay_pri", "relay_time", "relay_host", "relay_app",
                "relay_pid", "relay_message");
        assertThat(spec.toMap()).containsEntry("name_prefix", "relay_").doesNotContainKey("body_field");
        assertThat(SplitterSpec.fromMap(spec.toMap())).isEqualTo(spec);
        assertThatThrownBy(() -> SplitterSpec.builder("key_value").namePrefix("relay_").build())
                .hasMessageContaining("name_prefix is for syslog");
        assertThatThrownBy(() -> SplitterSpec.builder("syslog").namePrefix("relay-").build())
                .hasMessageContaining("name_prefix is for syslog");
    }

    // Relayed lines and lines the sources sent directly, in one stream.
    static final String RELAYED_AND_DIRECT = RELAYED + """
            <38>Nov  9 10:00:05 bastion03 sshd[3001]: user=erin action=login src=192.0.2.21
            <38>Nov  9 10:00:06 bastion03 sshd[3002]: user=frank action=failed src=192.0.2.22
            <38>Nov  9 10:00:07 bastion04 sshd[3003]: user=grace action=logout src=192.0.2.23
            """;

    @Test
    void relayedAndDirectLinesInOneStreamAreEachReadAsTheSourcesOwn() {
        final SplitterSpec spec = SplitterDryRun.inferSpec(RELAYED_AND_DIRECT).spec();

        // One regex, so a kind of event is one kind however its line came.
        assertThat(spec.kind()).isEqualTo("regex");
        assertThat(spec.names()).startsWith("relay_pri", "relay_time", "relay_host").contains("time", "host");
        assertThat(spec.body().kind()).isEqualTo("key_value");
        final List<Map<String, String>> records = SplitterDryRun.dryRun(spec, RELAYED_AND_DIRECT).records();
        assertThat(records).hasSize(6);
        // Either way, the source's own header is under the plain names.
        assertThat(records.getFirst()).containsEntry("host", "bastion01").containsEntry("time", "Nov  9 09:59:58")
                .containsEntry("relay_host", "relay01").containsEntry("user", "alice");
        assertThat(records.getLast()).containsEntry("host", "bastion04").containsEntry("time", "Nov  9 10:00:07")
                .containsEntry("relay_host", "").containsEntry("user", "grace");
    }

    @Test
    void relayedSourcesInAnotherHeaderFormatThanTheDirectOnesHaveAPartEach() {
        final String sample = """
                <13>Nov  9 10:00:01 relay01 <38>1 2026-11-09T09:59:58Z bastion01 sshd 2211 - - user=alice action=login
                <13>Nov  9 10:00:02 relay01 <38>1 2026-11-09T09:59:59Z bastion01 sshd 2215 - - user=bob action=logout
                <38>Nov  9 10:00:05 bastion03 sshd[3001]: user=erin action=login src=192.0.2.21
                <38>Nov  9 10:00:06 bastion03 sshd[3002]: user=frank action=logout src=192.0.2.22
                """;
        final SplitterSpec spec = SplitterDryRun.inferSpec(sample).spec();

        assertThat(spec.parts()).extracting(SplitterSpec::variant).containsExactly("syslog_relayed", "syslog");
        final List<Map<String, String>> records = SplitterDryRun.dryRun(spec, sample).records();
        assertThat(records.getFirst()).containsEntry("variant", "syslog_relayed").containsEntry("host", "bastion01")
                .containsEntry("time", "2026-11-09T09:59:58Z").containsEntry("relay_host", "relay01");
        assertThat(records.getLast()).containsEntry("variant", "syslog").containsEntry("host", "bastion03")
                .doesNotContainKey("relay_host");
    }

    @Test
    void relayedAndDirectSyslogAmongOtherFormatsAreOnePart() {
        final String sample = RELAYED_AND_DIRECT + """
                2026-11-09 10:00:08 ERROR disk full on /var
                2026-11-09 10:00:09 WARN queue backing up
                2026-11-09 10:00:10 INFO queue drained
                """;
        final SplitterSpec spec = SplitterDryRun.inferSpec(sample).spec();

        assertThat(spec.kind()).isEqualTo(SplitterSpec.ALTERNATIVES);
        assertThat(spec.parts()).extracting(SplitterSpec::variant).contains("syslog", "text")
                .doesNotContain("syslog_relayed");
        final List<Map<String, String>> records = SplitterDryRun.dryRun(spec, sample).records();
        assertThat(records).hasSize(9);
        assertThat(records).filteredOn(record -> "syslog".equals(record.get("variant"))).hasSize(6)
                .allSatisfy(record -> assertThat(record).containsKeys("relay_host", "host", "user"));
    }
}
