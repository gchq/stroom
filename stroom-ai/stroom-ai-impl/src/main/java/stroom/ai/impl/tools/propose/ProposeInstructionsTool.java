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

package stroom.ai.impl.tools.propose;

import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.AbstractWorkspaceTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolUserAction;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.instructions.CheckInstructionsTool;
import stroom.ai.impl.tools.instructions.InstructionChecks;
import stroom.ai.impl.tools.instructions.InstructionChecks.Facts;
import stroom.ai.impl.tools.instructions.InstructionChecks.Finding;
import stroom.ai.impl.tools.instructions.InstructionDraft;
import stroom.ai.impl.tools.instructions.InstructionDraft.Draft;
import stroom.ai.impl.tools.instructions.InstructionDraft.Plan;
import stroom.ai.impl.tools.instructions.InstructionDraft.Question;
import stroom.ai.impl.tools.instructions.InstructionEvidenceTool;
import stroom.ai.impl.tools.instructions.InstructionFacts;
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

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;

import java.util.ArrayList;
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
/// The proposed doc is checked first, as `check_instructions` checks a draft ([InstructionChecks]): a line of the
/// sections the tools apply themselves (`## Fields`, `## Required imports` and so on) they can't apply as written,
/// and what would work better, each with a fix, so the agent can correct the proposal before the user applies it.
///
/// `action=draft` drafts the folder's `## Fields` lines instead ([InstructionDraft], asked for by the user, as
/// propose_skill drafts a skill): for each key, the candidates of each index there from facts (what the author
/// said, the docs in force, the index plans, the field conventions); a form asks only what the author alone knows
/// (an index whose candidates differ, a key asked for with none found), or, where no form can be shown, the agent is
/// given the questions to ask in the chat. The draft (the folder's doc with the lines) is given back to propose.
public class ProposeInstructionsTool extends AbstractWorkspaceTool {

    static final String NAME = "propose_instructions";
    static final String FOLDER_ARG = "folder";
    static final String MARKDOWN_ARG = "markdown";
    static final String EDITS_ARG = ProposeCodeChangeTool.EDITS_ARG;
    static final String SUMMARY_ARG = "summary";
    static final String ACTION_ARG = "action";
    static final String FIELDS_ARG = "fields";
    static final String KEYS_ARG = "keys";
    static final String PROPOSE = "propose";
    static final String DRAFT = "draft";
    static final String DRAFT_FORM = "instructions-draft";
    static final int MAX_SUMMARY_CHARS = 500;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final DocumentAccess documentAccess;
    // The docs and indexes a doc is checked against and drafted from; unbound in a test's wiring.
    private InstructionFacts instructionFacts;

    @Inject
    ProposeInstructionsTool(final ChatWorkspace workspace, final DocumentAccess documentAccess) {
        super(workspace);
        this.documentAccess = documentAccess;
    }

