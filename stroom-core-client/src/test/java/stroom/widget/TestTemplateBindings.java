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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Checks that a UiBinder template and its owner agree (UI_AUDIT.md, decision D8): every `@UiField`
/// is used by the owner's Java, and every `ui:field` is bound to an `@UiField` or named in the
/// template itself. GWT's compiler catches an `@UiField` with no `ui:field`, but not a field that
/// nothing reads or a `ui:field` that nothing binds.
class TestTemplateBindings {

    // The modules with GWT client templates, from the repository's root
    private static final List<String> MODULES = List.of(
            "stroom-core-client",
            "stroom-core-client-widget",
            "stroom-dashboard-gwt",
            "stroom-app-gwt",
            "stroom-gwt/stroom-gwt-workbench",
            "stroom-statistics/stroom-statistics-client");

    // The source folders a template or its owner can be in, below a module
    private static final List<String> SOURCE_DIRS = List.of("src/main/java", "src/main/resources");

    private static final Pattern BINDER = Pattern.compile("UiBinder<[\\w.]+,\\s*(\\w+)>");
    private static final Pattern UI_TEMPLATE = Pattern.compile("@UiTemplate\\(\"([^\"]+)\"\\)");
    private static final Pattern UI_FIELD_DECLARATION = Pattern.compile(
            "@UiField(?:\\([^)]*\\))?\\s+(?:final\\s+)?[\\w.]+(?:<[^;=]*>)?\\s+(\\w+)\\s*(?:=[^;]*)?;");
    private static final Pattern TEMPLATE_UI_FIELD = Pattern.compile("\\sui:field=\"(\\w+)\"");
    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    @Test
    void testTemplatesAndOwnersAgree() {
        final Path root = repositoryRoot();
        final List<String> problems = new ArrayList<>();
        int owners = 0;
        for (final String module : MODULES) {
            for (final Path owner : javaFiles(root.resolve(module))) {
                final String java = read(owner);
                final Matcher binder = BINDER.matcher(java);
                if (!binder.find() || !owner.getFileName().toString().equals(binder.group(1) + ".java")) {
                    continue;
                }
                final Path template = templateOf(root.resolve(module), owner, java, binder.group(1));
                if (template == null) {
                    continue;
                }
                owners++;
                problems.addAll(problemsIn(root, owner, java, template));
            }
        }
        assertThat(owners)
                .as("template owners found under " + root)
                .isGreaterThan(300);
        assertThat(problems)
                .as("@UiFields nothing reads and ui:fields nothing binds")
                .isEmpty();
    }

    private static List<String> problemsIn(final Path root,
                                           final Path owner,
                                           final String java,
                                           final Path template) {
        final List<String> problems = new ArrayList<>();
        final String code = COMMENT.matcher(java).replaceAll("");
        final Set<String> fields = new LinkedHashSet<>();
        final Matcher declaration = UI_FIELD_DECLARATION.matcher(code);
        while (declaration.find()) {
            final String name = declaration.group(1);
            fields.add(name);
            final String rest = code.substring(0, declaration.start()) + code.substring(declaration.end());
            if (!Pattern.compile("(?<![\\w\"])" + name + "\\b").matcher(rest).find()) {
                problems.add(root.relativize(owner) + ": @UiField " + name + " is never used");
            }
        }
        final String markup = read(template);
        final Matcher uiField = TEMPLATE_UI_FIELD.matcher(markup);
        while (uiField.find()) {
            final String name = uiField.group(1);
            final boolean namedInTemplate = Pattern.compile("\\{" + name + "[.}]").matcher(markup).find();
            if (!fields.contains(name) && !namedInTemplate) {
                problems.add(root.relativize(template) + ": ui:field " + name + " is bound to nothing");
            }
        }
        return problems;
    }

    // The owner's @UiTemplate if it has one, else the template named after it, beside it in either
    // source folder
    private static Path templateOf(final Path module, final Path owner, final String java, final String ownerName) {
        final Matcher uiTemplate = UI_TEMPLATE.matcher(java);
        final String name = uiTemplate.find()
                ? uiTemplate.group(1)
                : ownerName + ".ui.xml";
        for (final String sourceDir : SOURCE_DIRS) {
            final Path base = module.resolve(sourceDir);
            if (owner.startsWith(base)) {
                final Path relativeDir = base.relativize(owner.getParent());
                for (final String candidateDir : SOURCE_DIRS) {
                    final Path candidate = module.resolve(candidateDir).resolve(relativeDir).resolve(name);
                    if (Files.isRegularFile(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static List<Path> javaFiles(final Path module) {
        final Path src = module.resolve("src/main/java");
        if (!Files.isDirectory(src)) {
            return List.of();
        }
        try (final Stream<Path> paths = Files.walk(src)) {
            return paths.filter(p -> p.toString().endsWith(".java")).toList();
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
}
