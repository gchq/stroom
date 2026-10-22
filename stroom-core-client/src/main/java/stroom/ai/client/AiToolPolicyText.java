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

package stroom.ai.client;

import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AiToolPolicy;
import stroom.ai.shared.AiToolSummary;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/// The text the MCP Tool Policies screen shows for a tool policy: who it covers, its categories, the tools its
/// categories grant, and why it cannot be saved.
public final class AiToolPolicyText {

    /// What the Covers column shows for a policy that covers every user.
    public static final String ALL_USERS = "All users";

    private AiToolPolicyText() {
    }

    /// @param policy A tool policy.
    /// @return "All users" if it covers every user, otherwise its members' names, comma-separated, with groups
    /// marked as groups.
    public static String covers(final AiToolPolicy policy) {
        if (policy.isAllUsers()) {
            return ALL_USERS;
        }
        return policy.getMembers()
                .stream()
                .map(AiToolPolicyText::memberName)
                .collect(Collectors.joining(", "));
    }

    /// @param member A user or group.
    /// @return Its name, followed by "(group)" if it is a group.
    public static String memberName(final UserRef member) {
        final String name = member.toDisplayString();
        return member.isGroup()
                ? name + " (group)"
                : name;
    }

    /// @param policy A tool policy.
    /// @return The display values of its categories, comma-separated, in the order they are declared.
    public static String categories(final AiToolPolicy policy) {
        return policy.getCategories()
                .stream()
                .sorted()
                .map(AiToolCategory::getDisplayValue)
                .collect(Collectors.joining(", "));
    }

    /// Lists, for each ticked category, the names of its tools, marking those that change things as not granted
    /// when the access is read-only.
    ///
    /// @param categories The ticked categories.
    /// @param access     The access the policy grants.
    /// @param tools      Stroom's tools, or null if they have not been fetched yet.
    /// @return The tools the categories grant, as HTML.
    public static SafeHtml grantedTools(final List<AiToolCategory> categories,
                                        final AiToolAccess access,
                                        final List<AiToolSummary> tools) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        if (tools == null) {
            builder.appendEscaped("Fetching the tools...");
            return builder.toSafeHtml();
        }
        if (NullSafe.isEmptyCollection(categories)) {
            builder.appendEscaped("Tick a category to see the tools it grants.");
            return builder.toSafeHtml();
        }

        final boolean readOnly = access != AiToolAccess.READ_WRITE;
        final List<AiToolCategory> sortedCategories = new ArrayList<>(categories);
        sortedCategories.sort(Comparator.naturalOrder());
        for (final AiToolCategory category : sortedCategories) {
            builder.appendHtmlConstant("<div><b>");
            builder.appendEscaped(category.getDisplayValue());
            builder.appendHtmlConstant("</b>");
            final List<AiToolSummary> categoryTools = toolsIn(category, tools);
            if (categoryTools.isEmpty()) {
                builder.appendHtmlConstant("<div>");
                builder.appendEscaped("No tools.");
                builder.appendHtmlConstant("</div>");
            } else {
                builder.appendHtmlConstant("<ul>");
                for (final AiToolSummary tool : categoryTools) {
                    builder.appendHtmlConstant("<li>");
                    builder.appendEscaped(tool.getName());
                    if (readOnly && !tool.isReadOnly()) {
                        builder.appendEscaped(tool.isReadOnlyCalls()
                                ? " (only its calls that cannot change anything, as access is "
                                  + AiToolAccess.READ_ONLY.getDisplayValue() + ")"
                                : " (not granted: it changes things and access is "
                                  + AiToolAccess.READ_ONLY.getDisplayValue() + ")");
                    }
                    builder.appendHtmlConstant("</li>");
                }
                builder.appendHtmlConstant("</ul>");
            }
            builder.appendHtmlConstant("</div>");
        }
        return builder.toSafeHtml();
    }

    /// @param policy A tool policy as entered in the dialog.
    /// @return Why it cannot be saved, or null if it can.
    public static String validationError(final AiToolPolicy policy) {
        if (NullSafe.isBlankString(policy.getName())) {
            return "A name is required.";
        }
        if (NullSafe.isEmptyCollection(policy.getCategories())) {
            return "At least one category is required.";
        }
        return null;
    }

    private static List<AiToolSummary> toolsIn(final AiToolCategory category, final List<AiToolSummary> tools) {
        return tools.stream()
                .filter(tool -> tool.getCategory() == category)
                .sorted(Comparator.comparing(AiToolSummary::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