    /// @param instructionFacts Gives the docs and the indexes (their fields and plans) a doc applies to.
    @com.google.inject.Inject(optional = true)
    void setInstructionFacts(final InstructionFacts instructionFacts) {
        this.instructionFacts = instructionFacts;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.USE)
                .note("Applying an existing doc's change needs Edit on it; creating a doc needs Create in the "
                      + "folder. Both are checked when the user applies it. A doc's ## Fields lines are checked "
                      + "against the fields of the data sources in the folder the user can Use.")
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
                                 + "imports', '## Component templates', '## Documentation' and '## Fields'.")
                        .chatOnly(" Draft rules from instruction_evidence's repeated choices or from what the "
                                  + "user said they always want, and read get_instructions for the folder first "
                                  + "(its guide='agent-instructions' says how the sections are written). The "
                                  + "proposal is checked as " + CheckInstructionsTool.NAME + " checks it. "
                                  + "action=draft drafts the folder's '## Fields' lines from the indexes there and "
                                  + "what the user said (fields), asking only what they alone know; then propose "
                                  + "the draft.")
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
                        .addStringProperty(SUMMARY_ARG, "propose (required): a sentence saying what the change "
                                                        + "does and why, citing the chats it comes from; shown "
                                                        + "with the diff.")
                        .addEnumProperty(ACTION_ARG, List.of(PROPOSE, DRAFT), "propose (the default) or draft.")
                        .addProperty(FIELDS_ARG, stringArraySchema("draft: what the user said holds "
                                                                              + "each thing, a 'key: field' each, "
                                                                              + "e.g. 'user: actor.login'."))
                        .addProperty(KEYS_ARG, stringArraySchema("draft: keys to give a line even if "
                                                                            + "nothing is found, e.g. 'host'."))
                        .required(FOLDER_ARG)
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String actionArg = NullSafe.nonBlankStringElse(NullSafe.trim(stringArg(request, ACTION_ARG)), PROPOSE);
        if (DRAFT.equals(actionArg)) {
            return draft(request, context);
        }
        if (!PROPOSE.equals(actionArg)) {
            throw new WorkspaceException("The action is " + PROPOSE + " or " + DRAFT + ".");
        }
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
        if (NullSafe.isBlankString(summary)) {
            throw new WorkspaceException("Give the " + SUMMARY_ARG + ": what the change does and why.");
        }
        if (summary.length() > MAX_SUMMARY_CHARS) {
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
        final String where = "folder '" + documentAccess.folderPath(folder) + "'";
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
        final String folderPath = documentAccess.folderPath(folder);
        final List<Finding> findings = instructionFacts == null
                ? InstructionChecks.check(folderPath, text, Facts.NONE, false)
                : instructionFacts.checkDraft(context.session(), "Standing instructions read to check a proposal "
                                                                 + "of the " + NAME + " tool", folderPath, text);
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
                + checked(text, findings)
                + "\nThe diff:\n" + diff.toUnifiedDiff(LineDiffResult.DEFAULT_CONTEXT_LINES))
                .withUserAction(action);
    }

    // The proposal's findings, for the agent to correct it before the user applies it.
    private static String checked(final String text, final List<Finding> findings) {
        if (findings.isEmpty()) {
            return "";
        }
        final String corrected = InstructionChecks.corrected(text, findings);
        return "\nIts checks (as " + CheckInstructionsTool.NAME + " gives them; if a problem is a slip, propose the "
               + "corrected text again: the user applies only one; ask the user before following a suggestion):\n"
               + InstructionChecks.describe(findings) + (corrected.equals(text)
                ? ""
                : "\nThe text with the problems' fixes applied:\n" + corrected);
    }

    // ------------------------------------------------------------------------------------------------
    // Drafts

    private ToolResult draft(final ToolCall request, final ToolCallContext context) {
        final String folderArg = stringArg(request, FOLDER_ARG);
        if (folderArg == null) {
            throw new WorkspaceException("Give the folder.");
        }
        if (instructionFacts == null) {
            return ToolResult.text("Error: drafts can't be made here.");
        }
        final DocRef folder = documentAccess.findFolder(folderArg).orElse(null);
        if (folder == null || !ExplorerConstants.FOLDER_TYPE.equals(folder.getType())) {
            return ToolResult.text("Error: no folder '" + folderArg + "' that you can see. Give a folder's UUID or "
                                   + "Explorer path.");
        }
        final Map<String, List<String>> stated;
        final List<String> requested = new ArrayList<>();
        try {
            stated = InstructionDraft.stated(stringListArg(request, FIELDS_ARG));
            for (final String key : NullSafe.list(stringListArg(request, KEYS_ARG))) {
                final String known = InstructionDraft.key(key);
                if (known == null) {
                    throw new IllegalArgumentException("'" + key + "' is no key the tools know.");
                }
                requested.add(known);
            }
        } catch (final IllegalArgumentException e) {
            return ToolResult.text("Error: " + e.getMessage());
        }
        final String folderPath = documentAccess.folderPath(folder);
        final List<InstructionDoc> docs = instructionFacts.docs(context.session(), "Standing instructions read to "
                                                                                    + "draft with the " + NAME
                                                                                    + " tool");
        final InstructionDoc own = docs.stream()
                .filter(doc -> doc.folder().equalsIgnoreCase(folderPath))
                .findFirst()
                .orElse(null);
        final Facts facts = instructionFacts.gather(docs, folderPath);
        final Plan plan = InstructionDraft.plan(folderPath, stated, requested, facts);
        final Map<String, Object> answers;
        if (plan.questions().isEmpty()) {
            answers = Map.of();
        } else if (!context.session().isChat() || !context.userQuestions().canAsk()) {
            return ToolResult.text("The user can't be asked in a form now. Ask them in the chat, then draft again "
                                   + "with " + FIELDS_ARG + " ('key: field' each) from their answers:\n"
                                   + String.join("\n", plan.questions().stream().map(question -> "- "
                                   + question.text() + (question.candidates().isEmpty()
                    ? ""
                    : " Found: " + question.found())).toList()));
        } else {
            answers = askAuthor(request, context, plan);
            if (answers.isEmpty()) {
                return ToolResult.text("The user didn't fill in the form, so nothing was drafted.");
            }
        }
        final Draft draft = InstructionDraft.draft(plan, answers, own == null
                ? ""
                : own.text());
        if (draft.lines().isEmpty()) {
            return ToolResult.text("Nothing to draft for " + folderPath + ": no index there has a field for a key "
                                   + "that no doc in force gives already. Give " + FIELDS_ARG + " from what the "
                                   + "user said, or " + KEYS_ARG + " to ask them.");
        }
        final List<InstructionDoc> others = docs.stream().filter(doc -> doc != own).toList();
        final List<Finding> findings = InstructionChecks.check(folderPath, draft.text(), new Facts(others,
                facts.indexes()), true);
        return ToolResult.text("A draft of the " + AgentInstructions.DOC_NAME + " doc in " + folderPath + " ("
                               + (own == null
                ? "a new doc"
                : "its doc with the lines changed") + "): " + String.join("; ", draft.lines().values())
                               + "." + (draft.notes().isEmpty()
                ? ""
                : " " + String.join(" ", draft.notes())) + "\nIts checks:\n" + InstructionChecks.describe(findings)
                               + "\n\nPropose it with " + NAME + " folder='" + folderPath + "' and this as "
                               + MARKDOWN_ARG + " (add any other rules the user gave as text), with a summary:\n\n"
                               + draft.text());
    }

    // What only the author knows, asked in a form: the answers, or empty if not given.
    private Map<String, Object> askAuthor(final ToolCall request, final ToolCallContext context, final Plan plan) {
        final Map<String, Object> properties = new LinkedHashMap<>();
        for (final Question question : plan.questions()) {
            final Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", "string");
            property.put("title", question.text());
            if (question.candidates().isEmpty()) {
                property.put("maxLength", 200);
            } else {
                final List<String> options = new ArrayList<>(question.candidates().stream()
                        .map(InstructionDraft.Candidate::field).toList());
                options.add(InstructionDraft.LEAVE_OUT);
                property.put("enum", options);
                property.put("description", "Found: " + question.found());
            }
            properties.put(question.id(), property);
        }
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        form.put("properties", properties);
        final ElicitResult answer = context.userQuestions().ask(NAME, UserQuestions.formCallId(request.id(),
                DRAFT_FORM), "Which fields hold these in " + plan.folder() + "? Only what the indexes and docs "
                             + "there don't settle is asked.", form);
        return answer.action() == ElicitResult.Action.ACCEPT
                ? new LinkedHashMap<>(NullSafe.map(answer.content()))
                : Map.of();
    }
}
