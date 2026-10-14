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

import stroom.openai.shared.McpToolGroupRule;
import stroom.openai.shared.McpToolPolicy;
import stroom.openai.shared.McpToolPolicyRule;
import stroom.openai.shared.McpToolServerConfig;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.Locale;

/// Decides which of an MCP server's tools may be offered to the model, from the server's allowed and denied
/// tool patterns and its `DENY` tool policies. Patterns are matched against the tool's own name (not the prefixed
/// name the model sees), ignoring case, and `*` matches any run of characters. A denied pattern always wins, and
/// so does a tool policy rule of `DENY` when it is the first rule to match the tool.
final class McpToolFilter {

    private final List<String> allowed;
    private final List<String> denied;
    private final List<McpToolPolicyRule> policies;
    private final List<McpToolGroupRule> groups;

    private McpToolFilter(final List<String> allowed,
                          final List<String> denied,
                          final List<McpToolPolicyRule> policies,
                          final List<McpToolGroupRule> groups) {
        this.allowed = clean(allowed);
        this.denied = clean(denied);
        this.policies = NullSafe.list(policies).stream()
                .filter(rule -> NullSafe.isNonBlankString(rule.getToolPattern()))
                .toList();
        this.groups = NullSafe.list(groups).stream()
                .filter(rule -> NullSafe.isNonBlankString(rule.getToolPattern())
                                && NullSafe.isNonBlankString(rule.getGroup()))
                .toList();
    }

    /// @param server The server's configuration.
    /// @return A filter for the server's tools.
    static McpToolFilter forServer(final McpToolServerConfig server) {
        return server == null
                ? new McpToolFilter(List.of(), List.of(), List.of(), List.of())
                : new McpToolFilter(server.getAllowedTools(), server.getDeniedTools(), server.getToolPolicies(),
                        server.getToolGroups());
    }

    /// @param toolName The tool's name, as given by its server.
    /// @return True if the tool may be offered to the model.
    boolean isAllowed(final String toolName) {
        if (toolName == null) {
            return false;
        }
        if (denied.stream().anyMatch(pattern -> matches(pattern, toolName))) {
            return false;
        }
        if (policyFor(toolName) == McpToolPolicy.DENY) {
            return false;
        }
        return allowed.isEmpty() || allowed.stream().anyMatch(pattern -> matches(pattern, toolName));
    }

    /// @param toolName The tool's name, as given by its server.
    /// @return The policy of the first rule that matches the tool, or null if none does.
    McpToolPolicy policyFor(final String toolName) {
        return policies.stream()
                .filter(rule -> matches(rule.getToolPattern().strip(), toolName))
                .map(McpToolPolicyRule::getPolicy)
                .findFirst()
                .orElse(null);
    }

    /// @param toolName The tool's name, as given by its server.
    /// @return The group of the first group rule that matches the tool (which may be
    /// [McpToolGroupRule#ALWAYS_OFFERED]), or null if none does.
    String groupFor(final String toolName) {
        return groups.stream()
                .filter(rule -> matches(rule.getToolPattern().strip(), toolName))
                .map(rule -> rule.getGroup().strip())
                .findFirst()
                .orElse(null);
    }

    /// @param pattern A tool name pattern, where `*` matches any run of characters.
    /// @param name    A tool name.
    /// @return True if the name matches the pattern, ignoring case.
    static boolean matches(final String pattern, final String name) {
        return matches(pattern.toLowerCase(Locale.ROOT), 0, name.toLowerCase(Locale.ROOT), 0);
    }

    private static boolean matches(final String pattern, final int patternIdx, final String name, final int nameIdx) {
        int p = patternIdx;
        int n = nameIdx;
        while (p < pattern.length()) {
            final char chr = pattern.charAt(p);
            if (chr == '*') {
                // Collapse consecutive stars, then try every possible length for the run they match.
                while (p < pattern.length() && pattern.charAt(p) == '*') {
                    p++;
                }
                if (p == pattern.length()) {
                    return true;
                }
                for (int i = n; i <= name.length(); i++) {
                    if (matches(pattern, p, name, i)) {
                        return true;
                    }
                }
                return false;
            }
            if (n >= name.length() || name.charAt(n) != chr) {
                return false;
            }
            p++;
            n++;
        }
        return n == name.length();
    }

    private static List<String> clean(final List<String> patterns) {
        return NullSafe.list(patterns).stream()
                .filter(NullSafe::isNonBlankString)
                .map(String::strip)
                .toList();
    }
}
