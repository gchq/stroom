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

import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;

import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/// `propose_instructions` - offers the user standing instructions (an AGENTS doc) for a folder, as a diff with a
/// 'Review and apply' button, as propose_documentation_change offers a document's documentation (asked for by the
/// user: rules drafted from their own chats, [InstructionEvidenceTool], reviewed as an XSLT edit proposal is). It
/// changes nothing itself. For a folder whose AGENTS doc exists, the button applies the change to the doc's editor,
/// unsaved, for the user to save (which needs Edit); for a folder with none, applying it creates the doc and saves
/// it (which needs Create in the folder).
///
/// Lines of the sections the tools apply themselves (`## Required imports` and so on) that name nothing the tools
/// know are pointed out ([InstructionRules#problems]): the tools leave them out without a word, which is right for a
/// note but not for a slip (e.g. 'indexng'), so the agent can correct the proposal before the user applies it.
class ProposeInstructionsTool extends AbstractWorkspaceTool {

    static final String NAME = "propose_instructions";
    static final String FOLDER_ARG = "folder";
    static final String MARKDOWN_ARG = "markdown";
    static final String EDITS_ARG = ProposeCodeChangeTool.EDITS_ARG;
    static final String SUMMARY_ARG = "summary";
    static final int MAX_SUMMARY_CHARS = 500;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final DocumentAccess documentAccess;

    @Inject
    ProposeInstructionsTool(final ChatWorkspace workspace, final DocumentAccess documentAccess) {
        super(workspace);
        this.documentAccess = documentAccess;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .note("Applying an existing doc's change needs Edit on it; creating a doc needs Create in the "
                      + "folder. Both are checked when the user applies it.")
                .build();
    }

