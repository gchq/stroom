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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.tools.IndexEvents.Find;
import stroom.ai.impl.tools.IndexEvents.Term;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/// search_events given an index: the Stroom MCP server's find_events, merged into it. What the search finds is
/// tested in [TestIndexEvents]; this is the call: its arguments, and its JSON result.
class TestSearchEventsTool {

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final IndexEvents indexEvents = Mockito.mock(IndexEvents.class);
    private final SearchEventsTool tool = new SearchEventsTool(workspace, indexEvents);

    @BeforeEach
    void setUp() {
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    }

    @Test
    void anIndexIsSearchedWithTheConditionsAndTheResultIsFindEventsJson() {
        final Map<String, Object> found = new LinkedHashMap<>();
        found.put("total", 2L);
        final Map<String, String> hit = new LinkedHashMap<>();
        hit.put("StreamId", "7");
        hit.put("EventId", "3");
        found.put("hits", List.of(hit));
        Mockito.when(indexEvents.find(Mockito.any(), Mockito.any())).thenReturn(found);

        final String result = run("""
                {"index": "i", "where": [{"field": "user.name", "value": "alice"},
                  {"field": "status", "condition": "between", "value": "100,300"}],
                  "timeFrom": "day()-7d", "fields": ["host.name"], "limit": 50, "allStreams": true}""");

        assertThat(result).isEqualTo("{\"total\":2,\"hits\":[{\"StreamId\":\"7\",\"EventId\":\"3\"}]}");
        final ArgumentCaptor<Find> asked = ArgumentCaptor.forClass(Find.class);
        Mockito.verify(indexEvents).find(Mockito.any(), asked.capture());
        // The limit is at most 20, and a condition not given is EQUALS.
        assertThat(asked.getValue()).isEqualTo(new Find("i", List.of(
                new Term("user.name", Condition.EQUALS, "alice"), new Term("status", Condition.BETWEEN, "100,300")),
                "day()-7d", null, List.of("host.name"), IndexEvents.MAX_HITS, true));
    }

    @Test
    void oneConditionGivenAsAnObjectOrAsJsonTextIsTakenToo() {
        Mockito.when(indexEvents.find(Mockito.any(), Mockito.any())).thenReturn(Map.of());

        run("{\"index\": \"i\", \"where\": {\"field\": \"user.name\", \"value\": \"alice\"}}");
        run("{\"index\": \"i\", \"where\": \"[{\\\"field\\\": \\\"user.name\\\", \\\"value\\\": \\\"alice\\\"}]\"}");

        final ArgumentCaptor<Find> asked = ArgumentCaptor.forClass(Find.class);
        Mockito.verify(indexEvents, Mockito.times(2)).find(Mockito.any(), asked.capture());
        asked.getAllValues().forEach(find -> {
            assertThat(find.where()).containsExactly(new Term("user.name", Condition.EQUALS, "alice"));
            assertThat(find.limit()).isEqualTo(IndexEvents.DEFAULT_HITS);
            assertThat(find.allStreams()).isFalse();
        });
    }

    @Test
    void aConditionStroomDoesNotTakeOrAConditionWithoutAFieldIsRefused() {
        assertThat(run("{\"index\": \"i\", \"where\": [{\"field\": \"a\", \"condition\": \"LIKE\"}]}"))
                .startsWith("Error: Condition 'LIKE' isn't one of EQUALS, NOT_EQUALS, CONTAINS");
        assertThat(run("{\"index\": \"i\", \"where\": [{\"value\": \"a\"}]}"))
                .isEqualTo("Error: where is a list of {\"field\": ..., \"condition\": ..., \"value\": ...}, each "
                           + "with a field.");
        assertThat(run("{\"index\": \"i\", \"where\": \"user.name = alice\"}"))
                .isEqualTo("Error: where is a list of {\"field\": ..., \"condition\": ..., \"value\": ...}.");
        Mockito.verifyNoInteractions(indexEvents);
    }

    @Test
    void aRefusalSaysWhy() {
        Mockito.when(indexEvents.find(Mockito.any(), Mockito.any()))
                .thenThrow(new WorkspaceException("Not fields of index 'web': user.nmae (like user.name)"));

        assertThat(run("{\"index\": \"i\", \"where\": [{\"field\": \"user.nmae\", \"value\": \"alice\"}]}"))
                .isEqualTo("Error: Not fields of index 'web': user.nmae (like user.name)");
    }

    @Test
    void withNeitherAFileNorAnIndexItSaysToGiveOne() {
        assertThat(run("{\"text\": \"x\"}"))
                .isEqualTo("Error: Give path (an events file), or index to find events in an index.");
    }

    @Test
    void anIndexIsSearchedWithinTheQueryTimeLimit() {
        final ToolCallContext context = ToolCalls.inChat(1, workspace);

        assertThat(tool.timeLimit(ToolCalls.call(SearchEventsTool.NAME, "{\"index\": \"i\"}"), context))
                .isEqualTo(new AiWorkspaceConfig().getQueryTimeLimit().getDuration());
        assertThat(tool.timeLimit(ToolCalls.call(SearchEventsTool.NAME, "{\"path\": \"/outputs/e.xml\"}"),
                context)).isNotEqualTo(new AiWorkspaceConfig().getQueryTimeLimit().getDuration());
    }

    private String run(final String arguments) {
        return tool.execute(ToolCalls.call(SearchEventsTool.NAME, arguments), ToolCalls.inChat(1, workspace)).text();
    }
}
