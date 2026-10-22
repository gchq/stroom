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
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.sample.RecordSplitter;
import stroom.ai.impl.tools.sample.Shapes;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.documentation.shared.DocumentationDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// set_shape_handling (the Stroom MCP server's `tools/sampling.py` `set_shape_handling`).
class TestSetShapeHandlingTool {

    private static final String RECORDS = """
            {"loggerName": "org.jgroups.protocols", "message": "view changed"}
            {"loggerName": "org.jgroups.protocols", "message": "view changed again"}
            {"loggerName": "com.acme.auth", "message": "login"}
            """;

    private final BuildWorld world = new BuildWorld();
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final Consent consent = new Consent(() -> auditLog);
    private final BuildPlan plan = new BuildPlan(new FakeBuildFacts(), world.guard(), consent, new BuildMemory());
    private final SetShapeHandlingTool tool = new SetShapeHandlingTool(
            new SurveyDocuments(world.guard(), world.documentAccess, new DocumentWrites(world.documentAccess)), consent,
            plan);

    @Test
    void droppingKindsIsConfirmedThenKeptInTheSurveyDoc() {
        final String uuid = survey();
        final String arguments = "{\"build\": \"acme-v1\", \"feed\": \"ACME\", \"shapes\": [\"org.jgroups\"], "
                                 + "\"handling\": \"drop\", \"reason\": \"cluster housekeeping, no security value\"}";

        // Nothing written until the user agrees, which they can't without a form.
        assertThat(call(arguments, PortedToolCalls.context()).text())
                .startsWith("Not done: Leave untranslated (no Events): 1 kind(s) of event in feed 'ACME'.");
        assertThat(SurveyDoc.dropped(SurveyDoc.readState(world.body(uuid)))).isEmpty();

        final PortedToolCalls.FormUser user = PortedToolCalls.user(Map.of("value", true));
        final Map<String, Object> done = json(call(arguments, user.context()));

        assertThat(user.forms()).singleElement().satisfies(asked -> {
            assertThat(asked.get("summary")).isEqualTo("Leave untranslated (no Events): 1 kind(s) of event in feed "
                                                       + "'ACME'");
            assertThat(String.valueOf(((Map<?, ?>) asked.get("details")).get("kinds")))
                    .contains("(2 records seen, 66.7%)");
        });

        assertThat(done.get("handling")).isEqualTo("drop");
        final String signature = String.valueOf(((List<?>) done.get("shapes")).getFirst());
        assertThat(signature).contains("org.jgroups");
        assertThat(done.get("left_untranslated")).isEqualTo(List.of(Map.of("shape", signature, "reason",
                "cluster housekeeping, no security value")));
        final List<?> locations = (List<?>) done.get("locations");
        assertThat(locations).hasSize(2).allMatch(location -> "none".equals(((Map<?, ?>) location).get("expect")));
        assertThat(String.valueOf(done.get("hint"))).startsWith("Add a drop rule for these kinds");
        assertThat(done).containsKey("next");
        assertThat(SurveyDoc.dropped(SurveyDoc.readState(world.body(uuid))))
                .containsEntry(signature, "cluster housekeeping, no security value");
    }

    @Test
    void translatingKindsAgainUndoesTheDrop() {
        survey();
        final PortedToolCalls.FormUser user = PortedToolCalls.user(Map.of("value", true));
        call("{\"build\": \"acme-v1\", \"feed\": \"ACME\", \"shapes\": \"org.jgroups\", \"handling\": \"drop\", "
             + "\"reason\": \"noise\"}", user.context());
        final String translate = "{\"build\": \"acme-v1\", \"feed\": \"ACME\", \"shapes\": \"org.jgroups\", "
                                 + "\"handling\": \"translate\"}";

        final Map<String, Object> done = json(call(translate, user.context()));

        assertThat(user.shown()).hasSize(2).last().asString().startsWith("Translate again: 1 kind(s)");

        assertThat(done.get("left_untranslated")).isEqualTo(List.of());
        assertThat((List<?>) done.get("locations")).noneMatch(location -> ((Map<?, ?>) location).containsKey(
                "expect"));
    }

    @Test
    void whatCantBeDoneIsRefusedSayingWhy() {
        assertThat(call("{\"build\": \"acme-v1\", \"feed\": \"ACME\", \"shapes\": [\"x\"], \"handling\": "
                        + "\"drop\", \"reason\": \"r\"}", PortedToolCalls.context()).text())
                .isEqualTo("No survey of 'ACME' in build 'acme-v1': run survey_feed with build first");
        survey();
        assertThat(call("{\"build\": \"acme-v1\", \"feed\": \"ACME\", \"shapes\": [\"loggerName\"], "
                        + "\"handling\": \"drop\", \"reason\": \"r\"}", PortedToolCalls.context()).text())
                .isEqualTo("'loggerName' matches 2 kinds of event in the survey; give the exact signature");
        assertThat(call("{\"build\": \"acme-v1\", \"feed\": \"ACME\", \"shapes\": [\"org.jgroups\"], "
                        + "\"handling\": \"drop\"}", PortedToolCalls.context()).text())
                .isEqualTo("Give the reason for leaving these records untranslated (the user's words)");
        assertThat(call("{\"build\": \"acme-v1\", \"feed\": \"ACME\", \"shapes\": [\"org.jgroups\"], "
                        + "\"handling\": \"ignore\"}", PortedToolCalls.context()).text())
                .isEqualTo("handling must be 'drop' or 'translate'");
    }

    @Test
    void itConfirmsItsOwnChange() {
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.hasReadOnlyCalls()).isFalse();
        assertThat(tool.confirmsItself()).isTrue();
    }

    // A survey of ACME in build acme-v1, as survey_feed keeps it.
    private String survey() {
        final RecordSplitter.Chunk chunk = RecordSplitter.split(RECORDS);
        final Shapes shapes = new Shapes();
        for (int i = 0; i < chunk.records().size(); i++) {
            shapes.add(chunk, i, 7);
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", "ACME");
        result.put("format", chunk.format());
        result.put("streams_read", List.of(7L));
        result.put("records_read", 3);
        result.put("new_shapes", 2);
        final Map<String, Object> state = SurveyDoc.merge(null, result, shapes.shapes(), 2);
        final String uuid = world.inBuild("acme-v1", DocumentationDoc.TYPE, SurveyDoc.docName("ACME"), null)
                .getUuid();
        world.docs.put(uuid, ((DocumentationDoc) world.docs.get(uuid)).copy().data(SurveyDoc.render(state))
                .build());
        return uuid;
    }

    private ToolResult call(final String arguments, final ToolCallContext context) {
        return tool.execute(PortedToolCalls.call(SetShapeHandlingTool.NAME, arguments), context);
    }

    private static Map<String, Object> json(final ToolResult result) {
        assertThat(result.isError()).as(result.text()).isFalse();
        return new LinkedHashMap<>(JsonUtil.readValue(result.text(), Map.class));
    }
}
