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

package stroom.gwt.workbench.framework.server;

import stroom.gwt.workbench.framework.client.story.StoryIds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// Finds Java source files in the workbench's source roots, e.g. to open a story's source in an
/// editor or to list the components that could have stories.
public class SourceFinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceFinder.class);

    private static final String JAVA_EXTENSION = ".java";
    private static final Pattern STORY_PATTERN = Pattern.compile("\\.story\\(\\s*\"([^\"]+)\"");
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile(
            "^public\\s+(?:final\\s+)?class\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE);

    private final List<Path> sourceRoots;

    /// @param sourceRoots The source directories, e.g. `src/main/java` of each GWT client project.
    public SourceFinder(final List<Path> sourceRoots) {
        this.sourceRoots = sourceRoots.stream()
                .map(root -> root.toAbsolutePath().normalize())
                .toList();
    }

    /// @return The source roots.
    public List<Path> getSourceRoots() {
        return sourceRoots;
    }

    /// @param className A fully qualified class name, e.g. `stroom.widget.button.client.Button`.
    /// @return The source file of the class, if it is in one of the source roots. Nested classes
    /// are found in their outer class's file.
    public Optional<Path> findFile(final String className) {
        if (className == null || !className.matches("[\\w.$]+")) {
            return Optional.empty();
        }
        final String outerClassName = className.contains("$")
                ? className.substring(0, className.indexOf('$'))
                : className;
        final String relativePath = outerClassName.replace('.', '/') + JAVA_EXTENSION;
        for (final Path root : sourceRoots) {
            final Path file = root.resolve(relativePath).normalize();
            if (file.startsWith(root) && FilePaths.isRegularFileWithin(file, root)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    /// @param file A Java source file in one of the source roots.
    /// @return The class name for the file, e.g. `stroom.widget.button.client.Button`.
    public Optional<String> toClassName(final Path file) {
        final Path normalised = file.toAbsolutePath().normalize();
        for (final Path root : sourceRoots) {
            if (normalised.startsWith(root) && normalised.toString().endsWith(JAVA_EXTENSION)) {
                final String relative = root.relativize(normalised).toString().replace('\\', '/');
                return Optional.of(relative.substring(0, relative.length() - JAVA_EXTENSION.length())
                        .replace('/', '.'));
            }
        }
        return Optional.empty();
    }

    /// Finds the line that defines a story, i.e. its `.story("ExportName", ...)` call.
    ///
    /// @param file    The source file.
    /// @param storyId The id of the story, e.g. `widgets-buttons-button--with-icons`.
    /// @return The line number (1 based), or 1 if the story can't be found.
    public int findStoryLine(final Path file, final String storyId) {
        if (storyId == null || !storyId.contains("--")) {
            return 1;
        }
        final String storyPart = storyId.substring(storyId.indexOf("--") + 2);
        final String source = readSource(file);
        // Match the whole source as the name may be on the line after '.story('
        final Matcher matcher = STORY_PATTERN.matcher(source);
        while (matcher.find()) {
            if (StoryIds.sanitise(StoryIds.storyNameFromExport(matcher.group(1))).equals(storyPart)) {
                return lineNumber(source, matcher.start());
            }
        }
        return 1;
    }

    /// Lists the classes that could be given stories: public classes in GWT `client` packages,
    /// apart from stories themselves.
    ///
    /// @param query      Text the class name must contain (ignoring case), may be blank.
    /// @param maxResults The most results to return.
    /// @return The matching components, sorted by name.
    public List<Component> findComponents(final String query, final int maxResults) {
        final String lowerCaseQuery = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);
        final List<Component> components = new ArrayList<>();
        for (final Path root : sourceRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (final Stream<Path> files = Files.walk(root)) {
                files.filter(file -> isComponentFile(root, file, lowerCaseQuery))
                        .forEach(file -> toClassName(file).ifPresent(className ->
                                components.add(new Component(className, root.relativize(file).toString()))));
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        components.sort((a, b) -> a.getSimpleName().compareToIgnoreCase(b.getSimpleName()));
        return components.size() > maxResults
                ? components.subList(0, maxResults)
                : components;
    }

    private boolean isComponentFile(final Path root, final Path file, final String lowerCaseQuery) {
        final String name = file.getFileName().toString();
        if (!name.endsWith(JAVA_EXTENSION) || name.endsWith("Stories.java")) {
            return false;
        }
        final String simpleName = name.substring(0, name.length() - JAVA_EXTENSION.length());
        if (!simpleName.toLowerCase(Locale.ROOT).contains(lowerCaseQuery)) {
            return false;
        }
        final String relative = root.relativize(file).toString().replace('\\', '/');
        if (!relative.contains("/client/") || !FilePaths.isRegularFileWithin(file, root)) {
            return false;
        }
        final Optional<String> source = readSourceIfPossible(file);
        if (source.isEmpty()) {
            return false;
        }
        final Matcher matcher = PUBLIC_CLASS_PATTERN.matcher(source.get());
        return matcher.find() && simpleName.equals(matcher.group(1));
    }

    /// @param file A Java source file.
    /// @return The fully qualified names of the classes it imports.
    public List<String> findImports(final Path file) {
        final List<String> imports = new ArrayList<>();
        final Matcher matcher = IMPORT_PATTERN.matcher(readSource(file));
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    /// Reads a source file as UTF-8, or if it isn't valid UTF-8 as ISO-8859-1, so a file with a
    /// different encoding can still be searched.
    ///
    /// @param file The file.
    /// @return Its contents.
    /// @throws UncheckedIOException If the file can't be read.
    static String readSource(final Path file) {
        try {
            final byte[] bytes = Files.readAllBytes(file);
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (final CharacterCodingException e) {
                return new String(bytes, StandardCharsets.ISO_8859_1);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<String> readSourceIfPossible(final Path file) {
        try {
            return Optional.of(readSource(file));
        } catch (final UncheckedIOException e) {
            LOGGER.debug("Unable to read {}", file, e);
            return Optional.empty();
        }
    }

    private static int lineNumber(final String source, final int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }


    // --------------------------------------------------------------------------------


    /// A class that could be given stories.
    public static final class Component {

        private final String className;
        private final String path;

        /// @param className The fully qualified class name.
        /// @param path      The path of its source file, relative to its source root.
        public Component(final String className, final String path) {
            this.className = Objects.requireNonNull(className);
            this.path = Objects.requireNonNull(path);
        }

        /// @return The fully qualified class name.
        public String getClassName() {
            return className;
        }

        /// @return The class name without its package.
        public String getSimpleName() {
            return className.substring(className.lastIndexOf('.') + 1);
        }

        /// @return The path of its source file, relative to its source root.
        public String getPath() {
            return path;
        }
    }
}
