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

package stroom.ai.impl.tools.instructions;

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.instructions.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.instructions.InstructionChecks.Facts;
import stroom.ai.impl.tools.instructions.InstructionChecks.Finding;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

/// `check_instructions` - reads the AGENTS docs that apply in a folder (most general first, as `get_instructions`
/// gives them), or a draft of a folder's doc, and reports what the tools can't apply as written in the sections they
/// apply themselves, and what would work better, each with a proposed fix ([InstructionChecks]), so the agent can
/// help the user write them (asked for by the user: the tools read those sections exactly and never guess; free
/// text only guides the agent and is never asked to change).
public class CheckInstructionsTool extends AbstractBuiltInTool {

    /// The tool's name.
    public static final String NAME = "check_instructions";
    static final String FOLDER_ARG = "folder";
    static final String MARKDOWN_ARG = "markdown";

    private static final String PURPOSE = "Standing instructions checked by the " + NAME + " tool";

    private final InstructionFacts facts;
    private final DocumentAccess documentAccess;

    @Inject
    CheckInstructionsTool(final InstructionFacts facts, final DocumentAccess documentAccess) {
        this.facts = facts;
        this.documentAccess = documentAccess;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.USE)
                .note("Only the AGENTS docs the user can view are read; a doc's ## Fields lines are checked against "
                      + "the fields of the data sources the user can Use.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Checks standing instructions (" + AgentInstructions.DOC_NAME + " docs): those that "
                             + "apply in a folder, or a draft of a folder's doc. Gives problems (a line of '## "
                             + "Fields', '## Required imports', '## Component templates' or '## Documentation' the "
                             + "tools can't apply as written: an unknown key, a field no index there has, field "
                             + "lines under another heading, a near-miss heading) and suggestions (a line the "
                             + "same as a more general doc's, a field the index plan maps otherwise), each with a "
                             + "fix to propose with propose_instructions. Other text only guides you and is never "
                             + "a problem.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(FOLDER_ARG, "The folder, by UUID or Explorer path: its docs are "
                                                       + "checked, or where the draft would live.")
                        .addStringProperty(MARKDOWN_ARG, "A draft of the folder's doc to check instead.")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final String folderArg = NullSafe.trim(stringArg(request, FOLDER_ARG));
        final String markdown = rawArg(request, MARKDOWN_ARG);
        final Optional<String> folder = NullSafe.isBlankString(folderArg)
                ? Optional.empty()
                : folderPath(folderArg);
        if (NullSafe.isNonBlankString(folderArg) && folder.isEmpty()) {
            return ToolResult.text("Error: no folder '" + folderArg + "' that you can see. Give a folder's UUID or "
                                   + "Explorer path.");
        }
        if (NullSafe.isNonBlankString(markdown)) {
            final List<Finding> findings = folder.isPresent()
                    ? facts.checkDraft(context.session(), PURPOSE, folder.get(), markdown)
                    : InstructionChecks.check(null, markdown, Facts.NONE, false);
            return ToolResult.text("Draft " + AgentInstructions.DOC_NAME + " doc" + folder.map(path -> " for " + path)
                    .orElse(" (no folder given, so only its form is checked)") + ":\n" + described(markdown,
                    findings));
        }
        if (folder.isEmpty()) {
            return ToolResult.text("Error: give the folder whose docs to check, or a draft's markdown.");
        }
        return checkFolder(context, folder.get());
    }

    // Every doc that applies in the folder, against the indexes where each applies.
    private ToolResult checkFolder(final ToolCallContext context, final String folder) {
        final List<InstructionDoc> docs = facts.docs(context.session(), PURPOSE);
        final List<InstructionDoc> applying = docs.stream()
                .filter(doc -> AgentInstructions.isWithin(folder, doc.folder()))
                .toList();
        final Facts gathered = facts.gather(docs, applying.isEmpty()
                ? folder
                : applying.getFirst().folder());
        final StringBuilder sb = new StringBuilder();
        if (applying.isEmpty()) {
            sb.append("No ").append(AgentInstructions.DOC_NAME).append(" docs apply in ").append(folder).append('.');
        } else {
            sb.append(applying.size()).append(' ').append(AgentInstructions.DOC_NAME).append(" doc(s) apply in ")
                    .append(folder).append(", most general first.");
        }
        boolean own = false;
        for (final InstructionDoc doc : applying) {
            final boolean here = doc.folder().equalsIgnoreCase(folder);
            own |= here;
            final List<Finding> findings = InstructionChecks.check(doc.folder(), doc.text(), gathered, here);
            sb.append("\n\nThe doc in ").append(doc.folder()).append(" (uuid ").append(doc.docRef().getUuid())
                    .append("):\n").append(here
                            ? described(doc.text(), findings)
                            : InstructionChecks.describe(findings));
        }
        if (!own) {
            // Where the folder has no doc of its own: what one would make the site's.
            final List<Finding> findings = InstructionChecks.check(folder, "", gathered, true);
            if (!findings.isEmpty()) {
                sb.append("\n\n").append(folder).append(" has no ").append(AgentInstructions.DOC_NAME)
                        .append(" doc of its own; one could say:\n").append(InstructionChecks.describe(findings));
            }
        }
        return ToolResult.text(sb.toString());
    }

    // The findings, and the text with the problems' fixes applied where there are any.
    private static String described(final String text, final List<Finding> findings) {
        final String corrected = InstructionChecks.corrected(text, findings);
        return InstructionChecks.describe(findings) + (corrected.equals(text)
                ? ""
                : "\n\nThe doc with the problems' fixes applied (suggestions left for the user), to propose with "
                  + "propose_instructions:\n" + corrected);
    }

    // A folder's path: one the user can see, or a path not made yet (where a draft would live).
    private Optional<String> folderPath(final String folder) {
        final Optional<DocRef> found = documentAccess.findFolder(folder);
        if (found.isPresent()) {
            return Optional.of(facts.folderPath(found.get()));
        }
        final String path = DocumentAccess.withoutSystemRoot(folder);
        return path.startsWith("/")
                ? Optional.of(AgentInstructions.normalise(path))
                : Optional.empty();
    }

    @Override
    public boolean readsUntrustedContent() {
        // Written by anyone who can edit the folder.
        return true;
    }
}
