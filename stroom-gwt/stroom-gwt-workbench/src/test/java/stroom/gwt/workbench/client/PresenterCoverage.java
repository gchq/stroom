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

package stroom.gwt.workbench.client;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// Reports which of Stroom's screens and dialogs the workbench's stories cover, by a static
/// reachability analysis of the source (the Gradle task `workbenchPresenterCoverage`).
///
/// * A **screen** is a concrete presenter that is, or extends, one of [#SCREEN_BASE_TYPES] (a
///   content tab, e.g. a document editor or the Users screen).
/// * A **dialog** is a concrete class shown with `ShowPopupEvent.builder(...)`: the class that
///   calls `builder(this)`, or the declared type of the presenter it passes.
/// * A screen or dialog is **covered by a story** if a story's source names it, or if
///   `presenter-coverage-evidence.json` (a test resource) names a story whose play opens it;
///   **reached** if it is only named by a covered presenter or plugin (perhaps shown on a path no
///   story takes); else **not covered**.
///
/// Classes under a `pathways` package are left out (they are being replaced). The analysis is by
/// simple class name, which is unique among Stroom's client classes but for a few.
public final class PresenterCoverage {

    /// The types whose concrete subclasses are screens.
    static final Set<String> SCREEN_BASE_TYPES = Set.of(
            "ContentTabPresenter",
            "DocTabPresenter",
            "TabData",
            "DocumentTabData");

    /// Types a `ShowPopupEvent.builder(...)` argument may be declared as that aren't dialogs.
    static final Set<String> NON_DIALOG_TYPES = Set.of(
            "MyPresenterWidget",
            "MyPresenter",
            "PresenterWidget",
            "Presenter",
            "Widget",
            "Composite");

    /// The resource naming screens and dialogs that plays open but no story names.
    static final String EVIDENCE_RESOURCE = "/presenter-coverage-evidence.json";

    private static final String EXCLUDED_PACKAGE = "/pathways/";
    private static final Pattern IMPORT = Pattern.compile("^import\\s.*$", Pattern.MULTILINE);
    private static final Pattern TYPE_NAME = Pattern.compile("\\b[A-Z]\\w*\\b");
    private static final Pattern POPUP_BUILDER = Pattern.compile(
            "ShowPopupEvent\\s*\\.\\s*builder\\(\\s*([^()]*(?:\\([^()]*\\))?)\\s*\\)");
    private static final Pattern THIS = Pattern.compile("^(?:\\w+\\.)?this$");
    private static final Pattern VARIABLE = Pattern.compile("^(\\w+)(?:\\.get\\(\\))?$");

    private final Map<String, SourceFile> sources;
    private final Set<String> storyReferences;
    private final Map<String, String> evidence;
    private final Map<String, Declaration> declarations = new LinkedHashMap<>();
    private final List<String> unresolvedDialogs = new ArrayList<>();
    private final Set<String> screens = new TreeSet<>();
    private final Set<String> dialogs = new TreeSet<>();

    /// @param sources         Stroom's client source files, by simple class name.
    /// @param storyReferences The type names the workbench's story sources use.
    /// @param evidence        Screens and dialogs that plays open but no story names, each with the
    ///                        id of a story that opens it.
    PresenterCoverage(final Map<String, SourceFile> sources,
                      final Set<String> storyReferences,
                      final Map<String, String> evidence) {
        this.sources = Map.copyOf(sources);
        this.storyReferences = Set.copyOf(storyReferences);
        this.evidence = Map.copyOf(evidence);
        sources.forEach((name, sourceFile) -> {
            final Declaration declaration = parseDeclaration(name, sourceFile.source());
            if (declaration != null) {
                declarations.put(name, declaration);
            }
        });
        findScreens();
        findDialogs();
    }

    /// Prints the report.
    ///
    /// @param args None. The repository's root is the system property `workbench.repoRoot`, or
    ///             else two directories up from the working directory.
    public static void main(final String[] args) {
        if (args.length > 0) {
            throw new IllegalArgumentException("No arguments expected");
        }
        final Path repoRoot = Path.of(System.getProperty("workbench.repoRoot", "../.."))
                .toAbsolutePath()
                .normalize();
        // A report for the user to read, not logging
        System.out.print(load(repoRoot).report());
    }

