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


import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.inject.Inject;
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
import java.util.stream.Collectors;

/// Offers a large set of MCP tools as groups, as VS Code does with its virtual tools: the model is given one
/// `activate_<group>` tool per group, with a summary of the group's tools, and once it calls one, the group's
/// tools are offered from the next request. This keeps tool definitions the model does not need out of its
/// context.
///
/// A tool's group is, in order: the group its server gives it, as `_meta.group`; the start of its name up to the
/// first `_`, if other tools on its server share it; or else its server.
final class ToolGroups {

    static final String ACTIVATE_PREFIX = "activate_";
    private static final int MAX_LISTED_NAMES = 15;
    private static final String NAME_SEPARATOR = "_";

    private final Map<String, Map<ToolSpecification, ToolExecutor>> groups;
    private final Map<ToolSpecification, ToolExecutor> alwaysOffered;

    private ToolGroups(final Map<String, Map<ToolSpecification, ToolExecutor>> groups,
                       final Map<ToolSpecification, ToolExecutor> alwaysOffered) {
        this.groups = groups;
        this.alwaysOffered = alwaysOffered;
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
        if (threshold <= 0 || mcpTools.size() <= threshold) {
            return new ToolGroups(Map.of(), new LinkedHashMap<>(mcpTools));
        }
        // Count the name prefixes on each server, to see which are shared.
        final Map<String, Long> prefixCounts = mcpTools.keySet().stream()
                .map(specification -> serverPrefix(toolset, specification.name()))
                .filter(key -> key != null)
                .collect(Collectors.groupingBy(key -> key, Collectors.counting()));

        final Map<String, Map<ToolSpecification, ToolExecutor>> groups = new LinkedHashMap<>();
        final Map<ToolSpecification, ToolExecutor> alwaysOffered = new LinkedHashMap<>();
        mcpTools.forEach((specification, executor) -> {
            final String group = groupOf(toolset, specification.name(), prefixCounts);
            if (group == null) {
                alwaysOffered.put(specification, executor);
            } else {
                groups.computeIfAbsent(group, key -> new LinkedHashMap<>()).put(specification, executor);
            }
        });
        return new ToolGroups(groups, alwaysOffered);
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
    /// each other group.
    ///
    /// @param active   The groups that are on.
    /// @param activate Turns a group on, when the model calls its tool.
    /// @return The tools, and how to run each of them.
    Map<ToolSpecification, ToolExecutor> offered(final Set<String> active, final Consumer<String> activate) {
        final Map<ToolSpecification, ToolExecutor> offered = new LinkedHashMap<>(alwaysOffered);
        groups.forEach((group, tools) -> {
            if (active.contains(group)) {
                offered.putAll(tools);
            } else {
                offered.put(activationSpecification(group, tools), (request, memoryId) -> {
                    activate.accept(group);
                    return "The '" + group + "' tools are now available, from your next step: "
                           + String.join(", ", toolNames(tools, Integer.MAX_VALUE)) + ".";
                });
            }
        });
        return offered;
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

    private static ToolSpecification activationSpecification(final String group,
                                                              final Map<ToolSpecification, ToolExecutor> tools) {
        final List<String> names = toolNames(tools, MAX_LISTED_NAMES);
        final String more = tools.size() > names.size()
                ? " and " + (tools.size() - names.size()) + " more"
                : "";
        return ToolSpecification.builder()
                .name(ACTIVATE_PREFIX + group)
                .description("Turns on the '" + group + "' group of " + tools.size() + " tools, so you can call "
                             + "them from your next step: " + String.join(", ", names) + more + ". Turn on a "
                             + "group only when you need its tools.")
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

    /// The groups the model has turned on in each chat. They stay on for the rest of the chat on this node, or
    /// until the chat is one of the least recently used past [#MAX_CHATS].
    @Singleton
    static final class Activations {

        private static final int MAX_CHATS = 10_000;

        private final Map<Integer, Set<String>> activeByChat = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<Integer, Set<String>> eldest) {
                return size() > MAX_CHATS;
            }
        };

        @Inject
        Activations() {
            // For injection.
        }

        /// @return The groups turned on in the chat.
        synchronized Set<String> active(final int chatId) {
            return Set.copyOf(activeByChat.getOrDefault(chatId, Set.of()));
        }

        /// Turns a group on in a chat.
        synchronized void activate(final int chatId, final String group) {
            activeByChat.computeIfAbsent(chatId, key -> new HashSet<>()).add(group);
        }
    }
}
