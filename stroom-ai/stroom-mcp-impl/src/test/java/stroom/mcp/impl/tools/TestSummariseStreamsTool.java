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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.shared.AiToolCategory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSummariseStreamsTool {

    private final StreamSummaries summaries = Mockito.mock(StreamSummaries.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final SummariseStreamsTool tool = new SummariseStreamsTool(summaries, documentAccess, auditLog);

    @BeforeEach
    void setUp() {
        Mockito.when(documentAccess.canViewData()).thenReturn(true);
    }

    @Test
    void errorsAreSummarisedStreamByStreamAndEventsTogether() {
        Mockito.when(summaries.summariseErrors(Mockito.anyLong())).thenReturn(Map.of("groups", List.of()));
        Mockito.when(summaries.summariseEvents(List.of(1L), 200)).thenReturn(Map.of("events", 3));

        assertThat(json("{\"stream_ids\": [1, 2], \"kind\": \"errors\"}"))
                .isEqualTo(Map.of("streams", Map.of("1", Map.of("groups", List.of()),
                        "2", Map.of("groups", List.of()))));
        assertThat(json("{\"stream_ids\": 1, \"kind\": \"events\"}")).isEqualTo(Map.of("events", 3));
    }

    @Test
    void oneUnreadableStreamLeavesTheOthersSummarised() {
        Mockito.when(summaries.summariseErrors(Mockito.anyLong())).thenAnswer(invocation -> {
            if (invocation.<Long>getArgument(0) == 2L) {
                throw new IllegalStateException("volume not found");
            }
            return Map.of("stream_id", invocation.<Long>getArgument(0), "groups", List.of());
        });

        final Map<String, Object> out = json("{\"stream_ids\": \"1,2,3\", \"kind\": \"errors\"}");

        final Map<?, ?> streams = (Map<?, ?>) out.get("streams");
        assertThat(((Map<?, ?>) streams.get("1")).get("groups")).isEqualTo(List.of());
        assertThat(((Map<?, ?>) streams.get("3")).get("groups")).isEqualTo(List.of());
        assertThat((String) ((Map<?, ?>) streams.get("2")).get("error")).contains("volume not found");
    }

    @Test
    void badArgumentsAreRefusedSayingWhy() {
        assertThat(text("{\"kind\": \"errors\"}")).isEqualTo("Error: Give stream_ids: the streams to summarise.");
        assertThat(text("{\"stream_ids\": [\"x\"], \"kind\": \"errors\"}")).contains("'x' isn't a stream id");
        assertThat(text("{\"stream_ids\": [1], \"kind\": \"everything\"}"))
                .isEqualTo("Error: kind must be errors or events.");
    }

    @Test
    void withoutViewDataNothingIsRead() {
        Mockito.when(documentAccess.canViewData()).thenReturn(false);

        assertThat(text("{\"stream_ids\": [1], \"kind\": \"errors\"}")).contains("don't have permission to view data");
        Mockito.verifyNoInteractions(summaries);
    }

    @Test
    void itIsAHeavyProcessingTool() {
        assertThat(tool.category()).isEqualTo(AiToolCategory.PROCESSING);
        assertThat(tool.usesRunSlot()).isTrue();
        assertThat(tool.isReadOnly()).isTrue();
    }

    private Map<String, Object> json(final String arguments) {
        return PortedToolCalls.json(text(arguments));
    }

    private String text(final String arguments) {
        final ToolResult result = tool.execute(PortedToolCalls.call(SummariseStreamsTool.NAME, arguments),
                PortedToolCalls.context());
        return result.text();
    }
}