    /// Reads Stroom's client sources and the workbench's story sources.
    ///
    /// @param repoRoot The repository's root directory.
    /// @return The coverage.
    static PresenterCoverage load(final Path repoRoot) {
        final Map<String, SourceFile> sources = new LinkedHashMap<>();
        final Path workbench = repoRoot.resolve("stroom-gwt");
        for (final Path file : javaFiles(repoRoot)) {
            final String path = toUnixPath(file);
            if (path.contains("/src/main/java/")
                && path.contains("/client/")
                && !file.startsWith(workbench)
                && !path.contains("/build/")) {
                final String name = file.getFileName().toString().replace(".java", "");
                sources.putIfAbsent(name, new SourceFile(name, path, read(file)));
            }
        }

        final Set<String> storyReferences = new HashSet<>();
        for (final Path file : javaFiles(workbench.resolve("stroom-gwt-workbench/src/main/java"))) {
            storyReferences.addAll(referencedTypeNames(read(file), true));
        }
        return new PresenterCoverage(sources, storyReferences, loadEvidence());
    }

    /// @return The screens and dialogs that plays open but no story names, each with the id of a
    /// story that opens it, from [#EVIDENCE_RESOURCE].
    static Map<String, String> loadEvidence() {
        try (final InputStream inputStream = PresenterCoverage.class.getResourceAsStream(EVIDENCE_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing resource " + EVIDENCE_RESOURCE);
            }
            final Map<String, String> evidence = new TreeMap<>();
            new ObjectMapper().readTree(inputStream)
                    .path("covered")
                    .properties()
                    .forEach(entry -> evidence.put(entry.getKey(), entry.getValue().asString()));
            return evidence;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// @return The report.
    String report() {
        final Set<String> reached = reachableNodes();
        final StringBuilder sb = new StringBuilder();
        appendGroup(sb, "Screens", screens, reached);
        appendGroup(sb, "Dialogs", dialogs, reached);
        if (!unresolvedDialogs.isEmpty()) {
            sb.append("\nShowPopupEvent.builder(...) arguments whose type wasn't found (")
                    .append(unresolvedDialogs.size())
                    .append("):\n");
            unresolvedDialogs.forEach(unresolved -> sb.append("  ").append(unresolved).append('\n'));
        }
        return sb.toString();
    }

    /// @return The screens.
    Set<String> getScreens() {
        return Collections.unmodifiableSet(screens);
    }

    /// @return The dialogs.
    Set<String> getDialogs() {
        return Collections.unmodifiableSet(dialogs);
    }

    /// @return The dialogs shown with an argument whose type wasn't found, as `Class: argument`.
    List<String> getUnresolvedDialogs() {
        return Collections.unmodifiableList(unresolvedDialogs);
    }

    /// @return The screens and dialogs that plays open but no story names, each with the id of a
    /// story that opens it.
    Map<String, String> getEvidence() {
        return evidence;
    }

    /// @param name A screen or dialog.
    /// @return How the stories cover it.
    Coverage getCoverage(final String name) {
        if (isCoveredByStory(name)) {
            return Coverage.STORY;
        } else if (reachableNodes().contains(name)) {
            return Coverage.REACHED;
        } else {
            return Coverage.NONE;
        }
    }

    /// Parses a type's declaration.
    ///
    /// @param name   The type's simple name.
    /// @param source Its source.
    /// @return The declaration, or null if the source doesn't declare the type.
    static Declaration parseDeclaration(final String name, final String source) {
        final Matcher matcher = Pattern.compile(
                "^(?:public\\s+|protected\\s+|private\\s+)?(?:static\\s+)?(?:final\\s+)?(abstract\\s+)?"
                + "(?:final\\s+)?(class|interface|enum|record)\\s+" + Pattern.quote(name) + "\\b(.*?)\\{",
                Pattern.MULTILINE | Pattern.DOTALL).matcher(source);
        if (!matcher.find()) {
            return null;
        }
        final String header = stripTypeArguments(matcher.group(3));
        final Matcher extendsMatcher = Pattern.compile("\\bextends\\s+([\\w.]+)").matcher(header);
        final String superclass = extendsMatcher.find()
                ? simpleName(extendsMatcher.group(1))
                : null;
        final List<String> interfaces = new ArrayList<>();
        final Matcher implementsMatcher = Pattern.compile("\\bimplements\\s+(.*)", Pattern.DOTALL).matcher(header);
        if (implementsMatcher.find()) {
            for (final String part : implementsMatcher.group(1).split(",")) {
                interfaces.add(simpleName(part.trim()));
            }
        }
        return new Declaration(
                "class".equals(matcher.group(2)),
                matcher.group(1) != null,
                superclass,
                interfaces);
    }

    /// Finds the type names a source uses.
    ///
    /// @param source         The source.
    /// @param includeImports Whether to count the names its imports use.
    /// @return The type names.
    static Set<String> referencedTypeNames(final String source, final boolean includeImports) {
        final String text = includeImports
                ? source
                : IMPORT.matcher(source).replaceAll("");
        final Set<String> names = new HashSet<>();
        final Matcher matcher = TYPE_NAME.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group());
        }
        return names;
    }

