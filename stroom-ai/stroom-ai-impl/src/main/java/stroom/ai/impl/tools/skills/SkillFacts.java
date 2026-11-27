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
import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiInstructionSuggestionsConfig;
import stroom.ai.impl.BuiltInToolGroups;
import stroom.ai.impl.CurrentToolGrants;
import stroom.ai.impl.ToolGrants;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.StroomTool;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.skills.SkillChecks.DataSource;
import stroom.ai.impl.tools.skills.SkillChecks.OtherSkill;
import stroom.ai.impl.tools.skills.SkillDoc.Skill;
import stroom.ai.impl.tools.timeline.EventChains;
import stroom.ai.impl.tools.timeline.TimelineChainCheck;
import stroom.ai.impl.tools.timeline.TimelineFields;
import stroom.ai.impl.tools.timeline.TimelineReadNotes;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.docref.DocRef;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// What the skill checks ([SkillChecks]) need of Stroom, as the current user sees it: the built-in tools and the
/// groups' tools that turn them on, what the user's tool policies allow, the data sources they can Use and their
/// fields, the other skills they can read, and how skills were used in their own chats.
class SkillFacts {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SkillFacts.class);

    /// The most chats read for how skills were used, newest first.
    static final int MAX_CHATS = 200;
    private static final int MAX_FIELDS = 2_000;
    // A timeline call's source keys naming a field, by the thing it holds.
    private static final Map<String, String> SOURCE_KEYS = Map.of("host", InstructionRules.HOST_FIELD,
            "object", InstructionRules.OBJECT_FIELD, "eventTypeField", InstructionRules.EVENT_TYPE_FIELD,
            "elementField", InstructionRules.ELEMENT_FIELD);

    private final Provider<BuiltInTools> builtInToolsProvider;
    // What the user's tool policies grant; unbound where there are none (e.g. a test's wiring): every tool allowed.
    private CurrentToolGrants currentToolGrants;
    // Checks a timeline skill's Event chains against recent data; unbound in a test's wiring.
    private TimelineChainCheck timelineChainCheck;
    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final SkillCatalogue catalogue;
    private final Provider<AiService> aiServiceProvider;
    private final Provider<AiConfig> aiConfigProvider;
    private final Provider<TimelineFields> timelineFieldsProvider;

    @Inject
    SkillFacts(final Provider<BuiltInTools> builtInToolsProvider,
               final DataSourceAccess dataSourceAccess,
               final Provider<QueryService> queryServiceProvider,
               final SkillCatalogue catalogue,
               final Provider<AiService> aiServiceProvider,
               final Provider<AiConfig> aiConfigProvider,
               final Provider<TimelineFields> timelineFieldsProvider) {
        this.timelineFieldsProvider = timelineFieldsProvider;
        this.builtInToolsProvider = builtInToolsProvider;
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.catalogue = catalogue;
        this.aiServiceProvider = aiServiceProvider;
        this.aiConfigProvider = aiConfigProvider;
    }

    /// @param currentToolGrants What the user's tool policies grant.
    @com.google.inject.Inject(optional = true)
    void setCurrentToolGrants(final CurrentToolGrants currentToolGrants) {
        this.currentToolGrants = currentToolGrants;
    }

    /// @param timelineChainCheck Checks a timeline skill's Event chains against recent data.
    @com.google.inject.Inject(optional = true)
    void setTimelineChainCheck(final TimelineChainCheck timelineChainCheck) {
        this.timelineChainCheck = timelineChainCheck;
    }

    /// @param skill     The skill being checked.
    /// @param doc       Its doc, or null for a draft.
    /// @param withUsage True to read the user's chats for how skills were used (only `check_skill` does).
    /// @return The facts for checking it.
    SkillChecks.Facts forSkill(final Skill skill, final DocRef doc, final boolean withUsage) {
        return forSkill(skill, doc, withUsage, null);
    }

    /// @param skill     The skill being checked.
    /// @param doc       Its doc, or null for a draft.
    /// @param withUsage True to read the user's chats for how skills were used (only `check_skill` does).
    /// @param session   The reader's session, to check a timeline skill's Event chains against recent data; null
    ///                  not to.
    /// @return The facts for checking it.
    SkillChecks.Facts forSkill(final Skill skill, final DocRef doc, final boolean withUsage,
                               final ToolSession session) {
        final ToolGrants grants = grants();
        final List<List<AiChatMessage>> chats = withUsage
                ? recentChats()
                : List.of();
        final Map<String, SkillUsage.Counts> usage = withUsage
                ? SkillUsage.collect(chats, List.of(skill.name()))
                : Map.of();
        return new SkillChecks.Facts() {
            @Override
            public Optional<ToolDefinition> tool(final String name) {
                return SkillFacts.this.tool(name);
            }

            @Override
            public Set<String> toolNames() {
                return SkillFacts.this.toolNames();
            }

            @Override
            public boolean allowed(final String name) {
                final BuiltInTools tools = builtInToolsProvider.get();
                return name.startsWith("activate_") || tools.category(name)
                        .map(category -> grants.offers(category, tools.isReadOnly(name), tools.hasReadOnlyCalls(name)))
                        .orElse(true);
            }

            @Override
            public DataSource dataSource(final String dataSource) {
                return SkillFacts.this.dataSource(dataSource);
            }

            @Override
            public List<String> dataSourcesLike(final String name) {
                return SkillFacts.this.dataSourcesLike(name);
            }

            @Override
            public Optional<String> closestByPurpose(final Set<String> words) {
                return SkillFacts.this.closestByPurpose(words);
            }

            @Override
            public List<OtherSkill> otherSkills() {
                return catalogue.readable(List.of()).stream()
                        .map(other -> new OtherSkill(other.skill().name(), other.skill().description(),
                                other.folderPath(), doc != null && doc.getUuid().equals(other.doc().getUuid())
                                                    && other.skill().name().equalsIgnoreCase(skill.name())))
                        .filter(other -> !other.isSame())
                        .toList();
            }

            @Override
            public SkillUsage.Counts usage(final String name) {
                return usage.get(name.toLowerCase(Locale.ROOT));
            }

            @Override
            public List<String> eventChains(final List<EventChains.Entry> entries,
                                            final TimelineChainCheck.GivenFields given) {
                return timelineChainCheck == null || session == null || entries.isEmpty()
                        ? List.of()
                        : timelineChainCheck.check(session, entries, given);
            }

            @Override
            public SkillChecks.TimelineReads timelineReads(final String name,
                                                           final List<Map<String, Object>> timelineCalls) {
                final List<String> planned = session == null
                        ? List.of()
                        : planned(session, timelineCalls);
                final TimelineReadNotes.Seen seen = withUsage
                        ? TimelineReadNotes.seen(SkillUsage.timelineResults(chats, name))
                        : null;
                return new SkillChecks.TimelineReads(planned, seen);
            }
        };
    }

    // What the skill's timeline calls' sources' fields say a run would read other than the index's fields: per
    // source a note each, each source once.
    private List<String> planned(final ToolSession session, final List<Map<String, Object>> timelineCalls) {
        final List<String> notes = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        for (final Map<String, Object> call : timelineCalls) {
            for (final Object given : NullSafe.list(call.get("sources") instanceof final List<?> list
                    ? list
                    : null)) {
                final Map<?, ?> spec = given instanceof final Map<?, ?> map
                        ? map
                        : Map.of("index", String.valueOf(given));
                final String name = String.valueOf(spec.get("index"));
                if (spec.get("index") == null || name.contains("{") || name.contains("[[") || !seen.add(name)) {
                    continue;
                }
                final List<DocRef> found = dataSourceAccess.find(name);
                final DataSource dataSource = dataSource(name);
                if (found.isEmpty() || dataSource.fields() == null) {
                    continue;
                }
                final Map<String, String> fields = new LinkedHashMap<>();
                SOURCE_KEYS.forEach((key, thing) -> NullSafe.consume(spec.get(key),
                        value -> fields.put(thing, String.valueOf(value))));
                try {
                    notes.addAll(TimelineReadNotes.planned(timelineFieldsProvider.get(), session, found.getFirst(),
                            fields, !spec.containsKey("roles"), dataSource.fields()::contains));
                } catch (final RuntimeException e) {
                    LOGGER.debug(() -> "Unable to read the fields of " + name, e);
                }
            }
        }
        return notes;
    }

    private Optional<ToolDefinition> tool(final String name) {
        if (name.startsWith("activate_") && BuiltInToolGroups.groupNames().contains(name.substring(9))) {
            return Optional.of(ToolDefinition.builder().name(name).description("Turns on the group's tools.")
                    .parameters(ObjectSchema.builder().build()).build());
        }
        return builtInToolsProvider.get().tool(name).map(StroomTool::definition);
    }

    private Set<String> toolNames() {
        final Set<String> names = builtInToolsProvider.get().tools().stream()
                .map(tool -> tool.definition().name())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        BuiltInToolGroups.groupNames().forEach(group -> names.add("activate_" + group));
        return names;
    }

    private ToolGrants grants() {
        if (currentToolGrants == null) {
            return ToolGrants.all();
        }
        try {
            return currentToolGrants.forCurrentUser();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the user's tool grants", e);
            return ToolGrants.NONE;
        }
    }

    private DataSource dataSource(final String name) {
        final List<DocRef> found = dataSourceAccess.find(name);
        if (found.isEmpty()) {
            return new DataSource(false, null);
        }
        try {
            final ResultPage<QueryField> fields = queryServiceProvider.get().findFields(new FindFieldCriteria(
                    new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, found.getFirst(), null,
                    null));
            final List<QueryField> values = NullSafe.list(NullSafe.get(fields, ResultPage::getValues));
            return new DataSource(true, values.isEmpty()
                    ? null
                    : values.stream().map(QueryField::getFldName).collect(Collectors.toSet()));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the fields of " + found.getFirst(), e);
            return new DataSource(true, null);
        }
    }

    // The data sources the user can use named like one they can't.
    private List<String> dataSourcesLike(final String name) {
        try {
            return SkillChecks.likeNamed(name, dataSourceAccess.listUsable().stream()
                    .map(DocRef::getName)
                    .filter(NullSafe::isNonBlankString)
                    .distinct()
                    .toList());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to list the data sources named like " + name, e);
            return List.of();
        }
    }

    private Optional<String> closestByPurpose(final Set<String> words) {
        if (words.isEmpty()) {
            return Optional.empty();
        }
        return builtInToolsProvider.get().tools().stream()
                .map(StroomTool::definition)
                .map(definition -> Map.entry(definition.name(), shared(words, definition)))
                .filter(entry -> entry.getValue() > 1)
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey);
    }

    private static int shared(final Set<String> words, final ToolDefinition definition) {
        final Set<String> described = SkillChecks.words(definition.name().replace('_', ' ') + " "
                                                        + NullSafe.string(definition.description()));
        described.retainAll(words);
        return described.size();
    }

    // The user's own chats in the evidence window, as instruction_evidence reads them, for how skills were used.
    private List<List<AiChatMessage>> recentChats() {
        try {
            final AiInstructionSuggestionsConfig config = NullSafe.getOrElseGet(aiConfigProvider.get(),
                    AiConfig::getInstructionSuggestions, AiInstructionSuggestionsConfig::new);
            final long since = System.currentTimeMillis() - config.getEvidenceWindow().toMillis();
            final AiService aiService = aiServiceProvider.get();
            final List<List<AiChatMessage>> chats = new ArrayList<>();
            for (final AiChat chat : aiService.listChats(new FindAiChatHistoryCriteria(new PageRequest(0,
                    MAX_CHATS), null, null)).getValues()) {
                if (chat.getUpdateTimeMs() >= since) {
                    chats.add(aiService.getMessages(chat.getId()));
                }
            }
            return chats;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read how skills were used", e);
            return List.of();
        }
    }
}
