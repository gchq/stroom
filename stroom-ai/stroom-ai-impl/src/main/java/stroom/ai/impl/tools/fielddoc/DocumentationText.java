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

package stroom.ai.impl.tools.fielddoc;

import stroom.ai.impl.tools.PyText;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The shape of a Documentation doc's text as `write_documentation` gives it: its name as its title, its sections at
/// `## `, and text added under a section of the agent's. Ported from the Stroom MCP server's `titled`,
/// `section_levels` and `_below_section` in `tools/builds.py`.
public final class DocumentationText {

    private static final Pattern TITLE = Pattern.compile("\\A\\s*#[ \\t]+[^\\n]*(?:\\n|\\z)",
            Pattern.UNICODE_CHARACTER_CLASS);
    // The guide's sections (stroom://guide/documentation) and an evaluation report's.
    private static final Pattern SECTION = Pattern.compile("^#[ \\t]+(Purpose and data|Processing|Field mapping|"
                                                           + "Output|Conformance|Errors|Open items|Event types|"
                                                           + "Version control|Change log)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern LEVEL_ONE = Pattern.compile("^#[ \\t]");
    private static final Pattern LEVEL_ONE_TO_FIVE = Pattern.compile("^#{1,5}[ \\t]");

    private DocumentationText() {
        // Static only.
    }

    /// `titled`: the documentation with its name as its title, in place of any title the agent gave it. Seen in
    /// production: a title naming V1.2 on the documentation of the V1.1 pipeline, whose name the doc carries.
    ///
    /// @param markdown The documentation, or null.
    /// @param name     The doc's name.
    /// @return It titled with the name.
    public static String titled(final String markdown, final String name) {
        final String text = markdown == null
                ? ""
                : markdown;
        final String untitled = TITLE.matcher(text).replaceFirst("");
        int start = 0;
        while (start < untitled.length() && untitled.charAt(start) == '\n') {
            start++;
        }
        return "# " + name + "\n\n" + untitled.substring(start);
    }

    /// `section_levels`: the documentation with its sections at `## `, where the agent wrote them at `# ` (seen:
    /// Haiku's index documentation began `# Purpose and data`; the survey was put in a `## Purpose and data` of its
    /// own above it, and as a pipeline's first line it would have been taken for the title). Every heading goes
    /// down a level, and a title of the agent's goes: the tool sets the title.
    ///
    /// @param markdown The documentation, or null.
    /// @return It with its sections at `## `, or as it is if none is at `# `.
    public static String sectionLevels(final String markdown) {
        final List<String> lines = new ArrayList<>(PyText.split(markdown == null
                ? ""
                : markdown, "\n"));
        boolean fenced = false;
        final List<Integer> levelOne = new ArrayList<>();
        for (int n = 0; n < lines.size(); n++) {
            final String line = lines.get(n);
            if (isFence(line)) {
                fenced = !fenced;
            } else if (!fenced && LEVEL_ONE.matcher(line).find()) {
                levelOne.add(n);
            }
        }
        if (levelOne.stream().noneMatch(n -> SECTION.matcher(lines.get(n)).find())) {
            return markdown;
        }
        Integer first = null;
        for (int n = 0; n < lines.size(); n++) {
            if (!PyText.strip(lines.get(n)).isEmpty()) {
                first = n;
                break;
            }
        }
        if (first != null && levelOne.contains(first) && !SECTION.matcher(lines.get(first)).find()) {
            lines.set(first, "");
        }
        fenced = false;
        for (int n = 0; n < lines.size(); n++) {
            final String line = lines.get(n);
            if (isFence(line)) {
                fenced = !fenced;
            } else if (!fenced && LEVEL_ONE_TO_FIVE.matcher(line).find()) {
                lines.set(n, "#" + line);
            }
        }
        return PyText.strip(String.join("\n", lines), "\n");
    }

    /// `_below_section`: the documentation with text added at the end of the `## heading` section (the agent's own
    /// words stay first), or the section added at the top with just the text.
    ///
    /// @param markdown The documentation.
    /// @param heading  The section's heading, e.g. `Purpose and data`.
    /// @param text     What to add.
    /// @return The documentation with the text added.
    public static String belowSection(final String markdown, final String heading, final String text) {
        final Matcher found = Pattern.compile("^## " + Pattern.quote(heading) + "[^\\n]*\\n(.*?)(?=^## |\\z)",
                Pattern.MULTILINE | Pattern.DOTALL).matcher(markdown);
        if (!found.find()) {
            return "## " + heading + "\n\n" + PyText.strip(NullSafe.string(text)) + "\n\n" + PyText.lstrip(markdown);
        }
        return PyText.rstrip(markdown.substring(0, found.end(1))) + "\n\n" + PyText.strip(NullSafe.string(text))
               + "\n\n" + PyText.lstrip(markdown.substring(found.end(1)));
    }

    private static boolean isFence(final String line) {
        final String stripped = PyText.lstrip(line);
        return stripped.startsWith("```") || stripped.startsWith("~~~");
    }
}
