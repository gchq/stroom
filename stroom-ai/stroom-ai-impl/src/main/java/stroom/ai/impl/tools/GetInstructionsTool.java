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

import stroom.ai.impl.tools.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.AgentInstructions.Standing;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/// `get_instructions` - the standing instructions (AGENTS docs) that apply to some folders or documents, as the
/// Stroom MCP server's tool of the same name gives them: most general first, with their text. They may say how XSLT
/// is written, how fields are mapped, how things are named, or which folders to leave out. Read as the user.
class GetInstructionsTool extends AbstractBuiltInTool {

    static final String NAME = "get_instructions";
    static final String FOLDERS_ARG = "folders";
    static final String UUIDS_ARG = "uuids";

    private final AgentInstructions agentInstructions;
    private final DocumentAccess documentAccess;

    @Inject
    GetInstructionsTool(final AgentInstructions agentInstructions, final DocumentAccess documentAccess) {
        this.agentInstructions = agentInstructions;
        this.documentAccess = documentAccess;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Gives the standing instructions (Documentation docs named " + AgentInstructions.DOC_NAME
                             + ") that apply to some folders or documents: a doc applies to its folder and below, "
                             + "and one at the top of the tree everywhere. They can say how XSLT is written, how "
                             + "fields are mapped, how things are named, or which folders searches leave out. Call "
                             + "it before proposing code or names for documents in a folder, and follow what it "
                             + "says; the user's own request takes precedence.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(FOLDERS_ARG, stringArraySchema("Folders the work touches, by UUID or Explorer "
                                                                      + "path, e.g. '/Feeds/Events'."))
                        .addProperty(UUIDS_ARG, stringArraySchema("Documents the work touches, e.g. an XSLT or a "
                                                                    + "pipeline; their folders are used."))
                        .build())
                .build();
    }

    @Override
    public String execute(final ToolExecutionRequest request) {
        return "Error: " + NAME + " can only be used in a chat.";
    }

    @Override
    public String execute(final ToolExecutionRequest request, final int chatId) {
        final List<String> paths = new ArrayList<>();
        final List<String> notFound = new ArrayList<>();
        for (final String folder : stringListArg(request, FOLDERS_ARG)) {
            documentAccess.findFolder(folder).ifPresentOrElse(
                    docRef -> paths.add(folderPath(docRef)),
                    () -> notFound.add(folder));
        }
        for (final String uuid : stringListArg(request, UUIDS_ARG)) {
            documentAccess.findDocRef(uuid).ifPresentOrElse(
                    docRef -> paths.add(documentAccess.path(docRef)),
                    () -> notFound.add(uuid));
        }
        if (paths.isEmpty() && notFound.isEmpty()) {
            // Only those that apply everywhere.
            paths.add("/");
        }

        final Standing standing = agentInstructions.load(chatId, "Standing instructions read by the " + NAME
                                                                 + " tool");
        final List<InstructionDoc> applying = standing.applyingTo(paths);
        final StringBuilder sb = new StringBuilder();
        if (applying.isEmpty()) {
            sb.append("No ").append(AgentInstructions.DOC_NAME).append(" docs apply there. Users can add one: a "
                                                                       + "Documentation doc named ")
                    .append(AgentInstructions.DOC_NAME).append(" in a folder applies to that folder and below.");
        } else {
            sb.append(applying.size()).append(" ").append(AgentInstructions.DOC_NAME).append(" doc(s) apply.")
                    .append(standing.note(paths));
        }
        final List<String> elsewhere = standing.docs().stream()
                .filter(doc -> !applying.contains(doc))
                .map(InstructionDoc::folder)
                .toList();
        if (!elsewhere.isEmpty()) {
            sb.append("\n\nOther ").append(AgentInstructions.DOC_NAME).append(" docs, which don't apply there: in ")
                    .append(String.join(", ", elsewhere)).append('.');
        }
        if (!notFound.isEmpty()) {
            sb.append("\n\nNot found, or you don't have permission to view them: ")
                    .append(String.join(", ", notFound)).append('.');
        }
        return sb.toString().strip();
    }

    // The path of a folder itself (not of the folder it is in), or `/` for the top of the tree.
    private String folderPath(final DocRef folder) {
        if (ExplorerConstants.SYSTEM_DOC_REF.equals(folder)) {
            return "/";
        }
        return AgentInstructions.normalise(documentAccess.path(folder) + "/" + folder.getName());
    }

    @Override
    public boolean readsUntrustedContent() {
        // Written by anyone who can edit the folder.
        return true;
    }
}
