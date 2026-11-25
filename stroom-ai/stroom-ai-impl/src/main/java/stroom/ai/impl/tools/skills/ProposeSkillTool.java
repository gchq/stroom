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

import stroom.ai.api.AiService;
import stroom.ai.impl.ToolCallRecord;
import stroom.ai.impl.ToolResultRecord;
import stroom.ai.impl.UserActionRecord;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.AbstractWorkspaceTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolUserAction;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.propose.ProposeCodeChangeTool;
import stroom.ai.impl.tools.skills.SkillChecks.Finding;
import stroom.ai.impl.tools.skills.SkillDoc.HistoryEntry;
import stroom.ai.impl.tools.skills.SkillDoc.Skill;
import stroom.ai.impl.tools.skills.SkillTemplates.Template;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineDiffResult;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// `propose_skill` - writes skills (saved, parameterised procedures) with the user and proposes them for a skills
/// doc, as `propose_instructions` proposes AGENTS docs: a diff with a 'Review and apply' button, nothing changing
/// until the user applies it (to an existing doc's editor, unsaved, for them to save, refused if the doc has been
/// saved with other text since; or, for a folder with no `SKILLS` doc, by creating and saving one). The doc's front
/// matter, with the change's history entry, is part of the diff. Its actions:
///
/// - `propose` (the default): a skill (its markdown, or a draft a trial run saved) added to the doc, or put in
///   place of the one of its name, checked first ([SkillChecks]);
/// - `draft`: a draft from the chat's calls (the steps' example calls; the concrete values offered as parameters)
///   or from a description, with what only the author knows (the name, how people ask for it, which values are
///   parameters, where the results go) asked in a form;
/// - `template`: the built-in starting points ([SkillTemplates]), or one to fill;
/// - `trial`: a draft run once on given values before it is saved: the draft is kept in the workspace, checked,
///   given the values, and its steps come back for the model to follow now (the model runs a skill by following its
///   steps, so a trial is the same run the saved skill would get, its problems found in real output);
/// - `remove`: a skill taken out of the doc;
/// - `undo`: the reverse of a change applied earlier in this chat and saved, from the text kept in the chat from
///   before it, with a history entry saying so;
/// - `record`: a history entry for an edit by hand since the latest entry.
public class ProposeSkillTool extends AbstractWorkspaceTool {

    static final String NAME = "propose_skill";
    static final String ACTION_ARG = "action";
    static final String FOLDER_ARG = "folder";
    static final String DOC_ARG = "doc";
    static final String MARKDOWN_ARG = "markdown";
    static final String DRAFT_ARG = "draft";
    static final String NAME_ARG = "name";
    static final String CHANGE_ARG = "change";
    static final String PARAMETERS_ARG = "parameters";
    static final String FROM_ARG = "from";
    static final String DESCRIPTION_ARG = "description";

    static final String PROPOSE = "propose";
    static final String DRAFT = "draft";
    static final String TEMPLATE = "template";
    static final String TRIAL = "trial";
    static final String REMOVE = "remove";
    static final String UNDO = "undo";
    static final String RECORD_HAND_EDIT = "record";
    static final List<String> ACTIONS = List.of(PROPOSE, DRAFT, TEMPLATE, TRIAL, REMOVE, UNDO, RECORD_HAND_EDIT);
    static final String FROM_CHAT = "chat";
    static final String FROM_DESCRIPTION = "description";

    /// Where a trial run keeps its draft.
    static final String DRAFTS = "/outputs/skills/";
    static final int MAX_CHANGE_CHARS = 300;
    /// The most concrete values a draft from a chat offers as parameters.
    static final int MAX_CANDIDATES = 15;
    static final String DRAFT_FORM = "skill-draft";
    static final String FORM_NAME = "name";
    static final String FORM_WHEN = "when_to_use";
    static final String FORM_PARAMETERS = "parameters";
    static final String FORM_OUTPUT = "output";
    static final List<String> OUTPUTS = List.of("A table in the chat", "A timeline in the chat", "A dashboard",
            "A summary in the chat");

    // Tools whose calls are looking around, not steps of the work, left out of a draft from a chat.
    private static final Set<String> NOT_STEPS = Set.of("ask_user", "task_list", "get_instructions", "memory",
            "ls", "find_skills", "check_skill", NAME, "list_data_sources", "describe_data_source", "read_document",
            "document_info", "document_documentation", "folder_contents", "get_current_date",
            "get_current_datetime", "get_current_time", "get_utc_time_offset", "search_history", "read_turn",
            "validate_stroomql");
    private static final Set<String> NOT_PARAMETERS = Set.of("path", "out", "join", "on", "groupBy", "select",
            "fields", "by", "column", "as", "expression", "function", "condition", "field", "index", "data_source",
            "namespaces", "xpath", "jq", "timeZone");
    private static final Pattern QUOTED = Pattern.compile(
            "(?:([A-Za-z_][A-Za-z0-9_.]*)\\s*(?:=|!=|<>|>=|<=|>|<|(?i:contains|like))\\s*)?'([^']{2,80})'");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final DocumentAccess documentAccess;
    private final SkillCatalogue catalogue;
    private final SkillFacts facts;
    private final Provider<AiService> aiServiceProvider;
    private final SecurityContext securityContext;

