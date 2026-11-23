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

import stroom.ai.impl.tools.SkillCatalogue.Listed;
import stroom.ai.impl.tools.SkillDoc.Parameter;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// `find_skills` - finds the skills the user can read ([SkillCatalogue]): by words, or one by name to read it whole
/// and follow it. Read by name, a skill is checked first ([SkillChecks], its problems only: a renamed index or a
/// changed tool says so before it runs); given `arguments`, its parameters are given their values, defaults
/// applied, and any required one not given is asked of the user in a form, and its steps come back with the values
/// in (a skill run by name with the chat's `/skill:` command comes here).
class FindSkillsTool extends AbstractBuiltInTool {

    static final String NAME = SkillCatalogue.FIND_SKILLS;
    static final String WORDS_ARG = "words";
    static final String NAME_ARG = "name";
    static final String FOLDER_ARG = "folder";
    static final String ARGUMENTS_ARG = "arguments";
    /// The most skills a search lists.
    static final int MAX_FOUND = 20;
    static final String FORM_KEY = "skill-parameters";

    private final SkillCatalogue catalogue;
    private final SkillFacts facts;
    private final DocumentAccess documentAccess;

    @Inject
    FindSkillsTool(final SkillCatalogue catalogue, final SkillFacts facts, final DocumentAccess documentAccess) {
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
                .note("Only the skills docs the user may view are read; the data sources a skill's calls name are "
                      + "checked for Use, as its problems are said before it runs.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Finds the skills the user can read (saved procedures, in Documentation docs "
                                 + "marked as skills docs): by words in their names, descriptions and 'When to "
                                 + "use', or one by name to read it whole, checked, and follow its steps. Give "
                                 + ARGUMENTS_ARG + " (the values the question gives, {} for none) to run it: "
                                 + "its defaults are applied, any required parameter not given is asked of the "
                                 + "user in a form, and the steps come back with the values in.")
                        .chatOnly(" The skills you can read are listed in your instructions; two of one name "
                                  + "are told apart by folder.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(WORDS_ARG, "Words to search for, e.g. 'timeline user activity'.")
                        .addStringProperty(NAME_ARG, "A skill's name (or its /skill: command's name), to read "
                                                     + "it whole.")
                        .addStringProperty(FOLDER_ARG, "The folder of its skills doc (path or UUID), where two "
                                                       + "skills share a name.")
                        .addProperty(ARGUMENTS_ARG, ObjectSchema.builder()
                                .description("With name: the skill's parameters' values, by name, to run it.")
                                .build())
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final List<DocRef> contextFolders = context.session().chatId().isPresent()
                ? catalogue.contextFolders(context.session().chatId().getAsInt())
                : List.of();
        final String name = NullSafe.trim(stringArg(request, NAME_ARG));
        if (NullSafe.isNonBlankString(name)) {
            return byName(request, context, name, contextFolders);
        }
        return search(NullSafe.trim(stringArg(request, WORDS_ARG)), contextFolders);
    }

    private ToolResult search(final String words, final List<DocRef> contextFolders) {
        final List<Listed> readable = catalogue.readable(contextFolders);
        if (readable.isEmpty()) {
            return ToolResult.text("There are no skills you can read. A skill is saved with propose_skill.");
        }
        final Set<String> wanted = SkillChecks.words(words);
        final List<Listed> found = wanted.isEmpty()
                ? readable
                : readable.stream()
                        .filter(skill -> SkillCatalogue.score(skill, wanted, true) > 0)
                        .sorted(Comparator.comparingInt((Listed skill) -> SkillCatalogue.score(skill, wanted, true))
                                .reversed())
                        .toList();
        if (found.isEmpty()) {
            return ToolResult.text("No skill you can read matches '" + words + "' (" + readable.size() + " skill(s) "
                                   + "in all: call with no words to list them).");
        }
        final StringBuilder sb = new StringBuilder(found.size() + " skill(s)")
                .append(wanted.isEmpty()
                        ? ""
                        : " matching '" + words + "'")
                .append(", a line each: name (folder): description. Read one whole with name=.\n");
        found.stream().limit(MAX_FOUND).forEach(skill -> sb.append(SkillCatalogue.line(skill)).append('\n'));
        if (found.size() > MAX_FOUND) {
            sb.append("… ").append(found.size() - MAX_FOUND).append(" more: give more words.\n");
        }
        return ToolResult.text(sb.toString().stripTrailing());
    }

    private ToolResult byName(final ToolCall request,
                              final ToolCallContext context,
                              final String name,
                              final List<DocRef> contextFolders) {
        final String folder = NullSafe.trim(stringArg(request, FOLDER_ARG));
        final DocRef wantedFolder = NullSafe.isBlankString(folder)
                ? null
                : documentAccess.findFolder(folder).orElse(null);
        final List<Listed> named = catalogue.readable(contextFolders).stream()
                .filter(skill -> skill.skill().name().equalsIgnoreCase(name)
                                 || SkillDoc.slug(skill.skill().name()).equals(SkillDoc.slug(name)))
                .filter(skill -> wantedFolder == null || skill.folder().equals(wantedFolder))
                .toList();
        if (named.isEmpty()) {
            return ToolResult.text("No skill named '" + name + "' that you can read" + (wantedFolder == null
                    ? ""
                    : " in folder '" + folder + "'") + ". Search with " + WORDS_ARG + "=.");
        }
        final Listed skill = named.getFirst();
        documentAccess.logView(context.session(), skill.doc(), "Skill '" + skill.skill().name() + "' read by the "
                                                               + NAME + " tool");
        final SkillDoc doc = catalogue.readDoc(skill.doc()).map(SkillCatalogue.SkillsDoc::parsed).orElse(null);
        final List<SkillChecks.Finding> problems = SkillChecks.problems(SkillChecks.check(skill.skill(), doc,
                facts.forSkill(skill.skill(), skill.doc(), false)));
        final StringBuilder sb = new StringBuilder("Skill '").append(skill.skill().name()).append("' (folder ")
                .append(skill.folderPath()).append(", doc ").append(skill.doc().getUuid()).append(")");
        if (named.size() > 1) {
            sb.append("; also named so: ");
            sb.append(String.join(", ", named.subList(1, named.size()).stream()
                    .map(other -> other.folderPath())
                    .toList()));
            sb.append(" (give folder= for one of those)");
        }
        sb.append(".\n");
        if (!problems.isEmpty()) {
            sb.append("\nIt has problems: say so to the user before running it, with the fixes (check_skill "
                      + "gives them all):\n").append(SkillChecks.describe(problems)).append('\n');
        }
        final Object given = argsMap(request).get(ARGUMENTS_ARG);
        if (given == null) {
            return ToolResult.text(sb.append('\n').append(skill.skill().text()).toString());
        }
        if (!(given instanceof final Map<?, ?> values)) {
            return ToolResult.text("Error: give " + ARGUMENTS_ARG + " as an object of the parameters' values.");
        }
        SkillParameters.Bound bound = SkillParameters.bind(skill.skill(), values);
        if (!bound.missing().isEmpty()) {
            final Map<String, Object> answered = ask(request, context, skill, bound.missing());
            if (answered == null) {
                return ToolResult.text(sb.append("\nNot run: it needs ").append(names(bound.missing()))
                        .append(", which the user can't be asked in a form now. Ask them in the chat, then call "
                                + "again with ").append(ARGUMENTS_ARG).append(".").toString());
            }
            if (answered.isEmpty()) {
                return ToolResult.text(sb.append("\nNot run: the user didn't give ").append(names(bound.missing()))
                        .append(". Don't run it without them.").toString());
            }
            final Map<Object, Object> merged = new LinkedHashMap<>(values);
            merged.putAll(answered);
            bound = SkillParameters.bind(skill.skill(), merged);
        }
        sb.append("\nRun it with ").append(bound.describe()).append(":\n");
        if (!bound.unknown().isEmpty()) {
            sb.append("(Ignored, as it has no such parameters: ").append(String.join(", ", bound.unknown()))
                    .append(".)\n");
        }
        sb.append("\n## Steps\n\n").append(bound.steps()).append("\n\n## Output\n\n").append(bound.output());
        return ToolResult.text(sb.toString().stripTrailing());
    }

    // Asks the user for the required parameters not given: their answers, empty if they gave none, or null if they
    // can't be asked.
    private Map<String, Object> ask(final ToolCall request,
                                    final ToolCallContext context,
                                    final Listed skill,
                                    final List<Parameter> missing) {
        if (!context.session().isChat() || !context.userQuestions().canAsk()) {
            return null;
        }
        final Map<String, Object> properties = new LinkedHashMap<>();
        for (final Parameter parameter : missing) {
            final Map<String, Object> field = new LinkedHashMap<>();
            field.put("type", "string");
            field.put("title", parameter.name());
            if (NullSafe.isNonBlankString(parameter.description())) {
                field.put("description", parameter.description());
            }
            field.put("maxLength", SkillParameters.MAX_VALUE_CHARS);
            properties.put(parameter.name(), field);
        }
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        form.put("properties", properties);
        form.put("required", missing.stream().map(Parameter::name).toList());
        final ElicitResult answer = context.userQuestions().ask(NAME,
                UserQuestions.formCallId(request.id(), FORM_KEY),
                "To run the skill '" + skill.skill().name() + "', give:", form);
        if (answer.action() != ElicitResult.Action.ACCEPT) {
            return Map.of();
        }
        final Map<String, Object> answered = new LinkedHashMap<>();
        NullSafe.map(answer.content()).forEach((key, value) -> {
            if (value != null && !String.valueOf(value).isBlank()) {
                answered.put(key, String.valueOf(value).strip());
            }
        });
        return answered;
    }

    private static String names(final List<Parameter> parameters) {
        return String.join(", ", parameters.stream().map(Parameter::name).toList());
    }
}
