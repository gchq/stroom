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

import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;

import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/// `propose_documentation_change` - offers the user a change to the Markdown on a document's Documentation tab,
/// shown as a diff with a "Review and apply" button. It changes nothing itself: the button opens a side by side
/// review, from which the user can apply the change to the Documentation tab's editor as an unsaved edit (which
/// needs Edit on the document, checked when they apply it) and undo it. The agent never saves it: the user does.
///
/// The change is diffed against the Documentation tab's text as the user has it (unsaved edits included) when the
/// tab they attached is the document's, and otherwise against the documentation as saved. It is only applied while
/// the tab still holds that text. The tab edits the document's own `description` field, so a folder, a
/// Documentation document (whose text is its content) and a type without the field are refused.
class ProposeDocumentationChangeTool extends AbstractWorkspaceTool {

    static final String NAME = "propose_documentation_change";
    static final String UUID_ARG = "uuid";
    static final String MARKDOWN_ARG = "markdown";
    static final String EDITS_ARG = ProposeCodeChangeTool.EDITS_ARG;
    static final String SUMMARY_ARG = "summary";
    static final int MAX_SUMMARY_CHARS = 500;
    /// The types whose Documentation tab doesn't edit their `description` field, or that aren't opened as
    /// documents in the Explorer tree: a Documentation document's tab is its content, and the Data Receipt Rules
    /// have a screen of their own.
    static final Set<String> EXCLUDED_TYPES = Set.of(DocumentationDoc.TYPE, ReceiveDataRules.TYPE);
    static final String NO_DOCUMENTATION = "has no Documentation tab whose text can be proposed";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final TabSnapshots tabSnapshots;
    private final DocumentAccess documentAccess;

    @Inject
    ProposeDocumentationChangeTool(final ChatWorkspace workspace,
                                   final TabSnapshots tabSnapshots,
                                   final DocumentAccess documentAccess) {
        super(workspace);
        this.tabSnapshots = tabSnapshots;
        this.documentAccess = documentAccess;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public ToolDefinition definition() {
        final ObjectSchema edit = ObjectSchema.builder()
                .addStringProperty(ProposeCodeChangeTool.OLD_TEXT_ARG, "Text to replace, exactly as it is in the "
                                                                       + "documentation, and appearing only once "
                                                                       + "in it.")
                .addStringProperty(ProposeCodeChangeTool.NEW_TEXT_ARG, "The text to replace it with.")
                .required(ProposeCodeChangeTool.OLD_TEXT_ARG, ProposeCodeChangeTool.NEW_TEXT_ARG)
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Proposes a change to the documentation of a Stroom document given its UUID: the "
                                 + "Markdown on its Documentation tab.")
                        .chatOnly(" Use it whenever the user asks you to write, document or improve a document's "
                                  + "documentation, rather than writing the Markdown out in your answer. The user "
                                  + "is shown a diff with a 'Review and apply' button; nothing changes until they "
                                  + "apply it to the Documentation tab's editor, unsaved, for them to save. Read the "
                                  + "current documentation first (document_documentation) and the document itself, "
                                  + "so what you write is accurate.")
                        .general(" Give either edits (best for small changes) or the whole revised Markdown, "
                                 + "keeping what needn't change exactly as it is.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(UUID_ARG, "The document's UUID.")
                        .addStringProperty(MARKDOWN_ARG, "The whole proposed Markdown. Give this or " + EDITS_ARG
                                                         + ", not both.")
                        .addProperty(EDITS_ARG, ArraySchema.builder()
                                .items(edit)
                                .description("Replacements made to the current documentation, in order. Give "
                                             + "this or " + MARKDOWN_ARG + ", not both.")
                                .build())
                        .addStringProperty(SUMMARY_ARG, ToolDescription.builder()
                                .general("A sentence saying what the change does and why")
                                .chatOnly(", shown with the diff")
                                .general(".")
                                .build())
                        .required(UUID_ARG, SUMMARY_ARG)
                        .build())
                .build();
    }

    @Override
    ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String uuid = stringArg(request, UUID_ARG);
        final String given = rawArg(request, MARKDOWN_ARG);
        final Object edits = argsMap(request).get(EDITS_ARG);
        final String summary = stringArg(request, SUMMARY_ARG);
        if (uuid == null) {
            throw new WorkspaceException("Give the document's uuid.");
        }
        final boolean hasMarkdown = given != null && !given.isBlank();
        if (hasMarkdown == (edits != null)) {
            throw new WorkspaceException("Give either " + EDITS_ARG + " or the whole proposed " + MARKDOWN_ARG
                                         + ", but not both.");
        }
        if (hasMarkdown && given.length() > UserActions.MAX_CODE_CHARS) {
            throw new WorkspaceException("The documentation may be at most " + UserActions.MAX_CODE_CHARS
                                         + " characters.");
        }
        if (summary != null && summary.length() > MAX_SUMMARY_CHARS) {
            throw new WorkspaceException("The summary may be at most " + MAX_SUMMARY_CHARS + " characters.");
        }
        if (context.runContext().isEmpty()) {
            return ToolResult.text("Error: a button can only be offered while answering a question in a chat.");
        }

