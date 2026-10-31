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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The named templates of shared XSLTs that a stage's existing pipelines call, and where: what a new XSLT of the
/// stage calls too, so the environment's common elements are written one way (asked for by the user: event
/// pipelines putting the stream's GUID in Event/Meta, indexing pipelines writing user.id, each through a standard
/// import). Read from the children of the stage's templates ([PipelineTemplates#find], then their children's
/// XSLTs, [PipelineTemplates#sharedXsltUsage]), before an XSLT is drafted and when one is saved: describe_template
/// gave them only at the pipeline step, after the translation had been generated without them.
///
/// Only a call made by at least [#MIN_PIPELINES] pipelines counts (one pipeline's call is its own business), and
/// for each place the call most pipelines make.
class SiblingSharedCalls {

    /// The fewest pipelines that must make a call for a new XSLT to make it too.
    static final int MIN_PIPELINES = 2;
    /// The most of a stage's templates whose children are read.
    static final int MAX_TEMPLATES = 5;

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SiblingSharedCalls.class);
    private static final Pattern CALL = Pattern.compile("<xsl:call-template\\s[^>]*name=\"([^\"]+)\"");
    private static final int MAX_NAMED = 5;

    private final PipelineTemplates templates;

    /// @param templates Finds the stage's templates, their children, and what those call.
    @Inject
    SiblingSharedCalls(final PipelineTemplates templates) {
        this.templates = templates;
    }

    /// @param stage The stage, `translation` or `indexing`.
    /// @return The calls the stage's pipelines make, most used first, one for each place; none when they can't
    /// be read.
    List<Call> forStage(final String stage) {
        try {
            final List<Map<String, Object>> children = new ArrayList<>();
            final List<Map<String, Object>> candidates = JsonValues.maps(templates.find(stage).get("candidates"));
            for (final Map<String, Object> candidate : candidates.subList(0, Math.min(MAX_TEMPLATES,
                    candidates.size()))) {
                if (candidate.get("children") instanceof final Number count && count.intValue() > 0) {
                    templates.children(String.valueOf(candidate.get("uuid"))).stream()
                            .filter(child -> !child.containsKey("unreadable"))
                            .forEach(children::add);
                }
            }
            return children.isEmpty()
                    ? List.of()
                    : calls(templates.sharedXsltUsage(children), !"translation".equals(stage));
        } catch (final RuntimeException e) {
            // An extra: drafting and saving go on without it.
            LOGGER.debug(() -> LogUtil.message("Unable to read the {} pipelines' shared calls: {}", stage,
                    e.getMessage()), e);
            return List.of();
        }
    }

    /// The calls of shared templates that pipelines make, from their usage
    /// ([PipelineTemplates#sharedXsltUsage]).
    ///
    /// @param usage The usage rows, most used first.
    /// @param keys  True for an indexing XSLT, whose places are a document's keys (`user.id`), not paths below
    ///              Event (`Meta`).
    /// @return One call for each place, the one most pipelines make; only those [#MIN_PIPELINES] make.
    static List<Call> calls(final List<Map<String, Object>> usage, final boolean keys) {
        final Map<String, Call> byPlace = new LinkedHashMap<>();
        for (final Map<String, Object> row : usage) {
            final List<String> at = JsonValues.listOrEmpty(row.get("at")).stream()
                    .map(String::valueOf)
                    .toList();
            final Set<String> usedBy = new LinkedHashSet<>();
            JsonValues.listOrEmpty(row.get("used_by")).forEach(name -> usedBy.add(String.valueOf(name)));
            if (!(row.get("template") instanceof final String template) || at.isEmpty()
                || usedBy.size() < MIN_PIPELINES) {
                continue;
            }
            final List<String> places = keys
                    ? at.stream().map(place -> place.replace('/', '.')).toList()
                    : at;
            final Map<String, String> withParams = new LinkedHashMap<>();
            if (row.get("with_params") instanceof final Map<?, ?> params) {
                params.forEach((name, value) -> withParams.put(String.valueOf(name), String.valueOf(value)));
            }
            // Most used first, so the first call for a place is the one most pipelines make.
            byPlace.putIfAbsent(String.join(" ", places), new Call(String.valueOf(row.get("href")), template,
                    places, withParams, List.copyOf(usedBy)));
        }
        return List.copyOf(byPlace.values());
    }

    /// The calls a new XSLT doesn't make as the stage's pipelines do: it doesn't import the shared XSLT, or
    /// doesn't call its template.
    ///
    /// @param calls   The pipelines' calls ([#forStage]).
    /// @param code    The new XSLT.
    /// @param written Whether the new code's mapping or plan writes a place itself, or null when it has none.
    /// @return A warning for each.
    static List<String> notFollowed(final List<Call> calls, final String code, final Predicate<String> written) {
        final String text = NullSafe.string(code);
        final Set<String> imported = new HashSet<>(SharedXslt.importsOf(text));
        final Set<String> called = calledTemplates(text);
        final List<String> out = new ArrayList<>();
        for (final Call call : calls) {
            if (imported.contains(call.href()) && called.contains(call.template())) {
                continue;
            }
            final String instead = written == null
                    ? "doesn't call it"
                    : call.places().stream().anyMatch(written)
                            ? "writes it itself"
                            : "leaves it out";
            out.add(String.join(", ", call.places()) + ": " + call.said() + ", but this XSLT " + instead
                    + ". Call it as they do (shared " + call.entry() + ") so it is written one way, unless the "
                    + "user says this one differs.");
        }
        return out;
    }

    /// @param code An XSLT.
    /// @return The names of the templates it calls.
    static Set<String> calledTemplates(final String code) {
        final Set<String> called = new HashSet<>();
        final Matcher matcher = CALL.matcher(NullSafe.string(code));
        while (matcher.find()) {
            called.add(matcher.group(1));
        }
        return called;
    }

    /// The pipelines' calls added to a drafted translation mapping as its shared entries, each in place of the
    /// draft's own entries below its element: what can be (a call writing one element, given no parameters, which
    /// read the pipelines' own input) and that the mapping doesn't already have.
    ///
    /// @param calls   The pipelines' calls.
    /// @param mapping The drafted mapping's JSON, changed in place.
    /// @return What was added and replaced, and what was left for the agent, for the draft's notes.
    static List<String> intoMapping(final List<Call> calls, final Map<String, Object> mapping) {
        final List<String> notes = new ArrayList<>();
        final List<Object> shared = new ArrayList<>(JsonValues.listOrEmpty(mapping.get("shared")));
        final Set<String> taken = new HashSet<>();
        JsonValues.maps(shared).forEach(entry -> taken.add(String.valueOf(entry.get("at"))));
        for (final Call call : calls) {
            final String at = call.places().getFirst();
            if (call.places().size() > 1 || !call.withParams().isEmpty()) {
                notes.add(String.join(", ", call.places()) + ": " + call.said() + (call.withParams().isEmpty()
                        ? ""
                        : " with parameters " + call.withParams().keySet()) + ". Add a shared entry for it (with "
                          + "this source's own values for its parameters) in place of what the draft maps there, "
                          + "unless the user says this one differs.");
                continue;
            }
            if (taken.contains(at)) {
                continue;
            }
            final List<String> replaced = new ArrayList<>();
            mapping.put("common", without(JsonValues.listOrEmpty(mapping.get("common")), at, replaced));
            final List<Object> events = new ArrayList<>();
            for (final Map<String, Object> rule : JsonValues.maps(mapping.get("events"))) {
                final Map<String, Object> changed = new LinkedHashMap<>(rule);
                changed.put("fields", without(JsonValues.listOrEmpty(rule.get("fields")), at, replaced));
                events.add(changed);
            }
            mapping.put("events", events);
            shared.add(call.entry());
            taken.add(at);
            notes.add(at + ": " + call.said() + ", so this mapping calls it too (shared)" + (replaced.isEmpty()
                    ? ""
                    : ", in place of " + ToolText.list(new ArrayList<>(new LinkedHashSet<>(replaced))))
                      + ". Remove the shared entry only if the user says this one differs.");
        }
        if (!shared.isEmpty()) {
            mapping.put("shared", shared);
        }
        return notes;
    }

    /// The pipelines' calls an index plan makes too, as its shared entries: those writing one key, given no
    /// parameters.
    ///
    /// @param calls The pipelines' calls.
    /// @param notes What was taken, and what was left for the agent, added to.
    /// @return The shared entries.
    static List<SharedTemplate> forPlan(final List<Call> calls, final List<String> notes) {
        final List<SharedTemplate> shared = new ArrayList<>();
        for (final Call call : calls) {
            if (call.places().size() > 1 || !call.withParams().isEmpty()) {
                notes.add(String.join(", ", call.places()) + ": " + call.said() + ". Give shared with it (and "
                          + "this source's values for its parameters) unless the user says this index differs.");
            } else {
                shared.add(call.asShared());
                notes.add(call.places().getFirst() + ": " + call.said() + ", so this plan calls it too. Give "
                          + "shared=[] only if the user says this index differs.");
            }
        }
        return shared;
    }

    // The entries, less those writing the element or below it.
    private static List<Object> without(final List<?> entries, final String at, final List<String> replaced) {
        final List<Object> kept = new ArrayList<>();
        for (final Object entry : entries) {
            final String path = entry instanceof final Map<?, ?> map
                    ? String.valueOf(map.get("path"))
                    : "";
            if (path.equals(at) || path.startsWith(at + "/")) {
                replaced.add(path);
            } else {
                kept.add(entry);
            }
        }
        return kept;
    }

    /// One call of a shared template, and the pipelines that make it.
    ///
    /// @param href       The shared XSLT, as imported.
    /// @param template   The named template called.
    /// @param places     What it writes: elements below Event, or keys of an indexing document.
    /// @param withParams The parameters the pipelines pass, by name.
    /// @param usedBy     The pipelines whose XSLTs call it there.
    record Call(String href, String template, List<String> places, Map<String, String> withParams,
                List<String> usedBy) {

        /// @return It as a mapping's or plan's shared entry.
        SharedTemplate asShared() {
            return new SharedTemplate(href, template, places.getFirst(), Map.of());
        }

        /// @return The entry, as JSON.
        Map<String, Object> entry() {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("href", href);
            entry.put("template", template);
            entry.put("at", places.getFirst());
            return entry;
        }

        /// @return Who calls it, said for a note: `N existing pipelines (a, b, ...) write it with T from H`.
        String said() {
            return usedBy.size() + " existing pipelines (" + String.join(", ", usedBy.subList(0,
                    Math.min(MAX_NAMED, usedBy.size()))) + (usedBy.size() > MAX_NAMED
                    ? ", ..."
                    : "") + ") write it with " + template + " from " + ToolText.repr(href);
        }
    }
}
