/*
 * Copyright 2025 Crown Copyright
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

package stroom.ai.impl;

import stroom.ai.api.AiService;
import stroom.ai.api.OpenAIModelStore;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.openai.shared.ListMcpToolsResponse;
import stroom.openai.shared.McpToolDescription;
import stroom.openai.shared.McpToolPolicy;
import stroom.openai.shared.McpToolServerConfig;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.openai.shared.OpenAIModelResource;
import stroom.openai.shared.OpenAIModelTestResponse;
import stroom.openai.shared.TestMcpToolServerRequest;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.http.HttpClientConfig;

import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@AutoLogged
public class OpenAIModelResourceImpl implements OpenAIModelResource, FetchWithUuid<OpenAIModelDoc> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenAIModelResourceImpl.class);

    // How many tool names a test of an MCP tool server lists.
    private static final int MAX_TOOLS_LISTED = 50;

    private final Provider<OpenAIModelStore> openAIModelStoreProvider;
    private final Provider<AiService> aiServiceProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<McpToolServerFactory> mcpToolServerFactoryProvider;

    @Inject
    OpenAIModelResourceImpl(
            final Provider<OpenAIModelStore> openAIModelStoreProvider,
            final Provider<AiService> aiServiceProvider,
            final Provider<DocumentResourceHelper> documentResourceHelperProvider,
            final Provider<SecurityContext> securityContextProvider,
            final Provider<McpToolServerFactory> mcpToolServerFactoryProvider) {
        this.openAIModelStoreProvider = openAIModelStoreProvider;
        this.mcpToolServerFactoryProvider = mcpToolServerFactoryProvider;
        this.aiServiceProvider = aiServiceProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.securityContextProvider = securityContextProvider;
    }

    @Override
    public OpenAIModelDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(openAIModelStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public OpenAIModelDoc update(final String uuid, final OpenAIModelDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(openAIModelStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(OpenAIModelDoc.TYPE)
                .build();
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Validate OpenAI model")
    public OpenAIModelTestResponse validateModel(final OpenAIModelDoc modelDoc) {
        if (NullSafe.isEmptyString(modelDoc.getModelId())) {
            return new OpenAIModelTestResponse(false, "Model ID must not be empty");
        }

        // Validating a model makes a server-side request to the model's (request-supplied) base URL, so
        // require USE permission on the model document rather than allowing any logged-in user. Deliberately
        // outside the try below, as a permission failure is not a validation result.
        final DocRef docRef = getDocRef(modelDoc.getUuid());
        final SecurityContext securityContext = securityContextProvider.get();
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have USE permission on AI model " + docRef);
        }

        try {
            final String model = aiServiceProvider.get().getModel(modelDoc);
            return new OpenAIModelTestResponse(model != null, model);
        } catch (final ModelNotFoundException e) {
            // The endpoint was reached but doesn't serve the model: the detail lists those it does.
            return new OpenAIModelTestResponse(false, e.getMessage(), e.detail());
        } catch (final NoSuchElementException e) {
            return new OpenAIModelTestResponse(false, "Model " + modelDoc.getModelId() + " not found");
        } catch (final RuntimeException e) {
            // Whatever went wrong, the user is testing a connection they are trying to get working, so tell
            // them what happened rather than letting this surface as an unexplained server error.
            LOGGER.debug(e::getMessage, e);
            return new OpenAIModelTestResponse(false, NullSafe.nonBlankStringElse(e.getMessage(), e.toString()));
        }
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Test MCP tool server")
    public OpenAIModelTestResponse testMcpToolServer(final TestMcpToolServerRequest request) {
        final McpToolServerConfig server = NullSafe.get(request, TestMcpToolServerRequest::getMcpToolServer);
        if (server == null || NullSafe.isBlankString(server.getEndpoint())) {
            return new OpenAIModelTestResponse(false, "Enter the server's endpoint first.");
        }

        // The test connects to an endpoint given in the request, with a stored credential named in it, so only
        // someone who could save these settings on the model may test them.
        final DocRef docRef = getDocRef(request.getModelUuid());
        final SecurityContext securityContext = securityContextProvider.get();
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have EDIT permission on AI model " + docRef);
        }

        final OpenAIModelDoc modelDoc = OpenAIModelDoc.builder()
                .uuid(docRef.getUuid())
                .name(docRef.getName())
                .mcpToolServers(List.of(server.copy().enabled(true).build()))
                .build();
        try (final McpToolset toolset = mcpToolServerFactoryProvider.get().createToolset(modelDoc)) {
            if (!toolset.getWarnings().isEmpty()) {
                return new OpenAIModelTestResponse(false, String.join("\n", toolset.getWarnings()));
            }
            return new OpenAIModelTestResponse(true, describeTools(toolset));
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return new OpenAIModelTestResponse(false, NullSafe.nonBlankStringElse(e.getMessage(), e.toString()));
        }
    }

    @Override
    public ListMcpToolsResponse listMcpTools(final TestMcpToolServerRequest request) {
        final McpToolServerConfig server = NullSafe.get(request, TestMcpToolServerRequest::getMcpToolServer);
        if (server == null || NullSafe.isBlankString(server.getEndpoint())) {
            return new ListMcpToolsResponse(List.of(), "The server has no endpoint.");
        }
        // As for testing a server: it connects to an endpoint, with a credential, given in the request.
        final DocRef docRef = getDocRef(request.getModelUuid());
        final SecurityContext securityContext = securityContextProvider.get();
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have EDIT permission on AI model " + docRef);
        }
        final OpenAIModelDoc modelDoc = OpenAIModelDoc.builder()
                .uuid(docRef.getUuid())
                .name(docRef.getName())
                .mcpToolServers(List.of(server.copy().enabled(true).build()))
                .build();
        try (final McpToolset toolset = mcpToolServerFactoryProvider.get().createToolset(modelDoc)) {
            if (!toolset.getWarnings().isEmpty()) {
                return new ListMcpToolsResponse(List.of(), String.join("\n", toolset.getWarnings()));
            }
            final List<McpToolDescription> tools = toolset.listServerTools().stream()
                    .map(serverTool -> new McpToolDescription(
                            serverTool.tool().name(),
                            serverTool.policy() != McpToolPolicy.DENY,
                            McpToolDocs.markdown(serverTool.tool(), serverTool.policy()),
                            McpToolDocs.classifications(serverTool.tool()),
                            McpToolset.declaredGroup(serverTool.tool())))
                    .toList();
            return new ListMcpToolsResponse(tools, null);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return new ListMcpToolsResponse(List.of(), NullSafe.nonBlankStringElse(e.getMessage(), e.toString()));
        }
    }

    // Says how many tools the server would offer, after its tool policies, and names some of them.
    private static String describeTools(final McpToolset toolset) {
        final List<String> names = toolset.tools().keySet().stream()
                .map(ToolSpecification::name)
                .map(toolset::toolInfo)
                .flatMap(Optional::stream)
                .map(McpToolInfo::toolName)
                .sorted()
                .toList();
        if (names.isEmpty()) {
            return "Connected, but the server offers no tools (or its tool policies deny them all).";
        }
        final List<String> shown = names.size() > MAX_TOOLS_LISTED
                ? names.subList(0, MAX_TOOLS_LISTED)
                : names;
        // One per line, as the message is shown as preformatted detail.
        return "Connected. " + names.size() + (names.size() == 1
                ? " tool is"
                : " tools are")
               + " offered:\n" + String.join("\n", shown)
               + (names.size() > shown.size()
                ? "\n… and " + (names.size() - shown.size()) + " more"
                : "");
    }

    @Override
    public HttpClientConfig getDefaultHttpClientConfig() {
        return aiServiceProvider.get().getDefaultHttpClientConfig();
    }
}