        final ExplorerNode node = documentAccess.find(uuid).orElse(null);
        if (node == null) {
            return ToolResult.text(DocumentAccess.NO_PERMISSION);
        }
        final DocRef docRef = node.getDocRef();
        final String described = ReadDocumentTool.describe(docRef);
        if (ExplorerConstants.FOLDER_TYPE.equals(docRef.getType()) || EXCLUDED_TYPES.contains(docRef.getType())) {
            return ToolResult.text(refusal(described));
        }
        final Object doc = documentAccess.read(docRef).orElse(null);
        if (doc == null) {
            return ToolResult.text("Error: " + described + " couldn't be read.");
        }
        if (!hasDocumentation(doc)) {
            return ToolResult.text(refusal(described));
        }

        final int chatId = context.session().requireChatId(NAME);
        final Base base = base(chatId, docRef, doc).orElse(null);
        if (base == null) {
            return ToolResult.text("The documentation of " + described + " is too large to have been shared in "
                                   + "full, so a change to it can't be proposed. Give the change as a code block "
                                   + "to copy.");
        }
        // The model often drops or adds a final line break, which would show as a change of its own.
        final String markdown = hasMarkdown
                ? ProposeCodeChangeTool.withLineEndingOf(base.text(), given)
                : ProposeCodeChangeTool.applyEdits(base.text(), edits, "the documentation");
        if (markdown.length() > UserActions.MAX_CODE_CHARS) {
            throw new WorkspaceException("The documentation may be at most " + UserActions.MAX_CODE_CHARS
                                         + " characters.");
        }
        final LineDiffResult diff = LineDiff.diff(base.text(), markdown);
        if (diff.isIdentical()) {
            return ToolResult.text("The proposed documentation is the same as the current documentation of "
                                   + described + ", so there is nothing to propose.");
        }

        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("doc", ProposeCodeChangeTool.docRefMap(docRef));
        arguments.put("request", markdown);
        arguments.put("base", base.text());
        arguments.put("base_hash", ProposeCodeChangeTool.sha256(base.text()));
        NullSafe.consume(base.snapshotHash(), hash -> arguments.put("snapshot_hash", hash));
        arguments.put("added", diff.getAddedLineCount());
        arguments.put("removed", diff.getRemovedLineCount());
        NullSafe.consume(summary, text -> arguments.put("summary", text));
        final ToolUserAction action = new ToolUserAction("apply-documentation-change-" + SEQUENCE.incrementAndGet(),
                UserActions.APPLY_DOCUMENTATION_CHANGE, arguments);
        documentAccess.logView(context.session(), docRef,
                "Proposed a change to its documentation with the " + NAME + " tool");

        final String lines = "+" + diff.getAddedLineCount() + " −" + diff.getRemovedLineCount() + " lines";
        return ToolResult.text(
                "The user has been shown the change to the documentation of " + described + " (" + lines + ", in "
                + diff.getChangedBlocks().size() + " place(s)) as a diff, with a 'Review and apply' button. It "
                + "was diffed against " + (base.fromTab()
                        ? "the Documentation tab as the user has it, unsaved edits included"
                        : "the documentation as saved")
                + ". Nothing changes until they apply it to the document's Documentation tab, unsaved; they save "
                + "it, and they can undo it. Tell them about the button rather than saying the documentation was "
                + "changed.\nThe diff:\n" + diff.toUnifiedDiff(LineDiffResult.DEFAULT_CONTEXT_LINES))
                .withUserAction(action);
    }

    /// @param doc A document, as its handler read it.
    /// @return True if it has a `description` field for its Documentation tab to edit, set or not.
    static boolean hasDocumentation(final Object doc) {
        try {
            return doc.getClass().getMethod("getDescription").getReturnType() == String.class;
        } catch (final NoSuchMethodException e) {
            return false;
        }
    }

    // The text the change is made to: the Documentation tab's as the user has it, if the tab they attached is the
    // document's and its snapshot has the text; otherwise the documentation as saved. Empty if the tab's text was
    // too large to share in full.
    private Optional<Base> base(final int chatId, final DocRef docRef, final Object doc) {
        final StoredTabSnapshot tab = tabSnapshots.forLatestQuestion(chatId).orElse(null);
        if (tab != null && tab.docRef() != null && Objects.equals(docRef.getUuid(), tab.docRef().getUuid())) {
            final UnsavedPart part = tabSnapshots.part(chatId, tab, AiTabSnapshotPart.DOCUMENTATION).orElse(null);
            if (part != null) {
                return part.cut()
                        ? Optional.empty()
                        : Optional.of(new Base(part.text(), true, tab.hash()));
            }
        }
        return Optional.of(new Base(DocumentDocumentationTool.description(doc).orElse(""), false, null));
    }

    private static String refusal(final String described) {
        return described + " " + NO_DOCUMENTATION + ". Only a document with a Documentation tab (not a folder, "
               + "and not a Documentation document, whose text is its content) can be given one. Give the text "
               + "as a code block to copy.";
    }


    // --------------------------------------------------------------------------------


    /// The text a change is proposed against.
    ///
    /// @param text         The text.
    /// @param fromTab      True if it is the Documentation tab's as the user has it, false if as saved.
    /// @param snapshotHash The hash of the tab snapshot it came from, or null.
    private record Base(String text, boolean fromTab, String snapshotHash) {

    }
}
