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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.generation.DumpMode;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The intermediate form a CEF forwarder of two stages hands its lines on in, read from the shared XSLT that turns
/// it into Kafka records: candidates from its patterns and the values it takes, each checked by running it; and a
/// chained plan's flattener, whose output the shared XSLT turns into kafka-records:1 carrying each line.
class TestCefHandoff {

    private static final String XSL = "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\"";
    private static final String FORK = "file://xml/schema/accounting/events";

    /// A shared Kafka XSLT, as a forwarding template might hold it: records of `data` name/value pairs in, one
    /// Kafka record a record out, its value the pair named `cef`, its key the host's, the topic set here, once.
    static final String SHARED = "<xsl:stylesheet " + XSL + " xpath-default-namespace=\"" + FORK + "\" "
                                 + "xmlns=\"kafka-records:1\"><xsl:template match=\"/records\"><kafkaRecords>"
                                 + "<xsl:for-each select=\"record\"><kafkaRecord topic=\"arcsight-prod\">"
                                 + "<key><xsl:value-of select=\"data[@name='host']/@value\"/></key>"
                                 + "<value><xsl:value-of select=\"data[@name='cef']/@value\"/></value>"
                                 + "</kafkaRecord></xsl:for-each></kafkaRecords></xsl:template></xsl:stylesheet>";

    @Test
    void theFormIsReadFromWhatTheSharedXsltTakesForTheRecordsValue() {
        final CefHandoff.Inferred inferred = CefHandoff.infer(SHARED).orElseThrow();

        assertThat(inferred.checked()).isTrue();
        assertThat(inferred.handoff()).isEqualTo(new CefHandoff(FORK, List.of("records", "record", "data"), "name",
                "cef", "value"));
        assertThat(inferred.handoff().said()).isEqualTo("`<records xmlns=\"" + FORK + "\"><record><data name=\"cef\" "
                                                        + "value=\"CEF line\"/></record></records>` (namespace `"
                                                        + FORK + "`)");
        // The key's pair is a candidate too, after the value's: running it puts no line in a value.
        assertThat(CefHandoff.candidates(SHARED)).hasSizeGreaterThanOrEqualTo(2).first()
                .isEqualTo(inferred.handoff());
    }

    @Test
    void aTemplateMatchingTheItemAnywhereGetsARootAndItsText() {
        // Items matched wherever they are, the line their text; no namespace.
        final String shared = "<xsl:stylesheet " + XSL + " xmlns=\"kafka-records:1\"><xsl:template match=\"/\">"
                              + "<kafkaRecords><xsl:apply-templates select=\"//line[@kind='cef']\"/></kafkaRecords>"
                              + "</xsl:template><xsl:template match=\"line[@kind='cef']\"><kafkaRecord topic=\"t\">"
                              + "<value><xsl:value-of select=\".\"/></value></kafkaRecord></xsl:template>"
                              + "</xsl:stylesheet>";

        final CefHandoff.Inferred inferred = CefHandoff.infer(shared).orElseThrow();

        assertThat(inferred.checked()).isTrue();
        assertThat(inferred.handoff()).isEqualTo(new CefHandoff("", List.of(CefHandoff.ANY_ROOT, "line"), "kind",
                "cef", null));
        assertThat(inferred.handoff().document(List.of("CEF:0|a<b"))).isEqualTo(
                "<records><line kind=\"cef\">CEF:0|a&lt;b</line></records>");
    }

    @Test
    void aPredicateTestingOnlyThatTheItemIsNamedGetsAName() {
        final String shared = "<xsl:stylesheet " + XSL + " xmlns=\"kafka-records:1\"><xsl:template match=\"/out\">"
                              + "<kafkaRecords><xsl:for-each select=\"entry[@name]\"><kafkaRecord topic=\"t\">"
                              + "<value><xsl:value-of select=\"@text\"/></value></kafkaRecord></xsl:for-each>"
                              + "</kafkaRecords></xsl:template></xsl:stylesheet>";

        assertThat(CefHandoff.infer(shared).orElseThrow().handoff())
                .isEqualTo(new CefHandoff("", List.of("out", "entry"), "name", CefHandoff.ANY_NAME, "text"));
    }

    @Test
    void aSharedXsltThatCantRunHereGivesItsBestFormUnchecked() {
        // It calls Stroom's own functions: compiled here, it fails; stepping checks it in Stroom.
        final String shared = SHARED.replace("<xsl:stylesheet ", "<xsl:stylesheet xmlns:stroom=\"stroom\" ")
                .replace("topic=\"arcsight-prod\"", "topic=\"{stroom:feed-name()}\"");

        final CefHandoff.Inferred inferred = CefHandoff.infer(shared).orElseThrow();

        assertThat(inferred.checked()).isFalse();
        assertThat(inferred.why()).startsWith("the shared XSLT can't be run here");
        assertThat(inferred.handoff().path()).containsExactly("records", "record", "data");
    }

