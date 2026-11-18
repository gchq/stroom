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

import stroom.ai.api.AiService;
import stroom.ai.impl.tools.StroomTool;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// The inventory of built-in tools in `stroom-ai/TOOLS.md`, checked against the tools that are registered, so it
/// can't silently drift from them: every registered tool has a row in its table and nothing else does, and each
/// row's read-only column is the tool's [StroomTool#isReadOnly()].
class TestToolInventory {

    private static final String INVENTORY = "TOOLS.md";
    private static final String TABLE_HEADING = "## Built-in tools";
    // A row of the table: | `name` | what it does | modes | read-only | ...
    private static final Pattern ROW = Pattern.compile("^\\| `([a-z_]+)` \\|[^|]*\\|[^|]*\\| (yes|no) \\|");

    private final BuiltInToolsTestWiring wiring = new BuiltInToolsTestWiring(Mockito.mock(AiService.class));

    @Test
    void everyRegisteredToolIsInTheInventoryAndNothingElse() throws IOException {
        final Map<String, Boolean> inventory = inventory();
        final Map<String, Boolean> registered = wiring.tools().stream()
                .collect(Collectors.toMap(tool -> tool.definition().name(), StroomTool::isReadOnly,
                        (a, b) -> a, TreeMap::new));

        assertThat(inventory.keySet()).containsExactlyElementsOf(registered.keySet());
        assertThat(inventory).isEqualTo(registered);
    }

    @Test
    void theInventoryListsEachToolOnce() throws IOException {
        final List<String> names = rows().stream()
                .map(matcher -> matcher.group(1))
                .toList();

        assertThat(names).isNotEmpty().doesNotHaveDuplicates();
    }

    // Each tool in the table, with whether it says the tool is read-only, by name.
    private static Map<String, Boolean> inventory() throws IOException {
        return rows().stream()
                .collect(Collectors.toMap(matcher -> matcher.group(1), matcher -> "yes".equals(matcher.group(2)),
                        (a, b) -> a, TreeMap::new));
    }

    // The rows of the table under its heading, up to the next heading.
    private static List<Matcher> rows() throws IOException {
        final List<String> lines = Files.readAllLines(inventoryFile());
        final int start = lines.indexOf(TABLE_HEADING);
        assertThat(start).as("The heading '" + TABLE_HEADING + "' in " + INVENTORY).isNotNegative();
        return lines.subList(start + 1, lines.size()).stream()
                .takeWhile(line -> !line.startsWith("## "))
                .map(ROW::matcher)
                .filter(Matcher::find)
                .toList();
    }

    // The inventory: beside the module's folder when Gradle runs the tests, or under the repository's root when an
    // IDE runs them from there.
    private static Path inventoryFile() {
        final Path workingDir = Paths.get("").toAbsolutePath();
        for (final Path candidate : List.of(workingDir.resolveSibling(INVENTORY),
                workingDir.resolve("stroom-ai").resolve(INVENTORY))) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to find " + INVENTORY + " from " + workingDir);
    }
}