    /// Finds the type that a `ShowPopupEvent.builder(...)` argument is declared as in a source.
    ///
    /// @param className The class whose source it is.
    /// @param source    The source.
    /// @param argument  The argument, e.g. `this`, `presenter` or `presenterProvider.get()`.
    /// @return The type's simple name, or null if it can't be found.
    static String resolveDialogType(final String className, final String source, final String argument) {
        if (THIS.matcher(argument).matches()) {
            return className;
        }
        final Matcher variable = VARIABLE.matcher(argument);
        if (!variable.matches()) {
            return null;
        }
        final String name = Pattern.quote(variable.group(1));
        final Matcher provided = Pattern.compile("\\b(?:Provider|LazyValue)<\\s*(\\w+)\\s*>\\s+" + name + "\\b")
                .matcher(source);
        if (provided.find()) {
            return provided.group(1);
        }
        final Matcher declared = Pattern.compile("\\b([A-Z]\\w*)(?:<[^<>]*>)?\\s+" + name + "\\s*[=;,)]")
                .matcher(source);
        return declared.find()
                ? declared.group(1)
                : null;
    }

    private void findScreens() {
        declarations.forEach((name, declaration) -> {
            // Only presenters: other classes implement TabData only to describe a tab
            if (name.endsWith("Presenter") && isConcreteClass(name) && !isExcluded(name)) {
                final Set<String> ancestors = ancestors(name);
                ancestors.retainAll(SCREEN_BASE_TYPES);
                if (!ancestors.isEmpty()) {
                    screens.add(name);
                }
            }
        });
    }

    private void findDialogs() {
        sources.forEach((name, sourceFile) -> {
            final Matcher matcher = POPUP_BUILDER.matcher(sourceFile.source());
            while (matcher.find()) {
                final String argument = matcher.group(1).trim();
                final String type = resolveDialogType(name, sourceFile.source(), argument);
                if (type == null) {
                    unresolvedDialogs.add(name + ": " + argument);
                } else if (isConcreteClass(type) && !NON_DIALOG_TYPES.contains(type) && !isExcluded(type)) {
                    dialogs.add(type);
                }
            }
        });
    }

    private Set<String> ancestors(final String name) {
        final Set<String> ancestors = new HashSet<>();
        final Deque<String> toVisit = new ArrayDeque<>();
        toVisit.add(name);
        while (!toVisit.isEmpty()) {
            final Declaration declaration = declarations.get(toVisit.pop());
            if (declaration != null) {
                final List<String> parents = new ArrayList<>(declaration.interfaces());
                if (declaration.superclass() != null) {
                    parents.add(declaration.superclass());
                }
                for (final String parent : parents) {
                    if (ancestors.add(parent)) {
                        toVisit.add(parent);
                    }
                }
            }
        }
        return ancestors;
    }

