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

package stroom.ai.impl;


import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Offers a large set of tools as groups: the model is given one `activate_<group>` tool per group, with what
/// the group is for and its tools' names, and once it calls one, the group's tools are offered from the next
/// request. This keeps tool definitions the model does not need out of its context.
///
/// A built-in tool's group is the one [BuiltInToolGroups] puts it in, if any. An MCP tool's group is, in order:
/// the group its server gives it, as `_meta.group`; the start of its name up to the first `_`, if other tools on
/// its server share it; or else its server.
final class ToolGroups {

    static final String ACTIVATE_PREFIX = "activate_";
    private static final int MAX_LISTED_NAMES = 15;
    private static final String NAME_SEPARATOR = "_";

    private final Map<String, Map<ToolSpecification, ToolExecutor>> groups;
    private final Map<String, String> purposes;
    private final Map<ToolSpecification, ToolExecutor> builtInsAlwaysOffered;
    private final Map<ToolSpecification, ToolExecutor> mcpToolsAlwaysOffered;

    private ToolGroups(final Map<String, Map<ToolSpecification, ToolExecutor>> groups,
                       final Map<String, String> purposes,
                       final Map<ToolSpecification, ToolExecutor> builtInsAlwaysOffered,
                       final Map<ToolSpecification, ToolExecutor> mcpToolsAlwaysOffered) {
        this.groups = groups;
        this.purposes = purposes;
        this.builtInsAlwaysOffered = builtInsAlwaysOffered;
        this.mcpToolsAlwaysOffered = mcpToolsAlwaysOffered;
    }

    /// Works out the groups of a set of MCP tools.
    ///
    /// @param mcpTools  The MCP tools, in the order to offer them.
    /// @param toolset   The toolset they come from, for their servers and the groups declared for them.
    /// @param threshold The tools are only grouped if there are more than this many; 0 never groups them.
    /// @return The groups, which are empty if the tools are not grouped.
    static ToolGroups of(final Map<ToolSpecification, ToolExecutor> mcpTools,
                         final McpToolset toolset,
                         final int threshold) {
        return of(Map.of(), mcpTools, toolset, threshold);
    }

    /// Works out the groups of the built-in tools and of a set of MCP tools. Each set is grouped on its own, only
    /// if it has more than `threshold` tools.
    ///
    /// @param builtIns  The built-in tools offered, in the order to offer them.
    /// @param mcpTools  The MCP tools, in the order to offer them.
    /// @param toolset   The toolset the MCP tools come from, for their servers and the groups declared for them.
    /// @param threshold A set of tools is only grouped if it has more than this many; 0 never groups them.
    /// @return The groups: the built-in tools' first, then the MCP tools'.
    static ToolGroups of(final Map<ToolSpecification, ToolExecutor> builtIns,
                         final Map<ToolSpecification, ToolExecutor> mcpTools,
                         final McpToolset toolset,
                         final int threshold) {
        final Map<String, Map<ToolSpecification, ToolExecutor>> groups = new LinkedHashMap<>();
        final Map<String, String> purposes = new LinkedHashMap<>();
        final Map<ToolSpecification, ToolExecutor> builtInsAlwaysOffered = new LinkedHashMap<>();
        final Map<ToolSpecification, ToolExecutor> mcpToolsAlwaysOffered = new LinkedHashMap<>();
        if (threshold > 0 && builtIns.size() > threshold) {
            // In the order the groups are listed, whatever order the tools come in.
            BuiltInToolGroups.PURPOSES.keySet().forEach(group -> groups.put(group, new LinkedHashMap<>()));
            builtIns.forEach((specification, executor) -> BuiltInToolGroups.groupOf(specification.name())
                    .ifPresentOrElse(group -> groups.get(group).put(specification, executor),
                            () -> builtInsAlwaysOffered.put(specification, executor)));
            // A group none of whose tools are offered (e.g. in Ask mode, or by the user's tool policies) isn't.
            groups.values().removeIf(Map::isEmpty);
            groups.keySet().forEach(group -> purposes.put(group, BuiltInToolGroups.PURPOSES.get(group)));
        } else {
            builtInsAlwaysOffered.putAll(builtIns);
        }
        mcpGroups(mcpTools, toolset, threshold).forEach((group, tools) -> {
            if (group == null) {
                mcpToolsAlwaysOffered.putAll(tools);
            } else {
                // An MCP server's group isn't merged into a built-in group of the same name.
                final String name = groups.containsKey(group)
                        ? "mcp_" + group
                        : group;
                groups.computeIfAbsent(name, key -> new LinkedHashMap<>()).putAll(tools);
            }
        });
        return new ToolGroups(groups, purposes, builtInsAlwaysOffered, mcpToolsAlwaysOffered);
    }

