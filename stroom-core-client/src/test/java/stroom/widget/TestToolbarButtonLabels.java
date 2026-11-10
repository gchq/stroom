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

package stroom.widget;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Checks the short labels toolbar buttons show when the toolbar display preference shows text
/// (`ToolbarButton.setLabel`): each is the start of its button's name, so that what a button shows
/// is in the name screen readers read (WCAG 2.5.3, label in name), and it is shorter than the name.
/// Read from the templates (`label` beside `title`) and from `Toolbar.button` and `toggle` calls.
class TestToolbarButtonLabels {

    // The code that makes toolbars (the workbench's stories aside)
    private static final List<String> CODE_DIRS = List.of(
            "stroom-core-client-widget/src/main/java",
            "stroom-core-client/src/main/java",
            "stroom-dashboard-gwt/src/main/java",
            "stroom-app-gwt/src/main/java");
    // A button in a template, its attributes
    private static final Pattern TEMPLATE_BUTTON = Pattern.compile(
            "<\\w+:(?:ToolbarButton|ToolbarToggleButton)\\b([^>]*?)/?>");
    private static final Pattern TITLE = Pattern.compile("\\stitle=\"([^\"]*)\"");
    private static final Pattern LABEL = Pattern.compile("\\slabel=\"([^\"]*)\"");
    // Toolbar.button(icon, "name", "label", ...) and toggle(...)
    private static final Pattern JAVA_BUTTON = Pattern.compile(
            "\\.(?:button|toggle)\\(\\s*SvgImage\\.\\w+,\\s*\"([^\"]+)\",\\s*\"([^\"]+)\",");

    private static List<Labelled> labelled;

    @BeforeAll
    static void read() {
        labelled = new ArrayList<>();
        final Path root = repositoryRoot();
        for (final String dir : CODE_DIRS) {
            for (final Path path : files(root.resolve(dir))) {
                final String text = read(path);
                final String where = root.relativize(path).toString();
                if (path.toString().endsWith(".ui.xml")) {
                    final Matcher button = TEMPLATE_BUTTON.matcher(text);
                    while (button.find()) {
                        final Matcher title = TITLE.matcher(button.group(1));
                        final Matcher label = LABEL.matcher(button.group(1));
                        if (label.find()) {
                            labelled.add(new Labelled(title.find()
                                    ? title.group(1)
                                    : null, label.group(1), where));
                        }
                    }
                } else {
                    final Matcher button = JAVA_BUTTON.matcher(text);
                    while (button.find()) {
                        labelled.add(new Labelled(button.group(1), button.group(2), where));
                    }
                }
            }
        }
    }

    @Test
    void labelsAreFound() {
        // Over a hundred buttons have labels; finding none would mean the patterns no longer match
        assertThat(labelled).hasSizeGreaterThan(100);
    }

    @Test
    void eachLabelStartsItsButtonsName() {
        final List<String> wrong = labelled.stream()
                .filter(button -> button.name() == null || !button.name().startsWith(button.label()))
                .map(Labelled::toString)
                .toList();
        assertThat(wrong)
                .as("labels that their buttons' names don't start with (the name must hold what is shown)")
                .isEmpty();
    }

    @Test
    void eachLabelIsShorterThanItsName() {
        final List<String> wrong = labelled.stream()
                .filter(button -> button.name() != null && button.label().length() >= button.name().length())
                .map(Labelled::toString)
                .toList();
        assertThat(wrong)
                .as("labels no shorter than their names (leave the label out to show the name)")
                .isEmpty();
    }

    @Test
    void noLabelIsBlank() {
        assertThat(labelled).noneMatch(button -> button.label().isBlank());
    }

    private static List<Path> files(final Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (final Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(p -> p.toString().endsWith(".ui.xml") || p.toString().endsWith(".java"))
                    .toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Tests run in their module's directory; the repository is its parent
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


    // --------------------------------------------------------------------------------


    private record Labelled(String name, String label, String where) {

        @Override
        public String toString() {
            return "\"" + label + "\" for \"" + name + "\" (" + where + ")";
        }
    }
}
