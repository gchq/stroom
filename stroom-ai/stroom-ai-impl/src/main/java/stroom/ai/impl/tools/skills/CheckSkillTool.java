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

package stroom.ai.impl.tools.skills;

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.skills.SkillCatalogue.Listed;
import stroom.ai.impl.tools.skills.SkillChecks.Finding;
import stroom.ai.impl.tools.skills.SkillDoc.Skill;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/// `check_skill` - reads a skill (saved, or a draft) and reports its problems (it won't work as written) and
/// suggestions (it would work better changed), each with a proposed fix ([SkillChecks]): its structure, its
/// parameters, its example calls against the tools' schemas and the user's tool policies, the data sources and
/// fields they name, a hand edit since its doc's latest history entry; vague steps, values that should be
/// parameters, a chart the chat doesn't draw, a thin "When to use", a near-duplicate; and, from the user's own
/// chats, a skill often offered and never chosen or asked for by name and not offered.
public class CheckSkillTool extends AbstractBuiltInTool {

    static final String NAME = "check_skill";
    static final String NAME_ARG = "name";
    static final String FOLDER_ARG = "folder";
    static final String MARKDOWN_ARG = "markdown";
    static final String DOC_ARG = "doc";

    private final SkillCatalogue catalogue;
    private final SkillFacts facts;
    private final DocumentAccess documentAccess;