    @Test
    void nothingIsReadFromAnXsltThatIsntWellFormedOrTakesNoValue() {
        assertThat(CefHandoff.candidates("<xsl:stylesheet")).isEmpty();
        assertThat(CefHandoff.infer(null)).isEmpty();
        assertThat(CefHandoff.infer("<xsl:stylesheet " + XSL + "><xsl:template match=\"/\"><kafkaRecords/>"
                                    + "</xsl:template></xsl:stylesheet>")).isEmpty();
    }

    @Test
    void theCheckSaysWhereTheSharedStagesOutputFallsShort() {
        final CefHandoff form = new CefHandoff(FORK, List.of("records", "record", "data"), "name", "cef", "value");
        final List<String> lines = List.of("CEF:0|a|b|1|c|d|3|suser=x", "CEF:0|a|b|1|c|d|3|suser=y");

        assertThat(CefHandoff.check(SHARED, form.document(lines), lines).problems()).isEmpty();
        // Another name: no line reaches a value.
        assertThat(CefHandoff.check(SHARED, form.document(lines).replace("\"cef\"", "\"other\""), lines).problems())
                .containsExactly("Kafka record 1's value isn't the CEF line handed on",
                        "Kafka record 2's value isn't the CEF line handed on");
        assertThat(CefHandoff.check(SHARED.replace(" topic=\"arcsight-prod\"", ""), form.document(lines), lines)
                .problems()).contains("Kafka record 1 has no topic");
        assertThat(CefHandoff.kafkaProblems("<records/>", lines)).containsExactly(
                "it wrote no kafkaRecords in kafka-records:1");
        assertThat(CefHandoff.kafkaProblems("<kafkaRecords xmlns=\"kafka-records:1\"/>", lines)).containsExactly(
                "it wrote 0 Kafka record(s) for 2 CEF line(s)");
    }

    @Test
    void aFormIsKeptAsJsonAndReadBack() {
        final CefHandoff form = new CefHandoff(FORK, List.of("records", "record", "data"), "name", "cef", "value");

        assertThat(CefHandoff.fromJson(form.toJson())).isEqualTo(form);
        assertThat(CefHandoff.fromJson(new CefHandoff("", List.of("r", "line"), null, null, null).toJson()))
                .isEqualTo(new CefHandoff("", List.of("r", "line"), null, null, null));
        assertThat(CefHandoff.fromJson(Map.of("path", List.of("only")))).isNull();
        assertThat(CefHandoff.fromJson(null)).isNull();
        assertThat(form.linesIn("<?xml version=\"1.1\"?><records xmlns=\"" + FORK + "\"><record><data name=\"host\" "
                                + "value=\"h\"/><data name=\"cef\" value=\"CEF:0|x\"/></record></records>"))
                .containsExactly("CEF:0|x");
        assertThat(form.linesIn("not xml")).isEmpty();
    }

    @Test
    void aChainedPlansFlattenerHandsEachLineOnAndTheSharedXsltSendsIt() {
        final CefHandoff form = CefHandoff.infer(SHARED).orElseThrow().handoff();
        final CefPlan plan = CefFixtures.drafted().plan().withTopic("ignored").withHandoff(form);

        assertThat(plan.chained()).isTrue();
        assertThat(plan.topic()).as("the shared XSLT sets the topic").isNull();
        assertThat(plan.problems()).isEmpty();
        final String xslt = plan.xslt();
        assertThat(xslt).doesNotContain("kafkaRecord", "topic=").contains("<data name=\"cef\" value=\"{$line}\"");
        final String output = CefSample.output(plan, CefFixtures.events(), 10);
        final List<String> lines = CefSample.linesOf(plan, output);
        assertThat(lines).hasSize(CefFixtures.events().size())
                .allSatisfy(line -> assertThat(line).startsWith("CEF:0|"));
        assertThat(CefSample.lines(plan.withOutput(CefPlan.KAFKA).withTopic("t"), CefFixtures.events(), 10))
                .as("the same lines as one stage writes").isEqualTo(lines);
        assertThat(CefHandoff.check(SHARED, output, lines).problems()).as("valid Kafka records, each its line")
                .isEmpty();
        // Kept with its form, and read back.
        final CefPlan kept = CefPlan.fromJson(plan.toJson(DumpMode.COMPACT));
        assertThat(kept.handoff()).isEqualTo(form);
        assertThat(kept.output()).isEqualTo(CefPlan.CHAINED);
        assertThat(kept.markdown(Map.of(), null, null)).contains("handed on to the shared XSLT");
        // With no form, a problem, and no XSLT.
        assertThat(plan.withHandoff(null).problems()).contains(
                "output chained needs the intermediate form the shared XSLT after it reads");
    }
}