    @Inject
    ProposeSkillTool(final ChatWorkspace workspace,
                     final DocumentAccess documentAccess,
                     final SkillCatalogue catalogue,
                     final SkillFacts facts,
                     final Provider<AiService> aiServiceProvider,
                     final SecurityContext securityContext) {
        super(workspace);
        this.documentAccess = documentAccess;
        this.catalogue = catalogue;
        this.facts = facts;
        this.aiServiceProvider = aiServiceProvider;
        this.securityContext = securityContext;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .note("Applying an existing doc's change needs Edit on it; creating a SKILLS doc needs Create in "
                      + "the folder. Both are checked before anything is proposed (a user without them is given "
                      + "the diff to pass on, no button) and again when the user applies it.")
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
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Writes a skill (a saved, parameterised procedure: a hunt, a timeline) with the "
                                 + "user and proposes it for a skills doc as a diff with a 'Review and apply' "
                                 + "button; nothing changes until they apply it (to the doc's editor, unsaved, "
                                 + "for them to save; or, for a folder with no " + SkillDoc.DOC_NAME + " doc, by "
                                 + "creating and saving one); refused, with the diff to pass on, where the user "
                                 + "can't edit the doc (or create one in the folder). The doc's history is "
                                 + "written for you. Actions: "
                                 + "draft (from this chat's calls, or a description: what only the user knows is "
                                 + "asked in a form), template (built-in starting points), trial (run a draft "
                                 + "once on given parameters before saving: follow the steps it gives back), "
                                 + "propose (default), remove, undo (the reverse of a change applied in this chat), "
                                 + "record (a hand edit in the history).")
                        .chatOnly(" A skill: '# Name', a first paragraph saying what it does, '## When to use' "
                                  + "(how people ask), '## Parameters' ('- name (required): ...' or '- name "
                                  + "(default 7): ...'), '## Steps' (numbered, {name} where a parameter goes, an "
                                  + "example call in fenced JSON {\"tool\": ..., \"arguments\": {...}}), '## "
                                  + "Output'; get_instructions guide='skills'.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addEnumProperty(ACTION_ARG, ACTIONS, "What to do (default propose).")
                        .addStringProperty(FOLDER_ARG, "The folder whose " + SkillDoc.DOC_NAME + " doc holds the "
                                                       + "skill (UUID or Explorer path).")
                        .addStringProperty(DOC_ARG, "The SKILLS doc by UUID or path, in place of folder.")
                        .addStringProperty(MARKDOWN_ARG, "The skill, from its '# Name' heading (propose, trial).")
                        .addStringProperty(DRAFT_ARG, "The draft a trial kept (the path its result gives), in "
                                                      + "place of markdown.")
                        .addStringProperty(NAME_ARG, "A skill's name (remove, record), or a template's.")
                        .addStringProperty(CHANGE_ARG, "What the change does, a line, for the doc's history and "
                                                       + "shown with the diff (propose, remove).")
                        .addProperty(PARAMETERS_ARG, ObjectSchema.builder()
                                .description("trial: the parameters' values, by name.")
                                .build())
                        .addEnumProperty(FROM_ARG, List.of(FROM_CHAT, FROM_DESCRIPTION), "draft: from this "
                                                                                         + "chat's calls "
                                                                                         + "(default), or from "
                                                                                         + "description.")
                        .addStringProperty(DESCRIPTION_ARG, "draft: what the skill does.")
                        .build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final String action = NullSafe.nonBlankStringElse(NullSafe.trim(stringArg(request, ACTION_ARG)), PROPOSE)
                .toLowerCase(Locale.ROOT);
        return switch (action) {
            case TEMPLATE -> template(request);
            case DRAFT -> draft(request, context);
            case TRIAL -> trial(request, context);
            case PROPOSE -> propose(request, context);
            case REMOVE -> remove(request, context);
            case UNDO -> undo(request, context);
            case RECORD_HAND_EDIT -> recordHandEdit(request, context);
            default -> throw new WorkspaceException("action must be one of " + String.join(", ", ACTIONS) + ".");
        };
    }

    // ------------------------------------------------------------------------------------------------
    // Templates and drafts

