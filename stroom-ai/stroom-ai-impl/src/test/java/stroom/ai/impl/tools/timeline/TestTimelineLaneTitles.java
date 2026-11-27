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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.timeline.TimelineReader.Source;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// What a timeline's lanes are, as its charts' lane axes are titled (chat 195: the generic "lane").
class TestTimelineLaneTitles {

    @Test
    void lanesByHostAreTitledByTheHostFieldsDescriptionElseItsName() {
        final Map<String, String> described = Map.of("host.name", "Device host name");

        assertThat(TimelineLaneTitles.hosts(List.of(source("host.name")), (source, field) -> described.get(field)))
                .isEqualTo("Device host name (host.name)");
        assertThat(TimelineLaneTitles.hosts(List.of(source("Computer")), (source, field) -> null))
                .isEqualTo("Computer");
        // Several sources: each field once, in order; a source with none is in its own lane.
        assertThat(TimelineLaneTitles.hosts(List.of(source("host.name"), source("Computer"), source("host.name"),
                source(null)), (source, field) -> described.get(field)))
                .isEqualTo("Device host name (host.name) / Computer (else source)");
        assertThat(TimelineLaneTitles.hosts(List.of(source(" ")), (source, field) -> null))
                .isEqualTo("Host (else source)");
    }

    @Test
    void aLongOrSeveralLineDescriptionIsCutToItsStart() {
        assertThat(TimelineLaneTitles.field("host.name", "The host's name.\nFrom EventSource/Device/HostName."))
                .isEqualTo("The host's name. (host.name)");
        assertThat(TimelineLaneTitles.field("h", "x".repeat(100)))
                .isEqualTo("x".repeat(TimelineLaneTitles.MAX_DESCRIPTION_CHARS - 1) + "… (h)");
        assertThat(TimelineLaneTitles.field("h", "  ")).isEqualTo("h");
    }

    @Test
    void lanesBySourceDayOrActivityAreTitledSo() {
        assertThat(TimelineLaneTitles.spans("source", "Device host name (host.name)")).isEqualTo("Source");
        assertThat(TimelineLaneTitles.spans(null, "Device host name (host.name)"))
                .isEqualTo("Device host name (host.name)");
        assertThat(TimelineLaneTitles.spans(null, null)).isEqualTo("Host");
        assertThat(TimelineLaneTitles.activities(null, "Australia/Sydney"))
                .isEqualTo("Day (Australia/Sydney)");
        assertThat(TimelineLaneTitles.activities("activity", "UTC")).isEqualTo("Activity");
    }

    private static Source source(final String hostField) {
        final Source source = Mockito.mock(Source.class);
        Mockito.when(source.hostField()).thenReturn(hostField);
        return source;
    }
}
