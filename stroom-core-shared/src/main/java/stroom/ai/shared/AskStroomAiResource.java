/*
 * Copyright 2017 Crown Copyright
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

package stroom.ai.shared;

import stroom.util.shared.FindNamedEntityCriteria;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.List;

@Tag(name = "AI")
@Path(AskStroomAiResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AskStroomAiResource extends RestResource, DirectRestService {

    String BASE_PATH = "/ai" + ResourcePaths.V1;

    String ASK_STROOM_AI_PATH_PART = "/askStroomAi";

    @POST
    @Path(ASK_STROOM_AI_PATH_PART)
    @Operation(
            summary = "Ask Stroom AI a question relating to the current search context",
            operationId = "askStroomAi")
    AskStroomAiResponse askStroomAi(
            @Parameter(description = "request", required = true) AskStroomAiRequest request);

    @POST
    @Path("/getDefaultConfig")
    @Operation(
            summary = "Get the default config to use for asking questions",
            operationId = "getDefaultConfig")
    AskStroomAIConfig getDefaultConfig();

    @POST
    @Path("/setDefaultAskStroomAIConfig")
    @Operation(
            summary = "Set the default Stroom AI config to use for asking questions",
            operationId = "setDefaultAskStroomAIConfig")
    Boolean setDefaultAskStroomAIConfig(
            @Parameter(description = "config", required = true) AskStroomAIConfig config);

    @POST
    @Path("/createChat")
    @Operation(
            summary = "Create a new AI chat conversation",
            operationId = "createChat")
    AiChat createChat();

    @POST
    @Path("/listChats")
    @Operation(
            summary = "List all AI chat conversations for the current user",
            operationId = "listChats")
    ResultPage<AiChat> listChats(@Parameter(description = "request", required = true) FindNamedEntityCriteria criteria);

    @POST
    @Path("/getChat/{chatId}")
    @Operation(
            summary = "Get an AI chat conversation by ID",
            operationId = "getChat")
    AiChat getChat(@PathParam("chatId") int chatId);

    @POST
    @Path("/deleteChat/{chatId}")
    @Operation(
            summary = "Delete an AI chat conversation",
            operationId = "deleteChat")
    Boolean deleteChat(@PathParam("chatId") int chatId);

    @POST
    @Path("/getMessages/{chatId}")
    @Operation(
            summary = "Get messages for an AI chat conversation",
            operationId = "getMessages")
    List<AiChatMessage> getMessages(@PathParam("chatId") int chatId);

    @POST
    @Path("/updateChatTitle/{chatId}")
    @Operation(
            summary = "Update the title of an AI chat conversation",
            operationId = "updateChatTitle")
    Boolean updateChatTitle(@PathParam("chatId") int chatId,
                            @Parameter(description = "title", required = true) String title);

    @POST
    @Path("/pollMessages/{chatId}")
    @Operation(
            summary = "Poll for new messages in an AI chat conversation since a given message ID",
            operationId = "pollMessages")
    AiChatPollResponse pollMessages(@PathParam("chatId") int chatId,
                                    @Parameter(description = "request", required = true)
                                    AiChatPollRequest request);

    @POST
    @Path("/cancelProcessing/{chatId}")
    @Operation(
            summary = "Cancel in-progress AI batch analysis for a chat",
            operationId = "cancelProcessing")
    Boolean cancelProcessing(@PathParam("chatId") int chatId);

    @POST
    @Path("/downloadChatHistory")
    @Operation(
            summary = "Download the chat history for a conversation as a Markdown file",
            operationId = "downloadChatHistory")
    ResourceGeneration downloadChatHistory(
            @Parameter(description = "request", required = true) DownloadChatHistoryRequest request);
}