    private ToolResult template(final ToolCall request) {
        final String name = NullSafe.trim(stringArg(request, NAME_ARG));
        if (NullSafe.isBlankString(name)) {
            final StringBuilder sb = new StringBuilder("Built-in skill templates (starting points, not saved "
                                                       + "skills); get one with name=:\n");
            for (final Template template : SkillTemplates.all()) {
                sb.append("- ").append(template.name()).append(": ").append(template.skill().description())
                        .append('\n');
            }
            return ToolResult.text(sb.toString().stripTrailing());
        }
        final Template template = SkillTemplates.find(name).orElseThrow(() -> new WorkspaceException(
                "No template '" + name + "'. The templates are " + String.join(", ", SkillTemplates.NAMES) + "."));
        return ToolResult.text("Template '" + template.name() + "'. Fill each [[...]] placeholder with the user, "
                               + "from their own indexes (list_data_sources, describe_data_source): "
                               + String.join("; ", template.skill().placeholders()) + ". Keep {name} where a "
                               + "parameter goes. Then try it (action=trial) and propose it.\n\n" + template.text());
    }

    private ToolResult draft(final ToolCall request, final ToolCallContext context) {
        final boolean fromChat = !FROM_DESCRIPTION.equals(NullSafe.trim(stringArg(request, FROM_ARG)));
        final String description = NullSafe.trim(stringArg(request, DESCRIPTION_ARG));
        final List<ExampleStep> steps = fromChat
                ? stepsOfChat(context)
                : List.of();
        if (fromChat && steps.isEmpty()) {
            return ToolResult.text("This chat has no calls to make steps of. Draft from a description instead "
                                   + "(from=description, description=...).");
        }
        final List<Candidate> candidates = candidates(steps);
        final Map<String, Object> answers = askAuthor(request, context, candidates, fromChat);
        if (answers == null) {
            return ToolResult.text("The user can't be asked in a form now. Ask them in the chat for the skill's "
                                   + "name, how people ask for it, which values vary"
                                   + (candidates.isEmpty()
                                           ? ""
                                           : " (" + String.join(", ", candidates.stream().map(Candidate::value)
                                                   .toList()) + ")")
                                   + " and where its results go, then write it and propose it.");
        }
        if (answers.isEmpty()) {
            return ToolResult.text("The user didn't fill in the form, so nothing was drafted.");
        }
        final String draft = draftText(answers, description, steps, candidates);
        final Skill skill = SkillDoc.parse(draft).skills().getFirst();
        final List<Finding> findings = SkillChecks.check(skill, null, facts.forSkill(skill, null, false));
        return ToolResult.text("A draft, from " + (fromChat
                ? "this chat's calls"
                : "the description") + " and the user's answers. Replace each [[...]] with what it says (the "
                               + "description, and each step in plain words; draft steps from the tools and the "
                               + "user's indexes, with describe_data_source), then try it (action=trial) and "
                               + "propose it.\nIts checks so far:\n" + SkillChecks.describe(findings) + "\n\n"
                               + draft);
    }

    // The steps a draft from the chat is made of: its calls of tools that do the work, in order, without repeats.
    private List<ExampleStep> stepsOfChat(final ToolCallContext context) {
        final int chatId = context.session().requireChatId(NAME);
        final List<ExampleStep> steps = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        for (final AiChatMessage message : aiServiceProvider.get().getMessages(chatId)) {
            if (message.getMessageType() != AiMessageType.TOOL_CALL) {
                continue;
            }
            try {
                final ToolCallRecord call = JsonUtil.readValue(message.getMessage(), ToolCallRecord.class);
                if (call.tool() == null || NOT_STEPS.contains(call.tool()) || call.tool().startsWith("activate_")
                    || !seen.add(call.tool() + call.arguments())) {
                    continue;
                }
                final Map<?, ?> arguments = NullSafe.isBlankString(call.arguments())
                        ? Map.of()
                        : JsonUtil.readValue(call.arguments(), Map.class);
                steps.add(new ExampleStep(call.tool(), arguments));
            } catch (final RuntimeException e) {
                // A call that can't be read isn't a step.
            }
        }
        return steps;
    }

    // The concrete values of the steps' calls that could vary from run to run.
    static List<Candidate> candidates(final List<ExampleStep> steps) {
        final Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (final ExampleStep step : steps) {
            collect(step.arguments(), null, candidates);
        }
        return candidates.values().stream().limit(MAX_CANDIDATES).toList();
    }

    private static void collect(final Object value, final String key, final Map<String, Candidate> candidates) {
        if (value instanceof final Map<?, ?> map) {
            final Object field = map.get("field");
            map.forEach((k, v) -> collect(v, "value".equals(k) && field instanceof final String name
                    ? name
                    : String.valueOf(k), candidates));
        } else if (value instanceof final List<?> list) {
            list.forEach(item -> collect(item, key, candidates));
        } else if (value instanceof final String text && key != null && !NOT_PARAMETERS.contains(key)) {
            final Matcher quoted = QUOTED.matcher(text);
            boolean anyQuoted = false;
            while (quoted.find()) {
                // A query's literal is named after the field it is compared with, where it is.
                anyQuoted = true;
                candidates.putIfAbsent(quoted.group(2), new Candidate(quoted.group(2), parameterName(
                        NullSafe.nonBlankStringElse(quoted.group(1), key))));
            }
            if (!anyQuoted && !"query".equals(key) && text.length() <= 80 && !text.startsWith("/")
                && !text.isBlank()) {
                candidates.putIfAbsent(text, new Candidate(text, parameterName(key)));
            }
        } else if (value instanceof final Number number && key != null && !NOT_PARAMETERS.contains(key)) {
            candidates.putIfAbsent(String.valueOf(number), new Candidate(String.valueOf(number), parameterName(key)));
        }
    }

