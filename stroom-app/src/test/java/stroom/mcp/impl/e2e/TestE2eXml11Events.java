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

import stroom.ai.impl.workspace.EventsFile;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A source value holding control characters, through Stroom to its Events and back through the tools. Seen in
/// chat 181: a Teams message held a form feed, which Stroom wrote as `&#xc;` in its XML 1.1 Events stream; its
/// schema filter passed the stream, but check_events refused it as not well-formed (reading it as XML 1.0), and
/// fetch_events kept the event as escaped text (`&amp;#xc;`), so no search for `&#xc;` found it.
class TestE2eXml11Events extends AbstractE2eTest {

    // The message holds a form feed and an ANSI colour code's escape, as JSON writes them.
    private static final String SAMPLE = "[{\"ts\": \"2026-09-28T11:00:00Z\", \"user\": \"dave\", \"host\": \"ws04\", "
                                         + "\"src\": \"10.0.1.4\", \"msg\": \"page one\\fpage two \\u001b[31mred\"}, "
                                         + "{\"ts\": \"2026-09-28T11:02:00Z\", \"user\": \"erin\", \"host\": \"ws05\", "
                                         + "\"src\": \"10.0.1.5\", \"msg\": \"plain\"}]";
    private static final String XSLT = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xpath-default-namespace="http://www.w3.org/2013/XSL/json" xmlns="event-logging:3"
                xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="3.0">
              <xsl:template match="/array">
                <Events xsi:schemaLocation="event-logging:3 file://event-logging-v%1$s.xsd" Version="%1$s">
                  <xsl:apply-templates />
                </Events>
              </xsl:template>
              <xsl:template match="map">
                <Event>
                  <EventTime><TimeCreated><xsl:value-of
                      select="stroom:format-date(string[@key='ts'], 'yyyy-MM-dd''T''HH:mm:ssX')" /></TimeCreated>
                  </EventTime>
                  <EventSource>
                    <System><Name>E2E</Name><Environment>Dev</Environment></System>
                    <Generator>e2e-xml11</Generator>
                    <Device><HostName><xsl:value-of select="string[@key='host']" /></HostName>
                      <IPAddress><xsl:value-of select="string[@key='src']" /></IPAddress></Device>
                    <User><Id><xsl:value-of select="string[@key='user']" /></Id></User>
                  </EventSource>
                  <EventDetail>
                    <TypeId>Message</TypeId>
                    <Description><xsl:value-of select="string[@key='msg']" /></Description>
                    <Authenticate>
                      <Action>Logon</Action>
                      <User><Id><xsl:value-of select="string[@key='user']" /></Id></User>
                      <Outcome><Success>true</Success></Outcome>
                    </Authenticate>
                  </EventDetail>
                </Event>
              </xsl:template>
            </xsl:stylesheet>
            """.formatted(VERSION);

    @Test
    void aFormFeedInASourceValueIsCheckedAndFetchedAsStroomStoresIt() {
        // onboard processes the sample and checks the Events stream with check_events (stream_ids): it must pass.
        final Onboarded onboarded = onboard(new Case("xml11", "Event Data (JSON)", SAMPLE, null, XSLT));

        final Map<String, Object> checked = json("check_events", args("stream_ids",
                List.of(onboarded.events())));
        assertThat(checked).as("check_events on the Events stream: %s", checked).containsEntry("ok", true);

        final Map<String, Object> page = json("fetch_events", args("streamId", onboarded.events(),
                "firstRecord", 0, "recordCount", 2));
        final List<String> records = ((List<?>) page.get("records")).stream()
                .map(record -> record instanceof final Map<?, ?> fields
                        ? String.valueOf(fields.get("data"))
                        : String.valueOf(record))
                .toList();
        assertThat(records).hasSize(2);
        assertThat(records.getFirst()).as("Stroom's Events, as stored")
                .startsWith("<?xml version=\"1.1\"")
                .contains("page one&#xc;page two &#x1b;[31mred");

        // fetch_events' out (chat only, so not callable over /mcp) writes each record so: the events file is XML
        // 1.1, and the event is kept as XML, the &#xc; as stored, not as escaped text.
        final StringBuilder file = new StringBuilder(EventsFile.start());
        for (int i = 0; i < records.size(); i++) {
            file.append(EventsFile.element(onboarded.events(), i + 1, records.get(i)));
        }
        file.append(EventsFile.end());
        assertThat(file.toString())
                .contains("page one&#xc;page two &#x1b;[31mred")
                .doesNotContain("format=\"text\"")
                .doesNotContain("&amp;#xc;");
        final Document parsed = parse(file.toString());
        final Element description = descendants(parsed.getDocumentElement(), "Description").getFirst();
        assertThat(description.getTextContent()).isEqualTo("page one\fpage two \u001b[31mred");
    }
}
