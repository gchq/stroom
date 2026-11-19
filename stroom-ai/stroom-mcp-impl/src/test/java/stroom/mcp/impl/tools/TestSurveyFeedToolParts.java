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
import stroom.ai.impl.AiConfig;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.sample.PartSampler;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// survey_feed on a stream of more parts than a call reads. Seen in user testing: an OpenWebUI audit stream of 787
/// parts was surveyed through its first 20 only, and the survey said it "read only the start of stream N, too big to
/// read whole in one go, and can't read further into it": the kinds of event only in its later parts could never be
/// seen.
class TestSurveyFeedToolParts {

    private static final long STREAM = 9L;
    private static final int PARTS = 40;
    // From this part on, each part holds a kind of event of its own.
    private static final int LATE_FROM = 25;

    private final BuildWorld world = new BuildWorld();
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final FeedNames feedNames = Mockito.mock(FeedNames.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final Consent consent = new Consent(() -> auditLog);
    // The plan's next step isn't what these tests are about: the result as the survey gave it.
    private final BuildPlan plan = Mockito.mock(BuildPlan.class);
    private final SourceTypeRegistries registries = Mockito.mock(SourceTypeRegistries.class);
    private final SurveyFeedTool tool = new SurveyFeedTool(streamReader, feedNames, world.guard(),
            new SurveyDocuments(world.guard(), world.documentAccess, new DocumentWrites(world.documentAccess)), consent,
            new FewerStreams(securityContext),
            world.documentAccess, auditLog, registries, world::records, () -> plan, AiConfig::new);

    TestSurveyFeedToolParts() {
        Mockito.when(plan.withNext(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        Mockito.when(world.documentAccess.canViewData()).thenReturn(true);
        Mockito.when(feedNames.resolve(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean())).thenReturn(ResultPage.empty());
        // Nothing is beside any feed.
        Mockito.when(registries.registry(any(), anyString())).thenReturn(Optional.empty());
        final Meta meta = Meta.builder().id(STREAM).feedName("OPENWEBUI-AUDIT").typeName("Raw Events")
                .status(Status.UNLOCKED).createMs(1_763_000_000_000L).build();
        Mockito.when(streamReader.meta(STREAM)).thenReturn(meta);
        Mockito.when(streamReader.partCount(STREAM)).thenReturn(PARTS);
        Mockito.when(streamReader.readHead(eq(STREAM), anyInt(), anyInt())).thenAnswer(invocation ->
                new StreamReader.RawHead(events(invocation.getArgument(1)), false, PARTS));
    }

    @Test
    void theKindsOfEventOnlyInPartsPastTheFirstTwentyAreSeen() {
        final Map<String, Object> result = json(call("{\"stream_ids\": [9]}"));

        final ArgumentCaptor<Integer> parts = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(streamReader, Mockito.atLeastOnce()).readHead(eq(STREAM), parts.capture(), anyInt());
        assertThat(parts.getAllValues()).as("the parts read, some from part %s on", LATE_FROM)
                .anyMatch(part -> part >= LATE_FROM);
        assertThat(String.valueOf(result.get("shapes"))).as("the kinds of event seen").contains("late");
    }

    @Test
    void aStreamOfMorePartsThanACallReadsNamesThePartsRead() {
        final Map<String, Object> result = json(call("{\"stream_ids\": [9]}"));

        assertThat(String.valueOf(result.get("coverage"))).doesNotContain("can't read further into it")
                .contains("Stream 9: 160 records from parts 0, 2, 4, 6, 8, 10, …, 30, 32, 34, 36 and 39")
                .contains("it has 40 parts, more than a call reads (20), so 20 were read, spread through it from "
                          + "its first to its last");
        assertThat((List<?>) result.get("per_stream")).anySatisfy(entry -> assertThat(((Map<?, ?>) entry)
                .get(SurveyFeedTool.PARTS_READ)).isEqualTo(PartSampler.spread(PARTS, 20)));
    }

    @Test
    void everyChosenPartOfATwentyPartStreamIsReadToItsOwnDepth() {
        // The survey's budget is each part's own (decided 2026-11-08: different parts can carry different
        // sources): a small max_chars_per_stream reads each part to it, never sharing it across the parts.
        Mockito.when(streamReader.partCount(STREAM)).thenReturn(20);
        Mockito.when(streamReader.readHead(eq(STREAM), anyInt(), anyInt())).thenAnswer(invocation ->
                new StreamReader.RawHead(events(invocation.getArgument(1)), true, 20));

        final Map<String, Object> result = json(call("{\"stream_ids\": [9], \"max_chars_per_stream\": 1000}"));

        final ArgumentCaptor<Integer> parts = ArgumentCaptor.forClass(Integer.class);
        final ArgumentCaptor<Integer> chars = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(streamReader, Mockito.atLeastOnce()).readHead(eq(STREAM), parts.capture(), chars.capture());
        assertThat(parts.getAllValues()).containsAll(IntStream.range(0, 20).boxed().toList());
        assertThat(chars.getAllValues()).containsOnly(1000);
        assertThat(String.valueOf(result.get("per_stream"))).contains("parts_read=" + IntStream.range(0, 20)
                .boxed().toList());
    }

    // A part's records: key=value logins, one a line; each part from LATE_FROM on of its own kind.
    private static String events(final int part) {
        final String kind = part >= LATE_FROM
                ? "late" + part
                : "login";
        return IntStream.range(0, 8)
                .mapToObj(i -> "time=2026-11-18T10:%02d:00Z action=%s user=u%d\n".formatted(i, kind, i))
                .collect(Collectors.joining());
    }

    private ToolResult call(final String arguments) {
        final ToolCall call = BuildToolCalls.call(SurveyFeedTool.NAME, arguments);
        return tool.execute(call, BuildToolCalls.context());
    }

    private static Map<String, Object> json(final ToolResult result) {
        assertThat(result.isError()).as(result.text()).isFalse();
        return new LinkedHashMap<>(JsonUtil.readValue(result.text(), Map.class));
    }
}
