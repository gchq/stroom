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

package stroom.ai.impl.extraction;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.extraction.ExtractionPlan.Sample;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolSession;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The namespace of an event an extraction was tried on, read as the user without stepping.
class TestExtractionEvents {

    private static final Sample SAMPLE = new Sample(7, 2);
    private static final ToolSession SESSION = ToolSession.chat(3);

    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final ExtractionEvents events = new ExtractionEvents(streamReader, securityContext, auditLog);

    @BeforeEach
    void setUp() {
        Mockito.when(securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(true);
    }

    @Test
    void theNamespaceIsThatOfTheEventsRootAndTheReadIsRecorded() {
        // Record indexes are from 0, event ids from 1.
        fetched(1, "<Events xmlns=\"event-logging:3\" Version=\"3.2.3\"><Event><Id>1</Id></Event></Events>");

        assertThat(events.namespace(SAMPLE, SESSION)).isEqualTo("event-logging:3");
        Mockito.verify(auditLog).streamsViewed(Mockito.eq(SESSION), Mockito.eq(List.of(7L)), Mockito.anyString());
    }

    @Test
    void aPrefixedRootOrNoneIsRead() {
        // As Stroom gives a prefixed root, closed by its local name.
        fetched(1, "<evt:Events xmlns:evt=\"event-logging:3\"><evt:Event/></Events>");
        assertThat(events.namespace(SAMPLE, SESSION)).isEqualTo("event-logging:3");

        fetched(1, "<Records><Record><Host>web1</Host></Record></Records>");
        assertThat(events.namespace(SAMPLE, SESSION)).isEmpty();
    }

    @Test
    void anEventThatCantBeReadWritesNothing() {
        fetched(1, null);
        assertThatThrownBy(() -> events.namespace(SAMPLE, SESSION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Event 2 of stream 7, which the extraction was tried on, can't be read");

        Mockito.when(streamReader.fetch(7, 1, null)).thenThrow(new RuntimeException("Permission denied"));
        assertThatThrownBy(() -> events.namespace(SAMPLE, SESSION)).hasMessageContaining("can't be read");

        Mockito.when(securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(false);
        assertThatThrownBy(() -> events.namespace(SAMPLE, SESSION))
                .hasMessageContaining("You don't have permission to view data");
    }

    private void fetched(final long record, final String data) {
        Mockito.when(streamReader.fetch(7, record, null)).thenReturn(new StreamReader.Fetched("ACME", "Events", data,
                true, 3L, List.of(), List.of()));
    }
}