    private static String parameterName(final String key) {
        final String name = key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return name.isEmpty() || !Character.isLetter(name.charAt(0))
                ? "value"
                : name;
    }

    // What only the author knows, asked in a form: the answers; empty if not given; null if they can't be asked.
    private Map<String, Object> askAuthor(final ToolCall request,
                                          final ToolCallContext context,
                                          final List<Candidate> candidates,
                                          final boolean fromChat) {
        if (!context.session().isChat() || !context.userQuestions().canAsk()) {
            return null;
        }
        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(FORM_NAME, Map.of("type", "string", "title", "The skill's name", "maxLength", 100));
        properties.put(FORM_WHEN, Map.of("type", "string", "title", "How people ask for it",
                "description", "In their words, one way per line, e.g. 'build a timeline of jsmith's activity'",
                "maxLength", 1_000));
        if (fromChat && !candidates.isEmpty()) {
            final List<String> options = candidates.stream().map(Candidate::label).toList();
            properties.put(FORM_PARAMETERS, Map.of("type", "array", "title", "Which of these vary from run to run "
                                                                             + "(made parameters)?",
                    "items", Map.of("type", "string", "enum", options)));
        } else {
            properties.put(FORM_PARAMETERS, Map.of("type", "string", "title", "What varies from run to run",
                    "description", "One parameter per line: its name, and what it is, e.g. 'user - the account'",
                    "maxLength", 1_000));
        }
        properties.put(FORM_OUTPUT, Map.of("type", "string", "title", "Where its results go", "enum", OUTPUTS,
                "default", OUTPUTS.getFirst()));
        final Map<String, Object> form = new LinkedHashMap<>();
        form.put("type", "object");
        form.put("properties", properties);
        form.put("required", List.of(FORM_NAME, FORM_WHEN));
        final ElicitResult answer = context.userQuestions().ask(NAME, UserQuestions.formCallId(request.id(),
                DRAFT_FORM), "A few things only you know about the skill to save", form);
        return answer.action() == ElicitResult.Action.ACCEPT
                ? new LinkedHashMap<>(NullSafe.map(answer.content()))
                : Map.of();
    }

    // The draft: the author's answers, the steps' calls with the values chosen made parameters.
    static String draftText(final Map<String, Object> answers,
                            final String description,
                            final List<ExampleStep> steps,
                            final List<Candidate> candidates) {
        final String name = NullSafe.nonBlankStringElse(string(answers.get(FORM_NAME)), "New skill")
                .replace('\n', ' ').strip();
        final StringBuilder sb = new StringBuilder("# ").append(name).append("\n\n")
                .append(NullSafe.nonBlankStringElse(description, "[[what it does, in a sentence or two]]"))
                .append("\n\n## When to use\n\n");
        for (final String line : NullSafe.string(string(answers.get(FORM_WHEN))).split("\n")) {
            if (!line.isBlank()) {
                sb.append("- \"").append(line.strip().replaceAll("^[-*]\\s*|^\"|\"$", "")).append("\"\n");
            }
        }
        final Map<String, String> chosen = new LinkedHashMap<>();
        final Object picked = answers.get(FORM_PARAMETERS);
        sb.append("\n## Parameters\n\n");
        if (picked instanceof final List<?> labels) {
            final Set<String> names = new LinkedHashSet<>();
            for (final Candidate candidate : candidates) {
                if (labels.contains(candidate.label())) {
                    String parameter = candidate.parameter();
                    for (int i = 2; !names.add(parameter); i++) {
                        parameter = candidate.parameter() + "_" + i;
                    }
                    chosen.put(candidate.value(), parameter);
                    sb.append("- ").append(parameter).append(" (required): [[what it is]] (e.g. ")
                            .append(candidate.value()).append(").\n");
                }
            }
        } else {
            for (final String line : NullSafe.string(string(picked)).split("\n")) {
                final String[] parts = line.strip().replaceAll("^[-*]\\s*", "").split("\\s*[-:]\\s*", 2);
                if (!parts[0].isBlank()) {
                    sb.append("- ").append(parameterName(parts[0])).append(" (required): ")
                            .append(parts.length > 1
                                    ? parts[1]
                                    : "[[what it is]]").append('\n');
                }
            }
        }
        sb.append("\n## Steps\n\n");
        if (steps.isEmpty()) {
            sb.append("1. [[the first step, with an example call]]\n");
        }
        int number = 1;
        for (final ExampleStep step : steps) {
            final Map<String, Object> call = new LinkedHashMap<>();
            call.put("tool", step.tool());
            call.put("arguments", withParameters(step.arguments(), chosen));
            sb.append(number++).append(". [[what this step does]]\n   ```json\n   ")
                    .append(JsonUtil.writeValueAsString(call, false)).append("\n   ```\n");
        }
        sb.append("\n## Output\n\n").append(NullSafe.nonBlankStringElse(string(answers.get(FORM_OUTPUT)),
                OUTPUTS.getFirst())).append(".\n");
        return sb.toString();
    }

