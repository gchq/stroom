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
import stroom.ai.impl.workspace.SafeJq;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;
import net.thisptr.jackson.jq.JsonQuery;

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
class JqTool extends AbstractWorkspaceTool {

    static final String NAME = "jq";
    static final String QUERY_ARG = "query";
    static final String RAW_ARG = "raw";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    JqTool(final ChatWorkspace workspace) {
        super(workspace);
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Runs a jq query on a JSON file in the workspace (one JSON value, or one per line), "
                             + "e.g. '.events[] | select(.severity == \"high\") | .host'. Each result is one line "
                             + "of JSON. Give out to write the results to a file. Regular expression functions "
                             + "(test, match, sub...) are not available; use grep or table for those.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "The JSON file.")
                        .addStringProperty(QUERY_ARG, "The jq query.")
                        .addBooleanProperty(RAW_ARG, "True to give string results without quotes.")
                        .addStringProperty(OUT_ARG, "A file in /outputs/ to write the results to.")
                        .required(PATH_ARG, QUERY_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final JsonQuery query = SafeJq.compile(rawArg(request, QUERY_ARG));
        final boolean raw = booleanArg(request, RAW_ARG, false);
        final WorkspacePath out = outArg(request);
        final Path file = workspace.localFile(chatId, path);

        final List<String> results = new ArrayList<>();
        final long[] total = {0};
        final Path temp = out != null
                ? workspace.tempFile()
                : null;
        try (final MappingIterator<JsonNode> inputs = MAPPER.readerFor(JsonNode.class).readValues(file.toFile());
                final BufferedWriter writer = temp != null
                        ? Files.newBufferedWriter(temp, StandardCharsets.UTF_8)
                        : null) {
            while (inputs.hasNextValue()) {
                ToolDeadline.check();
                final JsonNode input = inputs.nextValue();
                SafeJq.run(query, input, value -> {
                    total[0]++;
                    final String line = raw && value.isTextual()
                            ? value.asText()
                            : value.toString();
                    if (writer != null) {
                        write(writer, line);
                    } else if (results.size() < limits().getMaxLinesPerRead()) {
                        results.add(line);
                    }
                });
            }
            if (writer != null) {
                writer.close();
                workspace.write(chatId, out, temp, workspace.sourceDocUuid(chatId, path));
                return "Wrote " + total[0] + " result(s) to " + out + ".";
            }
            return boundedLines(results, total[0]);
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

    private static void write(final BufferedWriter writer, final String line) {
        try {
            writer.write(line);
            writer.newLine();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
