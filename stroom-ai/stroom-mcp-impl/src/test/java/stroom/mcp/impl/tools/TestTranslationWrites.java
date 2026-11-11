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

package stroom.mcp.impl.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// What a translation writes, read from its XSLT: a sample can say how often an event path is populated, never that
/// it isn't. Seen in testing: a byte count the Events held early in their stream was said to be one the index would
/// never get.
class TestTranslationWrites {

    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns="event-logging:3" xmlns:mcp="mcp">
              <xsl:template match="record">
                <Event>
                  <EventDetail>
                    <Send>
                      <Payload>
                        <Resource>
                          <InboundSize><xsl:value-of select="data[@name='inbound_size']/@value"/></InboundSize>
                        </Resource>
                      </Payload>
                      <xsl:sequence select="mcp:data('dlp_policy', data[@name='dlp_policy']/@value)"/>
                      <Data Name="folder" Value="{data[@name='folder']/@value}"/>
                    </Send>
                    <xsl:element name="Outcome"/>
                  </EventDetail>
                </Event>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void anElementOrDataEntryTheXsltWritesIsWritten() {
        assertThat(TranslationWrites.writes(XSLT, "EventDetail/*/Payload/Resource/InboundSize")).isTrue();
        // A Data entry, written by the tools' function or as an element.
        assertThat(TranslationWrites.writes(XSLT, "EventDetail//Data[@Name='dlp_policy']/@Value")).isTrue();
        assertThat(TranslationWrites.writes(XSLT, "EventDetail/Send/Data[@Name='folder']/@Value")).isTrue();
        // Written by xsl:element.
        assertThat(TranslationWrites.writes(XSLT, "EventDetail/Send/Outcome")).isTrue();
    }

    @Test
    void whatTheXsltDoesNotWriteIsNot() {
        assertThat(TranslationWrites.writes(XSLT, "EventDetail/*/Payload/Resource/OutboundSize")).isFalse();
        assertThat(TranslationWrites.writes(XSLT, "EventDetail//Data[@Name='tls_version']/@Value")).isFalse();
        // A name that only starts like one written.
        assertThat(TranslationWrites.writes(XSLT, "EventDetail/Send/Payload/Resource/Inbound")).isFalse();
        assertThat(TranslationWrites.writes(null, "EventDetail/Send/Outcome")).isFalse();
        assertThat(TranslationWrites.writes(XSLT, "")).isFalse();
    }

    @Test
    void anyOfSeveralTranslationsWritingItCounts() {
        assertThat(TranslationWrites.of(List.of("<Other/>", XSLT)).test("EventDetail/Send/Outcome")).isTrue();
        assertThat(TranslationWrites.of(List.of()).test("EventDetail/Send/Outcome")).isFalse();
    }
}
