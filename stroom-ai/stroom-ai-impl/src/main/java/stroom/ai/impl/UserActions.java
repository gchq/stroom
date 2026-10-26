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
import stroom.ai.impl.vis.VisualisationPlan;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.shared.QueryDoc;
import stroom.util.date.DateUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Checks the steps that an MCP tool's result says are the user's (its `structuredContent.user_actions`), or that
/// one of Stroom's own tools offers (e.g. propose_query's create_query), so they can be offered as buttons.
///
/// Tool results carry data from Stroom, so their text may have been written to mislead. So:
///
/// - only the actions named here are kept, each with typed arguments; any other entry is dropped. Those that
///   create documents or change editors (e.g. create_query, create_dashboard, apply_code_change and
///   apply_documentation_change) are only kept when one of Stroom's own tools offers them;
/// - each button's text is written by Stroom, from its own state, never from the server's `summary`;
/// - a filter must exist, be on the pipeline named, and be disabled (to enable it) or enabled (to disable it);
/// - an Elasticsearch request is only shown if it is one of three simple requests naming an index of the
///   pipelines in the result (the one it gives as its `doc`, or another action's), and Stroom never runs it. One
///   that isn't is left out, and logged, as the model has the request in the result's text to give the user.
///
/// The model can neither make nor click a button: only the user's click does anything, and it is checked
/// again then.
public class UserActions {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserActions.class);

    public static final String ENABLE_PROCESSOR_FILTER = "enable_processor_filter";
    public static final String DISABLE_PROCESSOR_FILTER = "disable_processor_filter";
    public static final String OPEN_DOCUMENT = "open_document";
    public static final String ELASTICSEARCH_REQUEST = "elasticsearch_request";
    /// Opens Stroom's New Query dialog with a suggested name; once created, the query is given the StroomQL.
    public static final String CREATE_QUERY = "create_query";
    /// Opens a review of a code change the agent proposed for a stepping element, which the user can apply to the
    /// element's editor (unsaved) and undo.
    public static final String APPLY_CODE_CHANGE = "apply_code_change";
    /// A revised query the agent proposed (only from Stroom's own propose_query_change tool) for the user's open
    /// Query, which the button puts in its editor (unsaved) and can undo.
    public static final String APPLY_QUERY_CHANGE = "apply_query_change";
    /// A change to a dashboard table that the agent proposed (only from Stroom's own propose_dashboard_change tool),
    /// which the button applies to the open dashboard, unsaved.
    public static final String APPLY_DASHBOARD_CHANGE = "apply_dashboard_change";
    /// Opens a review of a change to a document's Documentation tab that the agent proposed (only from Stroom's own
    /// propose_documentation_change tool), which the user can apply to the tab's editor (unsaved) and undo.
    public static final String APPLY_DOCUMENTATION_CHANGE = "apply_documentation_change";
    /// Opens Stroom's New Dashboard dialog with a suggested name; once created, the dashboard is given a query, a
    /// table and optionally a data preview, built from the StroomQL (only from Stroom's own propose_dashboard tool).
    public static final String CREATE_DASHBOARD = "create_dashboard";
    /// Opens Stroom's New Visualisation dialog with a suggested name in the dashboard's (or query's) folder; once
    /// created, the Visualisation is given a chart the agent planned, drawn with ECharts, and is added to the
    /// dashboard (or the query's `show` clause), unsaved (only from Stroom's own propose_visualisation tool).
    public static final String CREATE_VISUALISATION = "create_visualisation";
    /// Creates several planned Visualisations in one folder the user picks, and adds them to a dashboard.
    public static final String CREATE_VISUALISATIONS = "create_visualisations";
    /// The most Visualisations one create_visualisations button creates.
    static final int MAX_VISUALISATIONS = 12;

    /// How long a button works for, as for approvals and forms.
    static final Duration EXPIRY = Duration.ofHours(24);
    /// Pipelines that the FastMCP server makes are tagged with this.
    static final String MCP_GENERATED_TAG = "mcp-generated";

    static final int MAX_ACTIONS = 20;
    static final int MAX_AFTER = 10;
    static final int MAX_AFTER_CHARS = 200;
    static final int MAX_DETAILS_CHARS = 500;
    static final int MAX_REQUEST_CHARS = 100_000;
    static final int MAX_NAME_CHARS = 200;
    /// The most characters of code a proposed code change may have, on either side.
    public static final int MAX_CODE_CHARS = 500_000;
    // The most of a request Stroom will not show that is logged.
    private static final int MAX_LOGGED_CHARS = 200;

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    // A pipeline element's id, as the pipeline editor allows.
    private static final Pattern ELEMENT_ID_PATTERN = Pattern.compile("[^\\p{Cntrl}]{1,200}");
    // A SHA-256, as hex.
    private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");
    // The hash of a dashboard's settings, as the browser and server work it out.
    private static final Pattern SNAPSHOT_HASH_PATTERN = Pattern.compile("[0-9a-fA-F]{1,16}");
    private static final Pattern DOC_TYPE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,63}");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    // An Elasticsearch index or template name: lower case, and no wildcards, commas or leading '_', '-' or '+'.
    private static final String ES_NAME = "([a-z0-9][a-z0-9._-]{0,254})";
    private static final Pattern PUT_TEMPLATE = Pattern.compile("PUT _index_template/" + ES_NAME);
    private static final Pattern DELETE_DATA_STREAM = Pattern.compile("DELETE _data_stream/" + ES_NAME);
    private static final Pattern DELETE_INDEX = Pattern.compile("DELETE " + ES_NAME);
    // What a variable in an index name may be filled in with: the characters of an index name, no wildcards or
    // lists.
    private static final String VARIABLE_VALUE = "[a-z0-9._-]+";

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
        return check(userActions, List.of(), nowMs);
    }

    /// Checks the user actions a tool result gave: those an MCP server sent, and those one of Stroom's own tools
    /// offered.
    ///
    /// @param userActions    The result's `user_actions`, as an MCP server sent them, or null.
    /// @param builtInActions The actions one of Stroom's own tools offered, as maps of the same form.
    /// @param nowMs          The time now.
    /// @return The actions that may be offered, in the order given (an MCP server's first), each with Stroom's own
    /// text. Empty if there are none.
    public List<UserActionRecord> check(final Object userActions,
                                        final List<? extends Map<?, ?>> builtInActions,
                                        final long nowMs) {
        final List<Map<?, ?>> maps = new ArrayList<>();
        if (userActions instanceof final List<?> entries) {
            for (final Object entry : entries.subList(0, Math.min(entries.size(), MAX_ACTIONS))) {
                if (entry instanceof final Map<?, ?> map) {
                    maps.add(map);
                }
            }
        }
        // Only Stroom's own tools may offer actions that create documents. Kept by identity, so an MCP server
        // can't claim to be one.
        final Set<Map<?, ?>> builtIn = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final Map<?, ?> map : NullSafe.list(builtInActions)) {
            if (map != null && maps.size() < MAX_ACTIONS) {
                maps.add(map);
                builtIn.add(map);
            }
        }
        if (maps.isEmpty()) {
            return List.of();
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
                case ELASTICSEARCH_REQUEST -> elasticsearchRequest(id, map, pipelines, expiresMs);
                case CREATE_QUERY -> builtIn.contains(map)
                        ? createQuery(id, map, expiresMs)
                        : Optional.empty();
                case APPLY_CODE_CHANGE -> builtIn.contains(map)
                        ? codeChange(id, map, expiresMs)
                        : Optional.empty();
                case APPLY_QUERY_CHANGE -> builtIn.contains(map)
                        ? queryChange(id, map, expiresMs)
                        : Optional.empty();
                case APPLY_DASHBOARD_CHANGE -> builtIn.contains(map)
                        ? dashboardChange(id, map, expiresMs)
                        : Optional.empty();
                case APPLY_DOCUMENTATION_CHANGE -> builtIn.contains(map)
                        ? documentationChange(id, map, expiresMs)
                        : Optional.empty();
                case CREATE_DASHBOARD -> builtIn.contains(map)
                        ? createDashboard(id, map, expiresMs)
                        : Optional.empty();
                case CREATE_VISUALISATIONS -> builtIn.contains(map)
                        ? createVisualisations(id, map, expiresMs)
                        : Optional.empty();
                case CREATE_VISUALISATION -> builtIn.contains(map)
                        ? createVisualisation(id, map, expiresMs)
                        : Optional.empty();
                default -> Optional.empty();
            };
            if (record.isPresent()) {
                checked.put(id, record.get());
                if (map.get("after") instanceof final List<?> after) {
                    afters.put(id, after);
                }
            } else if (builtIn.contains(map)) {
                // Stroom's own tools check what they offer, and have told the model it is shown, so this is a fault.
                LOGGER.warn("Dropping user action '{}' ({}) offered by one of Stroom's own tools, as it cannot be "
                            + "offered", id, action);
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
                    record.expiresMs(), Status.PENDING, null, record.name(), record.codeChange(),
                    record.dashboardChange(), record.newDashboard()));
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

    /// A button that opens Stroom's New Query dialog with the suggested name, for a query the user may run: its
    /// data source must exist and the user must have Use on it.
    private Optional<UserActionRecord> createQuery(final String id, final Map<?, ?> map, final long expiresMs) {
        final String name = NullSafe.get(string(map.get("name")), String::strip);
        final String query = NullSafe.get(string(map.get("query")), String::strip);
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_CHARS || hasControlCharacters(name)
            || query == null || query.isEmpty() || query.length() > MAX_REQUEST_CHARS) {
            return Optional.empty();
        }
        final DocRef dataSource = lookup.usableDataSource(query).orElse(null);
        if (dataSource == null) {
            return Optional.empty();
        }
        final String description = NullSafe.get(string(map.get("description")), String::strip);
        return Optional.of(new UserActionRecord(id, CREATE_QUERY, "Create query '" + name + "', reading "
                                                                  + dataSource.getType() + " '"
                                                                  + dataSource.getName() + "'",
                NullSafe.isBlankString(description)
                        ? null
                        : truncate(description, MAX_DETAILS_CHARS),
                null, null, dataSource, query, List.of(), expiresMs, Status.PENDING, null, name));
    }

    /// A button that opens Stroom's New Dashboard dialog with the suggested name, for a dashboard built from a query
    /// the user may run: its data source must exist and the user must have Use on it.
    private Optional<UserActionRecord> createDashboard(final String id, final Map<?, ?> map, final long expiresMs) {
        final String name = NullSafe.get(string(map.get("name")), String::strip);
        final String query = NullSafe.get(string(map.get("query")), String::strip);
        final String queryType = string(map.get("query_type"));
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_CHARS || hasControlCharacters(name)
            || query == null || query.isEmpty() || query.length() > MAX_REQUEST_CHARS
            || queryType == null || !DashboardBuilder.QUERY_TYPES.contains(queryType)
            || !(map.get("preview") instanceof final Boolean preview)) {
            return Optional.empty();
        }
        final DocRef dataSource = lookup.usableDataSource(query).orElse(null);
        if (dataSource == null) {
            return Optional.empty();
        }
        final List<String> outline = new ArrayList<>();
        if (map.get("outline") instanceof final List<?> lines) {
            for (final Object line : lines.subList(0, Math.min(lines.size(), MAX_AFTER))) {
                final String text = string(line);
                if (text != null && !text.isBlank()) {
                    outline.add(truncate(text.strip(), MAX_DETAILS_CHARS));
                }
            }
        }
        final String description = NullSafe.get(string(map.get("description")), String::strip);
        final String label = "Create dashboard '" + name + "', reading " + dataSource.getType() + " '"
                             + dataSource.getName() + "', with "
                             + (DashboardBuilder.CLASSIC.equals(queryType)
                ? "a classic (expression) query and a table"
                : "a StroomQL query and its table")
                             + (preview
                ? " and a data preview"
                : "");
        return Optional.of(new UserActionRecord(id, CREATE_DASHBOARD, label,
                NullSafe.isBlankString(description)
                        ? null
                        : truncate(description, MAX_DETAILS_CHARS),
                null, null, dataSource, query, List.of(), expiresMs, Status.PENDING, null, name, null, null,
                new UserActionRecord.NewDashboard(queryType, preview, List.copyOf(outline))));
    }

    /// A button that opens Stroom's New Visualisation dialog with the suggested name, for a chart planned for a
    /// dashboard or StroomQL query the user may see. The plan is kept as JSON in `request`, the dashboard or query
    /// in `doc` and the suggested name in `name`; it is read and checked again when the button is clicked.
    private Optional<UserActionRecord> createVisualisation(final String id,
                                                           final Map<?, ?> map,
                                                           final long expiresMs) {
        final DocRef named = docRef(map.get("doc"));
        final String name = NullSafe.get(string(map.get("name")), String::strip);
        final String planJson = string(map.get("plan"));
        if (named == null
            || !(DashboardDoc.TYPE.equals(named.getType()) || QueryDoc.TYPE.equals(named.getType()))
            || name == null || name.isEmpty() || name.length() > MAX_NAME_CHARS || hasControlCharacters(name)
            || planJson == null || planJson.length() > MAX_REQUEST_CHARS) {
            return Optional.empty();
        }
        final VisualisationPlan plan;
        try {
            plan = VisualisationPlan.fromJson(planJson);
        } catch (final IllegalArgumentException e) {
            LOGGER.debug(() -> "Dropping a visualisation that can't be read: " + e.getMessage());
            return Optional.empty();
        }
        // A Query's show clause is planned for the Query; an embedded query's, and a table's chart, for a dashboard.
        final boolean forQueryDoc = plan.isQuery() && !plan.isEmbeddedQuery();
        if (forQueryDoc != QueryDoc.TYPE.equals(named.getType())) {
            return Optional.empty();
        }
        final DocRef target = lookup.find(named).orElse(null);
        if (target == null) {
            return Optional.empty();
        }
        final String summary = NullSafe.get(string(map.get("summary")), String::strip);
        final String label = plan.isReplacement()
                ? "Update visualisation '" + plan.replace().visualisation().getName() + "' to a "
                  + plan.chartKind().displayName().toLowerCase(Locale.ROOT) + " of table '" + plan.table()
                  + "', renamed '" + name + "' (saved at once), in pane '" + plan.replace().componentName()
                  + "' of dashboard '" + target.getName() + "'"
                : "Create visualisation '" + name + "' (" + plan.chartKind().displayName() + ") "
                  + (plan.isEmbeddedQuery()
                        ? "and show it with embedded query '" + plan.table() + "' on dashboard '"
                          + target.getName() + "'"
                        : plan.isQuery()
                        ? "and show it with query '" + target.getName() + "'"
                        : "and add it to dashboard '" + target.getName() + "', drawing table '" + plan.table()
                          + "'");
        return Optional.of(new UserActionRecord(id, CREATE_VISUALISATION, label,
                NullSafe.isBlankString(summary)
                        ? null
                        : truncate(summary, MAX_DETAILS_CHARS),
                null, null, target, plan.toJson(), List.of(), expiresMs, Status.PENDING, null, name));
    }

    /// A button that creates several charts planned for a dashboard's tables, in one folder the user picks, for a
    /// dashboard the user may see. The charts (each a name and a plan) are kept as JSON in `request`; they are read
    /// and checked again when the button is clicked. Each is a new chart of a dashboard table: none replaces one,
    /// nor goes in a query's show clause.
    private Optional<UserActionRecord> createVisualisations(final String id,
                                                            final Map<?, ?> map,
                                                            final long expiresMs) {
        final DocRef named = docRef(map.get("doc"));
        if (named == null || !DashboardDoc.TYPE.equals(named.getType())
            || !(map.get("charts") instanceof final List<?> charts) || charts.isEmpty()
            || charts.size() > MAX_VISUALISATIONS) {
            return Optional.empty();
        }
        final List<Map<String, String>> kept = new ArrayList<>();
        final List<String> kinds = new ArrayList<>();
        for (final Object item : charts) {
            if (!(item instanceof final Map<?, ?> chart)) {
                return Optional.empty();
            }
            final String name = NullSafe.get(string(chart.get("name")), String::strip);
            final String planJson = string(chart.get("plan"));
            if (name == null || name.isEmpty() || name.length() > MAX_NAME_CHARS || hasControlCharacters(name)
                || planJson == null || planJson.length() > MAX_REQUEST_CHARS / MAX_VISUALISATIONS) {
                return Optional.empty();
            }
            final VisualisationPlan plan;
            try {
                plan = VisualisationPlan.fromJson(planJson);
            } catch (final IllegalArgumentException e) {
                LOGGER.debug(() -> "Dropping visualisations that can't be read: " + e.getMessage());
                return Optional.empty();
            }
            if (plan.isQuery() || plan.isReplacement()) {
                return Optional.empty();
            }
            kept.add(Map.of("name", name, "plan", plan.toJson()));
            kinds.add(plan.chartKind().displayName().toLowerCase(Locale.ROOT));
        }
        final DocRef target = lookup.find(named).orElse(null);
        if (target == null) {
            return Optional.empty();
        }
        final String summary = NullSafe.get(string(map.get("summary")), String::strip);
        final String label = "Create " + kept.size() + " visualisations (" + String.join(", ", kinds)
                             + ") and add them to dashboard '" + target.getName() + "'";
        return Optional.of(new UserActionRecord(id, CREATE_VISUALISATIONS, label,
                NullSafe.isBlankString(summary)
                        ? null
                        : truncate(summary, MAX_DETAILS_CHARS),
                null, null, target, JsonUtil.writeValueAsString(kept, false), List.of(), expiresMs,
                Status.PENDING, null, null));
    }

    /// A button that opens a review of a change to a stepping element's code, for an element whose document exists
    /// and the user may view. Applying it needs Edit, which is checked when the user applies it.
    private Optional<UserActionRecord> codeChange(final String id, final Map<?, ?> map, final long expiresMs) {
        final DocRef named = docRef(map.get("doc"));
        final DocRef pipelineNamed = docRef(map.get("pipeline"));
        final String element = string(map.get("element"));
        final String proposed = string(map.get("request"));
        final String base = string(map.get("base"));
        final String baseHash = string(map.get("base_hash"));
        if (named == null || pipelineNamed == null || !PipelineDoc.TYPE.equals(pipelineNamed.getType())
            || element == null || !ELEMENT_ID_PATTERN.matcher(element).matches()
            || proposed == null || proposed.isEmpty() || proposed.length() > MAX_CODE_CHARS
            || base == null || base.length() > MAX_CODE_CHARS
            || baseHash == null || !HASH_PATTERN.matcher(baseHash).matches()) {
            return Optional.empty();
        }
        final DocRef doc = lookup.find(named).orElse(null);
        final DocRef pipeline = lookup.find(pipelineNamed).orElse(null);
        if (doc == null || pipeline == null) {
            return Optional.empty();
        }
        final Integer added = count(map.get("added"));
        final Integer removed = count(map.get("removed"));
        final String summary = NullSafe.get(string(map.get("summary")), String::strip);
        final String lines = "+" + Objects.requireNonNullElse(added, 0) + " "
                             + "−" + Objects.requireNonNullElse(removed, 0) + " lines";
        final String label = "Change to " + doc.getType() + " '" + doc.getName() + "' (element '" + element
                             + "' of pipeline '" + pipeline.getName() + "'): " + lines;
        return Optional.of(new UserActionRecord(id, APPLY_CODE_CHANGE, label,
                NullSafe.isBlankString(summary)
                        ? null
                        : truncate(summary, MAX_DETAILS_CHARS),
                null, null, doc, proposed, List.of(), expiresMs, Status.PENDING, null, null,
                new UserActionRecord.CodeChange(pipeline, element, base, baseHash.toLowerCase(Locale.ROOT),
                        string(map.get("snapshot_hash")), Objects.requireNonNullElse(added, 0),
                        Objects.requireNonNullElse(removed, 0))));
    }

    /// A button that puts a revised query in the editor of the user's open Query, for a Query that exists and the
    /// user may view. Applying it needs Edit, which is checked when the user clicks it. The code change it holds has
    /// no pipeline or element.
    private Optional<UserActionRecord> queryChange(final String id, final Map<?, ?> map, final long expiresMs) {
        final DocRef named = docRef(map.get("doc"));
        final String proposed = string(map.get("request"));
        final String base = string(map.get("base"));
        final String baseHash = string(map.get("base_hash"));
        if (named == null || !QueryDoc.TYPE.equals(named.getType())
            || proposed == null || proposed.isEmpty() || proposed.length() > MAX_CODE_CHARS
            || base == null || base.length() > MAX_CODE_CHARS
            || baseHash == null || !HASH_PATTERN.matcher(baseHash).matches()) {
            return Optional.empty();
        }
        final DocRef doc = lookup.find(named).orElse(null);
        if (doc == null) {
            return Optional.empty();
        }
        final Integer added = count(map.get("added"));
        final Integer removed = count(map.get("removed"));
        final String summary = NullSafe.get(string(map.get("summary")), String::strip);
        final String label = "Change to Query '" + doc.getName() + "': +" + Objects.requireNonNullElse(added, 0)
                             + " −" + Objects.requireNonNullElse(removed, 0) + " lines";
        return Optional.of(new UserActionRecord(id, APPLY_QUERY_CHANGE, label,
                NullSafe.isBlankString(summary)
                        ? null
                        : truncate(summary, MAX_DETAILS_CHARS),
                null, null, doc, proposed, List.of(), expiresMs, Status.PENDING, null, null,
                new UserActionRecord.CodeChange(null, null, base, baseHash.toLowerCase(Locale.ROOT),
                        string(map.get("snapshot_hash")), Objects.requireNonNullElse(added, 0),
                        Objects.requireNonNullElse(removed, 0))));
    }

    /// A button that opens a review of a change to the Documentation tab of a document that exists and the user may
    /// view. Applying it needs Edit, which is checked when the user applies it. The code change it holds has no
    /// pipeline or element.
    private Optional<UserActionRecord> documentationChange(final String id,
                                                           final Map<?, ?> map,
                                                           final long expiresMs) {
        final DocRef named = docRef(map.get("doc"));
        final String proposed = string(map.get("request"));
        final String base = string(map.get("base"));
        final String baseHash = string(map.get("base_hash"));
        if (named == null
            || proposed == null || proposed.length() > MAX_CODE_CHARS
            || base == null || base.length() > MAX_CODE_CHARS
            || baseHash == null || !HASH_PATTERN.matcher(baseHash).matches()) {
            return Optional.empty();
        }
        final DocRef doc = lookup.find(named).orElse(null);
        if (doc == null) {
            return Optional.empty();
        }
        final Integer added = count(map.get("added"));
        final Integer removed = count(map.get("removed"));
        final String summary = NullSafe.get(string(map.get("summary")), String::strip);
        final String label = "Change the documentation of " + doc.getType() + " '" + doc.getName() + "': +"
                             + Objects.requireNonNullElse(added, 0) + " −" + Objects.requireNonNullElse(removed, 0)
                             + " lines";
        return Optional.of(new UserActionRecord(id, APPLY_DOCUMENTATION_CHANGE, label,
                NullSafe.isBlankString(summary)
                        ? null
                        : truncate(summary, MAX_DETAILS_CHARS),
                null, null, doc, proposed, List.of(), expiresMs, Status.PENDING, null, null,
                new UserActionRecord.CodeChange(null, null, base, baseHash.toLowerCase(Locale.ROOT),
                        string(map.get("snapshot_hash")), Objects.requireNonNullElse(added, 0),
                        Objects.requireNonNullElse(removed, 0))));
    }

    /// A button that applies a change to a table on a dashboard the user may view, while the dashboard still has the
    /// settings it was proposed against. Applying it needs Edit, which is checked when the user clicks it; whether
    /// they could edit it when it was proposed is kept, so the button can say so.
    private Optional<UserActionRecord> dashboardChange(final String id, final Map<?, ?> map, final long expiresMs) {
        final DocRef named = docRef(map.get("doc"));
        final String component = NullSafe.get(string(map.get("component")), String::strip);
        final String componentId = NullSafe.get(string(map.get("component_id")), String::strip);
        final String snapshotHash = string(map.get("snapshot_hash"));
        if (named == null || !DashboardDoc.TYPE.equals(named.getType())
            || component == null || component.isEmpty() || component.length() > MAX_NAME_CHARS
            || hasControlCharacters(component)
            || componentId == null || !ID_PATTERN.matcher(componentId).matches()
            || snapshotHash == null || !SNAPSHOT_HASH_PATTERN.matcher(snapshotHash).matches()) {
            return Optional.empty();
        }
        final List<DashboardChanges.Change> changes;
        try {
            changes = DashboardChanges.parse(map.get("changes"));
        } catch (final IllegalArgumentException e) {
            LOGGER.debug(() -> "Dropping a dashboard change that can't be read: " + e.getMessage());
            return Optional.empty();
        }
        // A query's expression is applied as Stroom built it from the model's conditions, never as given.
        if (changes.stream().anyMatch(change -> DashboardChanges.SET_EXPRESSION.equals(change.op())
                                                && change.query() == null)) {
            LOGGER.debug("Dropping a dashboard change that sets a query's expression without one");
            return Optional.empty();
        }
        final DocRef dashboard = lookup.find(named).orElse(null);
        if (dashboard == null) {
            return Optional.empty();
        }
        final String summary = NullSafe.get(string(map.get("summary")), String::strip);
        final String label = "Change to table '" + component + "' on dashboard '" + dashboard.getName() + "': "
                             + changes.size() + " change(s)";
        return Optional.of(new UserActionRecord(id, APPLY_DASHBOARD_CHANGE, label,
                NullSafe.isBlankString(summary)
                        ? null
                        : truncate(summary, MAX_DETAILS_CHARS),
                null, null, dashboard, null, List.of(), expiresMs, Status.PENDING, null, null, null,
                new UserActionRecord.DashboardChange(component, componentId, snapshotHash.toLowerCase(Locale.ROOT),
                        lookup.canEdit(dashboard),
                        changes.stream().map(DashboardChanges.Change::toMap).toList(),
                        DashboardChanges.describe(changes))));
    }

    private static Integer count(final Object value) {
        if (value instanceof final Integer i && i >= 0) {
            return i;
        }
        if (value instanceof final Long l && l >= 0 && l <= Integer.MAX_VALUE) {
            return l.intValue();
        }
        return null;
    }

    private static boolean hasControlCharacters(final String text) {
        return text.chars().anyMatch(Character::isISOControl);
    }

    private Optional<UserActionRecord> elasticsearchRequest(final String id,
                                                            final Map<?, ?> map,
                                                            final Set<DocRef> pipelines,
                                                            final long expiresMs) {
        final String request = string(map.get("request"));
        // The pipeline the request is for, if it says, as well as any another action names.
        final Set<DocRef> candidates = new HashSet<>(pipelines);
        final DocRef doc = docRef(map.get("doc"));
        if (doc != null && PipelineDoc.TYPE.equals(doc.getType())) {
            candidates.add(doc);
        }
        final String problem = elasticsearchProblem(request, candidates);
        if (problem != null) {
            // Its first line, cut short: it is the server's text.
            LOGGER.info(() -> LogUtil.message("Not showing an Elasticsearch request from an MCP tool, as {} "
                                              + "Request: {}", problem, LogUtil.truncate(NullSafe.string(request)
                                                      .lines().findFirst().orElse(""), MAX_LOGGED_CHARS)));
            return Optional.empty();
        }
        return Optional.of(new UserActionRecord(id, ELASTICSEARCH_REQUEST,
                "Elasticsearch request, for the cluster admin to run", null, null, null, null, request.strip(),
                List.of(), expiresMs, Status.PENDING, null));
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
            // A template is named after the index it is for: up to its first variable, if it has any, e.g.
            // "ecs-acme-v1-{year}" names templates starting "ecs-acme-v1-".
            final String template = matcher.group(1);
            return indexes.stream()
                           .map(UserActions::fixedPart)
                           .anyMatch(index -> !index.isEmpty() && template.startsWith(index))
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
            final String name = matcher.group(1);
            return indexes.stream().anyMatch(index -> namesIndex(index, name))
                    ? null
                    : "it does not name an index of the pipelines here.";
        }
        return "it is not one of the requests Stroom allows (PUT _index_template/<name>, "
               + "DELETE _data_stream/<index> or DELETE <index>, with no wildcards or lists).";
    }

    /// @param indexName An index name a pipeline writes to, which may hold variables the pipeline fills in from
    ///                  each document, e.g. `stroom-ecs-twitter-v1-{_suffixName}`.
    /// @param name      The name of an index, as a request gives it.
    /// @return True if the pipeline may write to the index: the name is the index name, or one it may make, with
    /// each variable filled in. An index name that starts with a variable could make almost any name, so only
    /// matches itself.
    static boolean namesIndex(final String indexName, final String name) {
        if (indexName.equals(name)) {
            return true;
        }
        if (fixedPart(indexName).isEmpty() || indexName.indexOf('{') < 0) {
            return false;
        }
        final StringBuilder regex = new StringBuilder();
        int from = 0;
        while (from < indexName.length()) {
            final int open = indexName.indexOf('{', from);
            final int close = open < 0
                    ? -1
                    : indexName.indexOf('}', open);
            if (open < 0 || close < 0) {
                regex.append(Pattern.quote(indexName.substring(from)));
                break;
            }
            regex.append(Pattern.quote(indexName.substring(from, open))).append(VARIABLE_VALUE);
            from = close + 1;
        }
        return name.matches(regex.toString());
    }

    // The start of an index name, before any variable in it.
    private static String fixedPart(final String indexName) {
        final int variable = indexName.indexOf('{');
        return variable < 0
                ? indexName
                : indexName.substring(0, variable);
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