    @Override
    public boolean isChatOnly() {
        // The user applies it with a button in the chat.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public ToolDefinition definition() {
        final ObjectSchema edit = ObjectSchema.builder()
                .addStringProperty(ProposeCodeChangeTool.OLD_TEXT_ARG, "Text to replace, exactly as it is in the "
                                                                       + "doc, and appearing only once in it.")
                .addStringProperty(ProposeCodeChangeTool.NEW_TEXT_ARG, "The text to replace it with.")
                .required(ProposeCodeChangeTool.OLD_TEXT_ARG, ProposeCodeChangeTool.NEW_TEXT_ARG)
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Proposes standing instructions for a folder: the text of its "
                                 + AgentInstructions.DOC_NAME + " doc, which applies to the folder and everything "
                                 + "below it. The user is shown a diff with a 'Review and apply' button; nothing "
                                 + "changes until they apply it (to the doc's editor, unsaved, for them to save; "
                                 + "or, for a folder with no " + AgentInstructions.DOC_NAME + " doc, by creating "
                                 + "and saving one). Keep what the doc says already. Short, concrete rules, one "
                                 + "per bullet; the sections the tools apply themselves are '## Required "
                                 + "imports', '## Component templates' and '## Documentation'.")
                        .chatOnly(" Draft rules from instruction_evidence's repeated choices or from what the "
                                  + "user said they always want, and read get_instructions for the folder first "
                                  + "(its guide='agent-instructions' says how the sections are written).")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(FOLDER_ARG, "The folder, by UUID or Explorer path (e.g. "
                                                       + "'/Feeds/Events/Acme'). The doc proposed is the one "
                                                       + "directly in it.")
                        .addStringProperty(MARKDOWN_ARG, "The doc's whole proposed text. Give this or "
                                                         + EDITS_ARG + ", not both (a new doc needs this).")
                        .addProperty(EDITS_ARG, ArraySchema.builder()
                                .items(edit)
                                .description("Replacements made to the folder's existing doc, in order. Give "
                                             + "this or " + MARKDOWN_ARG + ", not both.")
                                .build())
                        .addStringProperty(SUMMARY_ARG, "A sentence saying what the change does and why, citing "
                                                        + "the chats it comes from; shown with the diff.")
                        .required(FOLDER_ARG, SUMMARY_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String folderArg = stringArg(request, FOLDER_ARG);
        final String given = rawArg(request, MARKDOWN_ARG);
        final Object edits = argsMap(request).get(EDITS_ARG);
        final String summary = stringArg(request, SUMMARY_ARG);
        if (folderArg == null) {
            throw new WorkspaceException("Give the folder.");
        }
        final boolean hasMarkdown = given != null && !given.isBlank();
        if (hasMarkdown == (edits != null)) {
            throw new WorkspaceException("Give either " + EDITS_ARG + " or the whole proposed " + MARKDOWN_ARG
                                         + ", but not both.");
        }
        if (summary != null && summary.length() > MAX_SUMMARY_CHARS) {
            throw new WorkspaceException("The summary may be at most " + MAX_SUMMARY_CHARS + " characters.");
        }
        if (context.runContext().isEmpty()) {
            return ToolResult.text("Error: a button can only be offered while answering a question in a chat.");
        }
        final DocRef folder = documentAccess.findFolder(folderArg).orElse(null);
        if (folder == null || !ExplorerConstants.FOLDER_TYPE.equals(folder.getType())) {
            return ToolResult.text("Error: no folder '" + folderArg + "' that you can see. Give a folder's UUID or "
                                   + "Explorer path.");
        }
        final DocRef existing = documentAccess.children(folder).stream()
                .map(ExplorerNode::getDocRef)
                .filter(child -> DocumentationDoc.TYPE.equals(child.getType())
                                 && AgentInstructions.DOC_NAME.equals(child.getName()))
                .findFirst()
                .orElse(null);
        final String where = "folder '" + documentAccess.path(folder) + "'";
        final String base;
        if (existing == null) {
            if (!hasMarkdown) {
                return ToolResult.text(where + " has no " + AgentInstructions.DOC_NAME + " doc, so there is nothing "
                                       + "to edit: give the new doc's whole " + MARKDOWN_ARG + ".");
            }
            base = "";
        } else {
            final Optional<DocumentationDoc> doc = documentAccess.read(existing, DocumentationDoc.class);
            if (doc.isEmpty()) {
                return ToolResult.text("Error: the " + AgentInstructions.DOC_NAME + " doc in " + where
                                       + " couldn't be read.");
            }
            base = NullSafe.string(doc.get().getData());
        }
        // A new doc is as given; a change ends as the doc does (the model often drops or adds a final line break).
        final String text = hasMarkdown
                ? existing == null
                        ? given
                        : ProposeCodeChangeTool.withLineEndingOf(base, given)
                : ProposeCodeChangeTool.applyEdits(base, edits, "the " + AgentInstructions.DOC_NAME + " doc");
        if (text.length() > UserActions.MAX_CODE_CHARS) {
            throw new WorkspaceException("The doc may be at most " + UserActions.MAX_CODE_CHARS + " characters.");
        }
        final List<String> problems = InstructionRules.problems(text);
        final LineDiffResult diff = LineDiff.diff(base, text);
        if (diff.isIdentical()) {
            return ToolResult.text("The proposed text is the same as the " + AgentInstructions.DOC_NAME + " doc in "
                                   + where + " already says, so there is nothing to propose.");
        }

        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("doc", ProposeCodeChangeTool.docRefMap(existing == null
                ? folder
                : existing));
        arguments.put("request", text);
        if (existing != null) {
            arguments.put("base", base);
            arguments.put("base_hash", ProposeCodeChangeTool.sha256(base));
            arguments.put("removed", diff.getRemovedLineCount());
        }
        arguments.put("added", diff.getAddedLineCount());
        NullSafe.consume(summary, value -> arguments.put("summary", value));
        final ToolUserAction action = existing == null
                ? new ToolUserAction("create-instructions-" + SEQUENCE.incrementAndGet(),
                UserActions.CREATE_INSTRUCTIONS, arguments)
                : new ToolUserAction("apply-instructions-change-" + SEQUENCE.incrementAndGet(),
                        UserActions.APPLY_DOCUMENTATION_CHANGE, arguments);
        documentAccess.logView(context.session(), existing == null
                ? folder
                : existing, "Proposed standing instructions with the " + NAME + " tool");

        final String lines = "+" + diff.getAddedLineCount() + " −" + diff.getRemovedLineCount() + " lines";
        return ToolResult.text(
                "The user has been shown the " + (existing == null
                        ? "new " + AgentInstructions.DOC_NAME + " doc for " + where
                        : "change to the " + AgentInstructions.DOC_NAME + " doc in " + where)
                + " (" + lines + ") as a diff, with a 'Review and apply' button. Nothing changes until they apply "
                + "it: " + (existing == null
                        ? "that creates the doc and saves it (they need Create in the folder)."
                        : "that puts it in the doc's editor, unsaved, for them to save, and they can undo it.")
                + " Tell them about the button rather than saying the instructions were changed."
                + (problems.isEmpty()
                        ? ""
                        : "\nThe tools will leave these lines out, as they name nothing the tools know: "
                          + String.join("; ", problems) + ". If one is a slip rather than a note, propose the "
                          + "corrected text again (the user applies only one).")
                + "\nThe diff:\n" + diff.toUnifiedDiff(LineDiffResult.DEFAULT_CONTEXT_LINES))
                .withUserAction(action);
    }
}
