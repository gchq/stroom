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

import stroom.ai.impl.ErrorTriageRuleConfig;
import stroom.ai.impl.tools.generation.XsltGenerator;
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
    // Stroom's messages for one rejected document, by its EventId or its place in the stream.
    private static final Pattern REJECTED_DOCUMENT = Pattern.compile("^Elasticsearch rejected (?:the document with "
                                                                     + "EventId|document) \\d+ ");
    // Stroom's message for a batch that rejected more documents than it lists one by one.
    private static final Pattern UNLISTED = Pattern.compile("^Elasticsearch rejected (\\d+) documents in this "
                                                            + "batch; only the first (\\d+) ");
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

    /// @param environmentRules Rules from the config, with the bundled rules' fields.
    /// @return Triage by those rules, then the bundled ones.
    /// @throws IllegalArgumentException if a rule has no valid class or no reason, or its message isn't a valid
    /// regular expression.
    public static ErrorTriage withEnvironmentRules(final List<ErrorTriageRuleConfig> environmentRules) {
        final List<Rule> all = new ArrayList<>();
        for (final ErrorTriageRuleConfig rule : NullSafe.list(environmentRules)) {
            if (rule != null) {
                all.add(Rule.of(asMap(rule)));
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
                return new Classified(rule.classification(), rule.reason(), rule.cause());
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
    /// @return `verdict` (blocking, review or clean), `groups_by_class` and `groups`. A group of rejected
    /// documents says, in `listed_only`, when its count leaves out documents Stroom rejected without listing them.
    /// A group whose rule marks it as a cause (text a parser lost, so what it read next was misread) comes first in
    /// its class, saying `cause`, and the other blocking and review groups say, in `may_follow_from`, that they may
    /// be its consequences.
    public Map<String, Object> triage(final List<Marker> markers,
                                      final Set<String> ownElements,
                                      final Integer recordCount,
                                      final int examples,
                                      final List<AcceptedErrors.Entry> accepted) {
        final Map<List<String>, Group> groups = new LinkedHashMap<>();
        long unlisted = 0;
        String listedPerBatch = null;
        for (final Marker original : markers) {
            final Matcher batch = UNLISTED.matcher(original.message());
            if (batch.find()) {
                unlisted += Long.parseLong(batch.group(1)) - Long.parseLong(batch.group(2));
                listedPerBatch = batch.group(2);
            }
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
            // A field no rule reads is logged once a stream, but stepping keeps no value from record to record, so
            // there it is logged for each record: no sign our output is at fault (seen in the e2e suite: every
            // mapping leaving a field out stepped "review").
            if (BENIGN.equals(classified.classification()) && recordCount != null && recordCount > 0
                && Objects.requireNonNullElse(affected, group.count) >= recordCount
                && !firstMessage.contains(XsltGenerator.UNREAD_FIELD)) {
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
            if (unlisted > 0 && REJECTED_DOCUMENT.matcher(firstMessage).find()) {
                entry.put("listed_only", "Counts only the documents Stroom listed: it lists the first "
                                         + listedPerBatch + " a batch rejects, and " + unlisted
                                         + " more were rejected without being listed");
            }
            entry.put("examples", group.examples);
            if (known != null) {
                entry.put("accepted", true);
            } else if (classified.cause()) {
                entry.put("cause", true);
            }
            result.add(entry);
        }
        result.sort(Comparator.<Map<String, Object>>comparingInt(g -> CLASSES.indexOf(String.valueOf(g.get("class"))))
                .thenComparing(g -> !Boolean.TRUE.equals(g.get("cause")))
                .thenComparing(g -> -((Integer) g.get("count"))));
        markConsequences(result);
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

    // Says, on each blocking or review group that isn't a cause, that it may follow from the causes listed first.
    // Seen: a Data Splitter losing records longer than its buffer left it reading from part-way through a record,
    // and the schema errors that followed outnumbered it, so the agent chased them as a quoting fault for 10 minutes.
    private static void markConsequences(final List<Map<String, Object>> groups) {
        final List<String> causes = groups.stream()
                .filter(group -> Boolean.TRUE.equals(group.get("cause")))
                .map(group -> String.valueOf(group.get("element")))
                .distinct()
                .toList();
        if (causes.isEmpty()) {
            return;
        }
        final String note = "The " + String.join(" and ", causes) + " errors listed first lose text, so what is "
                            + "read after it is misread: these may follow from them. Fix those first, then process "
                            + "again before working on these.";
        groups.stream()
                .filter(group -> !Boolean.TRUE.equals(group.get("cause")))
                .filter(group -> BLOCKING.equals(group.get("class")) || REVIEW.equals(group.get("class")))
                .forEach(group -> group.put("may_follow_from", note));
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

    // A config rule as the bundled YAML has it, so both are read alike. Fields left out are left out here too.
    private static Map<String, Object> asMap(final ErrorTriageRuleConfig rule) {
        final Map<String, Object> map = new LinkedHashMap<>();
        putIfSet(map, "severity", rule.getSeverity());
        putIfSet(map, "element", rule.getElement());
        putIfSet(map, "own", rule.getOwn());
        putIfSet(map, "message", rule.getMessage());
        putIfSet(map, "class", rule.getClassification());
        putIfSet(map, "reason", rule.getReason());
        putIfSet(map, "cause", rule.getCause());
        return map;
    }

    private static void putIfSet(final Map<String, Object> map, final String key, final Object value) {
        if (value != null) {
            map.put(key, value);
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
    /// @param cause          True if errors after it may follow from it (see [Rule#cause()]).
    public record Classified(String classification, String reason, boolean cause) {

        /// @param classification blocking, review or benign.
        /// @param reason         Why.
        public Classified(final String classification, final String reason) {
            this(classification, reason, false);
        }
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
    /// @param cause          True if the error loses text (a parser's lost record), so errors after it in the same
    ///                       stream may be its consequences: its group is listed first, and the others say so.
    public record Rule(List<String> severity,
                       String element,
                       Boolean own,
                       Pattern message,
                       String classification,
                       String reason,
                       boolean cause) {

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
                    String.valueOf(reason),
                    Boolean.parseBoolean(String.valueOf(map.get("cause"))));
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
