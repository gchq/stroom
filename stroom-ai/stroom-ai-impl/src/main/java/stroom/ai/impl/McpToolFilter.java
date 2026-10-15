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

import stroom.openai.shared.McpToolPolicy;
import stroom.openai.shared.McpToolPolicyRule;
import stroom.openai.shared.McpToolServerConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// Applies an MCP server's tool policies. Each policy has a regular expression that must match the tool's whole
/// name (its own name, not the prefixed name the model sees), ignoring case, and the first policy to match a tool
/// applies. A tool whose policy is [McpToolPolicy#DENY] is never offered to the model.
final class McpToolFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpToolFilter.class);

    // Tool names are short, so a longer pattern is a mistake (or an attempt to make matching slow).
    static final int MAX_PATTERN_LENGTH = 500;

    private final List<CompiledRule> rules;

    private McpToolFilter(final List<CompiledRule> rules) {
        this.rules = rules;
    }

    /// @param server The server's configuration.
    /// @return A filter for the server's tools.
    static McpToolFilter forServer(final McpToolServerConfig server) {
        final List<CompiledRule> rules = new ArrayList<>();
        if (server != null) {
            for (final McpToolPolicyRule rule : NullSafe.list(server.getToolPolicies())) {
                compile(rule).ifPresentOrElse(rules::add, () ->
                        LOGGER.warn(() -> "Ignoring tool policy '" + rule + "' of MCP tool server '"
                                          + server.getName() + "' as its pattern is not a valid regular "
                                          + "expression"));
            }
        }
        return new McpToolFilter(List.copyOf(rules));
    }

    /// @param toolName The tool's name, as given by its server.
    /// @return True if the tool may be offered to the model.
    boolean isAllowed(final String toolName) {
        return toolName != null && policyFor(toolName) != McpToolPolicy.DENY;
    }

    /// @param toolName The tool's name, as given by its server.
    /// @return The policy of the first rule that matches the tool, or null if none does.
    McpToolPolicy policyFor(final String toolName) {
        if (toolName == null) {
            return null;
        }
        return rules.stream()
                .filter(rule -> rule.pattern().matcher(toolName).matches())
                .map(CompiledRule::policy)
                .findFirst()
                .orElse(null);
    }

    /// @param pattern A tool policy's pattern.
    /// @return Why the pattern cannot be used, or null if it can.
    static String patternError(final String pattern) {
        if (NullSafe.isBlankString(pattern)) {
            return "The pattern is empty";
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            return "The pattern is longer than " + MAX_PATTERN_LENGTH + " characters";
        }
        try {
            Pattern.compile(pattern.strip());
            return null;
        } catch (final PatternSyntaxException e) {
            return e.getDescription();
        }
    }

    private static Optional<CompiledRule> compile(final McpToolPolicyRule rule) {
        if (rule == null || patternError(rule.getToolPattern()) != null) {
            return Optional.empty();
        }
        return Optional.of(new CompiledRule(
                Pattern.compile(rule.getToolPattern().strip(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                rule.getPolicy()));
    }

    private record CompiledRule(Pattern pattern, McpToolPolicy policy) {

    }
}
