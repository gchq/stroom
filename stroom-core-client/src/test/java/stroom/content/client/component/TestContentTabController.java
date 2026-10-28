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

package stroom.content.client.component;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// A content tab's type is the type of the reference a tab session saves for it, and the type its
// plugin registers to open it again, so two screens with one type open each other's screen
class TestContentTabController {

    // The client modules with content tabs, from the repository's root
    private static final List<String> MODULES = List.of(
            "stroom-core-client",
            "stroom-statistics/stroom-statistics-client");

    private static final Pattern TAB_TYPE = Pattern.compile(
            "static\\s+final\\s+String\\s+TAB_TYPE\\s*=\\s*\"([^\"]+)\"\\s*;");

    @Test
    void testTabTypesAreUnique() {
        final Map<String, Set<String>> classesByType = new TreeMap<>();
        for (final String module : MODULES) {
            for (final Path file : javaFiles(repositoryRoot().resolve(module).resolve("src/main/java"))) {
                final Matcher matcher = TAB_TYPE.matcher(read(file));
                while (matcher.find()) {
                    classesByType.computeIfAbsent(matcher.group(1), k -> new TreeSet<>())
                            .add(file.getFileName().toString());
                }
            }
        }

        // Properties once had Caches' type, and a document's permissions the batch screen's
        assertThat(classesByType).containsKeys("Caches", "Properties", "DocumentPermissions",
                "DocumentUserPermissions");
        final Map<String, Set<String>> shared = classesByType.entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(shared)
                .as("tab types given to more than one class")
                .isEmpty();
    }

    private static List<Path> javaFiles(final Path dir) {
        try (final Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path repositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("stroom-core-client-widget"))) {
            dir = dir.getParent();
        }
        assertThat(dir)
                .as("the repository's root, above " + Path.of("").toAbsolutePath())
                .isNotNull();
        return dir;
    }
}
