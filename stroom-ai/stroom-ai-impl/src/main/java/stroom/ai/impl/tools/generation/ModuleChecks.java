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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The checks of an aggregated feed's translation modules ([SourceTypeModule], [DispatcherXslt], [HelpersXslt]),
/// made by the generator on what it writes and by check_xslt on any code saved or stepped (a hand-edited group, say).
///
/// At run time the dispatcher, every group, the common module, the helpers and the site's libraries are one compiled
/// stylesheet, so a group holds only templates in its own mode, its own names prefixed with its mode, and its imports:
/// a stylesheet-level declaration in a group (xsl:output, a key, a global parameter) would apply to every group, and a
/// template matching the record outside its mode would take every group's records. Imports must not cycle (a group
/// importing the dispatcher), and they nest no deeper than [#MAX_IMPORT_DEPTH].
///
/// A module says what it is in a mark Saxon ignores, a user-defined data element:
/// `<mcp:module kind="group" name="PEGA security" mode="pega_security"><mcp:source-type>...</mcp:source-type>`.
public final class ModuleChecks {

    /// The local name of a module's mark, in the `mcp` namespace.
    public static final String MARKER = "module";
    /// The local name of a group's source type, inside its mark.
    public static final String SOURCE_TYPE = "source-type";
    /// The kind of the feed's dispatcher's mark.
    public static final String DISPATCHER = "dispatcher";
    /// The kind of the feed's helpers module's mark.
    public static final String HELPERS = "helpers";
    /// How deep imports may nest below the XSLT checked: dispatcher, group, common module, a site library and its
    /// own imports fit well within it.
    public static final int MAX_IMPORT_DEPTH = 8;

    // What only the stylesheet as a whole may declare: the dispatcher's, never a group's.
    private static final Set<String> STYLESHEET_LEVEL = Set.of("output", "strip-space", "preserve-space", "param",
            "key", "accumulator", "decimal-format", "namespace-alias", "character-map", "attribute-set", "mode",
            "global-context-item", "use-package", "include", "import-schema", "expose", "accept");
    // An import or include and its href, as Stroom's resolver finds them.
    private static final Pattern IMPORT = Pattern.compile(
            "<(?:[\\w.-]+:)?(?:import|include)\\b[^>]*?\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    private ModuleChecks() {
        // Static only.
    }

