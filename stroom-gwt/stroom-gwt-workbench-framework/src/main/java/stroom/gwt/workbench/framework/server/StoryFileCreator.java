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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Creates a stories class for a component, for the 'Create a new story' dialog, and registers it
/// in the class that adds all the stories (e.g. `AllStories`), the equivalent of React
/// Storybook creating a `*.stories.tsx` file.
///
/// Imports are added in the order the Stroom checkstyle rules want: `stroom.` imports, then
/// other third party imports, then `java.`/`javax.` imports, then static imports, each group
/// sorted and separated by a blank line.
public class StoryFileCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoryFileCreator.class);

    private static final String REGISTER_MARKER = "return registry;";
    private static final String TITLE_PREFIX = "Widgets/";
    private static final String STORY_REGISTRY_CLASS = "stroom.gwt.workbench.framework.client.story.StoryRegistry";
    private static final String LABEL_CLASS = "com.google.gwt.user.client.ui.Label";
    private static final String IMPORT_PREFIX = "import ";
    private static final String STATIC_IMPORT_PREFIX = "import static ";
    private static final int STATIC_GROUP = 3;

    private final Path storiesSourceRoot;
    private final String storiesPackage;
    private final Path allStoriesFile;

    /// @param storiesSourceRoot The source root to create stories in.
    /// @param storiesPackage    The package to create stories in.
    /// @param allStoriesFile    The class that adds all the stories, which must contain
    ///                          `return registry;`.
    public StoryFileCreator(final Path storiesSourceRoot,
                            final String storiesPackage,
                            final Path allStoriesFile) {
        this.storiesSourceRoot = Objects.requireNonNull(storiesSourceRoot);
        this.storiesPackage = Objects.requireNonNull(storiesPackage);
        this.allStoriesFile = Objects.requireNonNull(allStoriesFile);
    }

    /// Creates the stories for a component whose source isn't known, so it is assumed to have a
    /// public no-argument constructor.
    ///
    /// @param componentClassName The fully qualified name of the component's class.
    /// @return What was created.
    /// @throws IOException              If a file can't be read or written.
    /// @throws IllegalStateException    If the stories already exist or they can't be registered.
    /// @throws IllegalArgumentException If the class name is invalid.
    public Result create(final String componentClassName) throws IOException {
        return create(componentClassName, null);
    }

    /// Creates the stories for a component. Nothing is changed if this fails.
    ///
    /// If the component can be made with a public no-argument constructor the story shows one,
    /// otherwise it shows a label saying the component needs constructing.
    ///
    /// @param componentClassName The fully qualified name of the component's class.
    /// @param componentFile      The component's source file, or null if not known.
    /// @return What was created.
    /// @throws IOException              If a file can't be read or written.
    /// @throws IllegalStateException    If the stories already exist or they can't be registered.
    /// @throws IllegalArgumentException If the class name is invalid.
    public synchronized Result create(final String componentClassName, final Path componentFile)
            throws IOException {
        if (componentClassName == null || !componentClassName.matches("[\\w.]+")) {
            throw new IllegalArgumentException("Invalid class name " + componentClassName);
        }
        final String simpleName = componentClassName.substring(componentClassName.lastIndexOf('.') + 1);
        final String storiesClassName = simpleName + "Stories";
        final Path file = storiesSourceRoot.resolve(storiesPackage.replace('.', '/'))
                .resolve(storiesClassName + ".java");
        if (Files.exists(file)) {
            throw new IllegalStateException(storiesClassName + " already exists");
        }

        // Work out both files before writing either, so a failure doesn't leave a stray file
        final String originalAllStories = Files.readString(allStoriesFile);
        final String newAllStories = register(originalAllStories, storiesPackage + "." + storiesClassName,
                storiesClassName);
        final boolean constructable = componentFile == null || hasPublicNoArgConstructor(componentFile, simpleName);
        final String storiesSource = createSource(componentClassName, simpleName, storiesClassName, constructable);

        Files.createDirectories(file.getParent());
        try {
            Files.writeString(file, storiesSource, StandardOpenOption.CREATE_NEW);
        } catch (final FileAlreadyExistsException e) {
            throw new IllegalStateException(storiesClassName + " already exists", e);
        }
        try {
            Files.writeString(allStoriesFile, newAllStories);
        } catch (final IOException | RuntimeException e) {
            Files.deleteIfExists(file);
            throw e;
        }

        final String title = TITLE_PREFIX + simpleName;
        return new Result(file, StoryIds.storyId(title, "Default"));
    }

    private String createSource(final String componentClassName,
                                final String simpleName,
                                final String storiesClassName,
                                final boolean constructable) {
        final List<String> imports = new ArrayList<>();
        imports.add(STORY_REGISTRY_CLASS);
        final String story;
        final String componentLink;
        if (constructable) {
            if (!isInPackage(componentClassName, storiesPackage)) {
                imports.add(componentClassName);
            }
            componentLink = simpleName;
            story = "context -> new " + simpleName + "()";
        } else {
            // The component isn't imported as it isn't used, so link to it by its full name
            imports.add(LABEL_CLASS);
            componentLink = componentClassName;
            story = "context -> new Label(\"Construct a " + simpleName + " here.\")";
        }

        return "/*\n"
               + " * Copyright " + Year.now().getValue() + " Crown Copyright\n"
               + " *\n"
               + " * Licensed under the Apache License, Version 2.0 (the \"License\");\n"
               + " * you may not use this file except in compliance with the License.\n"
               + " * You may obtain a copy of the License at\n"
               + " *\n"
               + " *     http://www.apache.org/licenses/LICENSE-2.0\n"
               + " *\n"
               + " * Unless required by applicable law or agreed to in writing, software\n"
               + " * distributed under the License is distributed on an \"AS IS\" BASIS,\n"
               + " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
               + " * See the License for the specific language governing permissions and\n"
               + " * limitations under the License.\n"
               + " */\n"
               + "\n"
               + "package " + storiesPackage + ";\n"
               + "\n"
               + importBlock(imports)
               + "\n"
               + "/// Stories for [" + componentLink + "].\n"
               + "public final class " + storiesClassName + " {\n"
               + "\n"
               + "    private " + storiesClassName + "() {\n"
               + "        // Static utility\n"
               + "    }\n"
               + "\n"
               + "    /// Adds the stories to the registry.\n"
               + "    ///\n"
               + "    /// @param registry The registry to add to.\n"
               + "    public static void addTo(final StoryRegistry registry) {\n"
               + "        registry.component(\"" + TITLE_PREFIX + simpleName + "\", " + storiesClassName
               + ".class)\n"
               + "                .story(\"Default\", " + story + ");\n"
               + "    }\n"
               + "}\n";
    }

    /// @param componentFile The component's source file.
    /// @param simpleName    The component's class name without its package.
    /// @return True if the component is a concrete class that can be made with `new X()` from
    /// another package, i.e. it has no constructors or a public one without arguments. If the
    /// source can't be read it is assumed it can be.
    static boolean hasPublicNoArgConstructor(final Path componentFile, final String simpleName) {
        final String source;
        try {
            source = SourceFinder.readSource(componentFile);
        } catch (final UncheckedIOException e) {
            LOGGER.debug("Unable to read {}", componentFile, e);
            return true;
        }
        final String name = Pattern.quote(simpleName);
        if (Pattern.compile("\\babstract\\s+class\\s+" + name + "\\b").matcher(source).find()) {
            return false;
        }
        // A constructor starts a line with optional modifiers then the class name, unlike a call
        // to it which has 'new' first
        final Matcher matcher = Pattern.compile(
                        "^\\s*((?:@\\w+\\s+)*(?:public|protected|private)?\\s*)" + name + "\\s*\\(([^)]*)\\)",
                        Pattern.MULTILINE)
                .matcher(source);
        boolean hasConstructor = false;
        while (matcher.find()) {
            hasConstructor = true;
            if (matcher.group(1).contains("public") && matcher.group(2).isBlank()) {
                return true;
            }
        }
        return !hasConstructor;
    }

    /// Adds `XStories.addTo(registry);` before `return registry;` and imports the class.
    ///
    /// @param source           The source of the class that adds all the stories.
    /// @param storiesClassName The fully qualified name of the stories class.
    /// @param simpleName       The stories class name without its package.
    /// @return The new source.
    /// @throws IllegalStateException If the source doesn't contain `return registry;`.
    String register(final String source, final String storiesClassName, final String simpleName) {
        final int markerIndex = source.indexOf(REGISTER_MARKER);
        if (markerIndex < 0) {
            throw new IllegalStateException(allStoriesFile + " doesn't contain '" + REGISTER_MARKER + "'");
        }
        final int lineStart = source.lastIndexOf('\n', markerIndex) + 1;
        final String indent = source.substring(lineStart, markerIndex);
        final String registered = source.substring(0, lineStart)
                                  + indent + simpleName + ".addTo(registry);\n"
                                  + source.substring(lineStart);

        final String packageName = findPackage(registered);
        if (registered.contains(IMPORT_PREFIX + storiesClassName + ";")
            || isInPackage(storiesClassName, packageName)) {
            return registered;
        }
        return addImport(registered, storiesClassName);
    }

    /// Adds an import in its sorted position, starting a new group if there are no imports in
    /// its group yet.
    ///
    /// @param source    Java source.
    /// @param className The fully qualified name of the class to import.
    /// @return The source with the import.
    static String addImport(final String source, final String className) {
        final List<String> lines = new ArrayList<>(Arrays.asList(source.split("\n", -1)));
        final String importLine = IMPORT_PREFIX + className + ";";
        final int group = importGroup(className);

        int insertBefore = -1;
        int lastInGroup = -1;
        int firstInLaterGroup = -1;
        int lastImport = -1;
        int packageLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            if (line.startsWith("package ")) {
                packageLine = i;
            } else if (line.startsWith(IMPORT_PREFIX)) {
                lastImport = i;
                final int lineGroup = line.startsWith(STATIC_IMPORT_PREFIX)
                        ? STATIC_GROUP
                        : importGroup(importedName(line));
                if (lineGroup == group) {
                    lastInGroup = i;
                    if (insertBefore < 0 && compareImports(importedName(line), className) > 0) {
                        insertBefore = i;
                    }
                } else if (lineGroup > group && firstInLaterGroup < 0) {
                    firstInLaterGroup = i;
                }
            }
        }

        if (insertBefore >= 0) {
            lines.add(insertBefore, importLine);
        } else if (lastInGroup >= 0) {
            lines.add(lastInGroup + 1, importLine);
        } else if (firstInLaterGroup >= 0) {
            lines.addAll(firstInLaterGroup, List.of(importLine, ""));
        } else if (lastImport >= 0) {
            lines.addAll(lastImport + 1, List.of("", importLine));
        } else {
            lines.addAll(packageLine + 1, List.of("", importLine));
        }
        return String.join("\n", lines);
    }

    /// Orders imports the way checkstyle's `CustomImportOrder` does, comparing each part of the
    /// name in turn.
    ///
    /// @param import1 A fully qualified class name.
    /// @param import2 Another fully qualified class name.
    /// @return Less than 0 if `import1` comes first, more than 0 if `import2` does, otherwise 0.
    static int compareImports(final String import1, final String import2) {
        final String[] tokens1 = import1.split("\\.", -1);
        final String[] tokens2 = import2.split("\\.", -1);
        for (int i = 0; i < tokens1.length && i < tokens2.length; i++) {
            final int result = tokens1[i].compareTo(tokens2[i]);
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(tokens1.length, tokens2.length);
    }

    private static String importBlock(final List<String> classNames) {
        final List<String> sorted = new ArrayList<>(classNames);
        sorted.sort((a, b) -> {
            final int groupResult = Integer.compare(importGroup(a), importGroup(b));
            return groupResult != 0
                    ? groupResult
                    : compareImports(a, b);
        });
        final StringBuilder block = new StringBuilder();
        int previousGroup = -1;
        for (final String className : sorted) {
            final int group = importGroup(className);
            if (previousGroup >= 0 && group != previousGroup) {
                block.append("\n");
            }
            block.append(IMPORT_PREFIX).append(className).append(";\n");
            previousGroup = group;
        }
        return block.toString();
    }

    /// @return 0 for `stroom.` classes, 1 for other third party classes and 2 for the JDK's.
    private static int importGroup(final String className) {
        if (className.startsWith("stroom.")) {
            return 0;
        } else if (className.startsWith("java.") || className.startsWith("javax.")) {
            return 2;
        } else {
            return 1;
        }
    }

    private static String importedName(final String importLine) {
        final int end = importLine.indexOf(';');
        return importLine.substring(IMPORT_PREFIX.length(), end < 0
                ? importLine.length()
                : end).trim();
    }

    private static String findPackage(final String source) {
        final Matcher matcher = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE).matcher(source);
        return matcher.find()
                ? matcher.group(1)
                : "";
    }

    private static boolean isInPackage(final String className, final String packageName) {
        final int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 && className.substring(0, lastDot).equals(packageName);
    }


    // --------------------------------------------------------------------------------


    /// What [#create(String, Path)] created.
    public static final class Result {

        private final Path file;
        private final String storyId;

        /// @param file    The new stories file.
        /// @param storyId The id of the new story.
        Result(final Path file, final String storyId) {
            this.file = file;
            this.storyId = storyId;
        }

        /// @return The new stories file.
        public Path getFile() {
            return file;
        }

        /// @return The id of the new story.
        public String getStoryId() {
            return storyId;
        }
    }
}
