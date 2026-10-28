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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Checks that no UiBinder template declares one of GWT's widgets that Stroom has its own version
/// of (AGENTS.md, "Stroom's widgets only"). Java's imports and constructions are checked by
/// checkstyle (`GwtWidgetWithStroomVersion`); templates aren't Java,
/// so they are checked here.
class TestStroomWidgetsOnly {

    // The modules with GWT client templates, from the repository's root
    private static final List<String> MODULES = List.of(
            "stroom-core-client",
            "stroom-core-client-widget",
            "stroom-dashboard-gwt",
            "stroom-app-gwt",
            "stroom-gwt/stroom-gwt-workbench",
            "stroom-statistics/stroom-statistics-client");

    // GWT's widget -> Stroom's version of it
    private static final Map<String, String> STROOM_VERSIONS = Map.ofEntries(
            Map.entry("Button", "Button, ToolbarButton or FabButton (stroom.widget.button.client)"),
            Map.entry("PushButton", "Button (stroom.widget.button.client)"),
            Map.entry("ToggleButton", "ToolbarToggleButton (stroom.widget.button.client)"),
            Map.entry("Hyperlink", "LinkButton (stroom.widget.button.client)"),
            Map.entry("InlineHyperlink", "LinkButton (stroom.widget.button.client)"),
            Map.entry("CheckBox", "TickBox (stroom.widget.tickbox.client)"),
            Map.entry("SimpleCheckBox", "TickBox (stroom.widget.tickbox.client)"),
            Map.entry("RadioButton", "RadioGroup (stroom.widget.form.client)"),
            Map.entry("ListBox", "SelectionBox (stroom.item.client)"),
            Map.entry("SuggestBox", "SuggestionBox (stroom.item.client)"),
            Map.entry("Label", "Label (stroom.widget.text.client)"),
            Map.entry("InlineLabel", "InlineLabel (stroom.widget.text.client)"),
            Map.entry("HTML", "Html (stroom.widget.text.client)"),
            Map.entry("InlineHTML", "Html (stroom.widget.text.client)"),
            Map.entry("Frame", "Frame (stroom.widget.text.client)"),
            Map.entry("Anchor", "Link (stroom.widget.text.client)"),
            Map.entry("TextBox", "TextBox (stroom.widget.form.client)"),
            Map.entry("TextArea", "TextArea (stroom.widget.form.client)"),
            Map.entry("PasswordTextBox", "PasswordBox (stroom.widget.form.client)"),
            Map.entry("DialogBox", "Stroom's dialogs"),
            Map.entry("TabPanel", "Stroom's tabs"),
            Map.entry("TabLayoutPanel", "Stroom's tabs"),
            Map.entry("SplitLayoutPanel", "SplitPane (stroom.widget.layout.client)"));

    // Stroom's own versions are built on GWT's (its copies in GWT's package)
    private static final Set<String> STROOM_VERSION_TEMPLATES = Set.of("PasswordBox.ui.xml");

    private static final Pattern GWT_UI_PREFIX = Pattern.compile(
            "xmlns:(\\w+)=\"urn:import:com\\.google\\.gwt\\.user\\.client\\.ui\"");

    @Test
    void testTemplatesUseStroomWidgets() {
        final Path root = repositoryRoot();
        final List<String> problems = new ArrayList<>();
        int templates = 0;
        for (final String module : MODULES) {
            final Path src = root.resolve(module).resolve("src");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (final Stream<Path> paths = Files.walk(src)) {
                for (final Path path : paths.filter(p -> p.toString().endsWith(".ui.xml")).toList()) {
                    templates++;
                    problems.addAll(problemsIn(root, path));
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        assertThat(templates)
                .as("templates found under " + root)
                .isGreaterThan(100);
        assertThat(problems)
                .as("GWT widgets declared where Stroom has its own version")
                .isEmpty();
    }

    private static List<String> problemsIn(final Path root, final Path path) {
        if (STROOM_VERSION_TEMPLATES.contains(path.getFileName().toString())) {
            return List.of();
        }
        final String template;
        try {
            template = Files.readString(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        final Matcher prefix = GWT_UI_PREFIX.matcher(template);
        if (!prefix.find()) {
            return List.of();
        }
        final List<String> problems = new ArrayList<>();
        final Matcher tag = Pattern.compile("<" + prefix.group(1) + ":(\\w+)[\\s/>]").matcher(template);
        while (tag.find()) {
            final String stroomVersion = STROOM_VERSIONS.get(tag.group(1));
            if (stroomVersion != null) {
                problems.add(root.relativize(path) + ": GWT's " + tag.group(1) + ", not Stroom's " + stroomVersion);
            }
        }
        return problems;
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
}
