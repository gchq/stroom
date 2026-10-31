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
import stroom.ai.shared.AiToolEffectiveGrant;
import stroom.ai.shared.AiToolPolicy;
import stroom.ai.shared.AiToolSummary;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// The text the MCP Tool Policies screen shows for a tool policy: who it covers, the tools of a category it
/// grants and how much of each, the tool search's matches, and why it cannot be saved.
public final class AiToolPolicyText {

    /// What is shown for a policy that covers every user.
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

    /// @param category A category of tools.
    /// @param tools    Stroom's tools.
    /// @return The category's tools, by name.
    public static List<AiToolSummary> toolsIn(final AiToolCategory category, final List<AiToolSummary> tools) {
        return NullSafe.list(tools).stream()
                .filter(tool -> tool.getCategory() == category)
                .sorted(Comparator.comparing(AiToolSummary::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /// @param tool   A tool.
    /// @param access The access its category is granted.
    /// @return False if none of it is granted: it changes things, every call of it, and the access is read-only.
    public static boolean isGranted(final AiToolSummary tool, final AiToolAccess access) {
        return access == AiToolAccess.READ_WRITE
               || tool.isReadOnly()
               || tool.isReadOnlyCalls();
    }

    /// @param tool   A tool, or null for its category.
    /// @param access The access its category is granted, or null if the category is not granted.
    /// @return For the Read column: null if the tool has no calls that only read (nothing to show), otherwise
    /// whether they are permitted. A category's are if it is granted at all.
    public static Boolean canRead(final AiToolSummary tool, final AiToolAccess access) {
        if (tool != null && !tool.isReadOnly() && !tool.isReadOnlyCalls()) {
            return null;
        }
        return access != null;
    }

    /// @param tool   A tool, or null for its category.
    /// @param access The access its category is granted, or null if the category is not granted.
    /// @return For the Write column: null if the tool cannot change anything (nothing to show), otherwise whether
    /// its calls that change things are permitted, which needs read and write.
    public static Boolean canWrite(final AiToolSummary tool, final AiToolAccess access) {
        if (tool != null && tool.isReadOnly()) {
            return null;
        }
        return access == AiToolAccess.READ_WRITE;
    }

    /// @param grant A category a user's policies grant them, or null if they grant it nothing.
    /// @param tool  One of the category's tools, or null for the category itself.
    /// @return The policies the access comes from, by name: for the category, those granting its widest access;
    /// for a tool, those granting whatever of it is permitted (so any of them for one that cannot change anything).
    public static List<String> grantedBy(final AiToolEffectiveGrant grant, final AiToolSummary tool) {
        if (grant == null) {
            return List.of();
        }
        if (Boolean.TRUE.equals(canWrite(tool, grant.getAccess()))) {
            return grant.getReadWriteBy();
        }
        if (!Boolean.TRUE.equals(canRead(tool, grant.getAccess()))) {
            return List.of();
        }
        return Stream.concat(grant.getReadWriteBy().stream(), grant.getReadOnlyBy().stream())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /// @param tool  A tool.
    /// @param query What is searched for, or blank.
    /// @return True if the tool's name contains the query, ignoring case.
    public static boolean matches(final AiToolSummary tool, final String query) {
        return contains(tool.getName(), query);
    }

    /// @param text  Some text, or null.
    /// @param query What is searched for, or blank.
    /// @return True if the text contains the query, ignoring case. Nothing matches a blank query.
    public static boolean contains(final String text, final String query) {
        if (NullSafe.isBlankString(query) || text == null) {
            return false;
        }
        return text.toLowerCase().contains(query.trim().toLowerCase());
    }

    /// @param text  Plain text, or null.
    /// @param query What is searched for, or blank.
    /// @return The text, escaped, with each occurrence of the query (ignoring case) highlighted.
    public static SafeHtml highlight(final String text, final String query) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        final String plain = NullSafe.string(text);
        if (NullSafe.isBlankString(query)) {
            return builder.appendEscaped(plain).toSafeHtml();
        }
        final String needle = query.trim().toLowerCase();
        final String haystack = plain.toLowerCase();
        int from = 0;
        int found = haystack.indexOf(needle);
        while (found >= 0) {
            builder.appendEscaped(plain.substring(from, found));
            builder.appendHtmlConstant("<span class=\"findCell-highlight\">");
            builder.appendEscaped(plain.substring(found, found + needle.length()));
            builder.appendHtmlConstant("</span>");
            from = found + needle.length();
            found = haystack.indexOf(needle, from);
        }
        return builder.appendEscaped(plain.substring(from)).toSafeHtml();
    }

    /// @param text    Plain text, or null, shown on one line.
    /// @param query   What is searched for, or blank.
    /// @param tooltip The full text to show on hover, or null for none.
    /// @return The text on one line, highlighted, in an element showing the tooltip.
    public static SafeHtml oneLine(final String text, final String query, final String tooltip) {
        final String line = NullSafe.string(text).replaceAll("\\s+", " ").trim();
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant(NullSafe.isBlankString(tooltip)
                ? "<span>"
                : "<span title=\"" + SafeHtmlUtils.htmlEscape(tooltip.trim()) + "\">");
        builder.append(highlight(line, query));
        builder.appendHtmlConstant("</span>");
        return builder.toSafeHtml();
    }

    /// @param text Plain text, its paragraphs separated by blank lines, or null.
    /// @return The text as HTML paragraphs, escaped.
    public static SafeHtml paragraphs(final String text) {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        for (final String paragraph : NullSafe.string(text).split("\\n\\s*\\n")) {
            if (!NullSafe.isBlankString(paragraph)) {
                builder.appendHtmlConstant("<p>");
                builder.appendEscapedLines(paragraph.trim());
                builder.appendHtmlConstant("</p>");
            }
        }
        return builder.toSafeHtml();
    }

    /// @param policy A tool policy as entered in the dialog.
    /// @return Why it cannot be saved, or null if it can.
    public static String validationError(final AiToolPolicy policy) {
        if (NullSafe.isBlankString(policy.getName())) {
            return "A name is required.";
        }
        if (NullSafe.isEmptyCollection(policy.getGrants())) {
            return "At least one category is required.";
        }
        return null;
    }
}