    // The presenters, plugins and dialogs that the stories name, and those they name in turn
    private Set<String> reachableNodes() {
        final Set<String> reached = new HashSet<>();
        final Deque<String> toVisit = new ArrayDeque<>();
        storyReferences.stream()
                .filter(this::isNode)
                .forEach(toVisit::add);
        while (!toVisit.isEmpty()) {
            final String name = toVisit.pop();
            if (reached.add(name)) {
                for (final String referenced : referencedTypeNames(sources.get(name).source(), false)) {
                    if (isNode(referenced) && !reached.contains(referenced)) {
                        toVisit.add(referenced);
                    }
                }
            }
        }
        return reached;
    }

    private boolean isCoveredByStory(final String name) {
        return storyReferences.contains(name) || evidence.containsKey(name);
    }

    private boolean isNode(final String name) {
        return sources.containsKey(name)
               && (name.endsWith("Presenter") || name.endsWith("Plugin") || dialogs.contains(name));
    }

    private boolean isConcreteClass(final String name) {
        final Declaration declaration = declarations.get(name);
        return declaration != null && declaration.isClass() && !declaration.isAbstract();
    }

    private boolean isExcluded(final String name) {
        final SourceFile sourceFile = sources.get(name);
        return sourceFile != null && sourceFile.path().contains(EXCLUDED_PACKAGE);
    }

    private void appendGroup(final StringBuilder sb,
                             final String title,
                             final Set<String> names,
                             final Set<String> reached) {
        final List<String> byStory = new ArrayList<>();
        final List<String> onlyReached = new ArrayList<>();
        final List<String> notCovered = new ArrayList<>();
        for (final String name : names) {
            if (isCoveredByStory(name)) {
                byStory.add(name);
            } else if (reached.contains(name)) {
                onlyReached.add(name);
            } else {
                notCovered.add(name);
            }
        }
        sb.append(title).append(": ").append(names.size())
                .append(", covered by a story: ").append(byStory.size())
                .append(", only reached through another presenter: ").append(onlyReached.size())
                .append(", not covered: ").append(notCovered.size())
                .append('\n');
        appendNames(sb, "Only reached through another presenter", onlyReached);
        appendNames(sb, "Not covered", notCovered);
    }

    private static void appendNames(final StringBuilder sb, final String heading, final List<String> names) {
        if (!names.isEmpty()) {
            sb.append("  ").append(heading).append(":\n");
            names.forEach(name -> sb.append("    ").append(name).append('\n'));
        }
    }

    private static String stripTypeArguments(final String text) {
        String stripped = text;
        String previous;
        do {
            previous = stripped;
            stripped = stripped.replaceAll("<[^<>]*>", "");
        } while (!stripped.equals(previous));
        return stripped;
    }

    private static String simpleName(final String name) {
        final String trimmed = name.trim();
        return trimmed.substring(trimmed.lastIndexOf('.') + 1);
    }

    private static List<Path> javaFiles(final Path dir) {
        try (final Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !toUnixPath(path).contains("/node_modules/"))
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

    private static String toUnixPath(final Path path) {
        return path.toString().replace('\\', '/');
    }


    // --------------------------------------------------------------------------------


    /// How the stories cover a screen or dialog.
    enum Coverage {
        /// A story names it.
        STORY,
        /// A presenter or plugin that the stories cover names it.
        REACHED,
        /// Neither.
        NONE
    }


    // --------------------------------------------------------------------------------


    /// A source file.
    ///
    /// @param name   The simple name of the type it declares.
    /// @param path   Its path, with `/` separators.
    /// @param source Its content.
    record SourceFile(String name, String path, String source) {

    }


    // --------------------------------------------------------------------------------


    /// A type's declaration.
    ///
    /// @param isClass    Whether it is a class (not an interface, enum or record).
    /// @param isAbstract Whether it is abstract.
    /// @param superclass The simple name of the type it extends, or null.
    /// @param interfaces The simple names of the interfaces it implements.
    record Declaration(boolean isClass, boolean isAbstract, String superclass, List<String> interfaces) {

    }
}
