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

import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code pipeline} - runs several data tool steps (grep, jq, xpath, table) one after another, each working on the
 * file the step before wrote, so intermediate results never enter the conversation. The steps are given as a JSON
 * array of tool calls, never as a command string, and each is checked as if called on its own.
 */
class PipelineTool extends AbstractWorkspaceTool {

    static final String NAME = "pipeline";
    static final String STEPS_ARG = "steps";
    static final String PREVIOUS = "$prev";
    static final int MAX_STEPS = 10;

    private final Map<String, AbstractWorkspaceTool> tools;

    @Inject
    PipelineTool(final ChatWorkspace workspace,
                 final GrepTool grepTool,
                 final JqTool jqTool,
                 final XPathTool xPathTool,
                 final TableTool tableTool) {
        super(workspace);
        this.tools = Map.of(
                GrepTool.NAME, grepTool,
                JqTool.NAME, jqTool,
                XPathTool.NAME, xPathTool,
                TableTool.NAME, tableTool);
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Runs data tool steps one after another, each on the file the step before wrote, so "
                             + "intermediate results stay out of the conversation. Each step is {\"tool\": one of "
                             + "grep, jq, xpath or table, \"args\": the tool's arguments}. Steps after the first "
                             + "work on the previous step's output unless their args give a path; leave out "
                             + "\"out\" in steps. Give out to write the final result to a file.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(STEPS_ARG, JsonArraySchema.builder()
                                .items(JsonObjectSchema.builder()
                                        .addEnumProperty("tool", List.of(GrepTool.NAME, JqTool.NAME, XPathTool.NAME,
                                                TableTool.NAME), "The tool.")
                                        .addProperty("args", JsonObjectSchema.builder()
                                                .description("The tool's arguments.")
                                                .build())
                                        .required("tool", "args")
                                        .build())
                                .description("The steps, at most " + MAX_STEPS + ".")
                                .build())
                        .addStringProperty(OUT_ARG, "A file in /outputs/ to write the final result to.")
                        .required(STEPS_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final List<Step> steps = steps(request);
        final WorkspacePath out = outArg(request);
        final String runDir = "/outputs/pipeline/" + UUID.randomUUID().toString().substring(0, 8) + "/";
        final List<WorkspacePath> intermediate = new ArrayList<>();
        final StringBuilder log = new StringBuilder();
        String previous = null;
        try {
            for (int i = 0; i < steps.size(); i++) {
                final Step step = steps.get(i);
                final boolean last = i == steps.size() - 1;
                final Map<String, Object> args = new LinkedHashMap<>(step.args());
                final Object path = args.get(PATH_ARG);
                if (previous != null && (path == null || PREVIOUS.equals(path))) {
                    args.put(PATH_ARG, previous);
                } else if (path == null || PREVIOUS.equals(path)) {
                    throw new WorkspaceException("Step 1 needs a path.");
                }
                if (last) {
                    if (out != null) {
                        args.put(OUT_ARG, out.path());
                    } else {
                        args.remove(OUT_ARG);
                    }
                } else {
                    final WorkspacePath stepOut = WorkspacePath.parse(runDir + "step-" + (i + 1) + extension(step));
                    args.put(OUT_ARG, stepOut.path());
                    intermediate.add(stepOut);
                    previous = stepOut.path();
                }
                final ToolExecutionRequest stepRequest = ToolExecutionRequest.builder()
                        .id(request.id())
                        .name(step.tool())
                        .arguments(JsonUtil.writeValueAsString(args, false))
                        .build();
                final String result;
                try {
                    result = step.toolImpl().run(stepRequest, chatId);
                } catch (final WorkspaceException e) {
                    throw new WorkspaceException("Step " + (i + 1) + " (" + step.tool() + ") failed: "
                                                 + e.getMessage());
                }
                if (last) {
                    return log.append(result).toString();
                }
                log.append("Step ").append(i + 1).append(" (").append(step.tool()).append("): ")
                        .append(result.lines().findFirst().orElse("")).append('\n');
            }
            return log.toString().strip();
        } finally {
            for (final WorkspacePath path : intermediate) {
                if (workspace.find(chatId, path).isPresent()) {
                    workspace.delete(chatId, path);
                }
            }
        }
    }

    private List<Step> steps(final ToolExecutionRequest request) {
        final Map<?, ?> args;
        try {
            args = JsonUtil.readValue(request.arguments(), Map.class);
        } catch (final RuntimeException e) {
            throw new WorkspaceException("The arguments are not valid JSON.");
        }
        if (args == null || !(args.get(STEPS_ARG) instanceof final List<?> list) || list.isEmpty()) {
            throw new WorkspaceException("Give the steps, as a list of {\"tool\": ..., \"args\": {...}}.");
        }
        if (list.size() > MAX_STEPS) {
            throw new WorkspaceException("A pipeline may have at most " + MAX_STEPS + " steps.");
        }
        final List<Step> steps = new ArrayList<>();
        for (final Object item : list) {
            if (!(item instanceof final Map<?, ?> map) || !(map.get("args") instanceof final Map<?, ?> stepArgs)) {
                throw new WorkspaceException("Each step must be {\"tool\": ..., \"args\": {...}}.");
            }
            final String tool = String.valueOf(map.get("tool"));
            final AbstractWorkspaceTool toolImpl = tools.get(tool);
            if (toolImpl == null) {
                throw new WorkspaceException("'" + tool + "' cannot be a pipeline step. Use grep, jq, xpath or "
                                             + "table.");
            }
            final Map<String, Object> copy = new LinkedHashMap<>();
            stepArgs.forEach((key, value) -> copy.put(String.valueOf(key), value));
            steps.add(new Step(tool, toolImpl, copy));
        }
        return steps;
    }

    private static String extension(final Step step) {
        return switch (step.tool()) {
            case TableTool.NAME -> ".csv";
            case JqTool.NAME -> ".jsonl";
            default -> ".txt";
        };
    }

    private record Step(String tool, AbstractWorkspaceTool toolImpl, Map<String, Object> args) {

    }
}
