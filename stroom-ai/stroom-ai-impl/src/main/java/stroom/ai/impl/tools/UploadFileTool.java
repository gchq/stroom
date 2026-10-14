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
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code upload_file} - sends a workspace file's content to an MCP tool that takes data, e.g. the FastMCP
 * server's sample upload tool. The user must approve every call, as it sends data off this server, and the
 * approval card names the file and the tool.
 */
class UploadFileTool extends AbstractWorkspaceTool {

    static final String NAME = "upload_file";
    static final String TOOL_ARG = "tool";
    static final String CONTENT_ARGUMENT_ARG = "argument";
    static final String ARGUMENTS_ARG = "arguments";
    static final String DEFAULT_CONTENT_ARGUMENT = "content";
    static final long MAX_BYTES = 5L * 1024 * 1024;

    @Inject
    UploadFileTool(final ChatWorkspace workspace) {
        super(workspace);
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Sends a workspace file's content to an MCP tool that takes data, e.g. a sample "
                             + "upload tool, as one of its arguments. The user must approve every upload, as it "
                             + "sends data off this server. At most " + (MAX_BYTES / 1024 / 1024) + " MB.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "The workspace file to send.")
                        .addStringProperty(TOOL_ARG, "The MCP tool to send it to, by the name you were given.")
                        .addStringProperty(CONTENT_ARGUMENT_ARG, "The tool's argument that takes the file's "
                                                                 + "content. Defaults to '"
                                                                 + DEFAULT_CONTENT_ARGUMENT + "'.")
                        .addStringProperty(ARGUMENTS_ARG, "The tool's other arguments, as a JSON object, if any.")
                        .required(PATH_ARG, TOOL_ARG)
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final String tool = stringArg(request, TOOL_ARG);
        final McpToolCaller caller = ToolRunContext.current()
                .flatMap(ToolRunContext::getMcpToolCaller)
                .filter(candidate -> tool != null && candidate.hasTool(tool))
                .orElse(null);
        if (caller == null) {
            return "Error: there is no MCP tool '" + tool + "'. Upload only to an MCP tool you have been given.";
        }
        final long size = workspace.size(chatId, path);
        if (size > MAX_BYTES) {
            return "Error: " + path + " is " + size + " bytes, more than the " + MAX_BYTES + " bytes that can be "
                   + "uploaded.";
        }
        final Map<String, Object> arguments = new LinkedHashMap<>();
        final String otherArguments = stringArg(request, ARGUMENTS_ARG);
        if (NullSafe.isNonBlankString(otherArguments)) {
            try {
                arguments.putAll(JsonUtil.readValue(otherArguments, Map.class));
            } catch (final RuntimeException e) {
                return "Error: '" + ARGUMENTS_ARG + "' must be a JSON object.";
            }
        }
        final String contentArgument = NullSafe.nonBlankStringElse(stringArg(request, CONTENT_ARGUMENT_ARG),
                DEFAULT_CONTENT_ARGUMENT);
        arguments.put(contentArgument, read(chatId, path));
        final String result = caller.call(tool, JsonUtil.writeValueAsString(arguments, false));
        return "Sent " + path + " (" + size + " bytes) to " + tool + " on " + caller.serverOf(tool) + ". It said:\n"
               + result;
    }

    private String read(final int chatId, final WorkspacePath path) {
        try {
            return Files.readString(workspace.localFile(chatId, path), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean alwaysAsks() {
        return true;
    }
}
