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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// evaluate_pipeline's `reference` section for a reference data pipeline (one writing `reference-data:2` from Raw
/// Reference streams): the maps its XSLT writes (as find_reference_data reads them, [ReferenceData#mapsInXslt]), the
/// keys and values stepping saw in each, the feeds its processor filters read, and the pipelines that look those
/// maps up through a pipeline reference ([ReferenceData#referencesTo]).
///
/// Read as the user: only XSLTs and pipelines they may view; the feeds need the Manage Processors permission.
class ReferenceEvaluation {

    /// The most pipelines listed as looking the maps up.
    static final int USERS_SHOWN = 20;

    private final DocumentAccess documentAccess;
    private final ReferenceData referenceData;
    private final ProcessorFilters processorFilters;

    /// @param documentAccess   Reads the pipeline's XSLTs, as the user.
    /// @param referenceData    Reads the maps an XSLT writes, and the pipelines that look a feed's maps up.
    /// @param processorFilters Gives the feeds the pipeline's processor filters read.
    @Inject
    ReferenceEvaluation(final DocumentAccess documentAccess,
                        final ReferenceData referenceData,
                        final ProcessorFilters processorFilters) {
        this.documentAccess = documentAccess;
        this.referenceData = referenceData;
        this.processorFilters = processorFilters;
    }

    /// @param pipelineUuid A pipeline.
    /// @return The feeds its processor filters read; none if the user may not manage processors.
    Set<String> feeds(final String pipelineUuid) {
        return processorFilters.feedsByPipeline(processorFilters.all()).getOrDefault(pipelineUuid, Set.of());
    }

    /// The `reference` section.
    ///
    /// @param merged       The pipeline, as Stroom runs it.
    /// @param pipelineUuid Its UUID.
    /// @param outputs      What each record stepped wrote at its output element, or null if nothing was stepped.
    /// @return `status`, `maps` (each `{map, key, value, ranges, xslt, seen}`), `maps_seen_only`, `feeds`,
    /// `looked_up_by` and any `notes`.
    Map<String, Object> reference(final MergedPipeline merged,
                                  final String pipelineUuid,
                                  final List<String> outputs) {
        final List<String> notes = new ArrayList<>();
        final Map<String, Map<String, Object>> maps = new LinkedHashMap<>();
        for (final TranslationDoc entry : merged.translationDocs(pipelineUuid)) {
            if (!XsltDoc.TYPE.equals(entry.doc().getType())) {
                continue;
            }
            final Optional<XsltDoc> xslt = documentAccess.read(entry.doc(), XsltDoc.class);
            if (xslt.isEmpty()) {
                notes.add("XSLT '" + entry.doc().getName() + "' can't be read: it doesn't exist, or you may not view "
                          + "it");
                continue;
            }
            for (final Map<String, Object> written : ReferenceData.mapsInXslt(NullSafe.string(
                    xslt.get().getData()))) {
                final Map<String, Object> map = new LinkedHashMap<>(written);
                map.put("xslt", xslt.get().getName());
                maps.putIfAbsent(String.valueOf(written.get("map")), map);
            }
        }
        final Map<String, Map<String, Object>> seen = outputs == null
                ? Map.of()
                : seen(outputs);
        final List<Map<String, Object>> seenOnly = new ArrayList<>();
        seen.forEach((name, found) -> {
            if (maps.containsKey(name)) {
                maps.get(name).put("seen", found);
            } else {
                final Map<String, Object> map = new LinkedHashMap<>();
                map.put("map", name);
                map.putAll(found);
                seenOnly.add(map);
            }
        });
        if (outputs == null) {
            notes.add("Nothing was stepped, so no keys or values were seen.");
        }

        final Set<String> feeds = new TreeSet<>();
        if (processorFilters.canManageProcessors()) {
            feeds.addAll(feeds(pipelineUuid));
        } else {
            notes.add("Its feeds come from its processor filters, which need the Manage Processors permission: "
                      + "the pipelines looking up its maps are found by this pipeline as their loader only.");
        }
        final List<String> unreadable = new ArrayList<>();
        final List<Map<String, Object>> users = referenceData.referencesTo(feeds, pipelineUuid, unreadable);
        unreadable.forEach(said -> notes.add("Not read: " + said));

        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("status", maps.isEmpty() && seen.isEmpty()
                ? "no_maps"
                : "checked");
        section.put("maps", List.copyOf(maps.values()));
        if (!seenOnly.isEmpty()) {
            section.put("maps_seen_only", seenOnly);
        }
        section.put("feeds", List.copyOf(feeds));
        section.put("looked_up_by", users.subList(0, Math.min(USERS_SHOWN, users.size())));
        if (users.isEmpty()) {
            notes.add("No pipeline names its feeds as a reference: nothing looks its maps up yet.");
        }
        if (!notes.isEmpty()) {
            section.put("notes", notes);
        }
        return section;
    }