    @Inject
    CheckSkillTool(final SkillCatalogue catalogue, final SkillFacts facts, final DocumentAccess documentAccess) {
        this.catalogue = catalogue;
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
                .note("Data sources are checked for Use, as the reader would run the skill.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Checks a skill, saved (by name) or a draft (its markdown), and gives its problems "
                             + "and suggestions, each with a proposed fix: structure (description, steps, "
                             + "parameters used and listed), example calls against the tools' parameters and "
                             + "your tool policies, the data sources and fields they name, a hand edit since the "
                             + "doc's latest history entry; vague steps, values that should be parameters, a chart "
                             + "the chat doesn't draw, a thin 'When to use', near-duplicates, how your chats "
                             + "used it, and where a timeline skill reads the Events XML or traces streams for "
                             + "what its indexes don't map (from its sources' fields and its recent runs).")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(NAME_ARG, "A saved skill's name.")
                        .addStringProperty(FOLDER_ARG, "The folder of its skills doc, where two share a name; "
                                                       + "with markdown, the folder the draft is for.")
                        .addStringProperty(DOC_ARG, "A Documentation doc (UUID or path) to check every skill of.")
                        .addStringProperty(MARKDOWN_ARG, "A draft skill to check instead, from its '# Name' "
                                                         + "heading.")
                        .build())
                .build();
    }

    // Every skill of a doc, checked; a doc not named SKILLS isn't a skills doc, whatever its front matter says.
    private ToolResult checkDoc(final ToolCallContext context, final String docArg) {
        final DocRef doc = (docArg.startsWith("/")
                ? documentAccess.findByPath(docArg, DocumentationDoc.TYPE).map(ExplorerNode::getDocRef)
                : documentAccess.findDocRef(docArg))
                .filter(found -> DocumentationDoc.TYPE.equals(found.getType()))
                .orElse(null);
        if (doc == null) {
            return ToolResult.text("Error: no Documentation doc '" + docArg + "' that you can see.");
        }
        if (!SkillCatalogue.isSkillsDocName(doc.getName())) {
            return ToolResult.text("'" + doc.getName() + "' isn't a skills doc: only a Documentation doc named "
                                   + SkillDoc.DOC_NAME + " is, whatever its front matter says, so its skills are "
                                   + "never found. Fix: rename it " + SkillDoc.DOC_NAME + " (one a folder; move "
                                   + "its skills into the folder's SKILLS doc if it has one).");
        }
        final SkillCatalogue.SkillsDoc read = catalogue.readDoc(doc).orElse(null);
        if (read == null) {
            return ToolResult.text("Error: the skills doc '" + docArg + "' can't be read.");
        }
        documentAccess.logView(context.session(), read.doc(), "Skills checked by the " + NAME + " tool");
        final StringBuilder sb = new StringBuilder("Skills doc in folder ").append(read.folderPath()).append(":\n");
        for (final Skill skill : read.parsed().skills()) {
            sb.append("\nSkill '").append(skill.name()).append("':\n").append(SkillChecks.describe(
                    SkillChecks.check(skill, read.parsed(), facts.forSkill(skill, read.doc(), true,
                            context.session())))).append('\n');
        }
        if (read.parsed().skills().isEmpty()) {
            sb.append("It has no skills (a '# Name' heading each).");
        }
        return ToolResult.text(sb.toString().stripTrailing());
    }

    // The skills the user can read of a name (or its slug), in the folder given if one is found.
    private List<Listed> named(final ToolCall request, final ToolCallContext context, final String name) {
        final String folder = NullSafe.trim(stringArg(request, FOLDER_ARG));
        final DocRef wantedFolder = NullSafe.isBlankString(folder)
                ? null
                : documentAccess.findFolder(folder).orElse(null);
        final List<DocRef> contextFolders = context.session().chatId().isPresent()
                ? catalogue.contextFolders(context.session().chatId().getAsInt())
                : List.of();
        return catalogue.readable(contextFolders).stream()
                .filter(skill -> skill.skill().name().equalsIgnoreCase(name)
                                 || SkillDoc.slug(skill.skill().name()).equals(SkillDoc.slug(name)))
                .filter(skill -> wantedFolder == null || skill.folder().equals(wantedFolder))
                .toList();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final String markdown = rawArg(request, MARKDOWN_ARG);
        if (NullSafe.isNonBlankString(markdown)) {
            final List<Skill> skills = SkillDoc.parse(markdown).skills();
            if (skills.size() != 1) {
                return ToolResult.text("Error: give one skill, from its '# Name' heading.");
            }
            final Skill skill = skills.getFirst();
            // A draft of a saved skill is checked as of the doc being edited, so its saved version there isn't a
            // near-duplicate of it.
            final List<DocRef> docs = named(request, context, skill.name()).stream()
                    .map(Listed::doc)
                    .distinct()
                    .toList();
            final DocRef edited = docs.size() == 1
                    ? docs.getFirst()
                    : null;
            final List<Finding> findings = SkillChecks.check(skill, null, facts.forSkill(skill, edited, false,
                    context.session()));
            return ToolResult.text("Draft skill '" + skill.name() + "':\n" + SkillChecks.describe(findings));
        }
        final String docArg = NullSafe.trim(stringArg(request, DOC_ARG));
        if (NullSafe.isNonBlankString(docArg)) {
            return checkDoc(context, docArg);
        }
        final String name = NullSafe.trim(stringArg(request, NAME_ARG));
        if (NullSafe.isBlankString(name)) {
            return ToolResult.text("Error: give a saved skill's name, a doc, or a draft's markdown.");
        }
        final Listed listed = named(request, context, name).stream().findFirst().orElse(null);
        if (listed == null) {
            return ToolResult.text("No skill named '" + name + "' that you can read. Find it with "
                                   + FindSkillsTool.NAME + ".");
        }
        documentAccess.logView(context.session(), listed.doc(), "Skill '" + listed.skill().name()
                                                                + "' checked by the " + NAME + " tool");
        final SkillDoc doc = catalogue.readDoc(listed.doc()).map(SkillCatalogue.SkillsDoc::parsed).orElse(null);
        final List<Finding> findings = new ArrayList<>();
        if (doc != null && doc.frontMatterProblem() != null) {
            findings.add(Finding.problem("front_matter", "Its doc's history can't be read: "
                                                         + doc.frontMatterProblem() + ".",
                    "Propose any change to it with propose_skill, which writes the front matter afresh."));
        }
        findings.addAll(SkillChecks.check(listed.skill(), doc, facts.forSkill(listed.skill(), listed.doc(),
                true, context.session())));
        return ToolResult.text("Skill '" + listed.skill().name() + "' (folder " + listed.folderPath() + "):\n"
                               + SkillChecks.describe(List.copyOf(findings)));
    }
}
