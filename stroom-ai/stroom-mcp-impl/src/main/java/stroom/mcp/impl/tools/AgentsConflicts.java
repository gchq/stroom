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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.instructions.AgentInstructions.Standing;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.instructions.InstructionRules.ComponentRule;
import stroom.ai.impl.tools.instructions.InstructionRules.ComponentRules;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldMappingLayout;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.ai.impl.tools.instructions.InstructionRules.RequiredImport;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// The rules the tools apply themselves from AGENTS docs (`## Fields`, `## Required imports`, `## Component
/// templates`, `## Documentation`) that differ between the folders of a build's feeds (decided by the user,
/// 2026-11-25: asked, a conflict at a time; the answer recorded in the build and used). Before, the docs on every
/// feed's path were merged in order, and the most specific one's rule taken without a word.
///
/// A rule conflicts where two or more feeds' docs give it and their values differ; a rule only some feeds' docs give
/// is taken as it is, as before.
class AgentsConflicts {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AgentsConflicts.class);

    /// A conflict's key prefix for a `## Fields` line.
    static final String FIELDS = "fields: ";
    /// A conflict's key prefix for a stage's `## Required imports`.
    static final String IMPORTS = "required imports: ";
    /// A conflict's key for `## Component templates`.
    static final String COMPONENTS = "component templates";
    /// A conflict's key for `## Documentation`'s field mapping layout.
    static final String DOCUMENTATION = "documentation: field mapping";

    private final AgentInstructions agentInstructions;

    /// @param agentInstructions The AGENTS docs.
    @Inject
    AgentsConflicts(final AgentInstructions agentInstructions) {
        this.agentInstructions = agentInstructions;
    }

    /// @param session The session, for the AGENTS docs' audit.
    /// @param feeds   A build's feeds.
    /// @return The AGENTS docs that apply on each feed's path, most general first, by feed; none for a feed that
    /// can't be found, or when the docs can't be read.
    Map<String, List<InstructionDoc>> applyingByFeed(final ToolSession session, final List<String> feeds) {
        final Map<String, List<InstructionDoc>> out = new LinkedHashMap<>();
        try {
            final Standing standing = agentInstructions.load(session, "Standing instructions read to compare the "
                                                                      + "rules for a build's feeds");
            for (final String feed : feeds) {
                out.put(feed, standing.applyingTo(agentInstructions.targets(List.of(), List.of(), List.of(feed),
                        List.of()).paths()));
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The AGENTS docs for feeds {} weren't read: {}", feeds,
                    e.getMessage()), e);
        }
        return out;
    }

    /// @param byFeed The AGENTS docs that apply on each feed's path, most general first ([#applyingByFeed]).
    /// @return Each rule whose value differs between two or more feeds' docs, in a stable order.
    static List<Conflict> conflicts(final Map<String, List<InstructionDoc>> byFeed) {
        final Map<String, Map<String, Ruled>> rules = new LinkedHashMap<>();
        byFeed.forEach((feed, applying) -> {
            InstructionRules.fields(applying).forEach((key, rule) -> put(rules, FIELDS + key, feed,
                    new Ruled(String.join(", ", rule.fields()), rule.from())));
            InstructionRules.requiredImports(applying).forEach((stage, imports) -> put(rules, IMPORTS + stage, feed,
                    new Ruled(String.join(", ", imports.stream().map(RequiredImport::href).toList()),
                            imports.getFirst().from())));
            final ComponentRules components = InstructionRules.componentRules(applying);
            if (!components.include().isEmpty() || !components.exclude().isEmpty()) {
                put(rules, COMPONENTS, feed, new Ruled(componentsSaid(components), componentsFrom(components)));
            }
            InstructionRules.fieldMappingLayout(applying).ifPresent(layout -> put(rules, DOCUMENTATION, feed,
                    new Ruled(layout.layout().replace('_', ' '), layout.from())));
        });
        final List<Conflict> out = new ArrayList<>();
        rules.forEach((key, values) -> {
            final Set<String> distinct = new LinkedHashSet<>();
            values.values().forEach(ruled -> distinct.add(ruled.value()));
            if (values.size() > 1 && distinct.size() > 1) {
                out.add(new Conflict(key, Map.copyOf(values)));
            }
        });
        out.sort((a, b) -> a.key().compareTo(b.key()));
        return out;
    }

    /// The required imports for a stage of a build's XSLTs, as the user chose where the feeds' AGENTS docs
    /// conflict.
    ///
    /// @param chosen The user's answers kept in the build ([BuildRecords#agentsChoices]): each conflict's key and
    ///               the chosen `value`.
    /// @param stage  A stage, e.g. [InstructionRules#TRANSLATION].
    /// @param merged The imports the docs on every feed's path require, merged as before.
    /// @return The imports chosen for the stage, each from the chosen doc's folder; `merged` where nothing was
    /// chosen.
    static List<RequiredImport> importsChosen(final Map<String, Map<String, Object>> chosen,
                                              final String stage,
                                              final List<RequiredImport> merged) {
        final Map<String, Object> choice = NullSafe.map(chosen).get(IMPORTS + stage);
        if (choice == null || choice.get("value") == null) {
            return merged;
        }
        final String from = String.valueOf(NullSafe.map(choice).getOrDefault("folder", ""));
        final List<RequiredImport> out = new ArrayList<>();
        for (final String href : String.valueOf(choice.get("value")).split(",")) {
            if (!href.isBlank()) {
                out.add(new RequiredImport(href.strip(), from));
            }
        }
        return out;
    }

    /// @param chosen The user's answers kept in the build.
    /// @return The field mapping layout chosen, where the feeds' docs conflict; empty otherwise.
    static Optional<FieldMappingLayout> layoutChosen(final Map<String, Map<String, Object>> chosen) {
        final Map<String, Object> choice = NullSafe.map(chosen).get(DOCUMENTATION);
        if (choice == null || choice.get("value") == null) {
            return Optional.empty();
        }
        return Optional.of(new FieldMappingLayout(String.valueOf(choice.get("value")).replace(' ', '_'),
                String.valueOf(choice.getOrDefault("folder", ""))));
    }

    /// @param chosen The user's answers kept in the build.
    /// @return The `## Fields` lines chosen where the feeds' docs conflict, by field key; none otherwise.
    static Map<String, FieldRule> fieldsChosen(final Map<String, Map<String, Object>> chosen) {
        final Map<String, FieldRule> out = new LinkedHashMap<>();
        NullSafe.map(chosen).forEach((key, choice) -> {
            if (key.startsWith(FIELDS) && choice.get("value") != null) {
                final List<String> fields = new ArrayList<>();
                for (final String field : String.valueOf(choice.get("value")).split(",")) {
                    if (!field.isBlank()) {
                        fields.add(field.strip());
                    }
                }
                out.put(key.substring(FIELDS.length()), new FieldRule(fields,
                        String.valueOf(choice.getOrDefault("folder", ""))));
            }
        });
        return out;
    }

    /// @param chosen The user's answers kept in the build.
    /// @return The component template rules chosen where the feeds' docs conflict, as the chosen doc gives them
    /// (`include a, b; exclude c`); empty otherwise.
    static Optional<ComponentRules> componentsChosen(final Map<String, Map<String, Object>> chosen) {
        final Map<String, Object> choice = NullSafe.map(chosen).get(COMPONENTS);
        if (choice == null || choice.get("value") == null) {
            return Optional.empty();
        }
        final String from = String.valueOf(choice.getOrDefault("folder", ""));
        final List<ComponentRule> include = new ArrayList<>();
        final List<ComponentRule> exclude = new ArrayList<>();
        for (final String part : String.valueOf(choice.get("value")).split(";")) {
            final String rule = part.strip();
            final boolean including = rule.startsWith("include ");
            if (!including && !rule.startsWith("exclude ")) {
                continue;
            }
            for (final String name : rule.substring(rule.indexOf(' ') + 1).split(",")) {
                if (!name.isBlank()) {
                    (including
                            ? include
                            : exclude).add(new ComponentRule(name.strip(), from));
                }
            }
        }
        return Optional.of(new ComponentRules(List.copyOf(include), List.copyOf(exclude)));
    }

    private static void put(final Map<String, Map<String, Ruled>> rules,
                            final String key,
                            final String feed,
                            final Ruled ruled) {
        rules.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(feed, ruled);
    }

    private static String componentsSaid(final ComponentRules rules) {
        final List<String> parts = new ArrayList<>();
        if (!rules.include().isEmpty()) {
            parts.add("include " + String.join(", ", new TreeSet<>(rules.include().stream()
                    .map(ComponentRule::name)
                    .toList())));
        }
        if (!rules.exclude().isEmpty()) {
            parts.add("exclude " + String.join(", ", new TreeSet<>(rules.exclude().stream()
                    .map(ComponentRule::name)
                    .toList())));
        }
        return String.join("; ", parts);
    }

    private static String componentsFrom(final ComponentRules rules) {
        return rules.include().isEmpty()
                ? rules.exclude().getLast().from()
                : rules.include().getLast().from();
    }


    // --------------------------------------------------------------------------------


    /// A rule as one feed's AGENTS docs give it.
    ///
    /// @param value  Its value, in words (`Initiator.Id, user.name`; `compact`).
    /// @param folder The folder of the doc it is from.
    record Ruled(String value, String folder) {

    }


    // --------------------------------------------------------------------------------


    /// A rule the feeds' AGENTS docs give differently.
    ///
    /// @param key    The rule, e.g. `fields: user` or `required imports: translation`.
    /// @param byFeed Each feed's value and the folder of the doc it is from.
    record Conflict(String key, Map<String, Ruled> byFeed) {

        /// @return The values to choose between, each once with the feeds and folder it is from, in feed order.
        Map<String, Ruled> options(final List<String> feedOrder) {
            final Map<String, Ruled> out = new LinkedHashMap<>();
            final Map<String, List<String>> feedsOf = new LinkedHashMap<>();
            for (final String feed : feedOrder) {
                final Ruled ruled = byFeed.get(feed);
                if (ruled != null) {
                    feedsOf.computeIfAbsent(ruled.value(), k -> new ArrayList<>()).add(feed);
                    out.putIfAbsent(ruled.value(), ruled);
                }
            }
            final Map<String, Ruled> labelled = new LinkedHashMap<>();
            out.forEach((value, ruled) -> labelled.put(value + " (the AGENTS doc in " + NullSafe.nonBlankStringElse(
                    ruled.folder(), "/") + ", for " + String.join(", ", feedsOf.get(value)) + ")", ruled));
            return labelled;
        }

        /// @return The question asked of it, short (no Markdown in forms).
        String question() {
            return "The AGENTS docs of these feeds' folders differ on " + key + ": which applies to the documents "
                   + "they share?";
        }
    }
}