    /// The `## Reference maps` section of a reference data pipeline's documentation, from its `reference` facts.
    ///
    /// @param facts     The facts, as [#reference] gives them.
    /// @param perStream How many records were stepped of each stream, by stream.
    /// @return The section's body: which records were counted, then each map's key, value, entries and an example,
    /// the feeds that load it and the pipelines that look it up.
    static String markdown(final Map<String, Object> facts, final Map<String, Integer> perStream) {
        final List<String> counted = new ArrayList<>();
        perStream.forEach((stream, count) -> counted.add(count + " of stream " + stream));
        final int records = perStream.values().stream().mapToInt(Integer::intValue).sum();
        final StringBuilder out = new StringBuilder("Counts are of " + records + " sample records ("
                                                    + String.join(", ", counted) + ").\n\n");
        final List<Map<String, Object>> maps = new ArrayList<>();
        maps.addAll(JsonValues.maps(facts.get("maps")));
        maps.addAll(JsonValues.maps(facts.get("maps_seen_only")));
        if (maps.isEmpty()) {
            out.append("No reference map was found in its XSLT or in what the sample records wrote.\n");
        } else {
            out.append("| Map | Key | Value | Entries in the sample | Example key | Example value |\n");
            out.append("| --- | --- | --- | --- | --- | --- |\n");
            for (final Map<String, Object> map : maps) {
                final Map<String, Object> seen = map.get("seen") == null
                        ? map
                        : JsonValues.mapOrEmpty(map.get("seen"));
                out.append("| ").append(cell(map.get("map")))
                        .append(" | ").append(cell(Boolean.TRUE.equals(map.get("ranges"))
                                ? "range of " + ToolText.str(map.get("key"))
                                : map.get("key")))
                        .append(" | ").append(cell(map.get("value")))
                        .append(" | ").append(cell(Objects.requireNonNullElse(seen.get("entries"), 0)))
                        .append(" | ").append(cell(seen.get("example_key")))
                        .append(" | ").append(cell(seen.get("example_value")))
                        .append(" |\n");
            }
        }
        final List<?> feeds = facts.get("feeds") instanceof final List<?> list
                ? list
                : List.of();
        out.append("\nLoaded from feed(s): ").append(feeds.isEmpty()
                ? "none known (no processor filter of the pipeline names one)"
                : String.join(", ", feeds.stream().map(String::valueOf).toList())).append(".\n");
        final List<Map<String, Object>> users = JsonValues.maps(facts.get("looked_up_by"));
        out.append("\nLooked up by: ").append(users.isEmpty()
                ? "no pipeline names its feeds as a reference yet"
                : String.join(", ", users.stream()
                        .map(user -> user.get("pipeline") + " (feed " + user.get("feed") + ")")
                        .toList())).append(".\n");
        return out.toString();
    }

    /// @param outputs What each record stepped wrote: `reference-data:2` documents.
    /// @return Each map written, by name: how many entries, and an example key (or range) and value.
    static Map<String, Map<String, Object>> seen(final List<String> outputs) {
        final Map<String, Map<String, Object>> seen = new LinkedHashMap<>();
        for (final String output : outputs) {
            final Optional<Element> root = XmlTrees.tryParse(NullSafe.string(output));
            if (root.isEmpty()) {
                continue;
            }
            for (final Element reference : XmlTrees.named(root.get(), ReferenceData.REF_NS, "reference")) {
                final String name = XmlTrees.child(reference, ReferenceData.REF_NS, "map")
                        .map(XmlTrees::text)
                        .orElse("?");
                final Map<String, Object> map = seen.computeIfAbsent(name, k -> {
                    final Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("entries", 0);
                    return entry;
                });
                map.merge("entries", 1, (a, b) -> (Integer) a + (Integer) b);
                if (!map.containsKey("example_key")) {
                    map.put("example_key", key(reference));
                    map.put("example_value", value(reference));
                }
            }
        }
        return seen;
    }

    // A value for a table cell: no pipes or line breaks.
    private static String cell(final Object value) {
        if (value == null) {
            return "";
        }
        final String text;
        if (value instanceof final List<?> list) {
            text = String.join(", ", list.stream().map(String::valueOf).toList());
        } else if (value instanceof final Map<?, ?> map) {
            text = String.join(", ", map.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
        } else {
            text = String.valueOf(value);
        }
        return text.replace("|", "\\|").replace("\n", " ");
    }

    // An entry's key, or its range as from-to.
    private static String key(final Element reference) {
        final Optional<Element> key = XmlTrees.child(reference, ReferenceData.REF_NS, "key");
        if (key.isPresent()) {
            return ToolText.head(XmlTrees.text(key.get()), 100);
        }
        return XmlTrees.child(reference, ReferenceData.REF_NS, "range")
                .map(range -> XmlTrees.child(range, ReferenceData.REF_NS, "from").map(XmlTrees::text).orElse("")
                              + "-" + XmlTrees.child(range, ReferenceData.REF_NS, "to").map(XmlTrees::text)
                                      .orElse(""))
                .orElse("");
    }

    // An entry's value: its text, or each element it holds with its text, by the element's name.
    private static Object value(final Element reference) {
        final Optional<Element> value = XmlTrees.child(reference, ReferenceData.REF_NS, "value");
        if (value.isEmpty()) {
            return null;
        }
        if (XmlTrees.children(value.get()).isEmpty()) {
            return ToolText.head(XmlTrees.text(value.get()), 100);
        }
        final Map<String, String> leaves = new LinkedHashMap<>();
        for (final Element element : XmlTrees.descendantsAndSelf(value.get())) {
            if (element != value.get() && XmlTrees.isLeaf(element)) {
                leaves.putIfAbsent(element.getLocalName(), ToolText.head(XmlTrees.text(element), 100));
            }
        }
        return leaves;
    }
}
