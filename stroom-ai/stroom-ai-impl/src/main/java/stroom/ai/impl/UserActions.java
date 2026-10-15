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

package stroom.ai.impl;

import stroom.ai.impl.UserActionRecord.Status;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Checks the steps that an MCP tool's result says are the user's (its `structuredContent.user_actions`), so
/// they can be offered as buttons.
///
/// Tool results carry data from Stroom, so their text may have been written to mislead. So:
///
/// - only four actions are kept, each with typed arguments; any other entry is dropped;
/// - each button's text is written by Stroom, from its own state, never from the server's `summary`;
/// - a filter must exist, be on the pipeline named, and be disabled (to enable it) or enabled (to disable it);
/// - an Elasticsearch request is only shown if it is one of three simple requests naming an index of the
///   pipelines in the result, and Stroom never runs it.
///
/// The model can neither make nor click a button: only the user's click does anything, and it is checked
/// again then.
public class UserActions {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserActions.class);

    public static final String ENABLE_PROCESSOR_FILTER = "enable_processor_filter";
    public static final String DISABLE_PROCESSOR_FILTER = "disable_processor_filter";
    public static final String OPEN_DOCUMENT = "open_document";
    public static final String ELASTICSEARCH_REQUEST = "elasticsearch_request";

    /// How long a button works for, as for approvals and forms.
    static final Duration EXPIRY = Duration.ofHours(24);
    /// Pipelines that the FastMCP server makes are tagged with this.
    static final String MCP_GENERATED_TAG = "mcp-generated";

    static final int MAX_ACTIONS = 20;
    static final int MAX_AFTER = 10;
    static final int MAX_AFTER_CHARS = 200;
    static final int MAX_DETAILS_CHARS = 500;
    static final int MAX_REQUEST_CHARS = 100_000;

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    private static final Pattern DOC_TYPE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,63}");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    // An Elasticsearch index or template name: lower case, and no wildcards, commas or leading '_', '-' or '+'.
    private static final String ES_NAME = "([a-z0-9][a-z0-9._-]{0,254})";
    private static final Pattern PUT_TEMPLATE = Pattern.compile("PUT _index_template/" + ES_NAME);
    private static final Pattern DELETE_DATA_STREAM = Pattern.compile("DELETE _data_stream/" + ES_NAME);
    private static final Pattern DELETE_INDEX = Pattern.compile("DELETE " + ES_NAME);

    private final UserActionLookup lookup;

    @Inject
    public UserActions(final UserActionLookup lookup) {
        this.lookup = lookup;
    }

    /// Checks the user actions a tool result gave.
    ///
    /// @param userActions The result's `user_actions`, as the server sent them, or null.
    /// @param nowMs       The time now.
    /// @return The actions that may be offered, in the order given, each with Stroom's own text. Empty if there
    /// are none.
    public List<UserActionRecord> check(final Object userActions, final long nowMs) {
        if (!(userActions instanceof final List<?> entries) || entries.isEmpty()) {
            return List.of();
        }
        final List<Map<?, ?>> maps = new ArrayList<>();
        for (final Object entry : entries.subList(0, Math.min(entries.size(), MAX_ACTIONS))) {
            if (entry instanceof final Map<?, ?> map) {
                maps.add(map);
            }
        }

        // The pipelines named in the result, whose indexes an Elasticsearch request may name.
        final Set<DocRef> pipelines = new HashSet<>();
        for (final Map<?, ?> map : maps) {
            final DocRef doc = docRef(map.get("doc"));
            if (doc != null && PipelineDoc.TYPE.equals(doc.getType())) {
                pipelines.add(doc);
            }
        }

        final Map<String, UserActionRecord> checked = new LinkedHashMap<>();
        final Map<String, List<?>> afters = new LinkedHashMap<>();
        for (final Map<?, ?> map : maps) {
            final String id = string(map.get("id"));
            if (id == null || !ID_PATTERN.matcher(id).matches() || checked.containsKey(id)) {
                LOGGER.debug(() -> "Dropping a user action with a missing, bad or repeated id: " + map.get("id"));
                continue;
            }
            final String action = string(map.get("action"));
            final long expiresMs = nowMs + EXPIRY.toMillis();
            final Optional<UserActionRecord> record = switch (NullSafe.string(action)) {
                case ENABLE_PROCESSOR_FILTER -> filterAction(id, map, true, expiresMs);
                case DISABLE_PROCESSOR_FILTER -> filterAction(id, map, false, expiresMs);
                case OPEN_DOCUMENT -> openDocument(id, map, expiresMs);
                case ELASTICSEARCH_REQUEST -> Optional.of(elasticsearchRequest(id, map, pipelines, expiresMs));
                default -> Optional.empty();
            };
            if (record.isPresent()) {
                checked.put(id, record.get());
                if (map.get("after") instanceof final List<?> after) {
                    afters.put(id, after);
                }
            } else {
                LOGGER.debug(() -> "Dropping user action '" + id + "' (" + action + "), as it cannot be offered");
            }
        }

        // What must come first: another action (shown by its label) or a condition (shown as plain text).
        final List<UserActionRecord> result = new ArrayList<>();
        for (final UserActionRecord record : checked.values()) {
            final List<String> after = new ArrayList<>();
            for (final Object item : NullSafe.list(afters.get(record.id()))) {
                final String text = string(item);
                if (text == null || after.size() >= MAX_AFTER) {
                    continue;
                }
                final UserActionRecord other = checked.get(text);
                if (other != null && other != record) {
                    after.add(other.label());
                } else if (other == null && !text.isBlank()) {
                    after.add(truncate(text.strip(), MAX_AFTER_CHARS));
                }
            }
            result.add(new UserActionRecord(record.id(), record.action(), record.label(), record.details(),
                    record.warning(), record.filterId(), record.doc(), record.request(), List.copyOf(after),
                    record.expiresMs(), Status.PENDING, null));
        }
        return List.copyOf(result);
    }

    /// Checks that an action on a filter can still be done, as it may have changed since the button was shown.
    ///
    /// @param record The action.
    /// @return Why it cannot be done, or empty if it can.
    public Optional<String> recheck(final UserActionRecord record) {
        final boolean enable = ENABLE_PROCESSOR_FILTER.equals(record.action());
        final ProcessorFilter filter = record.filterId() == null
                ? null
                : lookup.filter(record.filterId()).orElse(null);
        if (filter == null) {
            return Optional.of("Processor filter " + record.filterId() + " no longer exists, or you cannot see it.");
        }
        if (record.doc() == null || !record.doc().getUuid().equals(filter.getPipelineUuid())) {
            return Optional.of("Processor filter " + record.filterId() + " is no longer on the pipeline named.");
        }
        if (filter.isEnabled() == enable) {
            return Optional.of("Processor filter " + record.filterId() + " is already "
                               + (enable
                    ? "enabled."
                    : "disabled."));
        }
        return Optional.empty();
    }

    private Optional<UserActionRecord> filterAction(final String id,
                                                    final Map<?, ?> map,
                                                    final boolean enable,
                                                    final long expiresMs) {
        final Integer filterId = integer(map.get("filter_id"));
        final DocRef named = docRef(map.get("doc"));
        if (filterId == null || named == null || !PipelineDoc.TYPE.equals(named.getType())) {
            return Optional.empty();
        }
        final ProcessorFilter filter = lookup.filter(filterId).orElse(null);
        // The filter must be on the pipeline named, and there must be something to do.
        if (filter == null
            || filter.getPipeline() == null
            || !PipelineDoc.TYPE.equals(filter.getPipeline().getType())
            || !named.getUuid().equals(filter.getPipelineUuid())
            || filter.isEnabled() == enable) {
            return Optional.empty();
        }
        final DocRef pipeline = lookup.find(named).orElse(null);
        if (pipeline == null) {
            return Optional.empty();
        }
        final String label = (enable
                ? "Enable"
                : "Disable")
                             + " processor filter " + filterId + " on pipeline '" + pipeline.getName() + "'";
        final String warning = lookup.tags(pipeline).contains(MCP_GENERATED_TAG)
                ? null
                : "This pipeline is not tagged '" + MCP_GENERATED_TAG + "', so it was not made by the MCP server.";
        return Optional.of(new UserActionRecord(id, enable
                ? ENABLE_PROCESSOR_FILTER
                : DISABLE_PROCESSOR_FILTER, label, describe(filter), warning, filterId, pipeline, null, List.of(),
                expiresMs, Status.PENDING, null));
    }

    private Optional<UserActionRecord> openDocument(final String id, final Map<?, ?> map, final long expiresMs) {
        final DocRef named = docRef(map.get("doc"));
        if (named == null) {
            return Optional.empty();
        }
        return lookup.find(named).map(doc -> new UserActionRecord(id, OPEN_DOCUMENT,
                "Open " + doc.getType() + " '" + doc.getName() + "'", null, null, null, doc, null, List.of(),
                expiresMs, Status.PENDING, null));
    }

    private UserActionRecord elasticsearchRequest(final String id,
                                                  final Map<?, ?> map,
                                                  final Set<DocRef> pipelines,
                                                  final long expiresMs) {
        final String request = string(map.get("request"));
        final String problem = elasticsearchProblem(request, pipelines);
        final String label = "Elasticsearch request, for the cluster admin to run";
        return problem == null
                ? new UserActionRecord(id, ELASTICSEARCH_REQUEST, label, null, null, null, null, request.strip(),
                List.of(), expiresMs, Status.PENDING, null)
                : new UserActionRecord(id, ELASTICSEARCH_REQUEST, label, null,
                        "Stroom has not shown this request, as " + problem, null, null, null, List.of(), expiresMs,
                        Status.PENDING, null);
    }

    /// @return Why the request cannot be shown, or null if it can.
    private String elasticsearchProblem(final String request, final Set<DocRef> pipelines) {
        if (request == null || request.isBlank() || request.length() > MAX_REQUEST_CHARS) {
            return "it is missing or too long.";
        }
        final String firstLine = request.strip().lines().findFirst().orElse("").strip();
        final Set<String> indexes = new HashSet<>();
        for (final DocRef pipeline : pipelines) {
            indexes.addAll(lookup.elasticIndexNames(pipeline));
        }
        Matcher matcher = PUT_TEMPLATE.matcher(firstLine);
        if (matcher.matches()) {
            // A template is named after the index it is for.
            final String template = matcher.group(1);
            return indexes.stream().anyMatch(template::startsWith)
                    ? null
                    : "the template is not named after an index of the pipelines here.";
        }
        if (request.strip().lines().count() > 1) {
            return "only an index template request may have a body.";
        }
        matcher = DELETE_DATA_STREAM.matcher(firstLine);
        if (!matcher.matches()) {
            matcher = DELETE_INDEX.matcher(firstLine);
        }
        if (matcher.matches()) {
            return indexes.contains(matcher.group(1))
                    ? null
                    : "it does not name an index of the pipelines here.";
        }
        return "it is not one of the requests Stroom allows (PUT _index_template/<name>, "
               + "DELETE _data_stream/<index> or DELETE <index>, with no wildcards or lists).";
    }

    /// @return What a filter selects and how fast it processes, as Stroom has it.
    private static String describe(final ProcessorFilter filter) {
        final String selection = ExpressionDescriber.describe(
                NullSafe.get(filter.getQueryData(), QueryData::getExpression));
        final StringBuilder sb = new StringBuilder(selection == null
                ? "Processes all data"
                : "Processes data where " + selection);
        NullSafe.consume(filter.getMinMetaCreateTimeMs(), ms -> sb.append(", created after ")
                .append(DateUtil.createNormalDateTimeString(ms)));
        NullSafe.consume(filter.getMaxMetaCreateTimeMs(), ms -> sb.append(", created before ")
                .append(DateUtil.createNormalDateTimeString(ms)));
        if (filter.isReprocess()) {
            sb.append(", processing it again");
        }
        sb.append(filter.getMaxProcessingTasks() > 0
                ? ". At most " + filter.getMaxProcessingTasks() + " tasks at a time."
                : ". No limit on tasks at a time.");
        return truncate(sb.toString(), MAX_DETAILS_CHARS);
    }

    private static DocRef docRef(final Object value) {
        if (!(value instanceof final Map<?, ?> map)) {
            return null;
        }
        final String type = string(map.get("type"));
        final String uuid = string(map.get("uuid"));
        if (type == null
            || uuid == null
            || !DOC_TYPE_PATTERN.matcher(type).matches()
            || !UUID_PATTERN.matcher(uuid).matches()) {
            return null;
        }
        // The name is the server's, so it is not used; Stroom looks up its own.
        return new DocRef(type, uuid.toLowerCase(Locale.ROOT));
    }

    private static Integer integer(final Object value) {
        if (value instanceof final Integer i && i > 0) {
            return i;
        }
        if (value instanceof final Long l && l > 0 && l <= Integer.MAX_VALUE) {
            return l.intValue();
        }
        return null;
    }

    private static String string(final Object value) {
        return value instanceof final String s
                ? s
                : null;
    }

    private static String truncate(final String text, final int max) {
        return text.length() > max
                ? text.substring(0, max) + "…"
                : text;
    }
}