    // The MCP tools by group, with those never grouped under null.
    private static Map<String, Map<ToolSpecification, ToolExecutor>> mcpGroups(
            final Map<ToolSpecification, ToolExecutor> mcpTools,
            final McpToolset toolset,
            final int threshold) {
        if (threshold <= 0 || mcpTools.size() <= threshold) {
            final Map<String, Map<ToolSpecification, ToolExecutor>> ungrouped = new LinkedHashMap<>();
            ungrouped.put(null, new LinkedHashMap<>(mcpTools));
            return ungrouped;
        }
        // Count the name prefixes on each server, to see which are shared.
        final Map<String, Long> prefixCounts = mcpTools.keySet().stream()
                .map(specification -> serverPrefix(toolset, specification.name()))
                .filter(key -> key != null)
                .collect(Collectors.groupingBy(key -> key, Collectors.counting()));

        final Map<String, Map<ToolSpecification, ToolExecutor>> groups = new LinkedHashMap<>();
        mcpTools.forEach((specification, executor) -> groups
                .computeIfAbsent(groupOf(toolset, specification.name(), prefixCounts), key -> new LinkedHashMap<>())
                .put(specification, executor));
        return groups;
    }

    /// @return True if the tools are offered as groups.
    boolean isGrouped() {
        return !groups.isEmpty();
    }

    /// @return The names of the groups, in order.
    Set<String> groupNames() {
        return groups.keySet();
    }

    /// The tools to offer now: those never grouped, the tools of the groups that are on, and a tool to turn on
    /// each other group; the built-in tools first.
    ///
    /// @param active   The groups that are on.
    /// @param activate Turns a group on, when the model calls its tool.
    /// @return The tools, and how to run each of them.
    Map<ToolSpecification, ToolExecutor> offered(final Set<String> active, final Consumer<String> activate) {
        final Map<ToolSpecification, ToolExecutor> offered = new LinkedHashMap<>(builtInsAlwaysOffered);
        // A built-in group has a purpose; an MCP server's doesn't.
        offerGroups(offered, active, activate, true);
        offered.putAll(mcpToolsAlwaysOffered);
        offerGroups(offered, active, activate, false);
        return offered;
    }

    // Adds the tools of each built-in group, or each MCP group, that is on, and a tool to turn on each other one.
    private void offerGroups(final Map<ToolSpecification, ToolExecutor> offered,
                             final Set<String> active,
                             final Consumer<String> activate,
                             final boolean builtIn) {
        groups.forEach((group, tools) -> {
            if (purposes.containsKey(group) != builtIn) {
                return;
            }
            if (active.contains(group)) {
                offered.putAll(tools);
            } else {
                offered.put(activationSpecification(group, purposes.get(group), tools), (request, memoryId) -> {
                    activate.accept(group);
                    return "The '" + group + "' tools are now available, from your next step: "
                           + String.join(", ", toolNames(tools, Integer.MAX_VALUE)) + ".";
                });
            }
        });
    }

