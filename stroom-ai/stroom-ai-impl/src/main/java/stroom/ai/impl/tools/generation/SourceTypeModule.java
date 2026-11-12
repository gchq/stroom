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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/// What makes a translation mapping one module of an aggregated feed's translation rather than a whole one (kept
/// with its XSLT as the mapping's `module`, written only when set, so other mappings are kept as they always were):
///
/// * a **source type group**'s (`group`): its templates in a mode of its own, writing its whole Event, the feed's
///   common module's named templates called for the parts every group writes one way, the generated helper functions
///   imported from the feed's helpers module, nothing stylesheet-level;
/// * the feed's **common module** (`common`): a named template a part (EventTime, EventSource's System, Generator and
///   Device, and the site's shared calls), generated from the wrapper's entries (the mapping's `common`).
///
/// The feed's dispatcher ([DispatcherXslt]) imports each group and hands each record to its group's mode.
///
/// @param kind        [#GROUP] or [#COMMON].
/// @param name        The group's name, e.g. `PEGA security`; the feed's for the common module.
/// @param mode        The mode a group's records are handed to, and the prefix of every name the module declares
///                    (templates, modes, functions, global variables), in the mapping's naming style.
/// @param sourceTypes `source_types`: a group's source types (the registry's row names); none for the common module.
/// @param common      A group's import of the feed's common module (its href, `uuid='…', name='…'`), or null for a
///                    group writing its whole Event itself.
/// @param helpers     The import of the feed's helpers module ([HelpersXslt]), or null.
/// @param parts       A group's: the common module's parts it calls where its rules map nothing of their own.
/// @param systemName  `system_name`: a group's EventSource/System/Name, passed to the common module's System part
///                    where it takes one; null for the common module's default.
/// @param field       The feed's source type field, which the dispatcher reads (so a group doesn't report it as a
///                    field no rule reads), or null.
public record SourceTypeModule(String kind,
                               String name,
                               String mode,
                               List<String> sourceTypes,
                               String common,
                               String helpers,
                               List<CommonPart> parts,
                               String systemName,
                               String field) {

    /// A source type group's module.
    public static final String GROUP = "group";
    /// The feed's common module.
    public static final String COMMON = "common";
    /// The kinds of module.
    public static final List<String> KINDS = List.of(GROUP, COMMON);
    /// The parameter the common module's System part takes the group's System Name in.
    public static final String SYSTEM_PARAM = "system_name";
    /// The elements (paths below Event) a part of the common module writes, in schema order: what every group of a
    /// feed writes one way from the wrapper's fields. A shared call's element is a part too.
    public static final List<String> PART_ROOTS = List.of("EventTime", "EventSource/System", "EventSource/Generator",
            "EventSource/Device");
    private static final Pattern NCNAME = Pattern.compile("[A-Za-z_][\\w.-]*", Pattern.UNICODE_CHARACTER_CLASS);

    /// Fills in empty lists for null ones.
    public SourceTypeModule {
        sourceTypes = List.copyOf(Objects.requireNonNullElse(sourceTypes, List.of()));
        parts = List.copyOf(Objects.requireNonNullElse(parts, List.of()));
    }

    /// Reads one from JSON-like values: lax types, defaults and the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static SourceTypeModule read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "SourceTypeModule");
        if (fields == null) {
            return null;
        }
        final String kind = fields.requiredLiteral("kind", KINDS);
        final String name = fields.requiredString("name");
        final String mode = fields.requiredString("mode");
        if (mode != null && !NCNAME.matcher(mode).matches()) {
            reader.valueError(ModelReader.at(loc, "mode"), "mode " + ToolText.repr(mode) + " is not an XML name "
                                                           + "(letters, digits, _ . -)", mode);
        }
        final SourceTypeModule module = new SourceTypeModule(kind, name, mode, fields.strings("source_types"),
                fields.optionalString("common"), fields.optionalString("helpers"),
                fields.models("parts", (value, at) -> CommonPart.read(reader, value, at)),
                fields.optionalString("system_name"), fields.optionalString("field"));
        return fields.ok()
                ? module
                : null;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the mode says, keys in the model's field order.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("kind", kind, null)
                .put("name", name, null)
                .put("mode", this.mode, null)
                .put("source_types", sourceTypes, List.of())
                .put("common", common, null)
                .put("helpers", helpers, null)
                .models("parts", parts, part -> part.toJson(mode))
                .put("system_name", systemName, null)
                .put("field", field, null)
                .build();
    }

    /// @return True for a source type group's module.
    public boolean isGroup() {
        return GROUP.equals(kind);
    }

    /// @return True for the feed's common module.
    public boolean isCommon() {
        return COMMON.equals(kind);
    }

    /// The same module importing other documents: the generator writes whichever set of UUIDs it is given (a
    /// build's working copies, or the promoted originals).
    ///
    /// @param commonHref  The common module's href, or null.
    /// @param helpersHref The helpers module's href, or null.
    /// @return This module with those imports.
    public SourceTypeModule withImports(final String commonHref, final String helpersHref) {
        return new SourceTypeModule(kind, name, mode, sourceTypes, commonHref, helpersHref, parts, systemName,
                field);
    }

    /// @param parts The common module's parts the group calls.
    /// @return This module with those parts.
    public SourceTypeModule withParts(final List<CommonPart> parts) {
        return new SourceTypeModule(kind, name, mode, sourceTypes, common, helpers, parts, systemName, field);
    }

    /// @param path An entry's path below Event, e.g. `EventSource/Device/HostName`.
    /// @return The part of [#PART_ROOTS] it is in, or null for none.
    public static String partOf(final String path) {
        if (path == null) {
            return null;
        }
        final String stripped = ToolText.strip(path, "/");
        return PART_ROOTS.stream()
                .filter(root -> stripped.equals(root) || stripped.startsWith(root + "/"))
                .findFirst()
                .orElse(null);
    }

    /// The common module's parts, as its mapping makes them: each element of [#PART_ROOTS] its `common` entries map,
    /// then each shared call's element, in that order; each a named template named for the element and prefixed with
    /// the module's mode (`pega_common_event_time`). The System part takes [#SYSTEM_PARAM] where its Name is a
    /// value (the default, which a group's System Name replaces).
    ///
    /// @param common The common module's mapping.
    /// @return Its parts.
    public static List<CommonPart> commonParts(final TranslationMapping common) {
        final List<CommonPart> out = new ArrayList<>();
        final String naming = common.style().naming();
        final String prefix = common.module() == null
                ? "common"
                : common.module().mode();
        for (final String root : PART_ROOTS) {
            final boolean mapped = common.common().stream()
                    .anyMatch(entry -> root.equals(partOf(entry.strippedPath())));
            if (mapped) {
                final boolean takesName = "EventSource/System".equals(root) && common.common().stream()
                        .anyMatch(entry -> "EventSource/System/Name".equals(entry.strippedPath())
                                           && entry.value() != null);
                out.add(new CommonPart(root, partName(prefix, root, naming), takesName
                        ? List.of(SYSTEM_PARAM)
                        : List.of(), reads(common.common().stream()
                        .filter(entry -> root.equals(partOf(entry.strippedPath())))
                        .toList())));
            }
        }
        for (final SharedTemplate use : common.shared()) {
            final String at = ToolText.strip(use.at(), "/");
            if (out.stream().noneMatch(part -> part.at().equals(at))) {
                out.add(new CommonPart(at, partName(prefix, at, naming), List.of(), List.of()));
            }
        }
        return out;
    }

    // The record's fields entries read (the first key of a dotted JSON field, the first name of a Data Splitter
    // path), sorted.
    private static List<String> reads(final List<FieldMapping> entries) {
        final Set<String> out = new TreeSet<>();
        for (final FieldMapping entry : FieldGroup.everyEntry(entries)) {
            final List<String> names = new ArrayList<>();
            if (entry.field() != null) {
                names.add(entry.field());
            }
            if (entry.anyOf() != null) {
                names.addAll(entry.anyOf());
            }
            if (entry.join() != null) {
                names.addAll(entry.join());
            }
            names.stream()
                    .filter(name -> !name.isBlank())
                    .map(name -> ToolText.split(ToolText.split(name, ".").getFirst(), "/").getFirst())
                    .forEach(out::add);
        }
        return List.copyOf(out);
    }

    // A part's template: the element's name, prefixed with the module's mode.
    private static String partName(final String prefix, final String at, final String naming) {
        final List<String> steps = ToolText.split(at, "/");
        return XsltNames.styleName(prefix + "-" + steps.getLast(), naming);
    }


    // --------------------------------------------------------------------------------


    /// A part of the feed's common module a group calls: a named template writing one element of the Event.
    ///
    /// @param at       The element it writes, as a path below Event, e.g. `EventTime` or `EventSource/System`.
    /// @param template The named template.
    /// @param params   The parameters it takes ([#SYSTEM_PARAM]).
    /// @param reads    The record's fields it reads, which a group calling it doesn't report as read by no rule.
    public record CommonPart(String at, String template, List<String> params, List<String> reads) {

        /// Fills in empty lists for null ones.
        public CommonPart {
            params = List.copyOf(Objects.requireNonNullElse(params, List.of()));
            reads = List.copyOf(Objects.requireNonNullElse(reads, List.of()));
        }

        /// Reads one from JSON-like values, every error collected.
        ///
        /// @param reader The reader, collecting the errors.
        /// @param input  The value given.
        /// @param loc    Where it is.
        /// @return It, or null (with the errors noted) if it doesn't validate.
        static CommonPart read(final ModelReader reader, final Object input, final List<Object> loc) {
            final ModelReader.Fields fields = reader.fields(input, loc, "CommonPart");
            if (fields == null) {
                return null;
            }
            final CommonPart part = new CommonPart(fields.requiredString("at"), fields.requiredString("template"),
                    fields.strings("params"), fields.strings("reads"));
            return fields.ok()
                    ? part
                    : null;
        }

        /// @param mode How to write it.
        /// @return It as JSON, as the mode says.
        public Map<String, Object> toJson(final DumpMode mode) {
            return new LinkedHashMap<>(mode.builder()
                    .put("at", at, null)
                    .put("template", template, null)
                    .put("params", params, List.of())
                    .put("reads", reads, List.of())
                    .build());
        }
    }
}
