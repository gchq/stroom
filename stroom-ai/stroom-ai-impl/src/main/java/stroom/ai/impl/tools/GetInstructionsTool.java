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

import stroom.ai.impl.tools.AgentInstructions.DocTarget;
import stroom.ai.impl.tools.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.AgentInstructions.Standing;
import stroom.ai.impl.tools.AgentInstructions.Targets;
import stroom.ai.impl.tools.AgentInstructions.UnreadableDoc;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `get_instructions` - the standing instructions (AGENTS docs) that apply to some folders, feeds or documents,
/// most general first, with their text. They may say how XSLT is written, how fields are mapped, how things are
/// named, or which folders to leave out. Read as the user. The result's structured content is JSON
/// ([AgentInstructions#applicableInstructions]).
///
/// It also gives the working guides bundled with Stroom ([AgentKnowledge]), which MCP clients can read as
/// resources too, so that the chat can read them.
class GetInstructionsTool extends AbstractBuiltInTool {

    static final String NAME = "get_instructions";
    static final String FOLDERS_ARG = "folders";
    static final String UUIDS_ARG = "uuids";
    static final String FEEDS_ARG = "feeds";
    static final String DOCS_ARG = "docs";
    static final String GUIDE_ARG = "guide";
    /// The guide name that lists the guides.
    static final String GUIDES = "guides";

    private final AgentInstructions agentInstructions;
    private final Optional<AgentKnowledge> knowledge;