    /// @param toolName A tool's name.
    /// @return The group the tool is in, or empty if it isn't in one.
    Optional<String> groupOf(final String toolName) {
        return groups.entrySet().stream()
                .filter(entry -> entry.getValue().keySet().stream()
                        .anyMatch(specification -> specification.name().equals(toolName)))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /// @param toolName A tool's name.
    /// @return True if it is a tool that turns on a group.
    static boolean isActivationTool(final String toolName) {
        return toolName != null && toolName.startsWith(ACTIVATE_PREFIX);
    }

    /// @param purpose What the group is for, or null if that isn't known (an MCP server's group).
    private static ToolSpecification activationSpecification(final String group,
                                                              final String purpose,
                                                              final Map<ToolSpecification, ToolExecutor> tools) {
        // A built-in group's tools are all named, as the guidance names them, so the model knows which to turn on.
        final List<String> names = toolNames(tools, purpose != null
                ? Integer.MAX_VALUE
                : MAX_LISTED_NAMES);
        final String more = tools.size() > names.size()
                ? " and " + (tools.size() - names.size()) + " more"
                : "";
        final String forWhat = purpose != null
                ? ", to " + purpose
                : "";
        return ToolSpecification.builder()
                .name(ACTIVATE_PREFIX + group)
                .description("Turns on the '" + group + "' group of " + tools.size() + " tools" + forWhat
                             + ", so you can call them from your next step: " + String.join(", ", names) + more
                             + ". Turn on a group only when you need its tools.")
                .build();
    }

    private static List<String> toolNames(final Map<ToolSpecification, ToolExecutor> tools, final int max) {
        final List<String> names = new ArrayList<>();
        for (final ToolSpecification specification : tools.keySet()) {
            if (names.size() >= max) {
                break;
            }
            names.add(specification.name());
        }
        return names;
    }

    /// @return The tool's group, or null if it is never grouped.
    private static String groupOf(final McpToolset toolset,
                                  final String toolName,
                                  final Map<String, Long> prefixCounts) {
        final McpToolInfo info = toolset.toolInfo(toolName).orElse(null);
        if (info == null) {
            // The resource tools, which are small and always useful.
            return null;
        }
        final String declared = toolset.declaredGroup(toolName).orElse(null);
        if (declared != null) {
            return clean(declared, info.serverKey());
        }
        final String serverPrefix = serverPrefix(toolset, toolName);
        if (serverPrefix != null && prefixCounts.getOrDefault(serverPrefix, 0L) > 1) {
            return clean(prefix(info.toolName()), info.serverKey());
        }
        return clean(info.serverKey(), "tools");
    }

    /// @return The tool's server and name prefix, as a key, or null if its name has no prefix.
    private static String serverPrefix(final McpToolset toolset, final String toolName) {
        return toolset.toolInfo(toolName)
                .map(info -> {
                    final String prefix = prefix(info.toolName());
                    return prefix != null
                            ? info.serverKey() + "\u0000" + prefix
                            : null;
                })
                .orElse(null);
    }

    private static String prefix(final String toolName) {
        final int separator = toolName.indexOf(NAME_SEPARATOR);
        return separator > 0
                ? toolName.substring(0, separator)
                : null;
    }

    /// @return The name made safe for a tool name: lower case letters, digits and `_`.
    private static String clean(final String name, final String fallback) {
        final String cleaned = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
        if (!cleaned.isEmpty()) {
            return cleaned;
        }
        return fallback.equals(name)
                ? "tools"
                : clean(fallback, fallback);
    }

    /// The groups that are on in each chat: those the model turned on, or called a tool of, in the chat's
    /// latest [#QUESTIONS_KEPT] questions (the one being answered and the one before), and those turned on since.
    /// A group the chat stops using goes off again, so a long chat isn't sent the definitions of every group it
    /// has ever used. They are worked out from the chat's tool calls as each run starts, so any node finds the
    /// same, e.g. after Stroom restarts; the model, seeing it used them, carries on as if they were on.
    @Singleton
    static final class Activations {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Activations.class);
        private static final int MAX_CHATS = 10_000;
        /// How many of a chat's latest questions keep on the groups they used.
        static final int QUESTIONS_KEPT = 2;

        private final Provider<AiDao> aiDaoProvider;

        private final Map<Integer, Set<String>> activeByChat = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<Integer, Set<String>> eldest) {
                return size() > MAX_CHATS;
            }
        };

        @Inject
        Activations(final Provider<AiDao> aiDaoProvider) {
            this.aiDaoProvider = aiDaoProvider;
        }

        /// Works out the groups that are on as a run starts in a chat: those its latest questions used.
        ///
        /// @param chatId  The chat.
        /// @param groupOf The group a tool is in, if any, by its name.
        /// @param needed  Groups to turn on whatever the chat used, e.g. those the question's guidance needs.
        synchronized void startRun(final int chatId,
                                   final Function<String, Optional<String>> groupOf,
                                   final Set<String> needed) {
            final Set<String> active = usedRecently(chatId, groupOf);
            active.addAll(needed);
            LOGGER.debug(() -> "Tool groups on in chatId=" + chatId + " as a run starts: " + active);
            activeByChat.put(chatId, active);
        }

        /// @return The groups on in the chat.
        synchronized Set<String> active(final int chatId) {
            return Set.copyOf(activeByChat.getOrDefault(chatId, Set.of()));
        }

        /// Turns a group on in a chat.
        synchronized void activate(final int chatId, final String group) {
            activeByChat.computeIfAbsent(chatId, key -> new HashSet<>()).add(group);
        }

        // The groups the chat's latest questions turned on, or called a tool of.
        private Set<String> usedRecently(final int chatId, final Function<String, Optional<String>> groupOf) {
            final List<AiChatMessage> messages = aiDaoProvider.get().getMessages(chatId);
            final List<Integer> questions = messages.stream()
                    .filter(message -> message.getMessageType() == AiMessageType.USER_MESSAGE)
                    .map(AiChatMessage::getTurnId)
                    .distinct()
                    .toList();
            final Set<Integer> recent = Set.copyOf(questions.subList(
                    Math.max(0, questions.size() - QUESTIONS_KEPT), questions.size()));
            final Set<String> used = new HashSet<>();
            for (final AiChatMessage message : messages) {
                if (message.getMessageType() == AiMessageType.TOOL_CALL && recent.contains(message.getTurnId())) {
                    final String tool = toolName(message.getMessage());
                    if (isActivationTool(tool)) {
                        used.add(tool.substring(ACTIVATE_PREFIX.length()));
                    } else if (tool != null) {
                        groupOf.apply(tool).ifPresent(used::add);
                    }
                }
            }
            return used;
        }

        private static String toolName(final String toolCallJson) {
            try {
                return JsonUtil.readValue(toolCallJson, ToolCallRecord.class).tool();
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unreadable tool call: " + toolCallJson, e);
                return null;
            }
        }
    }
}
