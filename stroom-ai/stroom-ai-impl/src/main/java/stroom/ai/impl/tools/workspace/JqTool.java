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

package stroom.ai.impl.tools.workspace;

import stroom.ai.impl.ToolOutputs;
import stroom.ai.impl.tools.AbstractWorkspaceTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SafeJq;
import stroom.ai.impl.workspace.SafePattern;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.inject.Inject;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Output;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code jq} - queries and reshapes JSON in the workspace with a jq query, run by a locked-down engine (see
 * {@link SafeJq}).
 */
public class JqTool extends AbstractWorkspaceTool {

    public static final String NAME = "jq";
    static final String QUERY_ARG = "query";
    static final String RAW_ARG = "raw";
    static final String SLURP_ARG = "slurp";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /// @param workspace The chats' workspaces, which the tool reads and writes.
    @Inject
    public JqTool(final ChatWorkspace workspace) {
        super(workspace);
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Runs a jq query on a JSON file")
                        .chatOnly(" in the workspace")
                        .general(" (one JSON value, or one per line), e.g. '.events[] | select(.severity == "
                                 + "\"high\") | .host'. Each result is one line of JSON.")
                        .chatOnly(" Give out to write the results to a file.")
                        .general(" Regular expression functions (test, match, capture, sub, gsub...) take "
                                 + "Java regular expressions of up to " + SafePattern.MAX_PATTERN_LENGTH
                                 + " characters.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "The JSON file.")
                        .addStringProperty(QUERY_ARG, "The jq query.")
                        .addBooleanProperty(RAW_ARG, "True to give string results without quotes.")
                        .addBooleanProperty(SLURP_ARG, "True to read every JSON value in the file into one "
                                                       + "array and run the query once on it (as jq -s).")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A file in /outputs/ to write the results to.")
                                .build())
                        .required(PATH_ARG, QUERY_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final JsonQuery query = SafeJq.compile(rawArg(request, QUERY_ARG));
        final boolean raw = booleanArg(request, RAW_ARG, false);
        final boolean slurp = booleanArg(request, SLURP_ARG, false);
        final WorkspacePath out = outArg(request);
        final Path file = workspace.localFile(path);

        final List<String> results = new ArrayList<>();
        final long[] total = {0};
        final Path temp = out != null
                ? workspace.tempFile()
                : null;
        try (final MappingIterator<JsonNode> inputs = MAPPER.readerFor(JsonNode.class).readValues(file.toFile());
                final BufferedWriter writer = temp != null
                        ? Files.newBufferedWriter(temp, StandardCharsets.UTF_8)
                        : null) {
            final Output output = value -> {
                total[0]++;
                final String line = raw && value.isTextual()
                        ? value.asText()
                        : value.toString();
                if (writer != null) {
                    write(writer, line);
                } else if (results.size() < limits(context).getMaxLinesPerRead()) {
                    results.add(line);
                }
            };
            if (slurp) {
                runOn(query, slurp(inputs), output);
            } else {
                long values = 0;
                while (inputs.hasNextValue()) {
                    ToolDeadline.check();
                    final JsonNode input = inputs.nextValue();
                    values++;
                    try {
                        runOn(query, input, output);
                    } catch (final WorkspaceException e) {
                        throw withValueCount(e, inputs, values);
                    }
                }
            }
            if (writer != null) {
                writer.close();
                workspace.write(out, temp, workspace.sourceDocUuid(path));
                return ToolResult.text("Wrote " + total[0] + " result(s) to " + out + ".");
            }
            return ToolResult.text(boundedLines(context, results, total[0]));
        } catch (final JsonProcessingException e) {
            throw new WorkspaceException(path + " is not JSON that can be read: " + e.getOriginalMessage());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (temp != null) {
                ChatWorkspace.deleteQuietly(temp);
            }
        }
    }

    // Every JSON value of the file in one array, as jq -s reads them.
    private static ArrayNode slurp(final MappingIterator<JsonNode> inputs) throws IOException {
        final ArrayNode all = MAPPER.createArrayNode();
        while (inputs.hasNextValue()) {
            ToolDeadline.check();
            all.add(inputs.nextValue());
        }
        return all;
    }

    // A query that failed on one value of a file holding several says so, as the query ran on each on its own and
    // a query written for all of them (group_by, .[]) fails on any one. Seen in chat 187: group_by on a JSON
    // lines file of fetched records, then grep in its place.
    private static WorkspaceException withValueCount(final WorkspaceException e,
                                                     final MappingIterator<JsonNode> inputs,
                                                     final long failedValue) {
        if (!e.getMessage().startsWith(SafeJq.FAILED)) {
            // Stopped for too much work or too deep a recursion: running it once over all of them won't help.
            return e;
        }
        long values = failedValue;
        boolean counted = true;
        try {
            while (inputs.hasNextValue()) {
                ToolDeadline.check();
                inputs.nextValue();
                values++;
            }
        } catch (final IOException | RuntimeException countError) {
            // A later value that can't be read, or the time limit: say how many were read.
            counted = false;
        }
        if (values < 2) {
            return e;
        }
        return new WorkspaceException(e.getMessage() + " The file holds " + (counted
                ? ""
                : "at least ") + values + " JSON values (e.g. one per line) and the query ran on each on its "
                                      + "own; it failed on value " + failedValue + ". Give " + SLURP_ARG
                                      + ": true to run it once on all of them as an array.");
    }

    private static void write(final BufferedWriter writer, final String line) {
        try {
            writer.write(line);
            writer.newLine();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The query run on one JSON value of the file; a path that doesn't fit it also says what the value holds, so
    // the next try needn't be a guess.
    private static void runOn(final JsonQuery query, final JsonNode input, final Output output) {
        try {
            SafeJq.run(query, input, output);
        } catch (final WorkspaceException e) {
            if (SafeJq.pathAdvice(e.getMessage()).isEmpty()) {
                throw e;
            }
            throw new WorkspaceException(e.getMessage() + " The input is " + ToolOutputs.shapeOf(input) + ".");
        }
    }
}