    /// @param xslt An XSLT's text.
    /// @return Its module mark, if it has one (and parses).
    public static Optional<Marker> marker(final String xslt) {
        final XmlElement root;
        try {
            root = XmlParser.parse(xslt);
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
        return marker(root);
    }

    /// @param root An XSLT's stylesheet element.
    /// @return Its module mark, if it has one.
    public static Optional<Marker> marker(final XmlElement root) {
        for (final XmlElement child : root.childElements()) {
            if (child.is(MappingFacts.MCP_NS, MARKER)) {
                final List<String> types = child.childElements(MappingFacts.MCP_NS, SOURCE_TYPE).stream()
                        .map(type -> ToolText.strip(String.valueOf(type.text()), " "))
                        .toList();
                return Optional.of(new Marker(String.valueOf(child.get("kind")), child.get("name"),
                        child.get("mode"), types));
            }
        }
        return Optional.empty();
    }

    /// What is wrong with a source type group's module: anything stylesheet-level, a template matching outside its
    /// mode (or matching the document), and a name it declares that isn't prefixed with its mode. An XSLT marked as
    /// another kind of module, or not marked, has none of these problems.
    ///
    /// @param xslt An XSLT's text.
    /// @return The problems, each saying why; none for a sound group, or an XSLT that isn't one.
    public static List<String> groupProblems(final String xslt) {
        final XmlElement root;
        try {
            root = XmlParser.parse(xslt);
        } catch (final RuntimeException e) {
            return List.of();
        }
        final Optional<Marker> marker = marker(root);
        if (marker.isEmpty() || !SourceTypeModule.GROUP.equals(marker.get().kind())) {
            return List.of();
        }
        final String mode = marker.get().mode();
        final String group = "Source type group '" + marker.get().name() + "' (mode " + mode + ")";
        final List<String> out = new ArrayList<>();
        if (mode == null || mode.isBlank()) {
            out.add(group + " has no mode in its mark: the dispatcher hands its records to that mode");
            return out;
        }
        for (final XmlElement child : root.childElements()) {
            if (!XmlTrees.XSL.equals(child.namespace())) {
                continue;
            }
            final String what = child.localName();
            if (STYLESHEET_LEVEL.contains(what)) {
                out.add(group + " declares xsl:" + what + ", which is stylesheet-level: in the one stylesheet the "
                        + "dispatcher and every group compile into it would apply to every group. It belongs in the "
                        + "dispatcher, which owns the stylesheet's declarations" + ("include".equals(what)
                        ? " (to use another XSLT's templates, xsl:import it)"
                        : ""));
            } else if ("template".equals(what)) {
                templateProblem(child, mode, group).ifPresent(out::add);
            } else if ("function".equals(what) || "variable".equals(what)) {
                final String name = String.valueOf(child.get("name"));
                final String local = name.contains(":")
                        ? name.substring(name.indexOf(':') + 1)
                        : name;
                if (!ownName(local, mode)) {
                    out.add(group + " declares xsl:" + what + " " + name + ", not prefixed with its mode: another "
                            + "group's of the same name would meet it in the dispatcher's stylesheet, and the one "
                            + "imported last would be used by both. Name it " + mode + "_...");
                }
            }
        }
        return out;
    }

    // A template matching outside the group's mode, matching the document, or named without the mode's prefix.
    private static Optional<String> templateProblem(final XmlElement template, final String mode, final String group) {
        final String match = template.get("match");
        final String name = template.get("name");
        if (match != null) {
            final String modes = template.get("mode");
            if (modes == null || modes.isBlank()) {
                return Optional.of(group + " has a template matching " + match + " outside its mode: it would take "
                                   + "records the dispatcher hands to other groups. Put it in mode " + mode
                                   + " (or a mode named " + mode + "_...)");
            }
            for (final String one : ToolText.split(modes.strip(), " ")) {
                if (!one.isEmpty() && !ownName(one, mode)) {
                    return Optional.of(group + " has a template in mode " + one + ", not its own: modes "
                                       + "#all, #default and another group's take other groups' records. Use "
                                       + mode + " or a mode named " + mode + "_...");
                }
            }
            if ("/".equals(match.strip())) {
                return Optional.of(group + " has a template matching the document (/): the dispatcher owns the "
                                   + "document and the Events; a group's templates match the records it is handed");
            }
        }
        if (name != null) {
            final String local = name.contains(":")
                    ? name.substring(name.indexOf(':') + 1)
                    : name;
            if (!ownName(local, mode)) {
                return Optional.of(group + " has a named template " + name + ", not prefixed with its mode: "
                                   + "another group's of the same name would meet it in the dispatcher's "
                                   + "stylesheet, and the one imported last would run for both. Name it " + mode
                                   + "_...");
            }
        }
        return Optional.empty();
    }

    /// @param name A name a group declares (a template's, a function's, a variable's, or a mode).
    /// @param mode The group's mode.
    /// @return True if the name is the group's own: its mode, or its mode followed by a separator (`_`, `-`, or the
    /// capital letter or digit a camelCase or PascalCase name starts its next word with), so mode `pega` can't claim
    /// `pegamail`'s names.
    public static boolean ownName(final String name, final String mode) {
        if (name == null || mode == null || mode.isEmpty() || !name.startsWith(mode)) {
            return false;
        }
        if (name.length() == mode.length()) {
            return true;
        }
        final char next = name.charAt(mode.length());
        return next == '_' || next == '-' || Character.isUpperCase(next) || Character.isDigit(next);
    }

    /// Two groups' modes meet when they are the same, or one is the other followed by a separator (`pega` and
    /// `pega_mail`): the names one declares could then be the other's, and the one imported last would be used by
    /// both.
    ///
    /// @param one   A group's mode.
    /// @param other Another group's mode.
    /// @return True if they meet.
    public static boolean modesMeet(final String one, final String other) {
        return ownName(one, other) || ownName(other, one);
    }

    /// @param xslt An XSLT's text, or null.
    /// @return The names it declares for importers to use: its named templates', functions' and global variables',
    /// and the modes of its templates (`#all` and the like aside); none for text that isn't XML.
    public static Set<String> declaredNames(final String xslt) {
        final Set<String> out = new LinkedHashSet<>();
        final XmlElement root;
        try {
            root = XmlParser.parse(NullSafe.string(xslt));
        } catch (final RuntimeException e) {
            return out;
        }
        for (final XmlElement child : root.childElements()) {
            if (!XmlTrees.XSL.equals(child.namespace())) {
                continue;
            }
            if (Set.of("template", "function", "variable").contains(child.localName()) && child.get("name") != null) {
                out.add(child.get("name"));
            }
            if ("template".equals(child.localName()) && child.get("mode") != null) {
                ToolText.split(child.get("mode").strip(), " ").stream()
                        .filter(mode -> !mode.isEmpty() && !mode.startsWith("#"))
                        .forEach(out::add);
            }
        }
        return out;
    }

    /// @param code An XSLT's text.
    /// @param name A name another XSLT declares ([#declaredNames]).
    /// @return True if the code names it: as a whole name, not part of a longer one.
    public static boolean uses(final String code, final String name) {
        return Pattern.compile("(?<![\\w.:-])" + Pattern.quote(name) + "(?![\\w.-])").matcher(NullSafe.string(code))
                .find();
    }

    /// @param xslt An XSLT's text.
    /// @return The hrefs of its imports and includes, in document order.
    public static List<String> hrefs(final String xslt) {
        final List<String> out = new ArrayList<>();
        final Matcher matcher = IMPORT.matcher(xslt == null
                ? ""
                : xslt);
        while (matcher.find()) {
            out.add(matcher.group(1) != null
                    ? matcher.group(1)
                    : matcher.group(2));
        }
        return out;
    }

    /// What is wrong with an XSLT's imports, followed through the documents they name: a cycle (an XSLT importing
    /// one that imports it, a module importing the dispatcher), and nesting deeper than [#MAX_IMPORT_DEPTH]. An
    /// href that names nothing is left to the check of missing imports.
    ///
    /// @param xslt    The XSLT's text.
    /// @param ownUuid Its document's UUID, where it is saved, or null.
    /// @param resolve Finds the document an href names (as Stroom's resolver does: by name, else the UUID in it).
    /// @return The problems, each saying why.
    public static List<String> importProblems(final String xslt,
                                              final String ownUuid,
                                              final Function<String, Optional<Imported>> resolve) {
        final List<String> out = new ArrayList<>();
        final Optional<Marker> own = marker(xslt);
        final boolean isDispatcher = own.map(marker -> DISPATCHER.equals(marker.kind())).orElse(false);
        final List<String> stack = new ArrayList<>();
        stack.add(ownUuid == null
                ? "(this XSLT)"
                : ownUuid);
        final List<String> names = new ArrayList<>(List.of(own.map(Marker::name).filter(n -> !n.isBlank())
                .orElse("this XSLT")));
        walk(xslt, isDispatcher, stack, names, resolve, out, new ArrayList<>());
        return out;
    }

    private static void walk(final String xslt,
                             final boolean fromDispatcher,
                             final List<String> stack,
                             final List<String> names,
                             final Function<String, Optional<Imported>> resolve,
                             final List<String> out,
                             final List<String> seen) {
        for (final String href : hrefs(xslt)) {
            final Optional<Imported> found = resolve.apply(href);
            if (found.isEmpty()) {
                continue;
            }
            final Imported imported = found.get();
            final List<String> chain = new ArrayList<>(names);
            chain.add(imported.name());
            if (stack.contains(imported.uuid())) {
                note(out, "Imports cycle: " + String.join(" -> ", chain) + ". An XSLT can't import one that "
                          + "imports it; Saxon refuses the stylesheet, and every record fails");
                continue;
            }
            final boolean importsDispatcher = marker(imported.text())
                    .map(marker -> DISPATCHER.equals(marker.kind()))
                    .orElse(false);
            if (importsDispatcher && !fromDispatcher) {
                note(out, String.join(" -> ", chain) + ": a module imports the feed's dispatcher. The dispatcher "
                          + "imports each source type group (and they the common and helpers modules), never the "
                          + "other way round: drop the import of '" + imported.name() + "'");
                continue;
            }
            if (stack.size() > MAX_IMPORT_DEPTH) {
                note(out, "Imports nest more than " + MAX_IMPORT_DEPTH + " deep (" + String.join(" -> ", chain)
                          + "): import what is needed nearer the top");
                continue;
            }
            if (seen.contains(imported.uuid())) {
                // The same module imported again (every group importing the common module): allowed, and its
                // imports were followed once already.
                continue;
            }
            seen.add(imported.uuid());
            stack.add(imported.uuid());
            names.add(imported.name());
            walk(imported.text(), false, stack, names, resolve, out, seen);
            stack.removeLast();
            names.removeLast();
        }
    }

    private static void note(final List<String> out, final String problem) {
        if (!out.contains(problem)) {
            out.add(problem);
        }
    }


    // --------------------------------------------------------------------------------


    /// A module's mark.
    ///
    /// @param kind        `group`, `common`, [#HELPERS] or [#DISPATCHER].
    /// @param name        Its name.
    /// @param mode        Its mode (a group's records are handed to it), the prefix of its names.
    /// @param sourceTypes A group's source types.
    public record Marker(String kind, String name, String mode, List<String> sourceTypes) {

    }

    /// An imported document, found.
    ///
    /// @param uuid Its UUID.
    /// @param name Its name.
    /// @param text Its code.
    public record Imported(String uuid, String name, String text) {

    }
}
