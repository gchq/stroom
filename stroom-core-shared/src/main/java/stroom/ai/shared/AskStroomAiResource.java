/*
 * Copyright 2020 Crown Copyright
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

import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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
    @Path("/resumeRun")
    @Operation(
            summary = "Resume a chat whose last agent run stopped before finishing, by answering its last " +
                      "question again",
            operationId = "resumeRun")
    AskStroomAiResponse resumeRun(
            @Parameter(description = "request", required = true) AskStroomAiRequest request);

    @POST
    @Path("/editMessage")
    @Operation(
            summary = "Edit one of the user's earlier questions (or ask it again unchanged). The question and " +
                      "everything after it are removed from the chat, and the new text is answered",
            operationId = "editMessage")
    AskStroomAiResponse editMessage(
            @Parameter(description = "request", required = true) EditAiMessageRequest request);

    @POST
    @Path("/compactChat")
    @Operation(
            summary = "Compact a chat now: earlier turns are replaced, in what the model sees, by short digests and "
                      + "a summary. Nothing is deleted",
            operationId = "compactChat")
    AskStroomAiResponse compactChat(
            @Parameter(description = "request", required = true) CompactAiChatRequest request);

    @POST
    @Path("/undoCompaction")
    @Operation(
            summary = "Undo a chat's latest compaction, so the model sees the turns it covered as they were. Returns "
                      + "how much of the context window the chat now uses",
            operationId = "undoCompaction")
    AiContextUsage undoCompaction(
            @Parameter(description = "request", required = true) UndoAiCompactionRequest request);

    @POST
    @Path("/answerApproval")
    @Operation(
            summary = "Approve or reject something the agent asked the user's approval for",
            operationId = "answerAiApproval")
    Boolean answerApproval(
            @Parameter(description = "request", required = true) AnswerAiApprovalRequest request);

    @POST
    @Path("/setApproveEverything")
    @Operation(
            summary = "Turn 'approve everything' on or off for a chat, if an administrator has enabled it",
            operationId = "setAiApproveEverything")
    Boolean setApproveEverything(
            @Parameter(description = "request", required = true) SetAiApproveEverythingRequest request);

    @POST
    @Path("/listMcpPrompts")
    @Operation(
            summary = "List the prompts the model's MCP servers offer, as slash commands",
            operationId = "listAiMcpPrompts")
    List<AiMcpPrompt> listMcpPrompts(@Parameter(description = "config", required = true) AskStroomAiConfig config);

    @POST
    @Path("/getMcpPrompt")
    @Operation(
            summary = "Get an MCP server's prompt, filled in, for the user to check before sending it",
            operationId = "getAiMcpPrompt")
    AiMcpPromptText getMcpPrompt(@Parameter(description = "request", required = true) GetAiMcpPromptRequest request);

    @POST
    @Path("/answerElicitation")
    @Operation(
            summary = "Answer a form an MCP tool asked the user to fill in. The answer goes to the MCP server, "
                      + "never to the model",
            operationId = "answerAiElicitation")
    Boolean answerElicitation(
            @Parameter(description = "request", required = true) AnswerAiElicitationRequest request);

    @POST
    @Path("/listMemory/{chatId}")
    @Operation(
            summary = "List the memory a chat can see, and the changes the agent has made to memory in it",
            operationId = "listAiMemory")
    AiMemoryListResponse listMemory(@PathParam("chatId") int chatId);

    @POST
    @Path("/saveMemory")
    @Operation(
            summary = "Save a piece of memory the user has written or edited",
            operationId = "saveAiMemory")
    AiMemory saveMemory(@Parameter(description = "request", required = true) SaveAiMemoryRequest request);

    @POST
    @Path("/deleteMemory")
    @Operation(
            summary = "Delete a piece of memory, or all memory in a folder",
            operationId = "deleteAiMemory")
    Boolean deleteMemory(@Parameter(description = "request", required = true) DeleteAiMemoryRequest request);

    @POST
    @Path("/undoMemoryChange")
    @Operation(
            summary = "Undo a change the agent made to memory, if the memory has not changed since",
            operationId = "undoAiMemoryChange")
    Boolean undoMemoryChange(
            @Parameter(description = "request", required = true) UndoAiMemoryChangeRequest request);

    @POST
    @Path("/listToolApprovals/{chatId}")
    @Operation(
            summary = "List the decisions the user has made about tools in a chat, and whether 'approve "
                      + "everything' is on for it",
            operationId = "listAiToolApprovals")
    AiToolApprovalsResponse listToolApprovals(@PathParam("chatId") int chatId);

    @POST
    @Path("/removeToolApproval/{chatId}/{approvalId}")
    @Operation(
            summary = "Remove a decision the user made about a tool in a chat, so they are asked again",
            operationId = "removeAiToolApproval")
    Boolean removeToolApproval(@PathParam("chatId") int chatId,
                               @PathParam("approvalId") int approvalId);

    @POST
    @Path("/resetToolApprovals/{chatId}")
    @Operation(
            summary = "Remove every decision the user made about tools in a chat, so they are asked again",
            operationId = "resetAiToolApprovals")
    Boolean resetToolApprovals(@PathParam("chatId") int chatId);

    @GET
    @Path("/getChatStatuses/{sinceVersion}")
    @Operation(
            summary = "Get the statuses of the user's chats, for the switcher and badges, or nothing if none has "
                      + "changed since the given version",
            operationId = "getAiChatStatuses")
    AiChatStatusResponse getChatStatuses(@PathParam("sinceVersion") long sinceVersion);

    @POST
    @Path("/getDefaultConfig")
    @Operation(
            summary = "Get the default config to use for asking questions",
            operationId = "getDefaultConfig")
    AskStroomAiConfig getDefaultConfig();

    @POST
    @Path("/setTableAnalysisConfig")
    @Operation(
            summary = "Set the table analysis settings, which apply to everyone",
            operationId = "setAiTableAnalysisConfig")
    Boolean setTableAnalysisConfig(@Parameter(description = "config", required = true) TableAnalysisConfig config);

    @POST
    @Path("/suggestPrompts")
    @Operation(
            summary = "Suggest what the user might ask about what they have just attached to an AI chat, given the "
                      + "conversation so far",
            operationId = "suggestAiPrompts")
    List<String> suggestPrompts(@Parameter(description = "request", required = true)
                                SuggestAiPromptsRequest request);

    @POST
    @Path("/searchChats")
    @Operation(
            summary = "Search the user's chats by their titles and the user's questions in them, most recently "
                      + "active first",
            operationId = "searchAiChats")
    List<AiChatSearchHit> searchChats(@Parameter(description = "request", required = true)
                                      SearchAiChatsRequest request);

    @POST
    @Path("/clearChat/{chatId}")
    @Operation(
            summary = "Clear a chat so it starts afresh: its messages, attached data, workspace files and memory are "
                      + "deleted, and its title is reset. The chat, its mode and its tool approvals are kept.",
            operationId = "clearAiChat")
    Boolean clearChat(@PathParam("chatId") int chatId);

    @POST
    @Path("/deleteAllChats")
    @Operation(
            summary = "Delete all the user's chats, with their messages, attached data, workspace files, chat "
                      + "memory and tool approvals. The user's own memory is kept.",
            operationId = "deleteAllAiChats")
    Integer deleteAllChats();

    @POST
    @Path("/markAllChatsRead")
    @Operation(
            summary = "Mark everything in all the user's chats as read, clearing their unread marks",
            operationId = "markAllAiChatsRead")
    Integer markAllChatsRead();

    @PUT
    @Path("/setAgentMode/{chatId}/{mode}")
    @Operation(
            summary = "Set how the agent works in a chat: Agent, Ask or Plan",
            operationId = "setAiAgentMode")
    Boolean setAgentMode(@PathParam("chatId") int chatId, @PathParam("mode") AiAgentMode mode);

    @POST
    @Path("/userAction/{chatId}/{messageId}/{actionId}")
    @Operation(
            summary = "Do what a button that an MCP tool's result offered does, e.g. enable a processor filter, after "
                      + "checking it can still be done",
            operationId = "doAiUserAction")
    AiUserActionResult doUserAction(@PathParam("chatId") int chatId,
                                    @PathParam("messageId") int messageId,
                                    @PathParam("actionId") String actionId,
                                    @Parameter(description = "For a proposed code change, the parts of it the user "
                                                             + "rejected, by their number from 0, comma separated")
                                    @QueryParam("rejectedChanges") String rejectedChanges);

    @POST
    @Path("/writeExtraction/{chatId}/{messageId}/{actionId}")
    @Operation(
            summary = "Write the search extraction a create_extraction_pipeline button in the chat proposed (an XSLT "
                      + "and a pipeline), once the user has confirmed exactly what is written, in the folder they "
                      + "chose",
            operationId = "writeAiExtraction")
    AiWriteExtractionResult writeExtraction(@PathParam("chatId") int chatId,
                                            @PathParam("messageId") int messageId,
                                            @PathParam("actionId") String actionId,
                                            @Parameter(description = "The name, folder and hash the user confirmed",
                                                    required = true) AiWriteExtractionRequest request);

    @POST
    @Path("/undoUserAction/{chatId}/{messageId}/{actionId}")
    @Operation(
            summary = "Record that the user undid a proposed code change they applied to a stepping editor with a "
                      + "button in the chat, after checking they may still edit the document",
            operationId = "undoAiUserAction")
    AiUserActionResult undoUserAction(@PathParam("chatId") int chatId,
                                      @PathParam("messageId") int messageId,
                                      @PathParam("actionId") String actionId);

    @POST
    @Path("/resetAllUsersConfig")
    @Operation(
            summary = "Reset every user's Ask Stroom AI settings (model, prompts, dock position and so on) to the " +
                      "configured defaults",
            operationId = "resetAllUsersAskStroomAiConfig")
    Integer resetAllUsersConfig();

    @POST
    @Path("/setDefaultAskStroomAIConfig")
    @Operation(
            summary = "Set the default Stroom AI config to use for asking questions",
            operationId = "setDefaultAskStroomAIConfig")
    Boolean setDefaultAskStroomAIConfig(
            @Parameter(description = "config", required = true) AskStroomAiConfig config);

    @POST
    @Path("/createChat")
    @Operation(
            summary = "Create a new AI chat",
            operationId = "createChat")
    AiChat createChat();

    @POST
    @Path("/listChats")
    @Operation(
            summary = "List all AI chats for the current user",
            operationId = "listChats")
    ResultPage<AiChat> listChats(@Parameter(description = "request", required = true)
                                 FindAiChatHistoryCriteria criteria);

    @POST
    @Path("/getChat/{chatId}")
    @Operation(
            summary = "Get an AI chat by ID",
            operationId = "getChat")
    AiChat getChat(@PathParam("chatId") int chatId);

    @POST
    @Path("/deleteChat/{chatId}")
    @Operation(
            summary = "Delete an AI chat",
            operationId = "deleteChat")
    Boolean deleteChat(@PathParam("chatId") int chatId);

    @POST
    @Path("/sendSteerNow/{chatId}/{messageId}")
    @Operation(
            summary = "Send a message waiting for an AI chat's run to finish its current step now",
            operationId = "sendSteerNow")
    Boolean sendSteerNow(@PathParam("chatId") int chatId,
                         @PathParam("messageId") int messageId);

    @POST
    @Path("/deleteMessage/{chatId}/{messageId}")
    @Operation(
            summary = "Delete a message from an AI chat",
            operationId = "deleteMessage")
    Boolean deleteMessage(@PathParam("chatId") int chatId,
                          @PathParam("messageId") int messageId);

    @POST
    @Path("/deleteAllMessages/{chatId}")
    @Operation(
            summary = "Delete all messages and attachments from an AI chat",
            operationId = "deleteAllMessages")
    Boolean deleteAllMessages(@PathParam("chatId") int chatId);

    @POST
    @Path("/getMessages/{chatId}")
    @Operation(
            summary = "Get messages for an AI chat",
            operationId = "getMessages")
    List<AiChatMessage> getMessages(@PathParam("chatId") int chatId);

    @POST
    @Path("/updateChatTitle/{chatId}")
    @Operation(
            summary = "Update the title of an AI chat",
            operationId = "updateChatTitle")
    Boolean updateChatTitle(@PathParam("chatId") int chatId,
                            @Parameter(description = "title", required = true) String title);

    @POST
    @Path("/pollMessages/{chatId}")
    @Operation(
            summary = "Poll for new messages in an AI chat since a given message ID",
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
            summary = "Download the chat history for a chat as a Markdown file",
            operationId = "downloadChatHistory")
    ResourceGeneration downloadChatHistory(
            @Parameter(description = "request", required = true) DownloadChatHistoryRequest request);

    @POST
    @Path("/getAttachmentData")
    @Operation(
            summary = "Get a page of attachment table data",
            operationId = "getAttachmentData")
    AiAttachmentDataPage getAttachmentData(
            @Parameter(description = "request", required = true) GetAttachmentDataRequest request);
}