    private static Object withParameters(final Object value, final Map<String, String> chosen) {
        if (value instanceof final Map<?, ?> map) {
            final Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), withParameters(v, chosen)));
            return result;
        } else if (value instanceof final List<?> list) {
            return list.stream().map(item -> withParameters(item, chosen)).toList();
        } else if (value instanceof String || value instanceof Number) {
            final String text = String.valueOf(value);
            if (chosen.containsKey(text)) {
                return "{" + chosen.get(text) + "}";
            }
            String replaced = text;
            for (final Map.Entry<String, String> entry : chosen.entrySet()) {
                replaced = replaced.replace("'" + entry.getKey() + "'", "'{" + entry.getValue() + "}'");
            }
            return value instanceof String
                    ? replaced
                    : value;
        }
        return value;
    }

    // ------------------------------------------------------------------------------------------------
    // A trial run

    private ToolResult trial(final ToolCall request, final ToolCallContext context) {
        final String markdown = skillMarkdown(request, context);
        final Skill skill = oneSkill(markdown);
        final SessionWorkspace workspace = sessionWorkspace(context);
        final String draftPath = DRAFTS + SkillDoc.slug(skill.name()) + ".md";
        final WorkspacePath path = WorkspacePath.parse(draftPath);
        workspace.write(path, markdown, null);
        final List<Finding> findings = SkillChecks.check(skill, null, facts.forSkill(skill, null, false));
        final List<Finding> problems = SkillChecks.problems(findings);
        final Object given = argsMap(request).get(PARAMETERS_ARG);
        final SkillParameters.Bound bound = SkillParameters.bind(skill, given instanceof final Map<?, ?> map
                ? map
                : Map.of());
        if (!bound.missing().isEmpty()) {
            return ToolResult.text("Draft kept at " + draftPath + ". Not run: give " + PARAMETERS_ARG + " for "
                                   + String.join(", ", bound.missing().stream().map(SkillDoc.Parameter::name)
                                           .toList()) + " (ask the user if the question doesn't say).");
        }
        final StringBuilder sb = new StringBuilder("Trial run of the draft skill '").append(skill.name())
                .append("' (not saved; kept at ").append(draftPath).append(") with ").append(bound.describe())
                .append(".\n");
        if (!problems.isEmpty()) {
            sb.append("Its problems, which may stop the run:\n").append(SkillChecks.describe(problems))
                    .append('\n');
        }
        sb.append("Follow these steps now, as the saved skill would be followed, then show the user the result "
                  + "and ask what to change; propose it with ").append(DRAFT_ARG).append('=').append(draftPath)
                .append(" (or the corrected markdown).\n\n## Steps\n\n").append(bound.steps())
                .append("\n\n## Output\n\n").append(bound.output());
        return ToolResult.text(sb.toString().stripTrailing());
    }

    // ------------------------------------------------------------------------------------------------
    // Proposals

    private ToolResult propose(final ToolCall request, final ToolCallContext context) {
        final String markdown = skillMarkdown(request, context);
        final Skill skill = oneSkill(markdown);
        final Target target = target(request);
        final String change = change(request, target.existing() == null
                ? "Created with '" + skill.name() + "'"
                : target.parsed().skill(skill.name()).isPresent()
                        ? "Changed"
                        : "Added");
        final String text = target.parsed().withSkill(markdown, entry(target.parsed(), skill.name(), change,
                SkillDoc.HOW_AGENT, currentUser()));
        final List<Finding> findings = SkillChecks.check(skill, null, facts.forSkill(skill, target.existing(),
                false));
        return offer(target, text, change, context, "the skill '" + skill.name() + "'", findings);
    }

    private ToolResult remove(final ToolCall request, final ToolCallContext context) {
        final String name = NullSafe.trim(stringArg(request, NAME_ARG));
        if (NullSafe.isBlankString(name)) {
            throw new WorkspaceException("Give the name of the skill to remove.");
        }
        final Target target = target(request);
        if (target.existing() == null || target.parsed().skill(name).isEmpty()) {
            throw new WorkspaceException("There is no skill '" + name + "' in " + target.where() + ".");
        }
        final String change = change(request, "Removed");
        final String text = target.parsed().withoutSkill(name, entry(target.parsed(), name, change,
                SkillDoc.HOW_AGENT, currentUser()));
        return offer(target, text, change, context, "removing the skill '" + name + "'", List.of());
    }

    private ToolResult recordHandEdit(final ToolCall request, final ToolCallContext context) {
        final Target target = target(request);
        if (target.existing() == null) {
            throw new WorkspaceException(target.where() + " has no skills doc.");
        }
        if (!target.parsed().isEditedSinceLatestEntry()) {
            return ToolResult.text("The doc hasn't been changed by hand since its latest history entry, so there "
                                   + "is nothing to record.");
        }
        final String author = documentAccess.read(target.existing(), DocumentationDoc.class)
                .map(DocumentationDoc::getUpdateUser)
                .orElse(currentUser());
        final String skill = NullSafe.nonBlankStringElse(NullSafe.trim(stringArg(request, NAME_ARG)), "(any)");
        final String text = target.parsed().withBody(target.parsed().body(), entry(target.parsed(), skill,
                "Edited by hand", SkillDoc.HOW_USER, author));
        return offer(target, text, "Recorded an edit by hand", context, "recording an edit by hand", List.of());
    }

    // The reverse of the latest change proposed in this chat that the user applied (and saved since).
    private ToolResult undo(final ToolCall request, final ToolCallContext context) {
        final int chatId = context.session().requireChatId(NAME);
        final Applied applied = latestApplied(chatId)
                .orElseThrow(() -> new WorkspaceException("No change to a skills doc proposed in this chat has "
                                                          + "been applied, so there is nothing to undo."));
        final DocRef doc = applied.doc();
        if (doc == null) {
            throw new WorkspaceException("The skills doc the change was applied to can't be found.");
        }
        final String savedRaw = documentAccess.read(doc, DocumentationDoc.class)
                .map(DocumentationDoc::getData)
                .map(NullSafe::string)
                .orElseThrow(() -> new WorkspaceException("The skills doc can't be read."));
        final String saved = SkillDoc.parse(savedRaw).text();
        final String after = SkillDoc.parse(applied.after()).text();
        if (saved.equals(SkillDoc.parse(applied.before()).text())) {
            return ToolResult.text("The change was applied but not saved, so there is nothing saved to undo: the "
                                   + "user can close the doc without saving, or use the button's Undo apply.");
        }
        if (!saved.equals(after)) {
            return ToolResult.text("The doc has been saved with other text since the change was applied, so its "
                                   + "reverse can't be worked out. Propose the change wanted instead.");
        }
        final SkillDoc current = SkillDoc.parse(saved);
        final int version = current.latestEntry().map(HistoryEntry::version).orElse(0);
        final String skill = current.latestEntry().map(HistoryEntry::skill).orElse("(any)");
        final String change = "Undid version " + version;
        final String text = current.withBody(SkillDoc.parse(applied.before()).body(), entry(current, skill,
                change, SkillDoc.HOW_AGENT, currentUser()));
        final Target target = new Target(doc, null, current, "the skills doc '" + doc.getName() + "' in folder '"
                                                              + documentAccess.path(doc) + "'", savedRaw);
        return offer(target, text, change, context, "undoing version " + version, List.of());
    }

    private Optional<Applied> latestApplied(final int chatId) {
        for (final AiChatMessage message : aiServiceProvider.get().getMessages(chatId).reversed()) {
            if (message.getMessageType() != AiMessageType.TOOL_RESULT) {
                continue;
            }
            final ToolResultRecord result;
            try {
                result = JsonUtil.readValue(message.getMessage(), ToolResultRecord.class);
            } catch (final RuntimeException e) {
                continue;
            }
            if (!NAME.equals(result.tool())) {
                continue;
            }
            for (final UserActionRecord action : NullSafe.list(result.userActions())) {
                if (action.status() != UserActionRecord.Status.DONE) {
                    continue;
                }
                if (UserActions.APPLY_DOCUMENTATION_CHANGE.equals(action.action())) {
                    return Optional.of(new Applied(action.doc(), NullSafe.string(NullSafe.get(action.codeChange(),
                            UserActionRecord.CodeChange::baseCode)), action.request()));
                }
                if (UserActions.CREATE_INSTRUCTIONS.equals(action.action()) && action.doc() != null) {
                    return skillsDocIn(action.doc()).map(created -> new Applied(created, "", action.request()));
                }
            }
        }
        return Optional.empty();
    }

    // Offers the user the change, as a diff with a button: applied to the doc's editor, or creating the doc.
    private ToolResult offer(final Target target,
                             final String text,
                             final String change,
                             final ToolCallContext context,
                             final String what,
                             final List<Finding> findings) {
        if (context.runContext().isEmpty()) {
            return ToolResult.text("Error: a button can only be offered while answering a question in a chat.");
        }
        if (text.length() > UserActions.MAX_CODE_CHARS) {
            throw new WorkspaceException("The doc may be at most " + UserActions.MAX_CODE_CHARS + " characters.");
        }
        final String base = target.existing() == null
                ? ""
                : target.base();
        final LineDiffResult diff = LineDiff.diff(base, text);
        if (diff.isIdentical()) {
            return ToolResult.text("The proposed text is the same as " + target.where() + " has already.");
        }
        final Optional<String> refusal = refusal(target);
        if (refusal.isPresent()) {
            // A button the user can't use is no proposal: the change goes, as text, to someone who can apply it.
            return ToolResult.text("Not proposed: " + refusal.get() + " Tell the user they can't change it, and "
                                   + "give them " + what + " (the diff below) to pass on; don't propose it again."
                                   + "\nThe diff:\n" + diff.toUnifiedDiff(LineDiffResult.DEFAULT_CONTEXT_LINES));
        }
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("doc", ProposeCodeChangeTool.docRefMap(target.existing() == null
                ? target.folder()
                : target.existing()));
        arguments.put("request", text);
        if (target.existing() != null) {
            arguments.put("base", base);
            arguments.put("base_hash", ProposeCodeChangeTool.sha256(base));
            arguments.put("removed", diff.getRemovedLineCount());
        } else {
            arguments.put("name", SkillDoc.DOC_NAME);
        }
        arguments.put("added", diff.getAddedLineCount());
        arguments.put("summary", change);
        final ToolUserAction action = target.existing() == null
                ? new ToolUserAction("create-skills-" + SEQUENCE.incrementAndGet(), UserActions.CREATE_INSTRUCTIONS,
                        arguments)
                : new ToolUserAction("apply-skills-change-" + SEQUENCE.incrementAndGet(),
                        UserActions.APPLY_DOCUMENTATION_CHANGE, arguments);
        documentAccess.logView(context.session(), target.existing() == null
                ? target.folder()
                : target.existing(), "Proposed a skill change with the " + NAME + " tool");
        final List<Finding> problems = SkillChecks.problems(findings);
        final String lines = "+" + diff.getAddedLineCount() + " −" + diff.getRemovedLineCount() + " lines";
        return ToolResult.text("The user has been shown " + what + (target.existing() == null
                        ? " as a new " + SkillDoc.DOC_NAME + " doc for " + target.where()
                        : " in " + target.where()) + " (" + lines + ") as a diff, with the history entry, and a "
                               + "'Review and apply' button. Nothing changes until they apply it: "
                               + (target.existing() == null
                        ? "that creates the doc and saves it (they need Create in the folder)."
                        : "that puts it in the doc's editor, unsaved, for them to save (it is refused if the doc "
                          + "has been saved with other text since).")
                               + " Tell them about the button rather than saying the skill was saved."
                               + (problems.isEmpty()
                        ? ""
                        : "\nIts problems (fix them and propose it again; the user applies only one):\n"
                          + SkillChecks.describe(problems))
                               + "\nThe diff:\n" + diff.toUnifiedDiff(LineDiffResult.DEFAULT_CONTEXT_LINES))
                .withUserAction(action);
    }

    // Why the user couldn't apply a change to the target, if they couldn't: no Edit on its doc, or no Create in its
    // folder for a new one.
    private Optional<String> refusal(final Target target) {
        if (target.existing() != null) {
            return documentAccess.canEdit(target.existing())
                    ? Optional.empty()
                    : Optional.of("you can't edit " + target.where() + " (applying a change needs Edit on it), so "
                                  + "only someone with Edit on it (its owner, or an administrator) can make it.");
        }
        return documentAccess.canCreate(target.folder(), DocumentationDoc.TYPE)
                ? Optional.empty()
                : Optional.of("you can't create a Documentation doc in " + target.where() + ", so only someone who "
                              + "can (the folder's owner, or an administrator) can create its " + SkillDoc.DOC_NAME
                              + " doc.");
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers

    // The skills doc a proposal is for: the doc given, or the SKILLS doc in the folder given (none yet if absent).
    private Target target(final ToolCall request) {
        final String docArg = NullSafe.trim(stringArg(request, DOC_ARG));
        if (NullSafe.isNonBlankString(docArg)) {
            final DocRef doc = (docArg.startsWith("/")
                    ? documentAccess.findByPath(docArg, DocumentationDoc.TYPE).map(ExplorerNode::getDocRef)
                    : documentAccess.findDocRef(docArg))
                    .filter(found -> DocumentationDoc.TYPE.equals(found.getType()))
                    .orElseThrow(() -> new WorkspaceException("No Documentation doc '" + docArg + "' that you can "
                                                              + "see."));
            if (!SkillCatalogue.isSkillsDocName(doc.getName())) {
                // Skills are found by their doc's name, so one in another doc would never be found.
                throw new WorkspaceException("'" + doc.getName() + "' isn't a skills doc: only a Documentation doc "
                                             + "named " + SkillDoc.DOC_NAME + " is (rename it " + SkillDoc.DOC_NAME
                                             + "), or give its folder to use the folder's SKILLS doc.");
            }
            return existingTarget(doc);
        }
        final String folderArg = NullSafe.trim(stringArg(request, FOLDER_ARG));
        if (NullSafe.isBlankString(folderArg)) {
            throw new WorkspaceException("Give the folder (or the skills doc) the skill is for.");
        }
        final DocRef folder = documentAccess.findFolder(folderArg)
                .filter(found -> ExplorerConstants.FOLDER_TYPE.equals(found.getType())
                                 || ExplorerConstants.SYSTEM_DOC_REF.equals(found))
                .orElseThrow(() -> new WorkspaceException("No folder '" + folderArg + "' that you can see. Give a "
                                                          + "folder's UUID or Explorer path."));
        final Optional<DocRef> existing = skillsDocIn(folder);
        if (existing.isPresent()) {
            return existingTarget(existing.get());
        }
        return new Target(null, folder, SkillDoc.parse(""), "folder '" + documentAccess.folderPath(folder) + "'", "");
    }

    private Target existingTarget(final DocRef doc) {
        final DocumentationDoc read = documentAccess.read(doc, DocumentationDoc.class)
                .orElseThrow(() -> new WorkspaceException("The skills doc couldn't be read."));
        final DocRef named = doc.copy().name(read.getName()).build();
        final DocRef folder = documentAccess.parent(named).orElse(ExplorerConstants.SYSTEM_DOC_REF);
        return new Target(named, folder, SkillDoc.parse(read.getData()), "the skills doc '" + read.getName()
                                                                         + "' in folder '"
                                                                         + documentAccess.path(named) + "'",
                NullSafe.string(read.getData()));
    }

    private Optional<DocRef> skillsDocIn(final DocRef folder) {
        return documentAccess.children(folder).stream()
                .map(ExplorerNode::getDocRef)
                .filter(child -> DocumentationDoc.TYPE.equals(child.getType())
                                 && SkillDoc.DOC_NAME.equalsIgnoreCase(child.getName()))
                .findFirst();
    }

    // The skill's markdown: given, or the draft a trial kept.
    private String skillMarkdown(final ToolCall request, final ToolCallContext context) {
        final String markdown = rawArg(request, MARKDOWN_ARG);
        if (NullSafe.isNonBlankString(markdown)) {
            return markdown;
        }
        final String draft = NullSafe.trim(stringArg(request, DRAFT_ARG));
        if (NullSafe.isBlankString(draft)) {
            throw new WorkspaceException("Give the skill's " + MARKDOWN_ARG + ", or the " + DRAFT_ARG + " a trial "
                                         + "kept.");
        }
        try {
            return Files.readString(sessionWorkspace(context).localFile(WorkspacePath.parse(draft)),
                    StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new WorkspaceException("The draft " + draft + " can't be read: " + e.getMessage());
        }
    }

    private static Skill oneSkill(final String markdown) {
        final List<Skill> skills = SkillDoc.parse(markdown).skills();
        if (skills.size() != 1) {
            throw new WorkspaceException(skills.isEmpty()
                    ? "Give the skill from its '# Name' heading."
                    : "Give one skill at a time (a '# Name' heading each): this has " + skills.size() + ".");
        }
        return skills.getFirst();
    }

    private String change(final ToolCall request, final String otherwise) {
        final String change = NullSafe.nonBlankStringElse(NullSafe.trim(stringArg(request, CHANGE_ARG)), otherwise);
        if (change.length() > MAX_CHANGE_CHARS) {
            throw new WorkspaceException("The change may be at most " + MAX_CHANGE_CHARS + " characters.");
        }
        return change;
    }

    private static HistoryEntry entry(final SkillDoc doc,
                                      final String skill,
                                      final String change,
                                      final String how,
                                      final String author) {
        return new HistoryEntry(doc.nextVersion(), LocalDate.now(ZoneOffset.UTC).toString(), author, how, skill,
                change, null);
    }

    private String currentUser() {
        try {
            return NullSafe.getOrElse(securityContext.getUserRef(), UserRef::toDisplayString, "unknown");
        } catch (final RuntimeException e) {
            return "unknown";
        }
    }

    private static String string(final Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }


    // --------------------------------------------------------------------------------


    /// A call of the chat's that does the work, as a step of a draft.
    ///
    /// @param tool      The tool.
    /// @param arguments Its arguments.
    record ExampleStep(String tool, Map<?, ?> arguments) {

    }

    /// A concrete value of a call, offered as a parameter.
    ///
    /// @param value     The value.
    /// @param parameter The parameter's name it would have.
    record Candidate(String value, String parameter) {

        String label() {
            return value + " (" + parameter + ")";
        }
    }

    // The doc a proposal is for: existing (and read, its text as saved the base), or none yet in the folder.
    private record Target(DocRef existing, DocRef folder, SkillDoc parsed, String where, String base) {

    }

    // A change applied in the chat: its doc, and its text before and after.
    private record Applied(DocRef doc, String before, String after) {

    }
}