    @Inject
    GetInstructionsTool(final AgentInstructions agentInstructions, final Optional<AgentKnowledge> knowledge) {
        this.agentInstructions = agentInstructions;
        this.knowledge = knowledge;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Gives the standing instructions (Documentation docs named " + AgentInstructions.DOC_NAME
                             + ") that apply to some folders, feeds or documents: a doc applies to its folder and "
                             + "below, and one at the top of the tree everywhere. Most general first, with their "
                             + "text. They can say how XSLT is written, how fields are mapped, how things are "
                             + "named, or which folders searches leave out. Call it before proposing code or names "
                             + "for documents in a folder, and follow what it says, the most specific last; the "
                             + "user's own request takes precedence, and no instruction lifts an approval. Other "
                             + AgentInstructions.DOC_NAME + " docs are listed by folder so you know they exist. "
                             + "Give guide to read one of the working guides bundled with Stroom instead (how to "
                             + "write event-logging XSLT, a Data Splitter, reference data, an index, and so on).")
                .parameters(ObjectSchema.builder()
                        .addProperty(FOLDERS_ARG, stringArraySchema("Folders the work touches, by UUID or Explorer "
                                                                      + "path, e.g. '/Feeds/Events/Acme' (where the "
                                                                      + "feed or pipeline lives, or where it will be "
                                                                      + "promoted to)."))
                        .addProperty(UUIDS_ARG, stringArraySchema("Documents the work touches, by UUID, e.g. an "
                                                                    + "XSLT or a pipeline; their folders are used."))
                        .addProperty(FEEDS_ARG, stringArraySchema("Feed names the work touches; their folders are "
                                                                    + "used."))
                        .addProperty(DOCS_ARG, ArraySchema.builder()
                                .items(ObjectSchema.builder()
                                        .addStringProperty("type", "The document's type, e.g. 'Pipeline'.")
                                        .addStringProperty("name", "The document's name.")
                                        .addStringProperty("uuid", "Optional. The document's UUID.")
                                        .build())
                                .description("Documents the work touches, each {type, name, uuid?}, e.g. a "
                                             + "pipeline being updated.")
                                .build())
                        .addStringProperty(GUIDE_ARG, "Optional. A working guide to read instead, by name, e.g. "
                                                      + "'xslt', 'event-logging', 'indexing'; '" + GUIDES
                                                      + "' lists them.")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final String guide = stringArg(request, GUIDE_ARG);
        if (guide != null) {
            return ToolResult.text(guide(guide));
        }
        final Targets targets = agentInstructions.targets(stringListArg(request, FOLDERS_ARG),
                stringListArg(request, UUIDS_ARG), stringListArg(request, FEEDS_ARG), docTargets(request));
        final List<String> paths = targets.paths();

        final Standing standing = agentInstructions.load(context.session(), "Standing instructions read by the "
                                                                            + NAME + " tool");
        final List<InstructionDoc> applying = standing.applyingTo(paths);
        final StringBuilder sb = new StringBuilder();
        if (applying.isEmpty()) {
            sb.append("No ").append(AgentInstructions.DOC_NAME).append(" docs apply there. Users can add one: a "
                                                                       + "Documentation doc named ")
                    .append(AgentInstructions.DOC_NAME).append(" in a folder applies to that folder and below.");
        } else {
            sb.append(applying.size()).append(" ").append(AgentInstructions.DOC_NAME).append(" doc(s) apply.")
                    .append(standing.note(context.links(), paths));
        }
        final List<String> elsewhere = standing.docs().stream()
                .filter(doc -> !applying.contains(doc))
                .map(InstructionDoc::folder)
                .toList();
        if (!elsewhere.isEmpty()) {
            sb.append("\n\nOther ").append(AgentInstructions.DOC_NAME).append(" docs, which don't apply there: in ")
                    .append(String.join(", ", elsewhere)).append('.');
        }
        if (!standing.unreadable().isEmpty()) {
            sb.append("\n\nThese ").append(AgentInstructions.DOC_NAME).append(" docs couldn't be read, so they "
                                                                             + "aren't followed: ");
            final List<String> unreadable = new ArrayList<>();
            for (final UnreadableDoc doc : standing.unreadable()) {
                unreadable.add("the doc in " + doc.folder() + " (uuid " + doc.docRef().getUuid() + "): "
                               + doc.reason());
            }
            sb.append(String.join("; ", unreadable)).append('.');
        }
        if (!targets.notFound().isEmpty()) {
            sb.append("\n\nNot found, or you don't have permission to view them: ")
                    .append(String.join(", ", targets.notFound())).append('.');
        }
        if (!applying.isEmpty()) {
            sb.append("\n\n").append(AgentInstructions.HINT_APPLYING);
        }
        return ToolResult.text(sb.toString().strip())
                .withStructuredContent(standing.asJson(paths));
    }

    // A guide's text, or the list of them.
    private String guide(final String name) {
        if (knowledge.isEmpty() || knowledge.get().guideNames().isEmpty()) {
            return "Error: no working guides are bundled with this Stroom.";
        }
        final AgentKnowledge guides = knowledge.get();
        if (GUIDES.equalsIgnoreCase(name)) {
            final StringBuilder sb = new StringBuilder("# Guides\n");
            for (final String guide : guides.guideNames()) {
                sb.append("\n- ").append(guide).append(": ").append(guides.guideTitle(guide).orElse(""));
            }
            return sb.append("\n\nRead one with ").append(NAME).append(' ').append(GUIDE_ARG)
                    .append("='<name>'.").toString();
        }
        return guides.guide(name)
                .orElseGet(() -> "Error: No guide '" + name + "'. Guides: " + String.join(", ", guides.guideNames()));
    }

    // The documents the work touches, each {type, name, uuid?}; one given alone, not in a list, is taken too.
    private static List<DocTarget> docTargets(final ToolCall request) {
        final Object value = argsMap(request).get(DOCS_ARG);
        final List<?> items = value instanceof final List<?> list
                ? list
                : NullSafe.singletonList(value);
        final List<DocTarget> docs = new ArrayList<>();
        for (final Object item : items) {
            if (item instanceof final Map<?, ?> map) {
                docs.add(new DocTarget(string(map.get("type")), string(map.get("name")), string(map.get("uuid"))));
            }
        }
        return docs;
    }

    private static String string(final Object value) {
        return value == null
                ? null
                : String.valueOf(value).strip();
    }

    @Override
    public boolean readsUntrustedContent() {
        // Written by anyone who can edit the folder.
        return true;
    }
}
