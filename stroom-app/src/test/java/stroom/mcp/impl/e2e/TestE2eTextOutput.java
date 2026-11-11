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

package stroom.mcp.impl.e2e;

import stroom.explorer.shared.ExplorerNode;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Text an XSLT writes on purpose, told apart from the input's text copied through by templates that match nothing,
/// as a real Stroom steps them (asked for by the user: a CSV export was given a blocking verdict, as if its templates
/// had matched nothing). Driving the REAL tools on a real Stroom.
class TestE2eTextOutput extends AbstractE2eTest {

    private static final String STAMP = String.valueOf(System.currentTimeMillis() % 100_000);
    private static final String EVENTS = """
            <?xml version="1.1" encoding="UTF-8"?>
            <Events xmlns="event-logging:3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" Version="3.5.2" \
            xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
            <Event><EventTime><TimeCreated>2026-09-27T23:48:10.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Environment>Prod</Environment></System>\
            <Generator>SecretServer Audit</Generator>
              <Device><HostName>ss-web-02</HostName></Device><User><Id>priya.patel</Id></User></EventSource>
             <EventDetail><TypeId>User-Login</TypeId><Description>User logged in</Description><Authenticate>\
            <Action>Logon</Action><User><Id>priya.patel</Id></User></Authenticate></EventDetail></Event>
            <Event><EventTime><TimeCreated>2026-09-27T23:56:15.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Environment>Prod</Environment></System>\
            <Generator>SecretServer Audit</Generator>
              <Device><HostName>ss-web-01</HostName></Device><User><Id>riley.chen</Id></User></EventSource>
             <EventDetail><TypeId>Secret-View</TypeId><Description>Secret viewed</Description><View><Resource>\
            <Type>Secret</Type><Name>Firewall</Name></Resource></View></EventDetail></Event>
            </Events>
            """;
    // A CSV line per Event.
    private static final String CSV = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xpath-default-namespace="event-logging:3" version="2.0">
              <xsl:output method="text"/>
              <xsl:template match="/Events"><xsl:apply-templates select="Event"/></xsl:template>
              <xsl:template match="Event">
                <xsl:value-of select="string-join((EventTime/TimeCreated, EventSource/User/Id, \
            EventDetail/TypeId), ',')"/>
                <xsl:text>&#10;</xsl:text>
              </xsl:template>
            </xsl:stylesheet>
            """;
    // The same, in a namespace the Events are not in: no template matches.
    private static final String WRONG_NAMESPACE = CSV.replace("xpath-default-namespace=\"event-logging:3\"",
            "xpath-default-namespace=\"event-logging:4\"");

    @Test
    void aCsvExportStepsClean() {
        final Map<String, Object> stepped = stepped("csv", CSV);

        assertThat(stepped.get("verdict")).as("stepped: %s", stepped.get("groups")).isEqualTo("clean");
    }

    @Test
    void theEventsTextCopiedThroughByTemplatesMatchingNothingIsStillAnError() {
        // As text, and as the xml output method writes it.
        final Map<String, String> codes = Map.of("copied-text", WRONG_NAMESPACE,
                "copied-xml", WRONG_NAMESPACE.replace("<xsl:output method=\"text\"/>", ""));
        for (final Map.Entry<String, String> code : codes.entrySet()) {
            final Map<String, Object> stepped = stepped(code.getKey(), code.getValue());

            assertThat(stepped.get("verdict")).as("stepped: %s", stepped.get("groups")).isNotEqualTo("clean");
            assertThat(jsonOf(stepped.get("groups"))).contains("Output contains no XML elements");
        }
    }

    // The Events stepped through a text pipeline with the XSLT.
    private Map<String, Object> stepped(final String name, final String code) {
        final String unique = name + "-" + STAMP;
        final String build = "text-" + unique;
        final String feed = "TEXT-" + unique.toUpperCase(Locale.ROOT);
        agreed("create_feed", args("build", build, "name", feed, "stream_type", "Events"));
        final long eventsId = number(client.json("upload_sample", args("feed", feed, "sample", EVENTS,
                "stream_type", "Events")).get("stream_id"));
        final String xslt = saveXslt(build, feed + "-Text", code);
        final ExplorerNode folder = folder("System/E2E Text Output/" + unique);
        final Path output = Path.of(System.getProperty("java.io.tmpdir"), "e2e-text-" + unique + ".log");
        final String template = textFileTemplate(folder, "E2E text output " + unique, output.toString());
        final String pipeline = uuidOf(createPipeline(build, feed + "-File", template,
                docProperty("xsltFilter", "xslt", xslt, XsltDoc.TYPE)));
        dump("stepped output " + unique, stepOutput(pipeline, eventsId, 0, "xsltFilter"));
        final Map<String, Object> stepped = stepSample(pipeline, List.of(eventsId));
        dump("step_sample " + unique, stepped);
        return stepped;
    }
}
