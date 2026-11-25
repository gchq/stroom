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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.processing.TranslationInputText;
import stroom.ai.impl.tools.streams.StreamReader;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// survey_feed for a build describing a pipeline in place whose translation a pre-processor feeds (decided with the
/// user 2026-11-25): each stream is surveyed where the translation reads it (the pre-processor's output, stepped),
/// not as the parser reads it; any other build surveys the raw streams as before.
class TestSurveyAtTranslationInput {

    private static final long STREAM = 9L;
    private static final String BUILD = "vpn-described";
    // The raw records: operation codes the pre-processor turns into actions.
    private static final String RAW = """
            [{"op": "LGN", "user": "jb"}, {"op": "LGT", "user": "jb"}]""";
    // What the translation reads: the pre-processor's output, with the actions it added.
    private static final String AT_INPUT = """
            [{"op": "LGN", "user": "jb", "action": "login"}, {"op": "LGT", "user": "jb", "action": "logout"}]""";

    private final BuildWorld world = new BuildWorld();
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final FeedNames feedNames = Mockito.mock(FeedNames.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final BuildPlan plan = Mockito.mock(BuildPlan.class);
    private final SourceTypeRegistries registries = Mockito.mock(SourceTypeRegistries.class);
    private final TranslationInputs inputs = Mockito.mock(TranslationInputs.class);
    private final SurveyFeedTool tool = new SurveyFeedTool(streamReader, feedNames, world.guard(),
            new SurveyDocuments(world.guard(), world.documentAccess, new DocumentWrites(world.documentAccess)),
            new Consent(() -> auditLog), new FewerStreams(Mockito.mock(SecurityContext.class)), world.documentAccess,
            auditLog, registries, world::records, () -> plan, AiConfig::new);

    TestSurveyAtTranslationInput() {
        Mockito.when(plan.withNext(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        Mockito.when(world.documentAccess.canViewData()).thenReturn(true);
        Mockito.when(feedNames.resolve(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean())).thenReturn(ResultPage.empty());
        Mockito.when(registries.registry(any(), anyString())).thenReturn(Optional.empty());
        Mockito.when(streamReader.meta(STREAM)).thenReturn(Meta.builder().id(STREAM).feedName("VPN")
                .typeName("Raw Events").status(Status.UNLOCKED).createMs(1_763_000_000_000L).build());
        Mockito.when(streamReader.partCount(STREAM)).thenReturn(1);
        Mockito.when(streamReader.readHead(eq(STREAM), anyInt(), anyInt())).thenReturn(new StreamReader.RawHead(RAW,
                false, 1));
        world.folder("System/" + BuildWorld.WORKSPACE + "/" + BUILD);
        tool.setTranslationInputs(() -> inputs);
    }

    @Test
    void aPreProcessedTranslationsRecordsAreSurveyedAtItsInput() {
        final TranslationInputs.At at = new TranslationInputs.At("hand-pipeline", "translationFilter");
        Mockito.when(inputs.forBuild(BUILD)).thenReturn(Optional.of(at));
        Mockito.when(inputs.texts(any(), eq(at), anyList(), anyInt())).thenReturn(Map.of(STREAM,
                new TranslationInputText.Text(AT_INPUT, TranslationInputText.Format.JSON, 1)));

        final Map<String, Object> result = json(call());

        // The actions only the pre-processor writes are seen; the raw stream is never read for its records.
        assertThat(String.valueOf(result.get("shapes"))).contains("action");
        Mockito.verify(streamReader, Mockito.never()).readHead(eq(STREAM), anyInt(), anyInt());
        Mockito.verify(inputs).texts(any(), eq(at), eq(List.of(STREAM)), eq(TranslationInputs.SURVEY_RECORDS));
    }

    @Test
    void anyOtherBuildSurveysTheRawStreams() {
        Mockito.when(inputs.forBuild(BUILD)).thenReturn(Optional.empty());

        final Map<String, Object> result = json(call());

        assertThat(String.valueOf(result.get("shapes"))).doesNotContain("action");
        Mockito.verify(streamReader, Mockito.atLeastOnce()).readHead(eq(STREAM), anyInt(), anyInt());
        Mockito.verify(inputs, Mockito.never()).texts(any(), any(), any(), anyInt());
    }

    private ToolResult call() {
        final ToolCall call = BuildToolCalls.call(SurveyFeedTool.NAME, "{\"stream_ids\": [9], \"build\": \"" + BUILD
                                                                       + "\"}");
        return tool.execute(call, BuildToolCalls.context());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(final ToolResult result) {
        assertThat(result.isError()).as(result.text()).isFalse();
        return new LinkedHashMap<>(JsonUtil.readValue(result.text(), Map.class));
    }
}
