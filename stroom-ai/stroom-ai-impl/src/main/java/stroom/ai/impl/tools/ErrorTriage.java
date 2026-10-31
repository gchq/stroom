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

package stroom.ai.impl.tools;

import stroom.util.json.JsonUtil;
import stroom.util.shared.ElementId;
import stroom.util.shared.Location;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.yaml.YamlUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Groups Stroom error markers and classifies each group as blocking (must fix), review (may be caused by the
/// pipeline's own content) or benign (report only), by rules checked in order: the first match decides a group's
/// class.
///
/// Stroom's own rules are bundled (`error_rules.yaml`, next to this class); an administrator's
/// environment-specific rules (the `stroom.ai.errorTriage.rules` config property) come before them.
public final class ErrorTriage {

    /// The bundled rules, a classpath resource next to this class.
    public static final String BUNDLED_RULES = "error_rules.yaml";
    public static final String BLOCKING = "blocking";
    public static final String REVIEW = "review";
    public static final String BENIGN = "benign";
    /// The classes, in the order groups are given.
    public static final List<String> CLASSES = List.of(BLOCKING, REVIEW, BENIGN);

    private static final List<Rule> BUNDLED = loadBundled();
    private static final Pattern QUOTED = Pattern.compile("\"[^\"]*\"|'[^']*'");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern BULK = Pattern.compile("BulkResponse:\\s*(\\{.*\\})\\s*$", Pattern.DOTALL);
    // Stroom's own message for one document Elasticsearch rejected (since it stopped logging the whole response),
    // naming the document by its EventId: the record of the stream it was indexed from.
    private static final Pattern REJECTED_EVENT = Pattern.compile("^Elasticsearch rejected the document with "
                                                                  + "EventId (\\d+) \\(");
    private static final int DEFAULT_EXAMPLES = 3;

    private final List<Rule> rules;

    /// @param rules The rules, checked in order.
    public ErrorTriage(final List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /// @return Triage by the bundled rules alone.
    public static ErrorTriage bundled() {
        return new ErrorTriage(BUNDLED);
    }

    /// @param environmentRules Rules from the config, each a YAML (or JSON) mapping with the bundled rules' fields,
    ///                         e.g. `{element: decorationFilter, message: lookup, class: benign, reason: '...'}`.
    /// @return Triage by those rules, then the bundled ones.
    /// @throws IllegalArgumentException if a rule can't be read.
    public static ErrorTriage withEnvironmentRules(final List<String> environmentRules) {
        final List<Rule> all = new ArrayList<>();
        for (final String text : NullSafe.list(environmentRules)) {
            if (NullSafe.isNonBlankString(text)) {
                all.add(Rule.of(readMap(text)));
            }
        }
        all.addAll(BUNDLED);
        return new ErrorTriage(all);
    }

    /// @return The rules, in the order they are checked.
    public List<Rule> rules() {
        return rules;
    }

    /// @return The class of an error and why: that of the first rule it matches, or review if none does.
    public Classified classify(final String severity, final String element, final boolean own, final String message) {
        for (final Rule rule : rules) {
            if (rule.matches(severity, element, own, message)) {
                return new Classified(rule.classification(), rule.reason());
            }
        }
        return new Classified(REVIEW, "No rule matched");
    }

    /// Collapses the variable parts of a message so repeats of one problem group together: quoted values become
    /// `…` and numbers `N`.
    ///
    /// @param message A message.
    /// @return Its pattern.
    public static String normalise(final String message) {
        final String unquoted = QUOTED.matcher(NullSafe.string(message)).replaceAll("…");
        return DIGITS.matcher(unquoted).replaceAll("N").strip();
    }

    /// @param severity Stroom's severity, as it says it (`WARN`, `FATAL_ERROR` and the like are made `WARNING` and
    ///                 `FATAL`).
    /// @return A marker.
    public static Marker marker(final String severity,
                                final String element,
                                final String message,
                                final Map<String, Integer> location) {
        return new Marker(severity(severity), element, NullSafe.string(message), location, null, null);
    }

    /// @param error A Stroom stored error, from an Error stream or stepping's indicators.
    /// @return It as a marker, with its line and column if it has a real one.
    public static Marker fromStoredError(final StoredError error) {
        final Location location = error.getLocation();
        final int line = location == null
                ? -1
                : location.getLineNo();
        final Map<String, Integer> where = line > 0
                ? Map.of("line", line, "col", location.getColNo())
                : null;
        final String element = NullSafe.getOrElse(error.getElementId(), ElementId::getId, "unknown");
        return marker(NullSafe.getOrElse(error.getSeverity(), Severity::name, "ERROR"),
                NullSafe.isBlankString(element)
                        ? "unknown"
                        : element,
                error.getMessage(),
                where);
    }

    private static String severity(final String severity) {
        final String upper = NullSafe.string(severity).toUpperCase(Locale.ROOT);
        return switch (upper) {
            // Stroom reports WARN in some places and WARNING in others; stepping indicators say FATAL_ERROR.
            case "WARN" -> "WARNING";
            case "FATAL_ERROR" -> "FATAL";
            case "" -> "ERROR";
            default -> upper;
        };
    }

    /// Stroom reported an Elasticsearch bulk request that had failures as one message holding the whole response
    /// (cut short when large): one marker per rejected document instead, saying which (its place in the batch) and
    /// why. Stroom now reports each rejected document itself, naming it by its EventId: such a marker is given
    /// that record, so the rejected events can be stepped or located.
    ///
    /// @param marker A marker.
    /// @return The markers it stands for: itself (of its event's record, for one rejected document Stroom
    /// reported), unless it holds a bulk response with rejected documents.
    public static List<Marker> splitBulk(final Marker marker) {
        final Matcher rejected = REJECTED_EVENT.matcher(marker.message());
        if (rejected.find()) {
            return List.of(marker.withRecord(rejected.group(1)));
        }
        final Matcher found = BULK.matcher(marker.message());
        if (!found.find()) {
            return List.of(marker);
        }
        final List<?> items;
        try {
            items = NullSafe.list((List<?>) readJson(found.group(1)).get("items"));
        } catch (final RuntimeException e) {
            return List.of(marker);
        }
        final List<Marker> out = new ArrayList<>();
        for (int n = 1; n <= items.size(); n++) {
            if (!(items.get(n - 1) instanceof final Map<?, ?> item) || item.isEmpty()
                || !(item.values().iterator().next() instanceof final Map<?, ?> result)) {
                continue;
            }
            final Object error = result.get("error");
            if (error == null || Boolean.FALSE.equals(error)) {
                continue;
            }
            final String reason;
            final String kind;
            if (error instanceof final Map<?, ?> map) {
                reason = String.valueOf(map.get("reason"));
                kind = NullSafe.getOrElse(map.get("type"), String::valueOf, "");
            } else {
                reason = String.valueOf(error);
                kind = "";
            }
            out.add(new Marker(marker.severity(), marker.element(),
                    "Elasticsearch rejected document " + n + " of " + items.size() + " in a bulk request (" + kind
                    + "): " + reason, marker.location(), marker.record(), n));
        }
        return out.isEmpty()
                ? List.of(marker)
                : out;
    }

    /// As [#triage(List, Set, Integer, int, List)] with three examples a group, no record count and nothing
    /// accepted.
    public Map<String, Object> triage(final List<Marker> markers, final Set<String> ownElements) {
        return triage(markers, ownElements, null, DEFAULT_EXAMPLES, List.of());
    }

    /// Groups markers by (severity, element, normalised message) and classifies each group.
    ///
    /// A group that would be benign but occurs at least once per record is raised to review: a lookup that fails
    /// for every record usually means our output is at fault, not the reference data. A group the user accepted as
    /// benign for the pipeline is benign, saying so.
    ///
    /// @param markers     The markers.
    /// @param ownElements The elements the pipeline itself adds or configures.
    /// @param recordCount How many records were stepped or processed, or null if not known.
    /// @param examples    The most different examples kept per group.
    /// @param accepted    The errors the user accepted as benign for the pipeline.
    /// @return `verdict` (blocking, review or clean), `groups_by_class` and `groups`.
    public Map<String, Object> triage(final List<Marker> markers,
                                      final Set<String> ownElements,
                                      final Integer recordCount,
                                      final int examples,
                                      final List<AcceptedErrors.Entry> accepted) {
        final Map<List<String>, Group> groups = new LinkedHashMap<>();
        for (final Marker original : markers) {
            for (final Marker marker : splitBulk(original)) {
                final List<String> key = List.of(marker.severity(), marker.element(), normalise(marker.message()));
                final Group group = groups.computeIfAbsent(key, k -> new Group(marker.severity(), marker.element(),
                        ownElements.contains(marker.element())));
                group.count++;
                if (group.examples.size() < examples
                    && group.examples.stream().noneMatch(e -> e.get("message").equals(marker.message()))) {
                    final Map<String, Object> example = new LinkedHashMap<>();
                    example.put("message", marker.message());
                    example.put("location", marker.location());
                    group.examples.add(example);
                }
                if (marker.record() != null) {
                    group.records.add(marker.record());
                }
            }
        }

        final List<Map<String, Object>> result = new ArrayList<>();
        for (final Group group : groups.values()) {
            final String firstMessage = String.valueOf(group.examples.getFirst().get("message"));
            Classified classified = classify(group.severity, group.element, group.own, firstMessage);
            final Integer affected = group.records.isEmpty()
                    ? null
                    : group.records.size();
            if (BENIGN.equals(classified.classification()) && recordCount != null && recordCount > 0
                && Objects.requireNonNullElse(affected, group.count) >= recordCount) {
                classified = new Classified(REVIEW, "Occurs for every record; our output may be the cause");
            }
            final AcceptedErrors.Entry known = AcceptedErrors.match(accepted, group.element, firstMessage);
            if (known != null) {
                // The user said this kind of error is benign for this pipeline: reported, not raised again.
                classified = new Classified(BENIGN, "Accepted as benign by the user (" + known.accepted() + "): "
                                                    + known.reason());
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("class", classified.classification());
            entry.put("reason", classified.reason());
            entry.put("severity", group.severity);
            entry.put("element", group.element);
            entry.put("own_element", group.own);
            entry.put("count", group.count);
            entry.put("records_affected", affected);
            entry.put("examples", group.examples);
            if (known != null) {
                entry.put("accepted", true);
            }
            result.add(entry);
        }
        result.sort(Comparator.<Map<String, Object>>comparingInt(g -> CLASSES.indexOf(String.valueOf(g.get("class"))))
                .thenComparing(g -> -((Integer) g.get("count"))));
        final Map<String, Integer> byClass = new LinkedHashMap<>();
        CLASSES.forEach(c -> byClass.put(c, 0));
        result.forEach(g -> byClass.merge(String.valueOf(g.get("class")), 1, Integer::sum));
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("verdict", byClass.get(BLOCKING) > 0
                ? BLOCKING
                : byClass.get(REVIEW) > 0
                        ? REVIEW
                        : "clean");
        out.put("groups_by_class", byClass);
        out.put("groups", result);
        return out;
    }

    private static List<Rule> loadBundled() {
        try (final InputStream in = ErrorTriage.class.getResourceAsStream(BUNDLED_RULES)) {
            if (in == null) {
                throw new IllegalStateException("The bundled error triage rules (" + BUNDLED_RULES
                                                + ") are missing");
            }
            final Map<?, ?> yaml = YamlUtil.getMapper().readValue(in, Map.class);
            final List<Rule> rules = new ArrayList<>();
            for (final Object item : NullSafe.list((List<?>) yaml.get("rules"))) {
                rules.add(Rule.of((Map<?, ?>) item));
            }
            return List.copyOf(rules);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<?, ?> readMap(final String text) {
        try {
            return YamlUtil.getMapper().readValue(text, Map.class);
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("Unable to read the error triage rule " + text + ": "
                                               + e.getMessage(), e);
        }
    }

    private static Map<?, ?> readJson(final String json) {
        return JsonUtil.readValue(json, Map.class);
    }


    // --------------------------------------------------------------------------------


    /// One error marker.
    ///
    /// @param severity      FATAL, ERROR, WARNING or INFO.
    /// @param element       The pipeline element's id, or `pipeline` for an error of the whole pipeline.
    /// @param message       What it says.
    /// @param location      Its line and column (`line`, `col`), or null.
    /// @param record        The record it was met in, or null.
    /// @param batchPosition For one document of an Elasticsearch bulk request, its place in the batch; else null.
    public record Marker(String severity,
                         String element,
                         String message,
                         Map<String, Integer> location,
                         String record,
                         Integer batchPosition) {

        /// @param record The record it was met in.
        /// @return This marker, of that record.
        public Marker withRecord(final String record) {
            return new Marker(severity, element, message, location, record, batchPosition);
        }

        /// @param element The element it was met in.
        /// @return This marker, of that element.
        public Marker withElement(final String element) {
            return new Marker(severity, element, message, location, record, batchPosition);
        }
    }

    /// A class and why.
    ///
    /// @param classification blocking, review or benign.
    /// @param reason         Why.
    public record Classified(String classification, String reason) {

    }

    /// One triage rule. Every field but the class and reason is optional, and a rule matches an error that meets
    /// all it has.
    ///
    /// @param severity       The severities it matches, or null for any.
    /// @param element        A glob on the element id (case is ignored), or null for any.
    /// @param own            True for an element the pipeline sets itself, false for one it inherits, or null.
    /// @param message        A regular expression found in the message (case is ignored), or null.
    /// @param classification blocking, review or benign.
    /// @param reason         Why, for the agent and the user.
    public record Rule(List<String> severity,
                       String element,
                       Boolean own,
                       Pattern message,
                       String classification,
                       String reason) {

        /// @param map A rule as the YAML has it, with its class as `class`.
        /// @return The rule.
        /// @throws IllegalArgumentException if it has no valid class or no reason.
        public static Rule of(final Map<?, ?> map) {
            final String classification = NullSafe.getOrElse(map.get("class"), String::valueOf, "");
            if (!CLASSES.contains(classification)) {
                throw new IllegalArgumentException("An error triage rule's class must be one of " + CLASSES
                                                   + ": " + map);
            }
            final Object reason = map.get("reason");
            if (reason == null) {
                throw new IllegalArgumentException("An error triage rule needs a reason: " + map);
            }
            final List<String> severities = map.get("severity") instanceof final List<?> list
                    ? list.stream().map(s -> ErrorTriage.severity(String.valueOf(s))).toList()
                    : null;
            final Object own = map.get("own");
            return new Rule(severities,
                    NullSafe.get(map.get("element"), String::valueOf),
                    own == null
                            ? null
                            : Boolean.valueOf(String.valueOf(own)),
                    NullSafe.get(map.get("message"), m -> Pattern.compile(String.valueOf(m),
                            Pattern.CASE_INSENSITIVE)),
                    classification,
                    String.valueOf(reason));
        }

        /// @return True if an error meets everything the rule has.
        public boolean matches(final String severity, final String element, final boolean own, final String message) {
            return (this.severity == null || this.severity.contains(severity))
                   && (this.element == null || Globs.matches(element.toLowerCase(Locale.ROOT),
                    this.element.toLowerCase(Locale.ROOT)))
                   && (this.own == null || this.own == own)
                   && (this.message == null || this.message.matcher(NullSafe.string(message)).find());
        }
    }

    /// The markers of one group, as they are gathered.
    private static final class Group {

        private final String severity;
        private final String element;
        private final boolean own;
        private final List<Map<String, Object>> examples = new ArrayList<>();
        private final Set<String> records = new LinkedHashSet<>();
        private int count;

        private Group(final String severity, final String element, final boolean own) {
            this.severity = severity;
            this.element = element;
            this.own = own;
        }
    }
}
